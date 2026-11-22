# Roadmap

Goal: learn songs on the guitar with tabs, strumming patterns and a metronome.

## Rules

- No third-party tabs in the app or the repository. Tabs come from the player: imported files they own, tabs they write, or tabs an AI makes for them from their own audio.
- The app stores everything on the phone. An AI never connects to the app over the network: it makes a tab file, and the player opens it in GuitarNest.

## 1. Tab format and library
- Open tab format (`.gnt`, JSON, documented): song, artist, tuning, capo, tempo, time signature, sections, bars, notes, chords, strumming patterns, level.
- Library: list, search, sort; import with the Android file picker (no permission needed); export.
- Print and save as PDF with the Android print service, marked "For personal practice only".

## 2. Metronome and strumming
- Metronome: 30–250 BPM, accents, tap tempo, sample-accurate timing.
- Strumming patterns: show down and up strokes on the beat grid, play them with the metronome, start slow and speed up step by step.

## 3. Play along
- Tab view that follows the metronome; loop a section; slow down.
- Practice log on the phone (method: [SCIENCE.md](SCIENCE.md)).

## 4. Tab editor
- Write tabs and strumming patterns in the app.
- Import Guitar Pro and MusicXML files.

## 5. AI tab maker (outside the app)
- A toolkit in `ai/`: transcribe the player's own audio (notes, chords, tempo, tuning, capo, strumming), then write a `.gnt` tab for a chosen level:
  - beginner: open chords and a simple strumming pattern
  - intermediate: full chords, riffs, real strumming pattern
  - advanced: the full part as played
- A skill file that tells any AI agent how to use the toolkit. The agent runs on the player's computer; the player sends the `.gnt` file to the phone and opens it.

## 6. Song search (optional internet)
- Search song details (title, artist, length) in MusicBrainz (open data), only when the player taps Search.
- This adds the internet permission; the app says so in Permissions and PRIVACY.md. No tabs are downloaded.
