# PixelMusic Architecture & Workspace Organization Guide

This document outlines the organized architecture of the PixelMusic repository across its three primary layers: **Android App Architecture**, **Web & Landing Infrastructure**, and **Repository / Tooling Structure**.

---

## 1. High-Level Repository Layout

```
PixelMusicIO/
├── app/                        # Native Android Application (Kotlin, Jetpack Compose, Media3)
├── assets/                     # Media, design banners, screenshots & store graphics
│   ├── readme/                 # Light/dark responsive SVG headers & badges for README.md
│   ├── site/                   # Web-optimized assets and screenshots for GitHub Pages
│   └── store/                  # Store graphics, hero icons, and promotional assets
├── docs/                       # Project documentation & release guidelines
│   ├── ARCHITECTURE.md         # Full repository & architecture guide
│   ├── DEPENDENCY_LICENSES.md  # Open-source third-party dependencies
│   ├── DISCLAIMER.md           # Legal disclaimers
│   ├── PRIVACY_POLICY.md       # Privacy policy
│   ├── PROVENANCE.md           # Upstream fork provenance
│   ├── RELEASE.md              # Build & signing instructions
│   ├── SECURITY.md             # Security policy
│   └── THIRD_PARTY_NOTICES.md  # Third-party notices
├── gradle/                     # Gradle wrapper definitions
├── tools/                      # Automation tooling (stats updater, telegram counter)
│   └── stats/                  # Daily stats updater engine
├── .github/                    # GitHub configuration & workflows
│   ├── workflows/update-stats.yml # Daily cron job for release/community numbers
│   ├── CONTRIBUTING.md         # Contributor guidelines
│   └── FUNDING.yml             # Ko-fi & custom UPI donation links
├── index.html                  # Root landing page for GitHub Pages
├── robots.txt & sitemap.xml    # SEO optimization for Web search engines
└── stats.json                  # Real-time cached release and Telegram community stats
```

---

## 2. Android Architecture (`/app`)

The Android application is structured according to **Modern Android Architecture (MVI / MVVM with Jetpack Compose & Hilt)**:

### Package Organization: `com.unshoo.pixelmusic`

| Layer / Package | Responsibilities | Key Components |
|---|---|---|
| **`data/`** | Data persistence, network APIs, caching, hardware, and system integrations | `repository/`, `database/` (Room), `service/` (MediaSessionService), `network/` (Deezer, Jellyfin, Navidrome, LRCLIB), `backup/`, `equalizer/` |
| **`presentation/`** | UI state holders, Compose navigation, and screen implementations | `viewmodel/`, `screens/` (Home, Player, Albums, Explore, Lyrics, etc.), `components/` |
| **`ui/`** | Theming, design tokens, typography, and Glance app widgets | `theme/` (Material 3 Expressive, dynamic color schemes), `glancewidget/` |
| **`di/`** | Dependency injection bindings using Hilt / Dagger | Modules providing Repositories, Database DAOs, Network clients, and Media services |
| **`utils/`** | Cross-cutting helpers and formatting logic | Math, string extensions, shape geometry, and system helpers |

### Media Playback Pipeline (`androidx.media3`)
- `data/service/player`: Foreground `MediaLibraryService` handling background audio, lockscreen notifications, audio focus, and hardware output.
- `presentation/viewmodel/PlayerViewModel`: Interacts with the `MediaController` and exposes immutable Compose UI state.

---

## 3. Web & GitHub Pages Architecture

The web presence is fully static and served directly from the root repository branch (`main` / `root`):

1. **`index.html`**:
   - Zero external CSS/JS framework dependencies for sub-second load times.
   - Built-in Material 3 Expressive tokens and responsive mobile-first layout.
   - Micro-interactions: One-click UPI copy (`anshulyadavv@fam`), live stats loader via `stats.json`, dynamic dark/light theme support.
   - Rich Rich-Snippet JSON-LD metadata for Google search indexing.

2. **Automated Stats Engine (`tools/stats/`)**:
   - `.github/workflows/update-stats.yml` triggers daily at 03:17 UTC or manually via GitHub Actions.
   - Automatically polls the GitHub API for releases and Telegram public subscriber counts.
   - Generates updated `stats.json` and updates SVG badges without human intervention.
