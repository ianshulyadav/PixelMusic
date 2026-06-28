# Security Policy

This document explains how official Pixel Music APKs are distributed, how users can verify releases, and how to report security, copyright, licensing, impersonation, or abuse concerns.

## Official Distribution Channels

Official Pixel Music APKs are distributed only through the following official channels:

1. the official Pixel Music GitHub Releases page; and
2. the official Pixel Music Telegram channel, when linked from this repository or the project's official documentation.

Official Telegram channel:

```text
https://t.me/PixelMusicApp
```

Official GitHub Releases page:

```text
https://github.com/ianshulyadav/PixelMusic-Releases/releases
```

Users should not trust APKs downloaded from unofficial mirrors, third-party APK websites, reposted Telegram channels, forwarded Telegram messages, modified builds, re-signed packages, renamed APKs, or unknown sources.

APK files shared outside the official GitHub Releases page or the official Pixel Music Telegram channel should be treated as unofficial and potentially unsafe.

Unofficial copies may be modified, unsafe, outdated, malicious, misleading, or distributed without permission.

## Telegram Distribution

Pixel Music may publish APKs through the official Pixel Music Telegram channel for user convenience.

Telegram distribution is official only when the Telegram channel is clearly linked from the official project repository, official release repository, or official project documentation.

Each Telegram APK release should include, where practical:

- APK file name;
- version name;
- version code;
- SHA-256 checksum;
- short changelog;
- link to the matching GitHub Release;
- link to license, third-party notice, provenance, disclaimer, and security files.

Users should verify the Telegram APK checksum against the checksum published in the matching GitHub Release.

Forwarded APKs, re-uploaded APKs, renamed APKs, modified APKs, re-signed APKs, or APKs posted in unofficial Telegram channels/groups are not official Pixel Music releases.

If the checksum does not match the official release checksum, do not install the APK.

## APK Authenticity

Before installing Pixel Music, users are encouraged to verify that the APK came from an official release.

Each official release should include:

- APK file name;
- version name;
- version code;
- release notes;
- SHA-256 checksum;
- applicable license and notice files.

Example checksum verification:

```bash
sha256sum PixelMusic-v1.0.0-universal.apk
```

Compare the output with the official `SHA256SUMS.txt` provided in the matching GitHub Release.

If the checksum does not match, do not install the APK.

## No Unauthorized Redistribution

Pixel Music APKs, release files, branding, icons, screenshots, release metadata, and release packaging may not be redistributed, mirrored, re-uploaded, modified, re-signed, repackaged, sold, sublicensed, or published on third-party platforms without written permission from the project owner.

This restriction is intended to:

1. reduce malware and tampered APK distribution;
2. prevent impersonation of official Pixel Music releases;
3. protect users from unsafe modified builds;
4. preserve license, copyright, attribution, provenance, and disclaimer notices;
5. prevent unauthorized commercial, misleading, or infringing distribution.

## Copyright and Licensing Concerns

Pixel Music respects copyright, licensing obligations, third-party notices, and intellectual property rights.

If you believe that a Pixel Music release includes copyrighted material, code, assets, branding, media, trademarks, or other content that should not be distributed, please report it privately to the project maintainer with as much detail as possible.

A useful report should include:

- the affected APK version, file, release, or download link;
- the copyrighted, licensed, trademarked, or restricted material at issue;
- the original source or rights holder, if known;
- the license, restriction, or permission issue that may apply;
- any relevant evidence, URLs, screenshots, file comparisons, timestamps, or notices;
- your contact information for follow-up.

The project maintainer may review the report and, where appropriate, remove, replace, credit, relicense, disable, or otherwise remediate the affected material.

## Security Vulnerabilities

If you discover a security issue in Pixel Music, please report it privately to the project maintainer.

Examples of security issues include:

- leaked API keys or credentials;
- insecure network communication;
- unsafe file handling;
- privilege escalation;
- arbitrary code execution;
- exposed user data;
- insecure account or session handling;
- malicious or tampered release artifacts;
- APK signing or package integrity issues;
- unsafe permissions or unexpected background behavior.

Please include:

- affected version;
- affected APK file name;
- device model;
- Android version;
- steps to reproduce;
- expected result;
- actual result;
- logs, screenshots, or proof of concept if safe to share.

Do not include sensitive personal data in public issues.

## Responsible Disclosure

Please do not publicly disclose security vulnerabilities, copyright concerns, license issues, leaked secrets, impersonation reports, or abuse reports before the maintainer has had a reasonable opportunity to investigate and respond.

Responsible disclosure helps protect users, reduces abuse, and avoids unnecessary harm.

## Prohibited Abuse

Do not use Pixel Music releases, source materials, branding, release assets, APKs, or distribution channels for:

- malware distribution;
- phishing;
- impersonation;
- unauthorized APK re-signing;
- misleading modified builds;
- copyright infringement;
- unauthorized media distribution;
- commercial resale without permission;
- removal of license, attribution, provenance, security, or disclaimer notices;
- publication on third-party APK sites without permission;
- deceptive Telegram reposts or fake official channels;
- false claims of endorsement, affiliation, or official status.

## Third-Party Services and Content

Pixel Music may interact with third-party services, APIs, accounts, or user-authorized sources.

Users are responsible for ensuring that their use of Pixel Music complies with:

- applicable laws;
- copyright rules;
- third-party platform terms;
- account rules;
- API policies;
- content access permissions.

Pixel Music does not grant users rights to any third-party media, service, brand, API, copyrighted content, trademark, or platform-provided material.

## Official Notice Files

Users and distributors should review the following files before using or sharing Pixel Music:

- [`LICENSE`](LICENSE)
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- [`PROVENANCE.md`](PROVENANCE.md)
- [`DISCLAIMER.md`](DISCLAIMER.md)
- [`SECURITY.md`](SECURITY.md)

These files must not be removed, hidden, or altered from official release materials.

## Reporting Unofficial or Tampered APKs

If you find an unofficial, modified, re-signed, re-uploaded, mirrored, renamed, or suspicious Pixel Music APK, please report it to the project maintainer.

A useful report should include:

- download link or Telegram message link;
- channel, group, website, or platform name;
- APK file name;
- SHA-256 checksum, if available;
- screenshots, if relevant;
- why you believe it is unofficial, unsafe, misleading, or unauthorized.

## Contact

To report a security, copyright, licensing, impersonation, or abuse concern, contact the project maintainer privately.

Please include clear details and evidence so the issue can be reviewed efficiently.

If no private contact method is listed, use the official GitHub repository or official Telegram channel to request a private reporting method.
