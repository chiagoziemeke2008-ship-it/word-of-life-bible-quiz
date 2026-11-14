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
