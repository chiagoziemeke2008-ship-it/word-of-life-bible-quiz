# Word of Life — Bible Quiz

A mobile-first Bible trivia game prototype that runs in a browser and can be packaged for Android.

## Included
- 40 Bible questions across four categories
- 10-question rounds, randomized each time
- Four answer choices per question
- Instant answer feedback and Scripture references
- Score, accuracy, and locally saved best score
- Responsive mobile layout and offline PWA caching
- No paid APIs, accounts, or ads in this starter build

## Test it
Open `index.html` in a desktop browser. For PWA installation/offline testing, serve the folder over HTTPS (or localhost); service workers do not run from ordinary `file://` URLs.

## Android / Play Store path
This is a web game, not yet a signed Android App Bundle (.aab). To publish:
1. Host the folder on an HTTPS website.
2. Use a trusted Android web-app packaging workflow (for example, a Trusted Web Activity/PWABuilder route) to create an Android project or AAB.
3. Test on real Android devices, add a privacy policy if data/ad SDKs are introduced, and complete Play Console app-content and data-safety declarations.
4. Sign and upload the AAB in Google Play Console.

Google Play developer registration is separate and paid. Check the current Play Console requirements and fees before starting. A new personal developer account may also have testing requirements before production access.

## Before publishing
- Review every Bible question and reference against the Bible translation you choose.
- Choose and disclose a Bible translation. This prototype paraphrases questions and uses short references; it does not reproduce long copyrighted Bible passages.
- Add your own publisher name, support email, privacy policy, screenshots, feature graphic, and app icon.
- Add ads only through a compliant SDK and disclose ad-related data collection. This starter contains no ads.
