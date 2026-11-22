# GuitarNest for iPhone

SwiftUI app (iOS 17+) using the shared Kotlin code (`shared/`) as the `GuitarNestShared` framework.

## Build (macOS, Xcode 16, JDK 17)

```sh
./gradlew :shared:assembleGuitarNestSharedReleaseXCFramework   # from the project root
python3 scripts/ios_strings.py                                   # translations from the Android strings
brew install xcodegen swiftlint
cd ios && xcodegen generate && open GuitarNest.xcodeproj
```

CI does the same on every change (`.github/workflows/ios.yml`): SwiftLint, build, tests, simulator screenshots.

## Launch arguments (screenshots and tests)

`--demo`, `--open-example`, `--play`, `--tab metronome|tuner|listen`, `--demo-hz 110.1` (tuner without microphone).

## Differences from Android

- No capture from other apps: iOS does not allow it.
- Compressed MusicXML (`.mxl`) is not read; use `.musicxml`.
- The AI key is in the Keychain (this device only).
