"""End-to-end check of the AI toolkit on synthetic audio with a known answer.

Run: python -m pytest tests  (or: python tests/test_toolkit.py)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gntab import arrange, transcribe  # noqa: E402
from gntab.music import TUNINGS, TabNote  # noqa: E402
from gntab.synth import render  # noqa: E402

SHAPES = {"G": [3, 2, 0, 0, 0, 3], "C": [None, 3, 2, 0, 1, 0], "D": [None, None, 0, 2, 3, 2], "Em": [0, 2, 2, 0, 0, 0]}


def strummed(chords, bpm, pattern, capo):
    """Tab for a strummed progression (written for this test), plus its sounding chord per bar."""
    eighth = 60 / bpm / 2
    tab = []
    hits = [(bar * 8 + i, c) for bar in range(len(chords)) for i, c in enumerate(pattern) if c != "-"]
    for k, (pos, d) in enumerate(hits):
        shape = SHAPES[chords[pos // 8]]
        strings = [s for s in range(6) if shape[s] is not None]
        if d == "U":
            strings = strings[-4:][::-1]
        t0 = pos * eighth
        nxt = hits[k + 1][0] * eighth if k + 1 < len(hits) else t0 + 4 * eighth
        tab += [TabNote(round(t0 + j * 0.009, 4), s, shape[s], nxt - t0 - j * 0.009) for j, s in enumerate(strings)]
    return tab


def run(level, chords=("G", "C", "D", "Em") * 3, bpm=90, pattern="D-DU-UDU", capo=2, seed=3):
    audio = render(strummed(list(chords), bpm, pattern, capo), TUNINGS["standard"], capo, seed=seed)
    return arrange.make_song(transcribe.notes(audio), level, "Test")


def bar_chords(song):
    return [b["chords"][0]["name"] if b["chords"] else None for s in song["sections"] for _ in range(s["repeat"]) for b in s["bars"]]


def test_intermediate_finds_tempo_capo_chords_and_pattern():
    song = run("intermediate")
    assert abs(song["tempo"] - 90) <= 2, song["tempo"]
    assert song["capo"] == 2
    found = bar_chords(song)
    expected = ["G", "C", "D", "Em"] * 3
    hits = sum(a == b for a, b in zip(found, expected))
    assert hits >= 10, found
    assert song["patterns"][0]["steps"] == "D-DU-UDU", song["patterns"]


def test_beginner_uses_open_shapes_and_simple_pattern():
    song = run("beginner")
    assert song["level"] == "beginner"
    assert all(len(b["chords"]) <= 1 for s in song["sections"] for b in s["bars"])
    assert set(c for c in bar_chords(song) if c) <= arrange.EASY_SHAPES
    assert song["patterns"][0]["steps"] in ("D-DU-UDU", "D-D-D-D-")


def test_sections_find_repeats():
    song = run("intermediate")
    assert song["sections"][0]["repeat"] >= 2 or len({s["name"] for s in song["sections"]}) < len(song["sections"])


def test_transpose_and_capo():
    assert arrange.transpose("F#m", -2) == "Em"
    assert arrange.transpose("Bb7", 2) == "C7"
    assert arrange.capo_for_open_shapes(["C", "G", "Am", "Em"]) == 0
    assert arrange.capo_for_open_shapes(["A", "D", "E", "F#m"]) == 2  # G C D Em shapes
    assert arrange.capo_for_open_shapes(["B", "E", "F#", "G#m"]) == 4


if __name__ == "__main__":
    for name, f in list(globals().items()):
        if name.startswith("test_"):
            f()
            print("ok", name)
