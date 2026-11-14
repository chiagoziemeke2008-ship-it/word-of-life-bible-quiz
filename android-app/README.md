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




## Rewarded ads

- The Android app offers an optional rewarded video after every successfully passed level.
- The player must tap **Watch Ad**; the ad is never forced automatically.
- Completing the rewarded ad grants one hint token. A hint removes two incorrect answer choices from one question.
- During testing, the app uses Google's official rewarded test ad unit ID `ca-app-pub-3940256099942544/5224354917` and demo app ID. Test ads do not generate revenue.
- Replace the demo app ID and rewarded ad unit ID with the real IDs from the Word of Life AdMob account before release. Google requires test ads during development.

## Advertising formats

- A normal full-screen interstitial test ad is requested automatically at the end of every completed level. It is separate from the rewarded offer.
- An optional rewarded test video is offered on the results screen. The player must tap **Watch Ad** to earn one hint token; the video is never forced.
- A hint token removes two incorrect choices from one question.
- Testing uses Google's demo app ID `ca-app-pub-3940256099942544~3347511713`, interstitial test unit ID `ca-app-pub-3940256099942544/1033173712`, and rewarded test unit ID `ca-app-pub-3940256099942544/5224354917`. Test ads do not earn revenue.
- Replace all demo IDs with the real Word of Life AdMob IDs before release. Keep test IDs during development.
