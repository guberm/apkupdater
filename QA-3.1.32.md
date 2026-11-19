# APK Updater 3.1.32 — APKMirror bot-protection handling

## Change

APKMirror occasionally serves its Cloudflare bot-protection challenge instead of content, which previously surfaced as a cryptic "Download failed with HTTP 200" with no retry. This release:

- Detects the challenge on every request hop (`cf-mitigated` header, 403/503, HTML responses) and retries with increasing delays to ride out transient challenges.
- Adds an on-device solver: a transparent WebView overlay ("Verifying APKMirror access…") solves the challenge, and its cookies (`cf_clearance`) and user agent are replayed on subsequent APKMirror downloads.
- Adds an optional FlareSolverr backend (Settings → FlareSolverr): users running a FlareSolverr server on their network can configure its URL; it is tried first, sharing the same cookie/user-agent replay path.

## Regression gate

- `./gradlew test lint assembleDebug` passes on the release commit, including the new challenge-detection tests.
- CI signed build must pass `test lint assembleRelease`, produce both APKs, and verify identical signing certificates.
- On-device: reproduce a blocked APKMirror download, confirm the verify overlay appears and completes, confirm the download then proceeds; confirm Settings → FlareSolverr accepts a URL.
- Draft release must contain `com.guberdev.apkupdater-release.apk` and `com.guberdev.apkupdater-sensitive-logging-release.apk`.

## Local results

- `test lint assembleDebug`: `BUILD SUCCESSFUL` (Gradle 9.4.0 / JDK 17, platform 36), challenge-detection tests passed.
- Challenge verified against live APKMirror from a datacenter IP: 403 "Just a moment" reproduced with multiple user agents.
- On-device verification pending; publish only after the smoke test of the exact signed APKs.
