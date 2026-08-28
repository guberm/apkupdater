# APKUpdater TV

APKUpdater TV finds and installs updates for apps already installed on Android TV. It aggregates APKMirror, Aptoide, F-Droid, IzzyOnDroid, APKPure, GitLab, GitHub, and Google Play instead of depending on a single store.

This branch is TV-only. A Leanback-capable Android TV device is required; phone and tablet launchers and layouts are intentionally not included.

## Features

- TV-first, D-pad-friendly interface built with Jetpack Compose and Material 3.
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

- Android TV with Leanback support.
- Android 6.0 (API 23) or newer.
- Permission to install unknown apps for direct installation.

## Download

- [APKUpdater TV 3.1.0](https://github.com/guberm/apkupdater-private/releases/download/3.1.0/com.apkupdater-release.apk)

## Build

```bash
./gradlew test lint assembleDebug
```

Release builds use the signing configuration in `local.properties`; when it is absent, Gradle falls back to the debug key.

## License

Copyright © 2016–2024 rumboalla.

Licensed under the [GNU General Public License v3](LICENSE).
