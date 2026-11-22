"""Synthesise each test piece, transcribe it, make a tab, and score every step.

Run: .venv/bin/python evaluate.py
"""
import sys
import time

import numpy as np

from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "ai"))

from gntab import fingering, transcribe  # noqa: E402
from gntab.music import TUNINGS, tab_to_notes  # noqa: E402
from gntab.synth import render  # noqa: E402
from pieces import all_pieces  # noqa: E402

ONSET_TOLERANCE = 0.05  # 50 ms, as in mir_eval


def match(reference, estimate):
    """Greedy one-to-one match on equal pitch and onset within the tolerance."""
    used, pairs = set(), []
    for i, r in enumerate(reference):
        best = None
        for j, e in enumerate(estimate):
            if j in used or e.midi != r.midi:
                continue
            d = abs(e.time - r.time)
            if d <= ONSET_TOLERANCE and (best is None or d < best[0]):
                best = (d, j)
        if best:
            used.add(best[1])
            pairs.append((i, best[1]))
    return pairs


def f1(p, r):
    return 2 * p * r / (p + r) if p + r else 0.0


def stroke_direction(group_tab, tuning):
    """Down if the low strings sound first. Uses onset times of the notes in one strum."""
    if len(group_tab) < 3:
        return None
    s = np.array([n.string for n in group_tab], float)
    t = np.array([n.time for n in group_tab])
    if np.ptp(t) == 0:
        return None
    slope = np.polyfit(s, t, 1)[0]
    return "D" if slope > 0 else "U"


def main(seed=7):
    rows = []
    for piece in all_pieces():
        tuning = TUNINGS[piece["tuning"]]
        reference = tab_to_notes(piece["tab"], tuning, piece["capo"])
        audio = render(piece["tab"], tuning, piece["capo"], seed=seed)
        started = time.time()
        estimate = transcribe.notes(audio)
        seconds = time.time() - started
        pairs = match(reference, estimate)
        precision = len(pairs) / max(1, len(estimate))
        recall = len(pairs) / max(1, len(reference))

        # Tab from the perfect notes (fingering alone) and from the transcribed notes (full chain).
        setups_true = fingering.best_setup(reference)
        setups_est = fingering.best_setup(estimate)
        _, tun_est, capo_est, tab_est = setups_est[0]
        _, tab_given = fingering.fingering(reference, piece["tuning"], piece["capo"])
        ref_tab = sorted(piece["tab"], key=lambda n: (n.time, n.string))

        def tab_accuracy(tab):
            ref_pos = {(round(n.time, 2), n.string, n.fret) for n in ref_tab}
            return sum((round(n.time, 2), n.string, n.fret) in ref_pos for n in tab) / len(ref_tab)

        # Strum direction from the transcribed onsets.
        strokes_ok = strokes_n = 0
        for t0, direction in piece["strokes"]:
            group = [n for n in tab_est if t0 - 0.03 <= n.time <= t0 + 0.08]
            guess = stroke_direction(group, tuning)
            strokes_n += 1
            strokes_ok += guess == direction
        rows.append(dict(
            piece=piece["id"], notes=len(reference),
            precision=precision, recall=recall, f1=f1(precision, recall),
            tab_given_setup=tab_accuracy(tab_given),
            setup_from_true=f"{setups_true[0][1]}/{setups_true[0][2]}",
            setup_from_audio=f"{tun_est}/{capo_est}",
            setup_ok=(tun_est, capo_est) == (piece["tuning"], piece["capo"]),
            strum=f"{strokes_ok}/{strokes_n}" if strokes_n else "-",
            seconds=seconds, audio_seconds=len(audio) / 22050,
        ))
    print(f"{'piece':16} {'notes':>5} {'P':>5} {'R':>5} {'F1':>5} {'tab*':>5} "
          f"{'setup (true notes)':>20} {'setup (audio)':>20} {'ok':>3} {'strum':>6} {'speed':>6}")
    for r in rows:
        print(f"{r['piece']:16} {r['notes']:5d} {r['precision']:5.2f} {r['recall']:5.2f} {r['f1']:5.2f} "
              f"{r['tab_given_setup']:5.2f} {r['setup_from_true']:>20} {r['setup_from_audio']:>20} "
              f"{'yes' if r['setup_ok'] else 'no':>3} {r['strum']:>6} {r['audio_seconds'] / r['seconds']:5.0f}x")
    print("\ntab* = string and fret correct, with the right tuning and capo given and perfect notes.")
    print("speed = seconds of audio per second of processing (this machine, CPU).")
    return rows


if __name__ == "__main__":
    sys.exit(0 if main(int(sys.argv[1]) if len(sys.argv) > 1 else 7) else 1)
