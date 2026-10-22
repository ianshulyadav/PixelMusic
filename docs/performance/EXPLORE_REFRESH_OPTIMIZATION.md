# ThePixelMusic — Final Explore Refresh & Smooth-Scroll Optimisation

## Status

This document describes the required implementation for the Explore refresh change requested on 22 October 2026. It is grounded in a static inspection of `main` at commit `6eb69391cdad4fac6db990a79ab78b7abfc4d27c`.

**Important:** this is a design/review specification, not proof that a candidate build has passed. Do not merge a Kotlin implementation until it builds, passes the regression tests below, and is measured on a physical device.

## Exact product contract

### Online Explore entry and pull-to-refresh

1. The online Explore screen enters a blocking full-screen loading state immediately. Do not render cached/old online shelves, old mood chips, old Explore releases/albums, or partial new shelves during this transition.
2. The same coordinator owns all online Explore entry refresh triggers. Lifecycle callbacks, first composition, retry, preference changes, and pull-to-refresh must not start independent duplicate Explore Home requests.
3. Coalesce equivalent in-flight refreshes. If a newer refresh supersedes an older request, invalidate/cancel the older job; only the newest valid generation may publish.
4. Fetch the required source payloads. A primary `YouTube.home()` response with non-empty valid sections is mandatory for a successful online Explore replacement. Ancillary feeds may have explicitly documented independent fallback semantics; they must not cause a half-published Explore Home response.
5. Parse, filter, normalize, deduplicate, order and map the full final Explore dataset away from the main thread where appropriate. Validate that the prepared result has usable content and coherent section/chip/continuation metadata.
6. Publish one coherent `ExploreUiState` update for the Explore dataset. The content, mood chips, releases/albums and continuation token must correspond to the same successful generation.
7. Replace the entire online Explore dataset; do not append refreshed shelves to old shelves. Pagination is a separate operation and can merge only continuation data belonging to the current generation.
8. Load artwork asynchronously after the new data has been published; waiting for every artwork bitmap is not part of response validation.
9. If online refresh fails or returns an invalid/empty mandatory payload, show a clear error/retry state. Do not silently fall back to old online Explore data and present it as fresh.
10. Local/offline mode is a separate source of truth: query local/downloaded data and show it; do not require an online request.

### Re-entry semantics

Opening or returning to online Explore triggers a refresh even when the cache is recent. On every re-entry, the visible sequence is:

`full-screen loader → fetch → validate/prepare → atomic replacement → render new data`

This deliberately trades stale-while-revalidate speed for correctness/freshness, per the explicit user choice. A slow connection will therefore keep the loader visible longer. Consider a timeout and retry message, but do not surface old online content during the wait.

## Findings in current `main`

- `ExploreViewModel.init` restores `explore_cache.json`, may publish its sections/chips, and conditionally fetches based on a multi-tier TTL (30 min / 4 h / 24 h).
- `ExploreScreen` also calls `loadData()` from a lifecycle `ON_START` callback; pull-to-refresh additionally starts Explore, Quick Picks and Feed jobs together.
- `loadData()` has a 30-minute skip path, which conflicts with “refresh every time Explore opens”.
- `loadDataInternal` currently keeps cached content visible during refresh and uses separate StateFlows plus `_uiState`, so consumers can see inconsistent transition frames.
- On failed Home request, cache fallback is considered only when current sections are empty. That can leave old sections visible after a failed refresh.
- `loadMore()` merges sections using title-based deduplication, which is inappropriate for a full refresh but may still be the pagination contract after reviewed merge semantics.
- Feed and Quick Picks can request `YouTube.home()` independently; refresh coordination must avoid duplicate calls only when endpoint, auth/account, preferences and personalization parameters are provably equivalent.
- Explore uses many state sources and computed collections in one ~4k-line composable. Existing stable keys are a good base, but any expensive filtering/mapping/deduplication must be pulled out of composables and computed once per input generation.

## Implementation requirements by layer

### 1. ViewModel / request coordinator

- Introduce one serialized refresh coordinator (`Job` + generation/token, or a small dedicated coordinator) for the default online Explore dataset.
- `refreshOnEntry()` always forces a refresh; calls made while equivalent work is active share that work instead of duplicating it. Do not gate it on `lastFetchTimestamp`.
- Cancel old work explicitly and rethrow `CancellationException`. Check the generation immediately before commit.
- Remove network fetch from `init`; init may perform only preference/local observers. Do not let cache restoration publish online data before fresh validation.
- Keep offline/local load independent; when offline or local mode is selected, publish local data with the correct state.
- Make full refresh and pagination separate commands. Pagination must be disabled while refresh is running, must use the latest continuation token, and must not commit if the refresh generation changed.
- Use one immutable `ExploreUiState` snapshot as the single source of truth. Fine-grained flows, if retained for backward compatibility, should be derived from that state, not updated independently before `_uiState`.
- Ensure preference/account changes supersede requests prepared with obsolete settings; key coalescing by account/auth state, personalization settings, filters, and endpoint parameters.
- Any stage-2/ancillary operation must be cancelled/invalidated by a newer generation.

### 2. Fetch, processing and commit

- Use structured concurrency. Network I/O belongs on the appropriate I/O dispatcher; CPU-heavy mapping, sorting, deduplication and ranking belongs on `Dispatchers.Default`.
- Treat successful empty/malformed primary data as a failed online refresh. Define whether ancillary endpoints can degrade independently; never silently present old Home data as current.
- Build a complete candidate snapshot in local variables before mutating observable state.
- Validate: mandatory sections are non-empty; section titles/identities are valid; mood-chip metadata and continuation are from the same Home response; section items are supported and non-empty after filtering; duplicates use stable IDs rather than title alone.
- Commit exactly once via `_uiState.value = preparedState` or `_uiState.update { ... }`, with the new complete dataset and `isLoading=false`, `isRefreshing=false`, `isSettled=true`, `error=null` in that same update.
- Write cache atomically via temporary file + rename only after the successful in-memory commit or as part of the success pipeline. Never persist a partial initial page as if it were the complete dataset. Decide deliberately whether the continuation token belongs in cache; existing schema supports it.
- Do not mutate state inside a `withContext(Dispatchers.IO)` block if this would notify UI state from a background dispatcher. Return prepared data to the ViewModel coroutine and publish on its normal context.
- Catch ordinary failures to expose error state, but rethrow cancellation and prevent cancelled jobs from clearing flags belonging to a newer generation.

### 3. Compose screen transition

- During online initial entry/re-entry/manual refresh, show the loader based directly on the current refresh state. Do not require `!hasExploreContent` to show it.
- Keep the top bar and global player/navigation shell responsive; the body content area is the full-screen loader.
- Do not create the first `LazyColumn` until a validated complete Explore snapshot is ready.
- On error, render an error/retry state and no old online shelves. Retry invokes the same coordinator.
- When switching from local mode back to online mode, show the loader before the online request; never show local sections under online labels.
- Mood-chip selection has its own generation/token and atomic section replacement; selecting a mood must not allow a previous mood’s response to overwrite the newest selection.
- Pull-to-refresh currently refreshes three independent VMs. Keep the Explore body blocked according to the Explore contract, but ensure ancillary Feed / Quick Picks errors do not corrupt or mark the Explore Home generation as successful. Refactor shared requests only after equivalence is verified.

### 4. Scroll and CPU / allocation control

- Keep stable section IDs and stable item IDs. Prefer video/song/browse IDs; do not use an array index or mutable title alone where a canonical stable ID exists. Ensure uniqueness when a feed can contain the same entity in multiple conceptual sections.
- Supply `contentType` for repeated LazyColumn/LazyRow item shapes and preserve stable `LazyListState` ownership.
- Do not sort, map, filter, or `distinctBy` large collections in item composables. Prepare display collections in the ViewModel/use `remember` with complete, narrow keys.
- Avoid repeatedly flattening all sections to discover Quick Picks or artists on each unrelated state emission. Derive/calculate those lists once per Explore data generation.
- Avoid unbounded nested eager work; preserve lazy rows/grids and cap data work to visible/needed items where appropriate.
- Avoid animating every card during scroll. Keep expressive transitions for screen-level state changes; disable or shorten per-item crossfades/shimmers in dense scrolling content based on benchmark evidence.
- `SmartImage` must request images close to display size, use Coil memory/disk cache keys, and avoid repeated palette/blur extraction on recomposition. Do not globally downgrade art resolution without measurements.
- Keep infinite shimmer animation active only during loading. Use placeholder without infinite animation for offscreen/settled cards.
- Ensure image/decode, palette extraction, database calls, JSON parsing, and heavy list processing never block main. Verify with Perfetto; dispatcher labels alone do not prove non-blocking if a library internally blocks.

### 5. Feed / Quick Picks request de-duplication

- Measure actual Home request count on one Explore entry and one pull refresh.
- If calls are semantically identical for the same session/auth/preferences/cache key, put a keyed single-flight/cache layer at the network/repository boundary so consumers await the same in-flight request.
- Do not share a result across accounts, cookies, locale, personalization settings, filters, or continuation/params. `YouTube.home()`, `YouTube.home(params=...)`, and continuation calls are not interchangeable.
- Use concurrency limits for expensive discovery searches, and honour cancellation.

### 6. Cache policy

- Online Explore cache is storage only under this selected policy; it must not be restored to visible online state on entry.
- Validate JSON/version/timestamp and required fields when reading it for offline diagnostics or future cache strategies.
- Write atomically, bound file size, avoid truncating sections silently, and never interpret a partial cache write as a complete valid response.
- Offline/local Explore may display local/downloaded records immediately because its data source is local and does not pretend to be current online results.

## Required tests

### Unit / coroutine tests

1. `loadData(forceRefresh=false)` / entry refresh requests fresh Home even if cache timestamp is current.
2. No cache restoration makes old online sections visible before response.
3. Double entry/lifecycle events coalesce; verify one equivalent Home request.
4. Forced refresh cancels or invalidates an older request; older late response cannot commit.
5. Cancellation rethrows and does not set loading/error state after a newer generation owns the state.
6. Valid Home response updates sections, mood chips, releases/albums, continuation, timestamp in one state publication.
7. Empty/malformed/failed Home response clears visible online content and displays error/retry rather than old cache.
8. Slow response: body stays loader; no section item can be rendered early.
9. Pagination merges only continuation data into current generation; it cannot replace or resurrect old data after refresh.
10. Mood A response arriving after Mood B cannot overwrite Mood B.
11. Account/personalization/filter change invalidates the previous keyed result.
12. Offline/local mode bypasses network and displays only local/offline-compatible content.
13. Cache writes are atomic and cannot be mistaken for a fresh network result after crash/truncation.

### Instrumented / macrobenchmark scenarios

- cold Explore entry, warm ViewModel re-entry, repeated ON_START, repeated taps/navigation, slow network, offline launch, connection loss mid-refresh, authenticated vs signed-out accounts, personalized setting changes, pull-to-refresh, mood-switch rapid tapping, pagination, scroll through at least 15 shelves, return from details/sheets, 5k–6k-song local library, low-memory pressure.
- Macrobenchmark metrics: `FrameTimingMetric`, frame overrun/jank, `StartupTimingMetric` where applicable; capture CPU/GC/allocation/trace evidence with Perfetto.
- Profile an actual mid-range device at its normal refresh rate. Report measured p50/p95/p99 frame duration and janky-frame percentage before/after.

## Acceptance gates

- **Freshness:** zero old online Explore section displayed between transition start and successful commit.
- **Coherence:** one candidate generation produces one complete, coherent UI state; no partial section/chip mismatch.
- **Concurrency:** at most one active equivalent default Home request per key, and zero stale generations commit.
- **Failure:** online failure never relabels stale cache as fresh; error/retry works.
- **Scroll:** no heavy transformation on the main thread; target frame budgets are 16.7 ms at 60 Hz, 11.1 ms at 90 Hz and 8.3 ms at 120 Hz. Report achieved jank, do not promise absolute zero freezes.
- **Safety:** tests pass; local debug/release builds pass; no playback/auth/local-library regressions; verify no accidental audio interruption.

## Recommended delivery sequence

1. Implement coordinator + generation-safe atomic state + online loader contract.
2. Add coroutine tests for stale/cache/overlap/error/offline/pagination/mood races.
3. Remove duplicate `YouTube.home()` calls by a key-aware single-flight layer only after equivalence tests.
4. Add scroll/refresh macrobenchmarks and a baseline trace.
5. Optimise only measured Compose/image/collection hotspots.
6. Run full Gradle tests/build, install on device, verify user paths, compare benchmark traces, then merge.

## Source references (main at audit time)

- `ExploreViewModel.kt`: cache-first init (`restoreFromCache`), multi-tier TTL, `loadData`, `loadDataInternal`, `loadMore`, mood-chip loading, offline loading, cache writes.
- `ExploreScreen.kt`: `ON_START`, pull-to-refresh launching Explore + Quick Picks + Feed, `showExploreLoading`, LazyColumn and derived collection logic.
- `FeedViewModel.kt` / `FeedRepository.kt`: separate refresh owner and another `YouTube.home()` call.
- `QuickPicksViewModel.kt`: separate refresh owner and Home fetch paths.
- `SmartImage.kt` / `ExploreMotion.kt` / `ExploreSections.kt`: image crossfades, shimmer, nested lazy rows, item composition.
- `performance_analysis.md`: existing detailed UI performance audit.
- `StartupBenchmarks.kt`: current startup benchmark profile; extend for Explore scroll and refresh.
