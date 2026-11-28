# Releasing GuitarNest

This page is for maintainers.

## How a release works

A tag `vX.Y.Z` starts the [release workflow](../.github/workflows/release.yml). It:

1. checks that the tag matches `versionName`,
2. runs the tests and builds the release APK and App Bundle,
3. signs them with the release key (GitHub environment `release`, tags `v*` only, your approval),
4. fails if the permissions differ from the list in [PRIVACY.md](../PRIVACY.md),
5. publishes `guitarnest-X.Y.Z.apk`, its `.sha256`, `guitarnest-X.Y.Z.aab` and the changelog as a GitHub release.

## Make a new release

1. In `app/build.gradle.kts`, increase `versionCode` by 1 and set `versionName`. In `ios/project.yml`, set `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` to the same values.
2. Add `fastlane/metadata/android/<lang>/changelogs/<versionCode>.txt` and `ios/fastlane/metadata/<lang>/release_notes.txt` for each language (500 characters max on Android).
3. Add the version to [CHANGELOG.md](../CHANGELOG.md).
4. Commit, push, then tag:
   ```sh
   git tag -a vX.Y.Z -m "GuitarNest X.Y.Z" && git push origin vX.Y.Z
   ```
5. Approve the `release` environment in the **Actions** tab, then check the run.

## Signing key (Android)

- Release key: `guitarnest-release.jks`, alias `guitarnest`, certificate `CN=GuitarNest`, RSA 4096.
- SHA-256 of the certificate: `BE:29:E6:23:E8:23:12:B6:F8:E5:50:DE:0C:A7:69:0B:38:2C:21:68:EB:0C:E1:5F:57:63:6E:0D:F7:F1:60:C0`.
- `keystore.properties` (git-ignored) holds the passwords for local release builds.
- GitHub environment `release` holds the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Keep a backup of the key and its password outside the project. **If the key is lost, no update can be installed over existing installs.**

## iOS

CI builds and tests the iOS app on every change (`.github/workflows/ios.yml`). The build log and simulator screenshots are kept with each run for 14 days (Actions → the run → Artifacts).

## The note model

`models/basic-pitch.gnm` is the Basic Pitch model (Spotify, Apache-2.0), converted by `ai/tools/export_model.py` from the official 0.4.0 release:

```sh
cd ai && ./fetch_model.sh && pip install onnx && python tools/export_model.py models/nmp.onnx ../models/basic-pitch.gnm
sha256sum ../models/basic-pitch.gnm   # 63c061291c3853f5f796e41ea3d2a67539e7a939bdd2edd5e7c5372bf51c026b
```

## Known notices

Lint (Android Gradle plugin 8.13) prints "Module was compiled with an incompatible version of Kotlin" with Kotlin 2.4. This is harmless. It goes away with the Android Gradle plugin 9 migration; Dependabot skips the updates that need it.
