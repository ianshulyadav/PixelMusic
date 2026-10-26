# Privacy Policy for PixelMusic

Last Updated: August 2026

## Introduction

PixelMusic is an independent Android music player application designed for music streaming, local audio playback, and media sharing features. This Privacy Policy explains how PixelMusic handles user information, permissions, and third-party integrations.

By using PixelMusic, you agree to the practices described in this Privacy Policy.

---

## Information We Collect

PixelMusic is designed with a privacy-first approach. We do not sell personal data, profile users for advertisement networks, or collect unnecessary personal information.

### Information Collected & Processed Locally

The application may locally process:

* **Device & Hardware Information:** Audio format compatibility, display metrics, and audio hardware capabilities.
* **Audio Metadata:** Track titles, artist names, album art, durations, and folder structures for local music organization.
* **Cache Data:** Temporary storage for audio buffers, lyrics, and Deezer/YouTube artwork thumbnails to facilitate fast, offline playback.
* **Diagnostics & Crash Logs:** Anonymized logs generated locally to assist in troubleshooting playback stability.

All local library data is processed directly on your device.

---

## Device Permissions & Usage

PixelMusic requests only the permissions necessary to deliver core playback and media management functionality:

* **Music and Audio / Storage:** To discover, index, and play local audio files and display embedded album art.
* **Internet Access:** To stream audio from user-authorized sources, query LRCLIB for synchronized lyrics, and fetch high-resolution artwork.
* **Foreground Service & Media Playback:** To ensure uninterrupted background playback and seamless notification/lockscreen controls.
* **Bluetooth / Connectivity:** To route audio output to wireless headphones, Bluetooth speakers, Android Auto, and Chromecast targets.

PixelMusic does not upload your local media files or library indexes to any external servers without explicit user initiation.

---

## Third-Party Services & Integrations

PixelMusic provides optional integrations with third-party platforms to enhance your music experience:

### 1. YouTube Music
* User authentication occurs securely through standard web sessions on-device.
* Authentication tokens and cookies remain stored in encrypted local storage.
* Used solely to retrieve user playlists, liked tracks, and audio streams.

### 2. Telegram Integration
* Connects via Telegram client protocols to stream audio from channels, chats, and saved messages.
* Credentials and session strings are saved securely on-device and never transmitted to third parties.

### 3. Snapchat Creative Kit
* Allows users to share custom song and lyric cards directly to Snapchat stories.
* Cards are composed locally on-device. PixelMusic never accesses private Snapchat messages, passwords, or contacts.

### 4. Lyrics & Metadata Providers (LRCLIB, Deezer, Last.fm, ListenBrainz)
* Public APIs are queried solely for lyrics fetching, scrobbling listening history (if enabled), and high-resolution artist artwork retrieval.

Third-party services are subject to their respective terms of service and privacy policies. PixelMusic is not affiliated with any third-party service provider.

---

## Data Storage & Retention

PixelMusic stores all user configuration, playback history, cached lyrics, and playlists locally on-device via Room SQLite databases and encrypted preferences.

* Users can clear cached lyrics, images, or reset app databases at any time in the app settings.
* Uninstalling the application completely removes all locally stored app data.

---

## Data Sharing & Third-Party Disclosure

PixelMusic does **not** sell, rent, or trade user data. We do not integrate third-party ad networks, trackers, or commercial telemetry SDKs.

Information is transmitted externally only when:
* Directly requested by you (e.g., streaming a track, querying lyrics, sharing a song card).
* Required to communicate with user-configured third-party service APIs.

---

## Security

PixelMusic incorporates modern security standards to protect your local data, including scoped storage compliance and secure sandbox boundaries. However, users are encouraged to maintain proper device-level security (PINs, biometric locks) to protect on-device data.

---

## Children's Privacy

PixelMusic does not knowingly collect or solicit personal information from children under the age of 13. If you believe any unauthorized data has been collected, please contact us.

---

## Open Source & Transparency

PixelMusic is developed transparently for the community. Documentation, security policies, and legal notices are accessible across official project channels.

---

## Changes to This Policy

We may occasionally update this Privacy Policy to reflect app updates and regulatory requirements. Revisions will be published with an updated "Last Updated" date.

---

## Contact

For questions, feedback, or privacy inquiries regarding PixelMusic:

* **Telegram Channel:** [https://t.me/PixelMusicApp](https://t.me/PixelMusicApp)
* **GitHub Repository:** [https://github.com/ianshulyadav/PixelMusicApp](https://github.com/ianshulyadav/PixelMusicApp)
