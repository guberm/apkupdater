# APK Updater 3.1.31 — background downloads and installs

## Fix

Downloads and installs no longer stop when the app goes to the background. The download/install jobs moved from view-model scopes to the application scope, and a new `dataSync` foreground service ("Installing updates" notification) runs while any download or install is active, keeping the process out of the freezable cached state. The service starts with the first job and stops itself when the queue drains.

Install bookkeeping (`cancelInstall`/`finishInstall`, installer mutex release, temp-file cleanup) now runs in the application scope too, so finishing an install after leaving the screen no longer depends on the view model being alive.

## Regression gate

- `./gradlew test lint assembleDebug` passes on the release commit, including the new `ForegroundJobsTest` (service start/stop transitions).
- CI signed build must pass `test lint assembleRelease`, produce both APKs, and verify identical signing certificates.
- On-device: start a download, background the app, confirm progress continues and the "Installing updates" notification appears, and that it disappears when the queue drains.
- Draft release must contain `com.guberdev.apkupdater-release.apk` and `com.guberdev.apkupdater-sensitive-logging-release.apk`.

## Local results

- `test lint assembleDebug`: `BUILD SUCCESSFUL` (Gradle 9.4.0 / JDK 17, platform 36), 2/2 new tests passed.
- Device test passed: download continued with the app in the background and the "Installing updates" notification appeared. Release 3.1.31 published after this smoke test.
