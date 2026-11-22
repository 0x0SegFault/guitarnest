# GuitarNest

[![Build](https://github.com/0x0SegFault/guitarnest/actions/workflows/build.yml/badge.svg)](https://github.com/0x0SegFault/guitarnest/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

A free and open-source guitar app for Android: tuner first, then practice tools and tabs. Offline, no account, no ads, no trackers. Works without Google services.

**Status: early development.** No release yet.

## Features

| Now | Planned |
|---|---|
| Tuner: 9 tunings (standard, half/whole step down, drop D, drop C, DADGAD, open D, G, E) | Metronome and slow-practice tools ([method](docs/SCIENCE.md#practice-method-planned)) |
| Automatic string detection, or tap a string | Key, tempo and chords from your own audio files |
| Reference pitch 415–466 Hz | Draft tabs from audio, with tuning, capo and strumming |
| English, French, German, Spanish, Italian | Tab editor, import (Guitar Pro, MusicXML) and printing |

## Privacy

- No internet permission. The app cannot send anything.
- The microphone works only while the tuner is open. Nothing is recorded.
- No song database: you work only with music you own. See [docs/COPYRIGHT.md](docs/COPYRIGHT.md).

Details: [PRIVACY.md](PRIVACY.md).

## How it works

- Pitch detection: YIN algorithm, in a Kotlin Multiplatform module (`shared/`) with tests on synthetic guitar sounds.
- Tabs from audio: a research prototype measures what is possible today. Results and limits: [research/transcription/](research/transcription/README.md).
- All sources: [docs/SCIENCE.md](docs/SCIENCE.md).

## Build

Requirements: JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew :shared:jvmTest assembleDebug
```

## Contribute

Bug reports, translations and code are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## AI-assisted

This project is developed with AI assistance. The maintainer reviews, tests and is responsible for all code.

## License

[GPL-3.0-or-later](LICENSE). Font: Nunito ([OFL](LICENSE-font-Nunito.txt)). Basic Pitch model (research only): Apache-2.0, Spotify.
