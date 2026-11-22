"""Tempo, beat grid, chords and strumming from transcribed notes.

Methods are simple and documented, so a player (or an AI agent) can check and correct them:
- tempo: autocorrelation of the onset train, with a mild preference for 70-140 BPM;
- chords: pitch-class profile per beat matched against chord templates;
- strumming: strums placed on an eighth-note grid; on-beat strokes are down, off-beat strokes up
  (the strumming hand moves down on the beat and up between beats).
"""
from collections import Counter
from dataclasses import dataclass

import numpy as np

from .fingering import groups
from .music import Note

NAMES = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
TEMPLATES = {"": (0, 4, 7), "m": (0, 3, 7), "7": (0, 4, 7, 10), "m7": (0, 3, 7, 10), "5": (0, 7)}
# Small cost for less common chord types, so plain triads win ties.
TYPE_COST = {"": 0.0, "m": 0.0, "7": 0.15, "m7": 0.15, "5": 0.3}


@dataclass
class Grid:
    bpm: float
    first_beat: float  # time of the first downbeat, seconds
    beats_per_bar: int = 4

    @property
    def beat(self) -> float:
        return 60.0 / self.bpm

    def beat_time(self, i: int) -> float:
        return self.first_beat + i * self.beat


def onset_strengths(notes: list[Note]) -> list[tuple[float, float]]:
    """(time, weight) per strum or note group; a big strum weighs more than a single note."""
    return [(g[0].time, float(len(g))) for g in groups(notes)]


def estimate_tempo(notes: list[Note], low=50, high=200) -> float:
    events = onset_strengths(notes)
    if len(events) < 4:
        return 90.0
    rate = 100  # 10 ms resolution
    end = int(max(t for t, _ in events) * rate) + 2
    train = np.zeros(end)
    for t, w in events:
        train[int(t * rate)] += w
    train = np.convolve(train, np.hanning(5), mode="same")
    best, best_score = 90.0, -1.0
    for bpm in np.arange(low, high, 0.5):
        lag = 60.0 / bpm * rate
        lo, hi = int(np.floor(lag)), int(np.ceil(lag))
        if hi >= len(train):
            continue
        frac = lag - lo
        score = (1 - frac) * np.dot(train[:-lo or None], train[lo:]) + frac * np.dot(train[:-hi or None], train[hi:])
        # Mild prior: most songs are 70-140 BPM; avoids picking half or double tempo.
        score *= np.exp(-0.5 * (np.log2(bpm / 100.0) / 0.6) ** 2)
        if score > best_score:
            best, best_score = bpm, score
    return float(best)


def beat_grid(notes: list[Note], bpm: float, beats_per_bar: int = 4) -> Grid:
    """Fits tempo and phase together: a small tempo error adds up over a song (0.5 BPM is 0.18 s after 48 beats)."""
    events = onset_strengths(notes)
    times = np.array([t for t, _ in events])
    weights = np.array([w for _, w in events])
    first = times.min()
    best = (-1.0, bpm, 0.0)
    for b in np.arange(bpm - 2, bpm + 2.01, 0.05):
        beat = 60.0 / b
        for phase in np.arange(0, beat, 0.005):
            d = (times - first - phase) / beat
            dist = np.abs(d - np.round(d)) * beat
            score = float(np.sum(weights * np.exp(-((dist / 0.025) ** 2))))
            if score > best[0]:
                best = (score, b, phase)
    _, bpm, phase = best
    beat = 60.0 / bpm
    start = first + phase
    while start - beat >= first - 0.04:
        start -= beat
    return Grid(float(bpm), float(start), beats_per_bar)


def chroma(notes: list[Note], start: float, end: float) -> np.ndarray:
    c = np.zeros(12)
    lowest = None
    for n in notes:
        overlap = min(end, n.time + n.duration) - max(start, n.time)
        if overlap <= 0:
            continue
        c[n.midi % 12] += overlap * n.strength
        if lowest is None or n.midi < lowest:
            lowest = n.midi
    if lowest is not None:
        c[lowest % 12] *= 1.5  # the bass note is usually the root
    return c / c.sum() if c.sum() > 0 else c


def name_chord(c: np.ndarray) -> str | None:
    if c.sum() == 0:
        return None
    best, score = None, -9.0
    for root in range(12):
        for kind, tones in TEMPLATES.items():
            mask = np.zeros(12)
            for t in tones:
                mask[(root + t) % 12] = 1
            s = c[mask == 1].sum() - c[mask == 0].sum() - TYPE_COST[kind] * 0.2
            if s > score:
                best, score = NAMES[root] + kind, s
    return best


def chords_per_beat(notes: list[Note], grid: Grid, beats: int) -> list[str | None]:
    return [name_chord(chroma(notes, grid.beat_time(i), grid.beat_time(i + 1))) for i in range(beats)]


def align_downbeat(per_beat: list[str | None], beats_per_bar: int) -> int:
    """Beat offset (0..beats_per_bar-1) where chord changes most often fall on the bar line."""
    changes = [i for i in range(1, len(per_beat)) if per_beat[i] and per_beat[i] != per_beat[i - 1]]
    counts = Counter(i % beats_per_bar for i in changes)
    return counts.most_common(1)[0][0] if counts else 0


def strum_steps(notes: list[Note], grid: Grid, bar: int, per_beat: int = 2, min_notes: int = 3) -> str:
    """Pattern of one bar: D on hit on-beat steps, U on hit off-beat steps, - elsewhere."""
    steps = grid.beats_per_bar * per_beat
    step_len = grid.beat / per_beat
    bar_start = grid.beat_time(bar * grid.beats_per_bar)
    hit = [False] * steps
    for g in groups(notes):
        if len(g) < min_notes:
            continue
        pos = (g[0].time - bar_start) / step_len
        k = int(round(pos))
        if 0 <= k < steps and abs(pos - k) < 0.3:
            hit[k] = True
    return "".join(("D" if k % 2 == 0 else "U") if h else "-" for k, h in enumerate(hit))
