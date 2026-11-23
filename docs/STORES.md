# Publishing on Google Play and the App Store

This page is for maintainers. Store rules change: check each item in the store's own documentation before you submit.

## What is ready in the repository

| Item | Google Play | App Store |
|---|---|---|
| App build | `guitarnest-X.Y.Z.aab` on each GitHub release | Xcode archive from `ios/` (your Apple account) |
| Store texts, 5 languages | `fastlane/metadata/android/<lang>/` | `ios/fastlane/metadata/<lang>/` |
| Icon | `fastlane/metadata/android/en-US/images/icon.png` (512 px) | `ios/GuitarNest/Assets.xcassets` (1024 px, no transparency) |
| Screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/` | `ios-preview` release of the iOS CI run (6.9-inch) |
| Privacy policy | `https://github.com/0x0SegFault/guitarnest/blob/main/PRIVACY.md` | same |
| Support | GitHub issues | GitHub issues |

## Google Play

### Account
1. Google Play Console account (US$25 once, identity check). Declare your EU trader status (a free app with no income is usually "non-trader").
2. New personal accounts must run a **closed test with at least 12 testers for 14 days** before production.

### App content forms
| Form | Answer for GuitarNest |
|---|---|
| App access | No login needed. For the AI features, the reviewer needs their own AI key: they are optional. |
| Ads | No ads |
| Content rating | Questionnaire: music tool, no user-generated content shared, no chat |
| Target audience | 13+ |
| Data safety | The developer collects nothing. Audio is processed on the device and not stored. When the user asks, the app sends the selected song and request to the AI service the user configured, search words to MusicBrainz, and, if the user turns on "Find the title", a sound fingerprint to AcoustID. Check Google's current rules for user-initiated transfers to a third party the user chooses, and declare them if required. |
| Foreground service permissions | `FOREGROUND_SERVICE_MEDIA_PROJECTION`: "Tab from audio, From another app": records the sound of apps that allow capture, started by the user, with a notification. Play asks for a short video of the feature. |
| Microphone (`RECORD_AUDIO`) | Tuner and recording the user's playing, only while the screen is open. |

### Signing and upload
Use **Play App Signing with your own key**: upload `guitarnest-release.jks` with the tool Play Console shows. The Play version then has the same signature as GitHub (users can switch without losing data). Upload the `.aab` to the closed test track first.

## App Store

### Account and tools
1. Apple Developer Program (US$99 per year). Individual: your legal name is shown as seller. Declare your EU trader status.
2. A Mac with Xcode, or a CI job with an App Store Connect API key and your signing certificate as secrets.

### Before you submit
- Set `DEVELOPMENT_TEAM` and automatic signing in Xcode (`ios/project.yml` uses no signing for CI).
- Test on a real iPhone: microphone, file import, recording, AI connection.

### App Store Connect
| Item | Answer for GuitarNest |
|---|---|
| Bundle ID | `io.github.x0x0segfault.guitarnest` |
| Category | Music (secondary: Education) |
| App Privacy | Data Not Collected (the developer receives nothing; AI and search requests go to services the user chooses) |
| Age rating | 4+ (no user-generated content shared) |
| Export compliance | Standard encryption only (HTTPS, backup encryption with system APIs): `ITSAppUsesNonExemptEncryption` is `NO` |
| Review notes | No account. AI features need the reviewer's own key and are optional. The app never downloads music; it analyses files the user owns or the user's own recordings. |

Relevant review rules: 5.1 (privacy: purpose string for the microphone is set), 5.2 (intellectual property: no song or tab database, no downloads).

## Licence note: GPL and the App Store

The App Store terms add restrictions that the FSF considers incompatible with the GPL. As the only copyright holder you can publish your own code there. Before you accept outside contributions to the iOS app, add a GPL section 7 permission for app stores, or use a contributor licence agreement. Ask a lawyer if you are unsure.
