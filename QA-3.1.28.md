# APK Updater 3.1.28 — GitHub quota recovery

## Changes

- Persist successful GitHub release responses, including empty results, and reuse them across app restarts.
- Share responses between update checks, searches and apps from the same repository.
- Pause the GitHub request queue on confirmed rate limits or an explicit retry deadline. Refreshing while paused sends no new GitHub requests.
- On the next refresh after reset, reuse earlier successful responses and check the remaining repositories.
- Normal response lifetime: 15 minutes. During recovery: at most 24 hours. Installed-version and release filtering still run against the saved responses.
- Exported diagnostics distinguish HTTP failures from checks deferred without a network request.

## Regression coverage

- Quota pause, persisted state and resume after recreating repository/preferences.
- 86 distinct repositories across two quota windows: 60 successful calls, one quota response, no calls during cooldown, remaining 26 calls after reset.
- Cache expiry and installed-version comparison against cached releases.
- Existing GitHub partial failure/retry/search, UI, download and package validation tests.

## Release gate

Local results: 35 JVM tests and 35 instrumentation tests on Pixel 7 Pro / Android 17 passed; lint and debug assembly passed. Gradle reported BUILD SUCCESSFUL.

Publish only after the complete local test suite, lint, CI signed build, and an in-place test of the exact signed release APK on the attached Pixel 7 Pro.
Final measured results are recorded in the published release notes and issue #3 comment.

## Limits

GitHub still controls the IP quota. Recovery runs on the next manual or scheduled refresh; it does not schedule a new wake-up exactly at reset.
No access token is required or collected in this release. Stale cache data is bounded as described above.
