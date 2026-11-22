"""From analysed audio to a GuitarNest song (.gnt) for a chosen level."""
from collections import Counter

from . import analyse
from .fingering import best_setup
from .music import TUNINGS, Note

EASY_SHAPES = {"C", "A", "G", "E", "D", "Am", "Em", "Dm", "E7", "A7", "D7", "G7", "C7", "B7", "Am7", "Em7", "Dm7"}
NAMES = analyse.NAMES


def transpose(chord: str, semitones: int) -> str:
    root = 2 if len(chord) > 1 and chord[1] in "#b" else 1
    name, kind = chord[:root], chord[root:]
    index = NAMES.index(name) if name in NAMES else ["C", "Db", "D", "D#", "E", "F", "Gb", "G", "G#", "A", "A#", "B"].index(name)
    return NAMES[(index + semitones) % 12] + kind


def capo_for_open_shapes(chords: list[str]) -> int:
    """The capo (0-7) that turns the most chords into easy open shapes; ties go to the lower capo."""
    used = [c for c in chords if c]
    if not used:
        return 0
    scores = [(sum(transpose(c, -k) in EASY_SHAPES for c in used), -k) for k in range(8)]
    return -max(scores)[1]


def sections_from_bars(bars: list[dict], phrase: int = 4) -> list[dict]:
    """Groups bars into phrases; equal phrases get the same name; repeats in a row are merged."""
    phrases = [bars[i:i + phrase] for i in range(0, len(bars), phrase)]
    names, out = {}, []
    for p in phrases:
        key = tuple(tuple(c["name"] for c in b.get("chords", [])) for b in p)
        name = names.setdefault(key, "Part " + chr(ord("A") + len(names)))
        if out and out[-1]["name"] == name and out[-1]["bars"] == p:
            out[-1]["repeat"] += 1
        else:
            out.append({"name": name, "repeat": 1, "bars": p})
    return out


def make_song(notes: list[Note], level: str, title: str, artist: str = "", source: str = "") -> dict:
    bpm = analyse.estimate_tempo(notes)
    grid = analyse.beat_grid(notes, bpm)
    end = max(n.time + n.duration for n in notes)
    total_beats = int((end - grid.first_beat) / grid.beat) + 1
    per_beat = analyse.chords_per_beat(notes, grid, total_beats)
    shift = analyse.align_downbeat(per_beat, grid.beats_per_bar)
    grid = analyse.Grid(bpm, grid.beat_time(shift), grid.beats_per_bar)
    per_beat = per_beat[shift:]
    bar_count = (len(per_beat) + grid.beats_per_bar - 1) // grid.beats_per_bar

    _, tuning_id, capo, tab = best_setup(notes)[0]
    tuning = TUNINGS[tuning_id]

    bars, patterns = [], Counter()
    for b in range(bar_count):
        beats = per_beat[b * grid.beats_per_bar:(b + 1) * grid.beats_per_bar]
        chords, last = [], None
        for i, c in enumerate(beats):
            if c and c != last:
                chords.append({"beat": i, "name": c})
                last = c
        if level == "beginner":
            common = Counter(c for c in beats if c).most_common(1)
            chords = [{"beat": 0, "name": common[0][0]}] if common else []
        bar = {"chords": chords}
        steps = analyse.strum_steps(notes, grid, b)
        if steps.strip("-"):
            patterns[steps] += 1
        if level == "advanced":
            start = grid.beat_time(b * grid.beats_per_bar)
            beats_out = {}
            for n in tab:
                at = round((n.time - start) / grid.beat * 4) / 4
                if 0 <= at < grid.beats_per_bar:
                    beats_out.setdefault(at, {})[6 - n.string] = n.fret
            bar["beats"] = [{"at": at, "notes": [{"string": s, "fret": f} for s, f in sorted(ns.items())]} for at, ns in sorted(beats_out.items())]
        bars.append(bar)

    # Chord names are as they sound. With a capo, show the shapes the player fingers.
    sounding = [c["name"] for bar in bars for c in bar["chords"]]
    if level == "beginner" and tuning_id == "standard":
        capo = capo_for_open_shapes(sounding)
    for bar in bars:
        for c in bar["chords"]:
            c["name"] = transpose(c["name"], -capo)

    main = patterns.most_common(1)[0][0] if patterns else "D-D-D-D-"
    if level == "beginner":
        main = "D-DU-UDU" if main.count("U") >= 2 else "D-D-D-D-"
    song_patterns = [{"id": "main", "name": "Main", "steps": main, "perBeat": 2}]
    if level == "beginner":
        for bar in bars:
            bar.pop("beats", None)

    sections = sections_from_bars(bars)
    for s in sections:
        s["strum"] = "main"
    return {
        "format": 1, "title": title, "artist": artist, "source": source or "AI toolkit, from my recording",
        "level": level, "tuning": tuning_id, "capo": capo, "tempo": int(round(bpm)), "beatsPerBar": grid.beats_per_bar,
        "patterns": song_patterns, "sections": sections,
        "notes": "Made from a recording with the GuitarNest AI toolkit. Tempo, chords, capo and strumming are estimates: check them by ear.",
    }
