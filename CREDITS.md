# Credits

GuitarNest is built on the work of many people. Thank you.

The same list is in the apps: Settings → About → Open-source credits. GuitarNest's own code is [EUPL-1.2](LICENSE).

## In the app

| Project | Used for | Licence |
|---|---|---|
| [Basic Pitch](https://github.com/spotify/basic-pitch) | Note model that finds the notes in audio (converted to run in GuitarNest's own interpreter) | Apache-2.0, Spotify AB |
| [Chromaprint](https://github.com/acoustid/chromaprint) | Audio fingerprint for song identification (algorithm ported to Kotlin) | MIT (ported files; the full library is LGPL-2.1), Lukáš Lalinský |
| [Kotlin and Kotlin Multiplatform](https://kotlinlang.org) | Language of the app and of the code shared with iPhone | Apache-2.0, JetBrains |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Reads and writes songs, backups and AI answers (JSON) | Apache-2.0, JetBrains |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Background work (listening, network) | Apache-2.0, JetBrains |
| [Jetpack Compose and Material 3](https://developer.android.com/jetpack/compose) | Android screens | Apache-2.0, The Android Open Source Project |
| [AndroidX Core, Activity, Lifecycle](https://developer.android.com/jetpack/androidx) | Android app basics | Apache-2.0, The Android Open Source Project |
| [Material Icons](https://fonts.google.com/icons) | Icons | Apache-2.0, Google |
| [Nunito](https://github.com/googlefonts/nunito) | Font | SIL Open Font License 1.1, The Nunito Project Authors |
| [Android platform](https://source.android.com) | Audio (AudioRecord, AudioTrack, MediaCodec), Keystore, printing | Apache-2.0, The Android Open Source Project |
| [Apple iOS frameworks](https://developer.apple.com) | iPhone app: SwiftUI, AVFoundation, CryptoKit, CommonCrypto, Keychain | Apple SDK licence |

## Online services (only when you use the feature)

| Project | Used for | Licence |
|---|---|---|
| [MusicBrainz](https://musicbrainz.org) | Song search (title, artist, album); only your search words are sent | Data CC0, MetaBrainz Foundation |
| [Mutopia Project](https://www.mutopiaproject.org) | Free guitar scores: the list is in the app; a score is downloaded only when you tap it | Each score: public domain or Creative Commons (shown with the song); volunteers of the Mutopia Project |
| [AcoustID](https://acoustid.org) | Finds the song title from a sound fingerprint, only if you turn on Find the title; the audio is never sent | Data CC BY-SA 3.0, AcoustID OÜ (Estonia, EU) |
| [Your AI provider](https://openrouter.ai) | Only if you connect one: Anthropic, OpenAI, Mistral, OpenRouter, LiteLLM, Ollama, LM Studio or any OpenAI-compatible server | Their own terms |
| [Scaleway](https://www.scaleway.com) | Hosts the anonymous report service (Paris, EU) | Service |
| [GitHub](https://github.com) | Source code, releases, issues | Service |
| [DuckDuckGo](https://duckduckgo.com) | "Search for tabs in the browser" opens a DuckDuckGo search | Service |

## Build and test tools (not in the app)

| Project | Used for | Licence |
|---|---|---|
| [Gradle and Android Gradle plugin](https://gradle.org) | Builds the Android app | Apache-2.0 |
| [JUnit 4](https://junit.org/junit4) | Android unit tests | EPL-1.0 |
| [XcodeGen](https://github.com/yonaskolb/XcodeGen) | Creates the iPhone Xcode project | MIT |
| [SwiftLint](https://github.com/realm/SwiftLint) | Checks the Swift code | MIT |
| [GitHub Actions: checkout, setup-java, setup-python, setup-node, upload-artifact, gradle/actions](https://github.com/actions) | Continuous integration | MIT |
| [Node.js](https://nodejs.org) | Runs the anonymous report service | MIT |
| [ONNX Runtime and ONNX](https://onnxruntime.ai) | AI toolkit and the conversion of the note model (not in the app) | MIT / Apache-2.0 |
| [NumPy and SciPy](https://numpy.org) | AI toolkit and research (not in the app) | BSD-3-Clause |
| [fastlane metadata layout](https://fastlane.tools) | Store texts and screenshots folders | MIT |

## Methods and formats

| Project | Used for | Licence |
|---|---|---|
| [YIN pitch detection](https://doi.org/10.1121/1.1458024) | Tuner. de Cheveigné & Kawahara 2002 | Published method |
| [Karplus-Strong and Jaffe-Smith plucked string](https://doi.org/10.2307/3680062) | Reference notes and test sounds. 1983 | Published methods |
| [Optimum path fingering](https://doi.org/10.2307/3680014) | Strings and frets for notes. Sayegh 1989 | Published method |
| [Practice research](https://github.com/0x0SegFault/guitarnest/blob/main/docs/SCIENCE.md) | Slow practice, fixing errors, spaced practice (see SCIENCE.md) | Published studies |
| [MusicXML](https://www.w3.org/2021/06/musicxml40/) | Score import | W3C Community Final Specification Agreement |
| [Standard MIDI File](https://midi.org) | MIDI import | MIDI Association specification |
| [ALTCHA proof-of-work format](https://altcha.org) | Anti-spam for anonymous reports (format only, own code) | MIT |
| [OAuth 2.0 PKCE (RFC 7636)](https://www.rfc-editor.org/rfc/rfc7636) | Sign in with OpenRouter | IETF |
| [SHA-256, PBKDF2, AES-GCM](https://csrc.nist.gov) | Reports anti-spam, encrypted backups | FIPS 180-4, RFC 8018, NIST SP 800-38D |

## Free music sources

| Project | Used for | Licence |
|---|---|---|
| [Mutopia Project](https://www.mutopiaproject.org) | Suggested source of free guitar music to import (public domain, Creative Commons) | Per piece |
| [OpenScore](https://openscore.cc) | Suggested source of free scores (CC0) | CC0 |

Licence texts shipped with the app: [Nunito](LICENSE-font-Nunito.txt), [Basic Pitch](models/LICENSE-basic-pitch.txt) and its [NOTICE](models/NOTICE-basic-pitch.txt).
