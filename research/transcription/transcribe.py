"""Audio to notes with Basic Pitch (Bittner et al. 2022, arXiv:2203.09893), ONNX model, no TensorFlow."""
from pathlib import Path

import numpy as np
import onnxruntime as ort

from music import Note

SR = 22050
HOP = 256
FPS = SR / HOP
WINDOW = SR * 2 - HOP
OVERLAP_FRAMES = 30
OVERLAP = OVERLAP_FRAMES * HOP
FRAMES_PER_WINDOW = 172
# Step by the frames kept per window, not WINDOW - OVERLAP: the 188-sample difference
# makes the note times drift by about 5 ms per second of audio.
STEP = (FRAMES_PER_WINDOW - OVERLAP_FRAMES) * HOP
MODEL = Path(__file__).parent / "models" / "nmp.onnx"


def activations(audio: np.ndarray):
    """Frame-wise note and onset probabilities, shape (frames, 88). Bin 0 is MIDI 21."""
    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    padded = np.concatenate([np.zeros(OVERLAP // 2, np.float32), audio.astype(np.float32)])
    windows = []
    for i in range(0, len(padded), STEP):
        w = padded[i:i + WINDOW]
        windows.append(np.pad(w, (0, WINDOW - len(w))))
    x = np.stack(windows)[:, :, None]
    note, onset = session.run(["StatefulPartitionedCall:1", "StatefulPartitionedCall:2"], {"serving_default_input_2:0": x})
    assert note.shape[1] == FRAMES_PER_WINDOW, note.shape
    half = OVERLAP_FRAMES // 2
    frames = int(len(audio) * FPS / SR)

    def unwrap(a):
        return a[:, half:-half, :].reshape(-1, a.shape[2])[:frames]

    return unwrap(note), unwrap(onset)


def notes(audio: np.ndarray, onset_threshold=0.5, frame_threshold=0.3, min_frames=6) -> list[Note]:
    """Onset peaks start a note; it lasts while the frame activation stays above the threshold."""
    frame, onset = activations(audio)
    out = []
    for b in range(onset.shape[1]):
        o = onset[:, b]
        peaks = np.where((o[1:-1] > onset_threshold) & (o[1:-1] >= o[:-2]) & (o[1:-1] > o[2:]))[0] + 1
        for p in peaks:
            end = p
            while end + 1 < len(frame) and frame[end + 1, b] > frame_threshold:
                end += 1
            if end - p < min_frames:
                continue
            # A ringing note often shows a weak onset when another note starts. Keep it only if
            # the onset is strong or the note gets louder (a real re-pluck).
            before = frame[max(0, p - 4), b]
            rise = frame[min(len(frame) - 1, p + 3), b] - before
            if before > 0.5 and o[p] < 0.76 and rise < 0.17:
                continue
            # Sub-frame onset time from a parabola through the peak.
            a, c, d = o[p - 1], o[p], o[p + 1]
            denominator = a - 2 * c + d
            shift = 0.5 * (a - d) / denominator if denominator else 0.0
            out.append(Note((p + shift) / FPS, b + 21, (end - p) / FPS, float(c)))
    # A string cannot restart within 60 ms: keep the stronger of two close onsets on one pitch.
    out.sort(key=lambda n: (n.midi, n.time))
    kept = []
    for n in out:
        if kept and kept[-1].midi == n.midi and n.time - kept[-1].time < 0.06:
            if n.strength > kept[-1].strength:
                kept[-1] = n
            continue
        kept.append(n)
    return sorted(kept, key=lambda n: (n.time, n.midi))
