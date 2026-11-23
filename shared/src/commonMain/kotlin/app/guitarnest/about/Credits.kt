package app.guitarnest.about

/** One open-source project, data source, service, format or method GuitarNest uses. */
data class Credit(val name: String, val use: String, val license: String, val url: String)

data class CreditSection(val key: String, val items: List<Credit>)

/**
 * Everything GuitarNest is built with, shown in the apps (About → Open-source credits) and in CREDITS.md.
 * A test checks that CREDITS.md names every entry. [CreditSection.key] is the text key of the heading.
 */
object Credits {
    val SECTIONS = listOf(
        CreditSection("credits_in_app", listOf(
            Credit("Basic Pitch", "Note model that finds the notes in audio (converted to run in GuitarNest's own interpreter)", "Apache-2.0, Spotify AB", "https://github.com/spotify/basic-pitch"),
            Credit("Chromaprint", "Audio fingerprint for song identification (algorithm ported to Kotlin)", "MIT (ported files; the full library is LGPL-2.1), Lukáš Lalinský", "https://github.com/acoustid/chromaprint"),
            Credit("Kotlin and Kotlin Multiplatform", "Language of the app and of the code shared with iPhone", "Apache-2.0, JetBrains", "https://kotlinlang.org"),
            Credit("kotlinx.serialization", "Reads and writes songs, backups and AI answers (JSON)", "Apache-2.0, JetBrains", "https://github.com/Kotlin/kotlinx.serialization"),
            Credit("kotlinx.coroutines", "Background work (listening, network)", "Apache-2.0, JetBrains", "https://github.com/Kotlin/kotlinx.coroutines"),
            Credit("Jetpack Compose and Material 3", "Android screens", "Apache-2.0, The Android Open Source Project", "https://developer.android.com/jetpack/compose"),
            Credit("AndroidX Core, Activity, Lifecycle", "Android app basics", "Apache-2.0, The Android Open Source Project", "https://developer.android.com/jetpack/androidx"),
            Credit("Material Icons", "Icons", "Apache-2.0, Google", "https://fonts.google.com/icons"),
            Credit("Nunito", "Font", "SIL Open Font License 1.1, The Nunito Project Authors", "https://github.com/googlefonts/nunito"),
            Credit("Android platform", "Audio (AudioRecord, AudioTrack, MediaCodec), Keystore, printing", "Apache-2.0, The Android Open Source Project", "https://source.android.com"),
            Credit("Apple iOS frameworks", "iPhone app: SwiftUI, AVFoundation, CryptoKit, CommonCrypto, Keychain", "Apple SDK licence", "https://developer.apple.com"),
        )),
        CreditSection("credits_services", listOf(
            Credit("MusicBrainz", "Song search (title, artist, album); only your search words are sent", "Data CC0, MetaBrainz Foundation", "https://musicbrainz.org"),
            Credit("AcoustID", "Finds the song title from a sound fingerprint, only if you turn on Find the title; the audio is never sent", "Data CC BY-SA 3.0, AcoustID OÜ (Estonia, EU)", "https://acoustid.org"),
            Credit("Your AI provider", "Only if you connect one: Anthropic, OpenAI, Mistral, OpenRouter, LiteLLM, Ollama, LM Studio or any OpenAI-compatible server", "Their own terms", "https://openrouter.ai"),
            Credit("Scaleway", "Hosts the anonymous report service (Paris, EU)", "Service", "https://www.scaleway.com"),
            Credit("GitHub", "Source code, releases, issues", "Service", "https://github.com"),
            Credit("DuckDuckGo", "\"Search for tabs in the browser\" opens a DuckDuckGo search", "Service", "https://duckduckgo.com"),
        )),
        CreditSection("credits_tools", listOf(
            Credit("Gradle and Android Gradle plugin", "Builds the Android app", "Apache-2.0", "https://gradle.org"),
            Credit("JUnit 4", "Android unit tests", "EPL-1.0", "https://junit.org/junit4"),
            Credit("XcodeGen", "Creates the iPhone Xcode project", "MIT", "https://github.com/yonaskolb/XcodeGen"),
            Credit("SwiftLint", "Checks the Swift code", "MIT", "https://github.com/realm/SwiftLint"),
            Credit("GitHub Actions: checkout, setup-java, setup-python, setup-node, upload-artifact, gradle/actions", "Continuous integration", "MIT", "https://github.com/actions"),
            Credit("Node.js", "Runs the anonymous report service", "MIT", "https://nodejs.org"),
            Credit("ONNX Runtime and ONNX", "AI toolkit and the conversion of the note model (not in the app)", "MIT / Apache-2.0", "https://onnxruntime.ai"),
            Credit("NumPy and SciPy", "AI toolkit and research (not in the app)", "BSD-3-Clause", "https://numpy.org"),
            Credit("fastlane metadata layout", "Store texts and screenshots folders", "MIT", "https://fastlane.tools"),
        )),
        CreditSection("credits_methods", listOf(
            Credit("YIN pitch detection", "Tuner. de Cheveigné & Kawahara 2002", "Published method", "https://doi.org/10.1121/1.1458024"),
            Credit("Karplus-Strong and Jaffe-Smith plucked string", "Reference notes and test sounds. 1983", "Published methods", "https://doi.org/10.2307/3680062"),
            Credit("Optimum path fingering", "Strings and frets for notes. Sayegh 1989", "Published method", "https://doi.org/10.2307/3680014"),
            Credit("Practice research", "Slow practice, fixing errors, spaced practice (see SCIENCE.md)", "Published studies", "https://github.com/0x0SegFault/guitarnest/blob/main/docs/SCIENCE.md"),
            Credit("MusicXML", "Score import", "W3C Community Final Specification Agreement", "https://www.w3.org/2021/06/musicxml40/"),
            Credit("Standard MIDI File", "MIDI import", "MIDI Association specification", "https://midi.org"),
            Credit("ALTCHA proof-of-work format", "Anti-spam for anonymous reports (format only, own code)", "MIT", "https://altcha.org"),
            Credit("OAuth 2.0 PKCE (RFC 7636)", "Sign in with OpenRouter", "IETF", "https://www.rfc-editor.org/rfc/rfc7636"),
            Credit("SHA-256, PBKDF2, AES-GCM", "Reports anti-spam, encrypted backups", "FIPS 180-4, RFC 8018, NIST SP 800-38D", "https://csrc.nist.gov"),
        )),
        CreditSection("credits_music", listOf(
            Credit("Mutopia Project", "Suggested source of free guitar music to import (public domain, Creative Commons)", "Per piece", "https://www.mutopiaproject.org"),
            Credit("OpenScore", "Suggested source of free scores (CC0)", "CC0", "https://openscore.cc"),
        )),
    )

    val ALL: List<Credit> get() = SECTIONS.flatMap { it.items }
}
