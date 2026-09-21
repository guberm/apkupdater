# APK Updater

APK Updater finds and installs updates for apps already installed on Android devices. It aggregates APKMirror, Aptoide, F-Droid, IzzyOnDroid, APKPure, APKCombo, Uptodown, GitLab, GitHub, and Google Play instead of depending on a single store.

This project is a modified fork of [rumboalla/APKUpdater](https://github.com/rumboalla/apkupdater) with additional features. The changes in this fork were made in 2026. This branch uses one TV-style, D-pad-friendly layout on phones, tablets, Android TV, and Google TV. The separate phone UI and UI mode switches are intentionally not included.

## Features

- A single TV-style interface built with Jetpack Compose and Material 3.
- Update and search results from APKMirror, Aptoide, F-Droid, IzzyOnDroid, APKPure, APKCombo, Uptodown, GitLab, GitHub, and Google Play.
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

- [Latest APK Updater release](https://github.com/guberm/apkupdater/releases/latest/download/com.guberdev.apkupdater-release.apk)

## Build

### GitHub quota recovery

GitHub's unauthenticated API quota is shared by the public IP address. If a check reaches the quota,
APK Updater pauses GitHub requests until the reported reset time. Refreshing during the pause does not
send more GitHub requests. After reset, the next manual or scheduled refresh continues using saved
successful responses and requests the missing repositories. There is no dedicated wake-up at reset time.

Successful release responses (including repositories with no updates) are saved for 15 minutes.
During quota recovery they can be reused for up to 24 hours, including after an app restart. The current
installed version and release filters are still applied to the saved release data. `Check details` and
exported logs distinguish deferred checks from HTTP failures. This reduces wasted requests; it does not
increase GitHub's quota or add GitHub authentication.

### Local build

```bash
./gradlew test lint assembleDebug
```

Release builds require signing values from `APKUPDATER_*` environment variables, `local.properties`, or `~/.android/apkupdater-signing.properties`. The build fails when release signing is not configured.

