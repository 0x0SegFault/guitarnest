#!/usr/bin/env python3
"""Create the files for a new language.

Usage: python3 scripts/new_language.py <code>
  <code> is an Android language code, e.g. pt, nl, pt-rBR (Brazil), zh-rCN.

Creates:
  app/src/main/res/values-<code>/strings.xml   (copy of English: translate each text)
  fastlane/metadata/android/<store code>/       (store texts: title, descriptions)
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if len(sys.argv) != 2:
    sys.exit(__doc__)
code = sys.argv[1]

res = ROOT / f"app/src/main/res/values-{code}"
if res.exists():
    sys.exit(f"{res} already exists")
res.mkdir(parents=True)
english = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
# app_name stays "GuitarNest" in every language.
english = "\n".join(l for l in english.splitlines() if 'translatable="false"' not in l) + "\n"
(res / "strings.xml").write_text(english, encoding="utf-8")

# Store folders use codes like pt-BR, nl-NL.
store_code = code.replace("-r", "-")
store = ROOT / f"fastlane/metadata/android/{store_code}"
if not store.exists():
    shutil.copytree(ROOT / "fastlane/metadata/android/en-US", store, ignore=shutil.ignore_patterns("images"))

print(f"Created {res.relative_to(ROOT)}/strings.xml and {store.relative_to(ROOT)}/")
print("Translate the texts, then run: python3 scripts/check_translations.py")
