#!/usr/bin/env python3
"""Check every translation against English.

Errors (fail CI): unknown keys, placeholders that differ from English, unescaped
apostrophes or quotes, plurals without "other", store texts over F-Droid limits.
Warnings: strings not translated yet (Android shows English for them).

Usage: python3 scripts/check_translations.py
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
FASTLANE = ROOT / "fastlane/metadata/android"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[ds]")

errors, warnings = [], []


def load(path):
    root = ET.parse(path).getroot()
    strings = {e.get("name"): e for e in root.findall("string")}
    plurals = {e.get("name"): e for e in root.findall("plurals")}
    return strings, plurals


def placeholders(text, formatted):
    if not formatted:
        return []
    return sorted(PLACEHOLDER.findall((text or "").replace("%%", "")))


def raw_text(element):
    return "".join(element.itertext())


def check_text(where, text):
    if re.search(r"(?<!\\)'", text):
        errors.append(f"{where}: escape apostrophes as \\'")
    if re.search(r'(?<!\\)"', text):
        errors.append(f'{where}: escape quotes as \\"')


en_strings, en_plurals = load(RES / "values/strings.xml")
translatable = {k: v for k, v in en_strings.items() if v.get("translatable") != "false"}

# The English file needs the same escaping (an unescaped apostrophe breaks the build).
for name, el in en_strings.items():
    check_text(f"en: {name}", raw_text(el))
for name, el in en_plurals.items():
    for item in el.findall("item"):
        check_text(f"en: {name} ({item.get('quantity')})", raw_text(item))

# Android lint (MissingQuantity) needs a "many" plural item in these languages.
NEEDS_MANY = {"fr", "es", "it", "pt", "ca"}

for path in sorted(RES.glob("values-*/strings.xml")):
    lang = path.parent.name.removeprefix("values-")
    strings, plurals = load(path)
    for name, el in strings.items():
        where = f"{lang}: {name}"
        if name not in translatable:
            errors.append(f"{where}: key does not exist in English (remove it)")
            continue
        formatted = el.get("formatted") != "false"
        if placeholders(raw_text(el), formatted) != placeholders(raw_text(translatable[name]), formatted):
            errors.append(f"{where}: placeholders must be {placeholders(raw_text(translatable[name]), True)}")
        check_text(where, raw_text(el))
    for name, el in plurals.items():
        where = f"{lang}: {name}"
        if name not in en_plurals:
            errors.append(f"{where}: plural does not exist in English (remove it)")
            continue
        items = {i.get("quantity"): raw_text(i) for i in el.findall("item")}
        if "other" not in items:
            errors.append(f'{where}: plural needs an item quantity="other"')
        if lang.split("-")[0] in NEEDS_MANY and "many" not in items:
            errors.append(f'{where}: plural needs an item quantity="many" (copy "other")')
        en_other = placeholders(raw_text(en_plurals[name].find("item[@quantity='other']")), True)
        if placeholders(items.get("other", ""), True) != en_other:
            errors.append(f"{where}: 'other' placeholders must be {en_other}")
        for q, text in items.items():
            check_text(f"{where} ({q})", text)
    missing = sorted(set(translatable) - set(strings)) + sorted(set(en_plurals) - set(plurals))
    if missing:
        warnings.append(f"{lang}: {len(missing)} not translated yet (English is shown): {', '.join(missing[:8])}"
                        + (" …" if len(missing) > 8 else ""))

LIMITS = {"title.txt": 50, "short_description.txt": 80, "full_description.txt": 4000}
for lang_dir in sorted(p for p in FASTLANE.iterdir() if p.is_dir()) if FASTLANE.is_dir() else []:
    for name, limit in LIMITS.items():
        f = lang_dir / name
        if f.exists() and len(f.read_text(encoding="utf-8").strip()) > limit:
            errors.append(f"store {lang_dir.name}/{name}: over {limit} characters")
    for f in sorted((lang_dir / "changelogs").glob("*.txt")):
        if len(f.read_text(encoding="utf-8")) > 500:
            errors.append(f"store {lang_dir.name}/changelogs/{f.name}: over 500 characters")

for w in warnings:
    print("warning:", w)
for e in errors:
    print("error:", e)
print(f"{len(errors)} error(s), {len(warnings)} warning(s)")
sys.exit(1 if errors else 0)
