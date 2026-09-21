# APK Updater 3.1.27 — GitHub partial results and diagnostics

- GitHub now distinguishes partially successful checks from a complete source failure, even when healthy repositories have no newer release. Successful updates remain available and partial sources remain retryable.
- `Check details` identifies each failed repository, its HTTP status and a safe error description. Rate-limit reset/retry time is displayed only when supported by response headers. A generic HTTP 403 is not automatically labeled a quota error.
- The last 20 GitHub update/search reports are stored locally across app restarts, including interrupted scans. Both Copy App Logs and Send App Logs include this history before logcat, with timestamps, build version and operation.
- Saved diagnostics contain repository IDs, classified reasons, HTTP codes and retry times, not response bodies, authorization headers, cookies or raw exception messages. Existing Android logcat still needs review/redaction before public sharing.

## Regression gate

```powershell
$env:ANDROID_HOME='C:\Users\michael.guber\AppData\Local\Android\Sdk'
$env:BUILD_TAG='.debug'
$env:BUILD_NUMBER='87'
.\gradlew.bat :app:testDebugUnitTest :app:lint :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon
```

The original code failed the new repository/HTTP diagnostic assertion on Pixel 7 Pro / Android 17. After the fix: **35 JVM tests and 32 device tests passed**, zero failures/errors/skips; lint and debug assembly passed. Coverage includes partial versus total failure, success with zero updates, bounded persisted history, log export without logcat, quota header classification, malformed reset values, and the details/retry UI. Existing source/download/package/UI regressions remain in the full suite.

This release stays a draft until the full suite and exact signed APK are checked. Final counts, APK SHA-256 and signed-device observations are recorded in the published release notes.

Issue #3's latest log still does not contain the original GitHub exception. This release makes the next report actionable; it does not claim to have identified that user's specific failing repository or bypass GitHub's service limits.
