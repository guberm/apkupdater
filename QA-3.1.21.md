# 3.1.21 requirement audit

Final delivery is 3.1.22: the overflow menu now groups all app-ignore actions first, then one divider, then all version-ignore actions. The device test verifies the exact top-to-bottom order for two sources. The pending 3.1.21 release job was cancelled to include this final UI clarification without moving an existing Git tag.

## Changes

- APKCombo version parsing excludes the release date/category. APK variants must match a supported ABI (or be universal).
- HTML sources retry individual transient failures, retain partial results, and use an inactivity timeout instead of aborting an entire phone scan after 30 seconds. Source status stays loading until completion.
- Retry preserves fresh partial results and cached download capabilities (Play and XAPK). Cached results are rechecked against installed packages/versions; icons are resolved from current resources.
- Updates from every source must have an exact installed package name. Main-package self-update uses this fork's standard release asset; debug builds do not receive main-package self-updates.
- Downloaded APKs and APK bundles are checked against the requested package before opening an installation session. Android additionally enforces split consistency and signing compatibility. Root installation also checks the package.
- The visible-card count reflects filtering/grouping. Update cards use one action row: Source, Download, and an overflow menu for ignore actions. Download always opens a source picker, including website-only sources; each entry explicitly says Download or Website.
- F-Droid selects the newest compatible variant after SDK/ABI filtering. APKMirror's TV-feature requirement only applies on TV devices. GitHub checks every configured mapping and chooses a release with the requested APK flavor.
- Uptodown links are resolved again at download time. If no direct URL is published, Website opens the source instead of pretending a direct download is available.

## Reproducible verification

PowerShell, with Android SDK configured and an authorized device attached:

```powershell
$env:BUILD_TAG='.debug'
$env:BUILD_NUMBER='81'
.\gradlew.bat :app:testDebugUnitTest :app:lint :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon
```

- 28 JVM tests and 17 instrumented tests passed on Pixel 9 Pro XL, Android 17.
- Instrumented checks include visible/clickable Download and Website actions, single-row actions and the overflow callback, cached link restoration, filtering/counts, and rejection of a wrong-package APK, wrong-package bundled APK, and invalid binary before any installer session is created.
- ARTEMIS independently exercised a nonexistent app filter: `0 shown`; returning to All restored the grouped list.

## Limits

- Availability depends on source coverage, connectivity, authentication and anti-bot restrictions. A failed or incomplete source is not proof that all installed apps are current.
- APKCombo/Uptodown can return HTTP blocks or omit download tokens. Website fallback is intentional; direct download is not guaranteed for every source card.
- Root/Shizuku execution and a full installation of every third-party update were not tested. Package identity does not imply a compatible signing key; Android remains the final installation authority.
- Prior legacy cache entries that never saved an XAPK URL require one successful source refresh to regain that URL.
- Signed release delivery and final main-package device smoke checks are reported in the GitHub release notes; the tests above use a separate debug package.
