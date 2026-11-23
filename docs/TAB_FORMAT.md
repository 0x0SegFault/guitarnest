# GuitarNest tab format (.gnt)

A `.gnt` file is UTF-8 JSON. It is open: other apps and tools may read and write it. Unknown fields are ignored, so new versions can add fields.

```json
{
  "format": 1,
  "title": "Four chords exercise",
  "artist": "GuitarNest",
  "source": "Written for GuitarNest",
  "level": "beginner",
  "tuning": "standard",
  "capo": 0,
  "tempo": 84,
  "beatsPerBar": 4,
  "patterns": [{ "id": "folk", "name": "Folk", "steps": "D-DU-UDU", "perBeat": 2 }],
  "sections": [
    {
      "name": "Verse", "repeat": 2, "strum": "folk",
      "bars": [
        { "chords": [{ "beat": 0, "name": "G" }, { "beat": 2, "name": "C" }] },
        { "chords": [{ "beat": 0, "name": "D" }],
          "beats": [{ "at": 0, "notes": [{ "string": 4, "fret": 0 }] },
                    { "at": 1.5, "notes": [{ "string": 3, "fret": 2, "tech": "h" }] }] }
      ]
    }
  ],
  "notes": "Free text for the player."
}
```

| Field | Meaning |
|---|---|
| `format` | Format version. This page describes version 1. |
| `level` | `beginner`, `intermediate` or `advanced`. |
| `tuning` | A preset (`standard`, `half_step_down`, `whole_step_down`, `drop_d`, `drop_c`, `dadgad`, `open_d`, `open_g`, `open_e`) or six notes from low to high, for example `"D2 A2 D3 G3 B3 E4"`. |
| `capo` | 0–12. Frets and chord names are relative to the capo (the shapes the player fingers). |
| `tempo`, `beatsPerBar` | Quarter-note beats per minute (20–300) and per bar (1–12). |
| `patterns[].kind` | `strum` (default) or `pick`. |
| `patterns[].steps` | One character per step. Strumming: `D` down, `U` up, `R` rasgueado, `x` muted strum, `-` no stroke. Fingerpicking: `B` bass string of the chord, `b` alternate bass, `1`–`6` string (1 = high E), `-` rest. `perBeat`: 1 (quarters), 2 (eighths) or 4 (sixteenths). The pattern repeats over the bar. |
| `shapes` | Optional chord diagrams chosen by the player: chord name → frets from the lowest string, `x` = not played, for example `{"G": "320003", "C": "x35553"}`; frets of 10 or more are written with dashes: `"8-10-10-9-8-8"`. |
| `sections[].strum`, `bars[].strum` | Pattern id for a section, or for one bar. |
| `chords[].beat` | Beat in the bar where the chord starts, from 0. |
| `beats[].at` | Position in the bar in beats, from 0, on a sixteenth-note grid (multiples of 0.25). |
| `notes[].string` | 1 = high E … 6 = low E, as in tab notation. |
| `notes[].tech` | Optional: `h` hammer-on, `p` pull-off, `/` slide up, `\` slide down, `b` bend, `~` vibrato, `x` muted. |

Limits checked by the app: capo 0–12, tempo 20–300, frets 0–24, strings 1–6, notes inside their bar, patterns referenced by id must exist.
