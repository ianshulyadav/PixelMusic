# Dependency License Review

This is a review aid for F-Droid/app-store submission. It is not legal advice. Re-run it whenever `gradle/libs.versions.toml`, `app/build.gradle.kts`, or Android Gradle Plugin versions change.

## Regenerate The Runtime Graph

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:dependencies --configuration releaseRuntimeClasspath > /tmp/opencode/pixelplayer-releaseRuntimeClasspath.txt
rg -o "[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+:-]+" /tmp/opencode/pixelplayer-releaseRuntimeClasspath.txt | sort -u | wc -l
```

Last local review result: 631 unique runtime coordinates in `releaseRuntimeClasspath`.

## Exact Review Items

These are the dependencies most likely to trigger manual review because they ship native code, come from JitPack, or have stronger copyleft obligations.

| Coordinate | Why reviewed | License/source evidence |
| --- | --- | --- |
| `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` | Ships `libffmpegJNI.so`; GPL component in APK | POM declares GPL-3.0; source `https://github.com/jellyfin/jellyfin-androidx-media` |
| `io.github.kyant0:taglib:1.0.6` | Ships `libtaglib.so` | POM declares Apache-2.0; source `https://github.com/Kyant0/taglib` |
| `androidx.graphics:graphics-shapes:1.1.0` | Ships `libandroidx.graphics.path.so` through `graphics-shapes-android` | POM declares Apache-2.0; source `https://android.googlesource.com/platform/frameworks/support` |
| `com.github.racra:smooth-corner-rect-android-compose:v1.0.0` | Direct JitPack dependency | POM/upstream declare MIT; source `https://github.com/racra/smooth-corner-rect-android-compose` |
| `com.github.philburk:jsyn:3f6b44b853bccc0d2e3027104d575fcc5ccb6d4e` | Transitive JitPack dependency of `androidx.media3:media3-exoplayer-midi` | POM declares Apache-2.0; source `https://github.com/philburk/jsyn` |

## Direct Dependency Families

| Family | Direct dependencies in this project | License/source expectation |
| --- | --- | --- |
| AndroidX app/platform | `androidx.core`, `androidx.appcompat`, `androidx.activity`, `androidx.profileinstaller`, `androidx.media`, `androidx.security`, `androidx.work` | Apache-2.0, AndroidX/AOSP source |
| AndroidX Compose/UI | Compose BOM, UI, UI graphics/tooling, Foundation, Animation, Material icons, Material3, ConstraintLayout Compose, Glance | Apache-2.0, AndroidX source |
| AndroidX architecture/data | Lifecycle, Navigation, Paging, Room, Hilt integration, Benchmark/profile modules | Apache-2.0, AndroidX source |
| Media playback | AndroidX Media3 ExoPlayer/session/UI/MIDI/Transformer, Jellyfin FFmpeg decoder | Apache-2.0 for AndroidX; Jellyfin FFmpeg decoder GPL-3.0 |
| Dependency injection | Dagger/Hilt and compilers | Apache-2.0 |
| Kotlin/KSP/serialization | Kotlin stdlib/test, coroutines, serialization, immutable collections, KSP | Apache-2.0 |
| Networking | Retrofit, Gson converter, OkHttp, logging interceptor, Gson | Apache-2.0 |
| Ktor/server transitive stack | Ktor CIO/core, Netty constraints | Apache-2.0 |
| Media metadata/audio parsing | TagLib, JAudioTagger, Vorbis Java | TagLib Apache-2.0; verify JAudioTagger/Vorbis Java POMs when versions change |
| UI utilities | Coil, Accompanist, Capturable, CodeView, Reorderables, Wavy Slider, smooth-corner JitPack | Apache-2.0/MIT-compatible; smooth-corner is MIT |
| Text/search utilities | Kuromoji IPADIC, pinyin4j | Apache-2.0/BSD-style; verify POMs when versions change |
| Security and compatibility constraints | Bouncy Castle, Commons Lang, JDOM2, jose4j, Apache HttpClient | Permissive library licenses; verify POMs when versions change |
| Testing | JUnit 4/5, AndroidX test, MockK, Turbine, Truth, Room testing, coroutines test, org.json | EPL/Apache-2.0/MIT/permissive; test-only and not shipped in release APK |

## Current JitPack Allowlist

`settings.gradle.kts` allows only:

| Group | Required by |
| --- | --- |
| `com.github.racra` | Direct smooth corner Compose dependency |
| `com.github.philburk` | Transitive JSyn dependency from Media3 MIDI |

No `FaceOnLive` JitPack dependency is currently allowed.
