package app.guitarnest.tab


/** A short exercise written for GuitarNest (not from any song), shown on first launch. */
object ExampleSong {
    private const val INTRO = """
| G . . . | Em . . . |
e|-------3-------3-|-------0-------0-|
B|-----0-------0---|-----0-------0---|
G|---0-------0-----|---0-------0-----|
D|-----------------|-----------------|
A|-----------------|-2---------------|
E|-3---------------|-----------------|
"""
    private const val VERSE = "| G | C | D | Em |"
    private const val CHORUS = "| C . G . | D . . . | C . G . | D . . . |"

    private fun bars(text: String) = TextTab.parseSection(text.trim(), 4).getOrThrow()

    val song = Song(
        title = "Four chords exercise",
        artist = "GuitarNest",
        source = "Written for GuitarNest",
        level = Level.BEGINNER,
        tempo = 84,
        patterns = listOf(StrumPattern("folk", "Folk", "D-DU-UDU"), StrumPattern("downs", "Downstrokes", "D-D-D-D-")),
        sections = listOf(
            Section("Intro", bars(INTRO)),
            Section("Verse", bars(VERSE), repeat = 2, strum = "downs"),
            Section("Chorus", bars(CHORUS), strum = "folk"),
        ),
        notes = "Keep your strumming hand moving down and up all the time, also when it does not hit the strings.",
    )
}
