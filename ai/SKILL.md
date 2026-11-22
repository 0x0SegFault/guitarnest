---
name: guitarnest-tab-maker
description: Make GuitarNest guitar tabs (.gnt) from the player's own recordings, for a chosen level (beginner, intermediate, advanced), with tempo, chords, capo, tuning and strumming pattern. Use when a player asks for a tab, chords or a strumming pattern for a song they recorded or own, or asks to make a tab easier or harder.
---

# GuitarNest tab maker

You help a guitar player learn a song. You make a tab file the player opens in the GuitarNest app (Songs → Import file).

## Rules

1. Never download audio (YouTube, Spotify or any other service). Work only from the player's material: a recording they made or own, a tab they wrote, or their description.
2. Do not write published tabs, chord sheets or lyrics from memory. If the player asks for "the tab of <song>", ask for their recording instead.
3. Say clearly that tempo, chords, capo and strumming are estimates, and ask the player to check them by ear.
4. Keep everything on the player's computer. Do not upload their audio anywhere.

## Setup (once)

```sh
cd ai
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
./fetch_model.sh          # Basic Pitch model, 230 KB, Apache-2.0
```
Formats other than WAV need `ffmpeg`.

## Steps

1. Ask: which file, which level, and anything special (for example "no barre chords", "only the intro").
2. Run:
   ```sh
   .venv/bin/python -m gntab recording.mp3 --level beginner --title "Song title" --artist "Artist" -o song.gnt --notes-json notes.json
   ```
   The line it prints gives tempo, tuning, capo, bar count and the main strumming pattern.
3. Read `song.gnt` (format: `docs/TAB_FORMAT.md`). Check that it makes sense:
   - Tempo between 60 and 180 BPM? If it looks double or half, fix `tempo`.
   - Chords: a capo makes the shapes easier; the names are the shapes the player fingers.
   - Sections: rename "Part A/B" to Verse, Chorus... if the player tells you the form.
4. Adapt it to the player's request by editing the JSON. Levels:
   - **beginner**: one chord per bar, open shapes (use a capo), one simple pattern ("D-D-D-D-" or "D-DU-UDU"), no single notes.
   - **intermediate**: chord changes inside bars, the detected pattern, short riffs.
   - **advanced**: the full part with notes (`beats`), techniques (h, p, /, b).
5. Check the file: `.venv/bin/python -m gntab.check song.gnt` must print `OK`.
6. Tell the player how to open it: send the file to the phone (cable, Nextcloud, Proton Drive, KDE Connect, e-mail to self), then in GuitarNest: Songs → Import file.

## Limits

The toolkit works best on a clear recording of one guitar. With voice, drums and bass, results get worse; say so. The measured accuracy is in `research/transcription/README.md`.
