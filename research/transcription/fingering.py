"""Notes to tab: choose string and fret for each note, and guess the tuning and capo.

Dynamic programming over note groups (notes that start together), with a hand-position cost:
the optimum path idea of Sayegh 1989 (doi:10.2307/3680014).
"""
import itertools

from music import TUNINGS, Note, TabNote

MAX_FRET = 15
GROUP_GAP = 0.06  # notes closer than this belong to one strum or chord


def groups(notes: list[Note]) -> list[list[Note]]:
    out = []
    for n in sorted(notes, key=lambda n: n.time):
        if out and n.time - out[-1][0].time < GROUP_GAP:
            out[-1].append(n)
        else:
            out.append([n])
    return out


def candidates(midi, tuning, capo):
    return [(s, midi - tuning[s] - capo) for s in range(6) if 0 <= midi - tuning[s] - capo <= MAX_FRET]


def shape_cost(choice):
    fretted = [f for _, f in choice if f > 0]
    if not fretted:
        return 0.0, None
    low, high = min(fretted), max(fretted)
    span = high - low
    cost = span * 1.0 + (20.0 if span > 4 else 0.0) + 0.25 * low + 0.15 * len(fretted)
    return cost, (low + high) / 2


def assignments(group, tuning, capo, limit=300):
    options = [candidates(n.midi, tuning, capo) for n in group]
    if any(not o for o in options):
        return None
    out = []
    for combo in itertools.product(*options):
        if len({s for s, _ in combo}) == len(combo):
            out.append(combo)
            if len(out) >= limit:
                break
    return out or None


def fingering(notes: list[Note], tuning_id: str, capo: int):
    """Returns (cost, tab). Notes that cannot be played get a high cost and are skipped."""
    tuning = TUNINGS[tuning_id]
    total_penalty = 0.0
    states = {None: (0.0, [])}  # hand position -> (cost, path)
    for group in groups(notes):
        options = assignments(group, tuning, capo)
        if options is None:
            # Drop the notes no string can play in this tuning and capo.
            playable = [n for n in group if candidates(n.midi, tuning, capo)]
            total_penalty += 10.0 * (len(group) - len(playable))
            group = playable
            options = assignments(group, tuning, capo) if group else None
            if options is None:
                continue
        new_states = {}
        for combo in options:
            cost, position = shape_cost(combo)
            for prev, (prev_cost, path) in states.items():
                move = 0.0 if prev is None or position is None else 0.5 * abs(position - prev)
                key = position if position is not None else prev
                key = round(key * 2) / 2 if key is not None else None
                total = prev_cost + cost + move
                if key not in new_states or total < new_states[key][0]:
                    new_states[key] = (total, path + [(group, combo)])
        states = new_states
    cost, path = min(states.values(), key=lambda v: v[0])
    tab = [TabNote(n.time, s, f, n.duration) for group, combo in path for n, (s, f) in zip(group, combo)]
    return cost + total_penalty, tab


# Prior cost per tuning: common tunings first. Some cases are ambiguous from pitch alone
# (power chords on the low strings sound the same in drop D and DADGAD), so the app shows
# the guess and lets the player correct it.
TUNING_PRIOR = {
    "standard": 0.0, "drop_d": 1.5, "half_step_down": 2.0, "whole_step_down": 3.0,
    "drop_c": 3.5, "open_g": 4.0, "open_d": 4.5, "dadgad": 4.5, "open_e": 5.0,
}


def best_setup(notes: list[Note], tunings=None, capos=range(0, 8)):
    """Try every tuning and capo; priors favour common tunings and no capo."""
    results = []
    for tuning_id in tunings or TUNINGS:
        for capo in capos:
            cost, tab = fingering(notes, tuning_id, capo)
            prior = TUNING_PRIOR[tuning_id] + 1.5 * capo
            results.append((cost / max(1, len(notes)) * 10 + prior, tuning_id, capo, tab))
    results.sort(key=lambda r: r[0])
    return results
