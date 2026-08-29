# APKUpdater

APKUpdater finds and installs updates for apps already installed on Android devices. It aggregates APKMirror, Aptoide, F-Droid, IzzyOnDroid, APKPure, GitLab, GitHub, and Google Play instead of depending on a single store.

This project is a modified fork of [rumboalla/APKUpdater](https://github.com/rumboalla/apkupdater) with additional features. The changes in this fork were made in 2026. This branch uses one TV-style, D-pad-friendly layout on phones, tablets, Android TV, and Google TV. The separate phone UI and UI mode switches are intentionally not included.

## Features

- A single TV-style interface built with Jetpack Compose and Material 3.
- Update and search results from APKMirror, Aptoide, F-Droid, IzzyOnDroid, APKPure, GitLab, GitHub, and Google Play.
- Direct APK, APKM, APKS, XAPK, and split-package installation where supported.
- A **Source** button that opens the provider page for each result.
- Background update checks and notifications.
- Grouped updates with a source selector.
- Alpha, beta, pre-release, architecture, signature, and Android TV compatibility filters.
- Optional root installation and unattended installation on supported Android versions.
- Dark, light, and system themes.
- No ads and no tracking.

## Requirements

- Android 6.0 (API 23) or newer.
- Permission to install unknown apps for direct installation.

## Download

- [APKUpdater 3.1.6](https://github.com/guberm/apkupdater/releases/download/3.1.6/com.apkupdater-release.apk)

## Build

```bash
./gradlew test lint assembleDebug
```

Release builds require signing values from `APKUPDATER_*` environment variables, `local.properties`, or `~/.android/apkupdater-signing.properties`. The build fails when release signing is not configured.

## License

Copyright © 2016–2024 rumboalla.

Licensed under the [GNU General Public License v3](LICENSE).
