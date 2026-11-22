# GuitarNest AI toolkit

Makes a GuitarNest tab (`.gnt`) from your own guitar recording: tempo, chords, tuning, capo, strumming pattern and (advanced level) the notes. It runs on your computer; your audio is not sent anywhere.

Use it alone from the command line, or let your own AI agent use it: [SKILL.md](SKILL.md) tells the agent what to do. Any agent that can run commands works (with a local or cloud model). In the app, the AI connection (Settings → AI connection) can then arrange the tab for another level.

## Install

```sh
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
./fetch_model.sh
```

## Use

```sh
.venv/bin/python -m gntab my-recording.wav --level beginner -o my-song.gnt
.venv/bin/python -m gntab.check my-song.gnt
```

Then send `my-song.gnt` to your phone and open it in GuitarNest: Songs → Import file.

## How it works

1. **Notes**: [Basic Pitch](https://github.com/spotify/basic-pitch) (Spotify, Apache-2.0) finds the notes, on the CPU.
2. **Tempo and beats**: autocorrelation of the note starts, then tempo and beat position are fitted together.
3. **Chords**: pitch classes per beat compared with chord templates (major, minor, 7, m7, 5).
4. **Tuning, capo, strings and frets**: cheapest hand path over all tunings and capo positions ([fingering.py](gntab/fingering.py)).
5. **Strumming**: strums placed on an eighth-note grid; down on the beat, up between beats.
6. **Level**: beginner gets one chord per bar in open shapes with a capo and a simple pattern; advanced gets every note.

Tests: `.venv/bin/python tests/test_toolkit.py` (synthetic audio with known answers).
