# APK Updater 3.1.30 — GPlayApi update gate

## Change

No application code changed since 3.1.29. This release ships only the scheduled-update workflow fix: the daily GPlayApi check now skips major-version bumps (3.6.4 -> 4.0.0 needs a manual API migration and cannot compile against the current app) and exits green instead of failing red every day. The attempted auto-commit was already discarded by the preflight step, so no installed release is affected.

## Regression gate

- `./gradlew test lint assembleDebug` passes on the release commit (verified locally).
- CI signed build must pass `test lint assembleRelease`, produce both APKs, and verify identical signing certificates.
- Draft release must contain `com.guberdev.apkupdater-release.apk` and `com.guberdev.apkupdater-sensitive-logging-release.apk`.

## Local results

- `test lint assembleDebug`: `BUILD SUCCESSFUL` (Gradle 9.4.0 / JDK 17, platform 36).
- Device testing: no user-visible behavior changed; the usual smoke install/start check before publishing still applies.
