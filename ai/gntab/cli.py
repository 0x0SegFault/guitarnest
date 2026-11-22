"""Command line: python -m gntab recording.wav --level beginner -o song.gnt"""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from scipy.io import wavfile
from scipy.signal import resample_poly

from . import transcribe
from .arrange import make_song

SR = 22050


def load_audio(path: Path) -> np.ndarray:
    """WAV directly; other formats (mp3, flac, ogg, m4a) through ffmpeg if it is installed."""
    if path.suffix.lower() == ".wav":
        rate, data = wavfile.read(path)
        data = data.astype(np.float32)
        if data.ndim > 1:
            data = data.mean(axis=1)
        if np.abs(data).max() > 1.5:
            data /= 32768.0
        if rate != SR:
            from math import gcd
            g = gcd(rate, SR)
            data = resample_poly(data, SR // g, rate // g).astype(np.float32)
        return data
    if not shutil.which("ffmpeg"):
        sys.exit("This format needs ffmpeg. Install it, or convert the file to WAV.")
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()


def main(argv=None):
    p = argparse.ArgumentParser(prog="gntab", description="Make a GuitarNest tab (.gnt) from your own guitar recording.")
    p.add_argument("audio", type=Path)
    p.add_argument("--level", choices=["beginner", "intermediate", "advanced"], default="intermediate")
    p.add_argument("--title", default=None)
    p.add_argument("--artist", default="")
    p.add_argument("-o", "--output", type=Path, default=None)
    p.add_argument("--notes-json", type=Path, default=None, help="also write the detected notes, for an AI agent to inspect")
    a = p.parse_args(argv)
    audio = load_audio(a.audio)
    notes = transcribe.notes(audio)
    if len(notes) < 8:
        sys.exit("Too few notes found. Use a clear recording of the guitar.")
    song = make_song(notes, a.level, a.title or a.audio.stem, a.artist)
    out = a.output or a.audio.with_suffix(".gnt")
    out.write_text(json.dumps(song, indent=2, ensure_ascii=False))
    if a.notes_json:
        a.notes_json.write_text(json.dumps([n.__dict__ for n in notes], indent=1))
    print(f"{out}: {song['tempo']} BPM, tuning {song['tuning']}, capo {song['capo']}, "
          f"{sum(len(s['bars']) * s['repeat'] for s in song['sections'])} bars, strum {song['patterns'][0]['steps']}")


if __name__ == "__main__":
    main()
