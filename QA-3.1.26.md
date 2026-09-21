# 3.1.26 regression verification

## Changes

- GitHub checks emit successful repositories independently. A failed repository no longer cancels its siblings; an incomplete update scan still reports failure and keeps available results. Search retains successful matches.
- Removed whole-GitHub-scan retries. Only network IO, HTTP 408 and HTTP 5xx failures receive up to three attempts; HTTP 403/404/429, malformed data and cancellation are not retried. At most four GitHub API calls run concurrently.
- GitHub search respects package-specific asset/flavor filters.
- Direct APK and bundle installs use the existing temporary-file downloader, with byte progress during network transfer, cancellation, incomplete-file cleanup and retries of interrupted response bodies before installation. Play and custom-directory downloads use the same cancellation ID.
- A canceled download is not presented as a download failure. Unknown content length remains indeterminate instead of falsely showing 100 percent.
- Download IDs can be negative because they are hash codes. Cancellation now accepts every ID; only a missing ID disables tracking. A regression test failed on the old sign guard before the fix.
- Releases are created as drafts. Publish only after testing the exact signed standard APK on the device.

## Automated retest

```powershell
$env:ANDROID_HOME='C:\Users\michael.guber\AppData\Local\Android\Sdk'
$env:BUILD_TAG='.debug'
$env:BUILD_NUMBER='86'
.\gradlew.bat :app:testDebugUnitTest :app:lint :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon
```

The suite covers source parsers, cache restoration, package rejection, UI source selectors, ignore-menu ordering, single-row actions, dark/light source icons and retained progress. New regressions cover isolated GitHub failures, bounded per-repository retries, partial search, HTTP/cancellation retry classification, interrupted body recovery, cancellation cleanup and non-retryable download errors.

The previous candidate passed 30 JVM tests and 28 device tests, but signed-device testing exposed the negative-ID cancellation defect. That candidate (3.1.25) was never published. The fixed candidate passed a fresh complete run: 30 JVM tests and 29 device tests, zero failures/errors/skips. Device: Pixel 7 Pro, Android 17. Final signed-release checks are recorded in the published release notes.

## Release gate

Record the final test counts, signed APK SHA-256, device version and manual test results in the release notes before publishing. Do not treat a debug-only test as verification of the signed APK.

## Boundaries

The issue attachment contains an F-Droid stream cancellation, but no GitHub failure stack trace. The GitHub failure-isolation defect is reproduced with controlled failures; the reporter's specific HTTP/transport failure is not established. External coverage, GitHub API quotas and source anti-bot blocks cannot be guaranteed away. Root/Shizuku and every third-party package/signing combination require separate environments; do not claim these were tested without evidence.

Use the standard APK. The sensitive-logging build is for private diagnostics only and can log authentication/cookie headers; do not share those logs.
