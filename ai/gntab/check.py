"""Checks a .gnt file with the same rules as the app: python -m gntab.check song.gnt"""
import json
import sys

PRESETS = {"standard", "half_step_down", "whole_step_down", "drop_d", "drop_c", "dadgad", "open_d", "open_g", "open_e"}


def problems(song: dict) -> list[str]:
    out = []
    if not str(song.get("title", "")).strip():
        out.append("The title is empty")
    tuning = song.get("tuning", "standard")
    if tuning not in PRESETS and len(str(tuning).split()) != 6:
        out.append(f"Unknown tuning: {tuning}")
    if not 0 <= song.get("capo", 0) <= 12:
        out.append("Capo must be 0 to 12")
    if not 20 <= song.get("tempo", 90) <= 300:
        out.append("Tempo must be 20 to 300 BPM")
    beats = song.get("beatsPerBar", 4)
    ids = set()
    for p in song.get("patterns", []):
        ids.add(p["id"])
        if set(p["steps"]) - set("DUx-"):
            out.append(f"Pattern {p['id']}: use only D, U, x and -")
    for s in song.get("sections", []):
        for ref in [s.get("strum")] + [b.get("strum") for b in s.get("bars", [])]:
            if ref is not None and ref not in ids:
                out.append(f"Section {s.get('name')}: unknown pattern {ref}")
        for b in s.get("bars", []):
            for beat in b.get("beats", []):
                if not 0 <= beat["at"] < beats:
                    out.append(f"Section {s.get('name')}: a note is outside its bar")
                for n in beat["notes"]:
                    if not 1 <= n["string"] <= 6 or not 0 <= n["fret"] <= 24:
                        out.append(f"Section {s.get('name')}: string {n['string']} fret {n['fret']} is not valid")
    return out


if __name__ == "__main__":
    found = problems(json.load(open(sys.argv[1], encoding="utf-8")))
    print("\n".join(found) if found else "OK")
    sys.exit(1 if found else 0)
