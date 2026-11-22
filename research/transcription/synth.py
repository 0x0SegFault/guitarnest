"""Plucked-string synthesis to make test audio from a tab.

Karplus & Strong 1983 (doi:10.2307/3680062), tuned with an all-pass filter as in Jaffe & Smith 1983
(doi:10.2307/3680063).
"""
import numpy as np

from music import TabNote

SR = 22050


def pluck(freq: float, seconds: float, rng, brightness=0.5, decay=0.996) -> np.ndarray:
    n = int(seconds * SR)
    period = SR / freq
    length = int(period - 0.6)
    frac = period - 0.5 - length
    c = (1 - frac) / (1 + frac)
    excitation = rng.uniform(-1, 1, length)
    # Pick position and brightness: a softer pick removes high harmonics.
    for _ in range(int((1 - brightness) * 4)):
        excitation = 0.5 * (excitation + np.roll(excitation, 1))
    line = list(excitation - excitation.mean())
    out = np.zeros(n, dtype=np.float32)
    idx, last, ap_in, ap_out = 0, 0.0, 0.0, 0.0
    for i in range(n):
        v = line[idx]
        out[i] = v
        lp = decay * 0.5 * (v + last)
        last = v
        ap = c * (lp - ap_out) + ap_in
        ap_in, ap_out = lp, ap
        line[idx] = ap
        idx = (idx + 1) % length
    return out


def render(tab: list[TabNote], tuning, capo, seed=0, noise=0.002) -> np.ndarray:
    rng = np.random.default_rng(seed)
    end = max(n.time + n.duration for n in tab) + 0.5
    audio = np.zeros(int(end * SR) + SR, dtype=np.float32)
    for n in tab:
        midi = tuning[n.string] + capo + n.fret
        freq = 440 * 2 ** ((midi - 69) / 12)
        tone = pluck(freq, n.duration, rng, brightness=rng.uniform(0.4, 0.8))
        # Short release so a note stops when the next one on the same string starts.
        fade = min(len(tone), int(0.02 * SR))
        tone[-fade:] *= np.linspace(1, 0, fade)
        start = int(n.time * SR)
        audio[start:start + len(tone)] += tone * rng.uniform(0.25, 0.4)
    audio += rng.normal(0, noise, len(audio)).astype(np.float32)
    return audio / max(1e-9, np.abs(audio).max()) * 0.8
