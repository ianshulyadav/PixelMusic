# Third-Party Notices

Pixel Music is licensed under the **GNU General Public License v3.0 (GPL-3.0-or-later)**.

This repository publishes compiled release APKs of Pixel Music. The application includes third-party libraries, native binaries, and font assets. This file records source and license evidence for upstream compliance.

---

## 🏛️ Upstream Project

Pixel Music is derived from and builds upon the open-source **PixelPlayerOSS** project and foundational PixelPlayer codebase:

- **Upstream Repository:** [https://github.com/PixelPlayerHQ/PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS)
- **Upstream License:** GNU General Public License v3.0 (`GPL-3.0-or-later`)
- **Foundational Base Repository:** [https://github.com/theovilardo/PixelPlayer](https://github.com/theovilardo/PixelPlayer)
- **Foundational MIT Base Commit:** `39030156fb6999b23f69076ae135e55832bb6d81`

The original MIT copyright notice from the foundational base is preserved below:

```text
MIT License

Copyright (c) 2024 Theo Vilardo / PixelPlayer Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 🔤 Bundled Font Assets

| Asset | Source / Specimen | License |
| --- | --- | --- |
| `Google Sans Flex` (`gflex_variable.ttf`) | [Google Fonts](https://fonts.google.com/specimen/Google+Sans+Flex) | SIL Open Font License 1.1 |
| `Roboto Flex` (`genre_variable.ttf`) | [Google Fonts Roboto Flex](https://github.com/googlefonts/roboto-flex) | SIL Open Font License 1.1 |

---

## ⚙️ Native / Binary Runtime Artifacts

| Coordinate | APK Native Library | Source / Upstream | License Evidence |
| --- | --- | --- | --- |
| `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` | `libffmpegJNI.so` | [Jellyfin AndroidX Media](https://github.com/jellyfin/jellyfin-androidx-media) | POM declares GPL-3.0 |
| `io.github.kyant0:taglib:1.0.6` | `libtaglib.so` | [Kyant0 TagLib](https://github.com/Kyant0/taglib) | POM declares Apache-2.0 |
| `androidx.graphics:graphics-shapes:1.1.0` | `libandroidx.graphics.path.so` | [AOSP Frameworks Support](https://android.googlesource.com/platform/frameworks/support) | Google Maven POM declares Apache-2.0 |
| `com.github.racra:smooth-corner-rect-android-compose:v1.0.0` | N/A (Compose Canvas) | [Racra Smooth Corner](https://github.com/racra/smooth-corner-rect-android-compose) | Upstream declares MIT |
| `com.github.philburk:jsyn:3f6b44b853bccc0d2e3027104d575fcc5ccb6d4e` | Transitive JSyn audio | [Phil Burk JSyn](https://github.com/philburk/jsyn) | POM declares Apache-2.0 |

The Jellyfin FFmpeg decoder bundled in distributed APKs is GPL-3.0, fully aligned with the Pixel Music license (**GNU GPL v3.0**).

---

## 📦 Direct Dependency Families & Licenses

| Dependency Family | Components | License |
| --- | --- | --- |
| **AndroidX Core & Platform** | Core KTX, AppCompat, Activity Compose, ProfileInstaller, Media, Security Crypto, WorkManager | Apache-2.0 |
| **AndroidX Jetpack Compose** | Compose BOM, UI, Tooling, Foundation, Animation, Material 3, Icons Extended, Glance Widgets | Apache-2.0 |
| **AndroidX Architecture & Data** | Lifecycle (ViewModel, Runtime Compose), Navigation Compose, Paging 3, Room DB | Apache-2.0 |
| **Media Playback Engine** | AndroidX Media3 (ExoPlayer, Session, UI, MIDI, Transformer), Jellyfin FFmpeg Decoder | Apache-2.0 / GPL-3.0 |
| **Dependency Injection** | Google Dagger / Hilt, Hilt Navigation Compose, Hilt Work | Apache-2.0 |
| **Kotlin & Concurrency** | Kotlin Standard Library, KotlinX Coroutines, KotlinX Serialization, Immutable Collections | Apache-2.0 |
| **Networking & HTTP** | Square Retrofit, Gson Converter, OkHttp, OkHttp Logging Interceptor, Ktor Client Core & CIO | Apache-2.0 |
| **Media Metadata & Tagging** | Kyant0 TagLib, JAudioTagger, Vorbis Java | Apache-2.0 / LGPL / BSD |
| **Image Loading & UI Utilities** | Coil Compose, Accompanist, Capturable, CodeView, Reorderable List, Wavy Slider | Apache-2.0 / MIT |
| **Search & Tokenization** | Kuromoji IPADIC (Japanese tokenization), Pinyin4j (Chinese romanization) | Apache-2.0 / BSD |
| **Security & Cryptography** | Bouncy Castle Cryptography Provider, Apache Commons Lang, JDOM2, Jose4j | Permissive / Apache-2.0 |
| **Testing Frameworks** | JUnit 4/5, AndroidX Test, MockK, Turbine, Truth, Room Test, Coroutines Test | Apache-2.0 / MIT / EPL (Test only) |

All third-party components remain subject to their respective upstream licenses. Nothing in Pixel Music alters or diminishes the rights granted under those licenses. For the full F-Droid dependency review breakdown, refer to [docs/DEPENDENCY_LICENSES.md](docs/DEPENDENCY_LICENSES.md).
