# Word of Life — Android app

This folder contains the native Android application project.

The app is a real Android package. The current Bible quiz game is copied into the APK as a local asset during the Gradle build. It does not open the GitHub Pages website and it does not need an internet connection to play.

## Build

Requirements: Android SDK Platform 36, JDK 17, and Gradle 8.13.

From this folder run:

```sh
gradle assembleDebug bundleRelease
```

The build automatically copies the current `../index.html` into the APK.

- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release Android App Bundle: `app/build/outputs/bundle/release/app-release.aab`

The release bundle is unsigned until a private upload keystore is configured. A signed AAB is required for Google Play publishing. Never commit a keystore or its passwords to GitHub.

## Automatic build

The GitHub Actions workflow at `.github/workflows/build-android.yml` builds both files whenever the game or Android project changes, and when manually started from the Actions tab.

The Android app uses package ID `com.wordoflife.biblequiz`, keeps progress on the device, and disables Android app-data backup so uninstalling and reinstalling starts fresh.


## AdMob interstitial ads

- The Android APK uses Google's Mobile Ads Next-Gen SDK `1.6.0`.
- A full-screen interstitial is requested after every **two passed levels** (not after failed attempts or during questions).
- The current project intentionally uses Google's official demo app ID and demo interstitial unit ID. These display **test ads only** and do not earn money.
- Before publishing, create/register the app in AdMob and replace both demo IDs in `MainActivity.java` with the real AdMob App ID and real interstitial ad unit ID.
- Keep using Google's test IDs while developing. Never click test ads expecting earnings.
- Ad delivery depends on network access, consent requirements, account approval and available ad fill. If no ad is ready at a level break, the game continues normally.
