# Roadmap

Status on 2026-11-22: steps 1 to 6 are in the Android and iPhone apps and in the toolkit; tabs from audio run on the phone. "Next" lists what is still open.

Goal: learn songs on the guitar with tabs, strumming patterns and a metronome.

## Rules

- Never download audio, from any service. Analyse only files the player owns (bought DRM-free, own CD rips, own recordings).
- No third-party tabs in the app or the repository. Tabs come from the player: imported files they own, tabs they write, or tabs an AI makes for them from their own audio.
- The app stores everything on the phone. The AI is the player's own: the app sends a song and a request only to the address the player sets, and only when they ask. The toolkit runs on the player's computer.

## 1. Tab format and library — done
- Open tab format (`.gnt`, JSON, documented): song, artist, tuning, capo, tempo, time signature, sections, bars, notes, chords, strumming patterns, level.
- Library: list, search, sort; import with the Android file picker (no permission needed); export.
- Print and save as PDF with the Android print service, marked "For personal practice only".

## 2. Metronome and strumming — done
- Metronome: 30–250 BPM, accents, tap tempo, sample-accurate timing.
- Strumming patterns: show down and up strokes on the beat grid, play them with the metronome, start slow and speed up step by step.

## 3. Play along — done
- Tab view that follows the metronome; loop a section; slow down.
- Practice log on the phone (method: [SCIENCE.md](SCIENCE.md)).

## 4. Tab editor — done (Guitar Pro: export to MusicXML with TuxGuitar or MuseScore, then import)
- Write tabs and strumming patterns in the app.
- Import Guitar Pro and MusicXML files.

## 5. AI tab maker — done: toolkit (`ai/`) and the player's own AI connection in the app
- A toolkit in `ai/`: transcribe the player's own audio (notes, chords, tempo, tuning, capo, strumming), then write a `.gnt` tab for a chosen level:
  - beginner: open chords and a simple strumming pattern
  - intermediate: full chords, riffs, real strumming pattern
  - advanced: the full part as played
- A skill file that tells any AI agent how to use the toolkit. The agent runs on the player's computer; the player sends the `.gnt` file to the phone and opens it.

## 6. Song search (optional internet) — done
- Search song details (title, artist, length) in MusicBrainz (open data), only when the player taps Search.
- This adds the internet permission; the app says so in Permissions and PRIVACY.md. No tabs are downloaded.

## Next

- Test the toolkit on real recordings (GuitarSet, CC BY 4.0) and publish the numbers.
- Tab editor: tap-to-edit notes on the fretboard, in addition to text.
- Test on real phones and real guitars; measure listening speed on a mid-range phone.
- Optional listener server (your own computer): guitar separation for full songs (Demucs or Open-Unmix), then the same tab steps.
