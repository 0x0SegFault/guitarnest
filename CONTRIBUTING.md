# Contributing to GuitarNest

GuitarNest is a small, privacy-first project. The maintainer reviews every contribution before merge.

## Ways to help

- **Report a bug** or **suggest a feature**: [open an issue](https://github.com/0x0SegFault/guitarnest/issues/new/choose). Do not attach copyrighted tabs or audio you do not own.
- **Translate**: copy `app/src/main/res/values/strings.xml` to `values-<lang>/`, or run `python3 scripts/new_language.py <lang>`. Check with `python3 scripts/check_translations.py`.
- **Code**: fork the repository, make a branch, and open a pull request against `main`.

## Ground rules

1. **Network only on the player's action** (song search, their own AI). No background requests. CI checks the permission list.
2. **No trackers, analytics, ads or proprietary libraries** (no Google Play Services, Firebase, etc.).
3. **No song database.** The app does not download or share tabs or songs. Users work only with music they own. See [docs/COPYRIGHT.md](docs/COPYRIGHT.md).
4. **Claims need evidence.** Cite the source in `docs/SCIENCE.md` and add tests.
5. **All user-facing text goes in `strings.xml`.**

## Development

Requirements: JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew :shared:jvmTest      # music logic tests (pitch, notes, tunings)
./gradlew lintDebug            # Android lint
./gradlew assembleDebug        # debug APK
```

The music logic is in `shared/` (Kotlin Multiplatform, no Android code), so an iOS app can reuse it. Do not use `java.*` imports there.

The AI toolkit is in [ai/](ai/README.md) (Python): `python ai/tests/test_toolkit.py`. Research and accuracy: [research/transcription/](research/transcription/).

## Pull request review

- CI must pass and the maintainer must approve.
- Keep each pull request to one change.
- Your contribution is licensed under the [GPL-3.0-or-later](LICENSE).

Follow the [Code of Conduct](CODE_OF_CONDUCT.md).
