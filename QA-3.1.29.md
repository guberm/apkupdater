# APK Updater 3.1.29 — unavailable Shizuku crash

## Fix

Installer permission checks now switch to Android's main dispatcher before displaying the Shizuku warning or opening the unknown-app installation settings. Callers then resume on their original dispatcher.

Previously, update downloads, search downloads and Install All called this shared check from an IO worker. When Shizuku was selected but unavailable or unauthorized, creating the warning Toast threw `Can't toast on a thread that has not called Looper.prepare()` before the download began.

The intended behavior is unchanged: show `Start Shizuku and grant APK Updater access first.` and return without downloading or installing. No silent fallback to a different installer is introduced.

## Regression gate

- Run the new device test against the old permission-check implementation and verify that it fails with the reported Looper exception.
- Run it against the fix from both an IO thread without a Looper and Android's main thread; both unavailable-Shizuku checks must return false without throwing.
- Run the complete JVM/device suite, lint and build.
- Verify the signed release certificate, install the exact APK in place, and confirm the installed version and app startup.

The test uses an isolated debug package without Shizuku access and restores its installer preference. It does not stop the user's Shizuku service or change other applications' permissions.

Final measured test and release results are recorded in the published release notes.

## Local results

- Old implementation: the focused test failed with the exact reported `Can't toast on a thread that has not called Looper.prepare()` exception in `SessionInstaller.checkPermission`.
- Fixed implementation: 35 JVM tests and 36 instrumentation tests passed (zero failures/errors/skips) on Pixel 7 Pro / Android 17. The new test covers IO and main-thread callers.
- Lint and debug assembly passed; Gradle reported `BUILD SUCCESSFUL`.
