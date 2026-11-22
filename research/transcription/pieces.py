"""Original test pieces (written for this test, not from any song). Each has a known tab, tuning and capo."""
from music import TabNote

CHORDS = {
    "G": [3, 2, 0, 0, 0, 3], "C": [None, 3, 2, 0, 1, 0], "D": [None, None, 0, 2, 3, 2],
    "Em": [0, 2, 2, 0, 0, 0], "Am": [None, 0, 2, 2, 1, 0], "F": [None, None, 3, 2, 1, 1],
    "E": [0, 2, 2, 1, 0, 0], "A": [None, 0, 2, 2, 2, 0], "B7": [None, 2, 1, 2, 0, 2],
    "D5": [0, 0, 0, None, None, None], "F5": [3, 3, 3, None, None, None],
    "G5": [5, 5, 5, None, None, None], "C5": [10, 10, 10, None, None, None],
}


def strum(chords, bpm, pattern, spread=0.009):
    """pattern: 8 characters per bar for eighth notes, D = down, U = up, - = rest.
    Returns (tab, strokes); strokes are (time, direction) for scoring."""
    eighth = 60 / bpm / 2
    tab, strokes = [], []
    hits = [(bar * 8 + i, c) for bar, ch in enumerate(chords) for i, c in enumerate(pattern) if c != "-"]
    for k, (pos, direction) in enumerate(hits):
        chord = CHORDS[chords[pos // 8]]
        strings = [s for s in range(6) if chord[s] is not None]
        if direction == "U":
            strings = strings[-4:][::-1]  # upstrokes hit the high strings first, and fewer of them
        t0 = pos * eighth
        nxt = hits[k + 1][0] * eighth if k + 1 < len(hits) else t0 + 4 * eighth
        for j, s in enumerate(strings):
            t = t0 + j * spread
            tab.append(TabNote(round(t, 4), s, chord[s], nxt - t))
        strokes.append((t0, direction))
    return tab, strokes


def arpeggio(chords, bpm, order):
    eighth = 60 / bpm / 2
    tab = []
    for bar, name in enumerate(chords):
        chord = CHORDS[name]
        for i, s in enumerate(order):
            if chord[s] is None:
                s = next(x for x in range(6) if chord[x] is not None)
            tab.append(TabNote(round((bar * 8 + i) * eighth, 4), s, chord[s], eighth * 2))
    return tab


def melody():
    notes = [(5, 0), (5, 2), (4, 0), (4, 2), (4, 4), (3, 2), (3, 0), (3, 2),
             (2, 2), (2, 0), (2, 2), (3, 0), (4, 2), (4, 0), (5, 0), (5, 3),
             (3, 5), (3, 7), (2, 5), (2, 7), (3, 5), (2, 7), (2, 5), (1, 7)]
    beat = 0.33
    return [TabNote(round(i * beat, 4), s, f, beat) for i, (s, f) in enumerate(notes)]


def power_riff():
    seq = ["D5", "D5", "F5", "D5", "G5", "F5", "D5", "D5", "C5", "D5"]
    tab, beat = [], 0.25
    for i, name in enumerate(seq):
        chord = CHORDS[name]
        for s in range(3):
            tab.append(TabNote(round(i * beat * 2 + s * 0.004, 4), s, chord[s], beat * 2))
    return tab


def all_pieces():
    strum_tab, strum_strokes = strum(["G", "C", "D", "Em"] * 2, 90, "D-DU-UDU")
    down_tab, down_strokes = strum(["E", "A", "B7", "E"], 80, "D-D-D-D-")
    return [
        dict(id="melody", tuning="standard", capo=0, tab=melody(), strokes=[]),
        dict(id="strum_capo2", tuning="standard", capo=2, tab=strum_tab, strokes=strum_strokes),
        dict(id="drop_d_riff", tuning="drop_d", capo=0, tab=power_riff(), strokes=[]),
        dict(id="arpeggio", tuning="standard", capo=0,
             tab=arpeggio(["Am", "C", "G", "F"] * 2, 100, [1, 3, 4, 5, 4, 3, 2, 3]), strokes=[]),
        dict(id="half_down_strum", tuning="half_step_down", capo=0, tab=down_tab, strokes=down_strokes),
    ]
