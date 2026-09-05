# RemindMe

<p align="center">
  <img src="icons/icon.png" alt="RemindMe logo" width="160" />
</p>

A private, offline-first Android reminder app with audible alarms.

<p align="center">
  <a href="https://github.com/ShambaC/RemindMe/actions/workflows/android.yml"><img src="https://github.com/ShambaC/RemindMe/actions/workflows/android.yml/badge.svg" alt="Android CI" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
  <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" /></a>
</p>

## Features

- One-time and recurring reminders.
- Exact alarm scheduling with Android alarm APIs.
- Audible alarm playback with configurable sound, vibration, speech, snooze, and ring duration.
- Full-screen alarm activity for supported Android lock-screen states.
- Calendar overview and occurrence management.
- Offline-first storage: reminder data stays on the device.
- Material 3 light, dark, and optional dynamic-color themes.
- Alarm health screen for notification, sound, exact-alarm, and full-screen access.

## Screenshots

Replace the four placeholders below with screenshots added to `screenshots/`.

<table>
  <tr>
    <td align="center">Home<br><code>screenshots/home.png</code></td>
    <td align="center">Add reminder<br><code>screenshots/add-reminder.png</code></td>
    <td align="center">Calendar<br><code>screenshots/calendar.png</code></td>
    <td align="center">Settings<br><code>screenshots/settings.png</code></td>
  </tr>
</table>

## Requirements

- Android Studio with an Android SDK configured.
- JDK 17.
- Android API 26 or newer.

Android may require notification, exact-alarm, full-screen-intent, and sound permissions before reminders can be relied on. Device power-management and Do Not Disturb policies can also affect delivery.

## Build and test

```bash
./gradlew lintDebug testDebugUnitTest assembleRelease
```

On Linux and macOS, make the wrapper executable if the checkout does not preserve its file mode:

```bash
chmod +x gradlew
```

The Android CI workflow runs lint, unit tests, and the release build on pushes and pull requests. That verification build is intentionally allowed to remain unsigned.

### Signed release APK

The release workflow builds and uploads an installable, signed APK when a GitHub Release is published. It requires these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded Java/Android keystore.
- `RELEASE_STORE_PASSWORD`: keystore password.
- `RELEASE_KEY_ALIAS`: signing key alias.
- `RELEASE_KEY_PASSWORD`: signing key password.

The workflow uploads the result as `RemindMe-<version>.apk`. The keystore must be kept permanently: Android updates require the same signing key.

## Windows R8 file-lock troubleshooting

If `minifyReleaseWithR8` reports that `classes.dex` is being used by another process, the build directory is locked by a Gradle daemon, Android Studio build, antivirus scanner, or indexer. Stop Gradle daemons, close parallel Android Studio builds, then rebuild once:

```bat
gradlew.bat --stop
```

If the lock remains after all builds are closed, delete `app\build` and retry. Do not run overlapping Gradle builds against the same project directory.

## Author

**ShambaC**

- Homepage: [shambac.online](https://shambac.online)
- Source: [github.com/ShambaC/RemindMe](https://github.com/ShambaC/RemindMe)
