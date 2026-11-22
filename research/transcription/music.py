"""Shared definitions: tunings, notes, tab events."""
from dataclasses import dataclass

NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]


def name(midi: int) -> str:
    return NAMES[midi % 12] + str(midi // 12 - 1)


# Open strings, lowest first. Same presets as the app (shared/.../Tuning.kt).
TUNINGS = {
    "standard": [40, 45, 50, 55, 59, 64],
    "half_step_down": [39, 44, 49, 54, 58, 63],
    "whole_step_down": [38, 43, 48, 53, 57, 62],
    "drop_d": [38, 45, 50, 55, 59, 64],
    "drop_c": [36, 43, 48, 53, 57, 62],
    "dadgad": [38, 45, 50, 55, 57, 62],
    "open_d": [38, 45, 50, 54, 57, 62],
    "open_g": [38, 43, 50, 55, 59, 62],
    "open_e": [40, 47, 52, 56, 59, 64],
}


@dataclass
class TabNote:
    """One note in a tab. String 0 is the lowest. Fret is counted from the capo."""
    time: float
    string: int
    fret: int
    duration: float = 0.5


@dataclass
class Note:
    """A transcribed note: pitch and time only."""
    time: float
    midi: int
    duration: float
    strength: float = 1.0


def tab_to_notes(tab, tuning, capo):
    return sorted(
        (Note(n.time, tuning[n.string] + capo + n.fret, n.duration) for n in tab),
        key=lambda n: (n.time, n.midi),
    )
