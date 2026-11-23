# Changelog

## 0.1.1 (2026-11-23)

- Chord diagrams computed for every chord (open and barre shapes up the neck); tap a diagram for another voicing, saved in the song.
- Play along shows the chord now and the next chord; with a fingerpicking pattern, the string to pick lights up.
- Fingerpicking patterns (bass, alternate bass, strings) and flamenco strumming: rumba, tangos, rasgueado, soleá and bulerías (12-beat compás).
- Print and PDF start with the chord diagrams; the text export lists the shapes.
- Tuner: the reading stays while the string rings out; tap a string to hear its note; play A4 as a tuning fork.
- Import MIDI files (for example public-domain guitar music from the Mutopia Project).
- Sign in with OpenRouter (one account for many AI models), and Anthropic's API directly.
- Report a problem or an idea anonymously (EU-hosted service), on GitHub or as text. The exact issue is shown before sending; crash details are anonymised. My reports shows the status and the public link; anonymised crash reports can be sent automatically (off by default).
- Song search retries when MusicBrainz is busy.
- iPhone app builds and passes its tests in CI.

## 0.1.0 (2026-11-22)

- Guitar tuner: YIN pitch detection, 9 tunings with their usual note names (E♭ in half step down), reference pitch 415–466 Hz. The six strings are shown; the string being tuned lights up; tap a string to lock it.
- Tab library: write, import (GuitarNest, MusicXML, .mxl), export, print or save as PDF. Example exercise.
- Play along with the metronome: current bar and strum shown, count-in, loop, 50–100 % tempo, speed trainer. Practice history.
- Metronome: accents, subdivisions, tap tempo, speed trainer, strumming patterns.
- Your own AI connection (any OpenAI-compatible endpoint): arrange for a level, add strumming patterns, write a new tab.
- Song search in MusicBrainz.
- Tab from audio on the phone: a file you own, a recording, or (Android) an app that allows capture. Notes found on the device by Basic Pitch in pure Kotlin; your AI can refine the draft.
- Encrypted backup and restore (same format on Android and iPhone).
- iPhone app (SwiftUI) with the same features, except capture from other apps.
- AI toolkit (`ai/`): tab from your own recording, with tempo, chords, capo, tuning and strumming; skill file for AI agents.
- Clear microphone permission screen. Internet only on the player's action.
- English, French, German, Spanish and Italian.
- Research: measured audio-to-tab accuracy (`research/transcription/`).
