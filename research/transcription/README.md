# Audio-to-tab research prototype

This prototype measures how accurate a tab from audio can be. It is not part of the app yet.

## Pipeline

1. **Test audio.** `synth.py` makes guitar audio from a known tab (Karplus-Strong plucked strings). The test pieces in `pieces.py` were written for this test. They are not from any song.
2. **Notes.** `transcribe.py` runs [Basic Pitch](https://github.com/spotify/basic-pitch) (Spotify, Apache-2.0, 230 KB ONNX model) on the CPU, then finds note starts.
3. **Tab.** `fingering.py` gives each note a string and a fret with dynamic programming over hand positions. It tries every tuning and capo (0–7) and keeps the cheapest.
4. **Strumming.** `evaluate.py` finds the stroke direction from the order of the string onsets in each strum.

## Run

```sh
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
./fetch_model.sh
.venv/bin/python evaluate.py        # optional: a random seed, for example 11
```

## Results (4 random seeds, 2026-11-22)

| Piece | Notes | Note F1 | Tab correct¹ | Tuning + capo found | Stroke direction |
|---|---|---|---|---|---|
| Melody, standard | 24 | 0.85–0.90 | 67 % | 4/4 | – |
| Strummed chords, capo 2 | 222 | 0.89–0.92 | 100 % | 4/4 | 85–94 % |
| Power-chord riff, drop D | 30 | 0.88–0.97 | 83 % | 4/4 | – |
| Fingerpicked arpeggio | 64 | 0.76–0.83 | 100 % | 4/4 | – |
| Downstrokes, half step down | 88 | 0.79–0.84 | 100 % | 3/4 | 31–75 % |

¹ With the correct notes, tuning and capo given. Note F1: same pitch, start within 50 ms.

## What we learned

- **Window step.** The note times drifted by about 5 ms per second when the window step was not equal to the frames kept per window (142 × 256 samples). The fix raised the strum F1 from 0.48 to 0.90. Upstream Basic Pitch uses the same step formula; check this before reporting it.
- **Ringing notes** give weak false onsets when a new note starts. A filter (weak onset and no level rise) raised the arpeggio precision from 0.47 to about 0.65.
- **Same pitch, many places.** A melody can be played in several positions. The tab is playable but not always the one the author used (67 %). The app must let the player move notes to another string.
- **Some tunings sound the same.** Power chords on the low strings are the same in drop D and DADGAD. The app shows its guess and the player confirms it.
- **Stroke direction** works on mixed down/up patterns (about 90 %) but is weak on all-downstroke patterns. Onset times are at the limit of the model resolution (11.6 ms frames).

## Limits

The test audio is clean, synthetic and guitar only. Real recordings add voice, drums, bass, effects and room sound. Next steps:

1. Test on real guitar recordings with known tabs (GuitarSet, CC BY 4.0).
2. Add source separation for full songs, and measure its cost on a phone.
3. Find the tempo and the beat grid, then the strumming pattern per bar.
