# Changelog

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
