package app.guitarnest.songbook

/**
 * The songs of the song book. Melodies only, no lyrics. Each melody is in the public domain in the EU
 * (author died 70+ years ago, or traditional) and in the US (published before 1931). The chords and
 * arrangements are GuitarNest's own. Melodies are written in guitar pitch (one octave above sound).
 */
internal object SongBookData {
    val SONGS = listOf(
        BookSong(
            "ode_to_joy", "Ode to Joy", "Ludwig van Beethoven (1824)", "Beethoven died in 1827", 100, 4,
            "E4:1 E4:1 F4:1 G4:1 | G4:1 F4:1 E4:1 D4:1 | C4:1 C4:1 D4:1 E4:1 | E4:1.5 D4:0.5 D4:2 | " +
                "E4:1 E4:1 F4:1 G4:1 | G4:1 F4:1 E4:1 D4:1 | C4:1 C4:1 D4:1 E4:1 | D4:1.5 C4:0.5 C4:2 | " +
                "D4:1 D4:1 E4:1 C4:1 | D4:1 E4:0.5 F4:0.5 E4:1 C4:1 | D4:1 E4:0.5 F4:0.5 E4:1 D4:1 | C4:1 D4:1 G3:2 | " +
                "E4:1 E4:1 F4:1 G4:1 | G4:1 F4:1 E4:1 D4:1 | C4:1 C4:1 D4:1 E4:1 | D4:1.5 C4:0.5 C4:2",
            "C . . . | G . . . | C . . . | G . . . | C . . . | G . . . | C . . . | G . C . | " +
                "G . C . | G . C . | G . C . | C . G . | C . . . | G . . . | C . . . | G . C .",
        ),
        BookSong(
            "twinkle", "Twinkle, Twinkle, Little Star", "Traditional (France, 1761)", "traditional melody", 90, 4,
            "C4:1 C4:1 G4:1 G4:1 | A4:1 A4:1 G4:2 | F4:1 F4:1 E4:1 E4:1 | D4:1 D4:1 C4:2 | " +
                "G4:1 G4:1 F4:1 F4:1 | E4:1 E4:1 D4:2 | G4:1 G4:1 F4:1 F4:1 | E4:1 E4:1 D4:2 | " +
                "C4:1 C4:1 G4:1 G4:1 | A4:1 A4:1 G4:2 | F4:1 F4:1 E4:1 E4:1 | D4:1 D4:1 C4:2",
            "C . . . | F . C . | F . C . | G . C . | C . F . | C . G . | C . F . | C . G . | " +
                "C . . . | F . C . | F . C . | G . C .",
        ),
        BookSong(
            "frere_jacques", "Frère Jacques", "Traditional (France, 18th century)", "traditional melody", 100, 4,
            "G4:1 A4:1 B4:1 G4:1 | G4:1 A4:1 B4:1 G4:1 | B4:1 C5:1 D5:2 | B4:1 C5:1 D5:2 | " +
                "D5:0.5 E5:0.5 D5:0.5 C5:0.5 B4:1 G4:1 | D5:0.5 E5:0.5 D5:0.5 C5:0.5 B4:1 G4:1 | G4:1 D4:1 G4:2 | G4:1 D4:1 G4:2",
            "G . . . | G . . . | G . C . | G . C . | G . . . | G . . . | G D G . | G D G .",
        ),
        BookSong(
            "au_clair_de_la_lune", "Au clair de la lune", "Traditional (France, 18th century)", "traditional melody", 90, 4,
            "C4:1 C4:1 C4:1 D4:1 | E4:2 D4:2 | C4:1 E4:1 D4:1 D4:1 | C4:4 | " +
                "C4:1 C4:1 C4:1 D4:1 | E4:2 D4:2 | C4:1 E4:1 D4:1 D4:1 | C4:4 | " +
                "D4:1 D4:1 D4:1 D4:1 | A3:2 A3:2 | D4:1 C4:1 B3:1 A3:1 | G3:4 | " +
                "C4:1 C4:1 C4:1 D4:1 | E4:2 D4:2 | C4:1 E4:1 D4:1 D4:1 | C4:4",
            "C . . . | C . G . | C . G . | C . . . | C . . . | C . G . | C . G . | C . . . | " +
                "Dm . . . | Dm . . . | G . . . | G . . . | C . . . | C . G . | C . G . | C . . .",
        ),
        BookSong(
            "alle_meine_entchen", "Alle meine Entchen", "Traditional (Germany)", "traditional melody", 100, 4,
            "C4:1 D4:1 E4:1 F4:1 | G4:2 G4:2 | A4:1 A4:1 A4:1 A4:1 | G4:4 | A4:1 A4:1 A4:1 A4:1 | G4:4 | " +
                "F4:1 F4:1 F4:1 F4:1 | E4:2 E4:2 | D4:1 D4:1 D4:1 D4:1 | C4:4",
            "C . . . | C . . . | F . . . | C . . . | F . . . | C . . . | F . . . | C . . . | G . . . | C . . .",
        ),
        BookSong(
            "jingle_bells", "Jingle Bells (chorus)", "James Lord Pierpont (1857)", "Pierpont died in 1893", 110, 4,
            "B4:1 B4:1 B4:2 | B4:1 B4:1 B4:2 | B4:1 D5:1 G4:1.5 A4:0.5 | B4:4 | " +
                "C5:1 C5:1 C5:1.5 C5:0.5 | C5:1 B4:1 B4:1 B4:0.5 B4:0.5 | B4:1 A4:1 A4:1 B4:1 | A4:2 D5:2 | " +
                "B4:1 B4:1 B4:2 | B4:1 B4:1 B4:2 | B4:1 D5:1 G4:1.5 A4:0.5 | B4:4 | " +
                "C5:1 C5:1 C5:1 C5:1 | C5:1 B4:1 B4:1 B4:0.5 B4:0.5 | D5:1 D5:1 C5:1 A4:1 | G4:4",
            "G . . . | G . . . | G . . . | G . . . | C . . . | G . . . | A7 . . . | D7 . . . | " +
                "G . . . | G . . . | G . . . | G . . . | C . . . | G . . . | D7 . . . | G . . .",
        ),
        BookSong(
            "amazing_grace", "Amazing Grace", "Traditional tune \"New Britain\" (USA, 1829)", "traditional melody, published 1829", 80, 3,
            "r:2 D4:1 | G4:2 B4:0.5 G4:0.5 | B4:2 A4:1 | G4:2 E4:1 | D4:2 D4:1 | G4:2 B4:0.5 G4:0.5 | B4:2 A4:0.5 B4:0.5 | D5:3 | " +
                "D5:2 B4:1 | D5:2 B4:0.5 G4:0.5 | B4:2 A4:1 | G4:2 E4:1 | D4:2 D4:1 | G4:2 B4:0.5 G4:0.5 | B4:2 A4:1 | G4:3",
            "G . . | G . . | G . . | C . . | G . . | G . . | G . . | D . . | " +
                "D . . | G . . | G . . | C . . | G . . | G . . | D . . | G . .",
        ),
        BookSong(
            "greensleeves", "Greensleeves", "Traditional (England, 16th century)", "traditional melody", 90, 3,
            "r:2 A3:1 | C4:2 D4:1 | E4:1.5 F4:0.5 E4:1 | D4:2 B3:1 | G3:1.5 A3:0.5 B3:1 | C4:2 A3:1 | A3:1.5 G#3:0.5 A3:1 | B3:2 G#3:1 | " +
                "E3:2 A3:1 | C4:2 D4:1 | E4:1.5 F4:0.5 E4:1 | D4:2 B3:1 | G3:1.5 A3:0.5 B3:1 | C4:1.5 B3:0.5 A3:1 | G#3:1.5 F#3:0.5 G#3:1 | A3:3",
            "Am . . | Am . . | C . . | G . . | Em . . | Am . . | Am . . | E . . | " +
                "E . . | Am . . | C . . | G . . | Em . . | Am . . | E . . | Am . .",
        ),
        BookSong(
            "saints", "When the Saints Go Marching In", "Traditional (USA, spiritual)", "traditional melody", 110, 4,
            "r:1 C4:1 E4:1 F4:1 | G4:4 | r:1 C4:1 E4:1 F4:1 | G4:4 | r:1 C4:1 E4:1 F4:1 | G4:2 E4:2 | C4:2 E4:2 | D4:4 | " +
                "r:1 E4:1 E4:1 D4:1 | C4:3 C4:1 | E4:2 G4:2 | G4:1 F4:3 | r:1 E4:1 F4:1 G4:1 | E4:2 C4:2 | D4:4 | C4:4",
            "C . . . | C . . . | C . . . | C . . . | C . . . | C . . . | C . . . | G . . . | " +
                "C . . . | C . . . | C . . . | F . . . | C . . . | C . . . | G . . . | C . . .",
        ),
        BookSong(
            "oh_susanna", "Oh! Susanna", "Stephen Foster (1848)", "Foster died in 1864", 110, 4,
            "r:3 C4:0.5 D4:0.5 | E4:1 G4:1 G4:1.5 A4:0.5 | G4:1 E4:1 C4:1.5 D4:0.5 | E4:1 E4:1 D4:1 C4:1 | D4:3 C4:0.5 D4:0.5 | " +
                "E4:1 G4:1 G4:1.5 A4:0.5 | G4:1 E4:1 C4:1.5 D4:0.5 | E4:1 E4:1 D4:1 D4:1 | C4:4 | " +
                "F4:2 F4:2 | A4:1 A4:2 A4:1 | G4:1 G4:1 E4:1 C4:1 | D4:3 C4:0.5 D4:0.5 | " +
                "E4:1 G4:1 G4:1.5 A4:0.5 | G4:1 E4:1 C4:1.5 D4:0.5 | E4:1 E4:1 D4:1 D4:1 | C4:4",
            "C . . . | C . . . | C . . . | C . . . | G . . . | C . . . | C . . . | C . G . | C . . . | " +
                "F . . . | F . . . | C . . . | G . . . | C . . . | C . . . | C . G . | C . . .",
        ),
        BookSong(
            "silent_night", "Silent Night", "Franz Xaver Gruber (1818)", "Gruber died in 1863", 70, 3,
            "D4:1.5 E4:0.5 D4:1 | B3:3 | D4:1.5 E4:0.5 D4:1 | B3:3 | A4:2 A4:1 | F#4:3 | G4:2 G4:1 | D4:3 | " +
                "E4:2 E4:1 | G4:1.5 F#4:0.5 E4:1 | D4:1.5 E4:0.5 D4:1 | B3:3 | E4:2 E4:1 | G4:1.5 F#4:0.5 E4:1 | D4:1.5 E4:0.5 D4:1 | B3:3 | " +
                "A4:2 A4:1 | C5:1.5 A4:0.5 F#4:1 | G4:3 | B4:3 | G4:1.5 D4:0.5 B3:1 | D4:1.5 C4:0.5 A3:1 | G3:3 | G3:3",
            "G . . | G . . | G . . | G . . | D . . | D . . | G . . | G . . | " +
                "C . . | C . . | G . . | G . . | C . . | C . . | G . . | G . . | " +
                "D . . | D . . | G . . | G . . | G . . | D . . | G . . | G . .",
        ),
        BookSong(
            "auld_lang_syne", "Auld Lang Syne", "Traditional (Scotland)", "traditional melody", 80, 4,
            "r:3 D4:1 | G4:1.5 G4:0.5 G4:1 B4:1 | A4:1.5 G4:0.5 A4:1 B4:1 | G4:1.5 G4:0.5 B4:1 D5:1 | E5:3 E5:1 | " +
                "D5:1.5 B4:0.5 B4:1 G4:1 | A4:1.5 G4:0.5 A4:1 B4:1 | G4:1.5 E4:0.5 E4:1 D4:1 | G4:4",
            "D . . . | G . . . | D . . . | G . . . | C . . . | G . . . | D . . . | Em . D . | G . . .",
        ),
        BookSong(
            "row_your_boat", "Row, Row, Row Your Boat", "Traditional (USA, 1852)", "published 1852", 160, 6,
            "C4:3 C4:3 | C4:2 D4:1 E4:3 | E4:2 D4:1 E4:2 F4:1 | G4:6 | C5:1 C5:1 C5:1 G4:1 G4:1 G4:1 | " +
                "E4:1 E4:1 E4:1 C4:1 C4:1 C4:1 | G4:2 F4:1 E4:2 D4:1 | C4:6",
            "C . . . . . | C . . . . . | C . . . . . | C . . . . . | C . . . . . | C . . . . . | G . . . . . | C . . . . .",
        ),
        BookSong(
            "mary_lamb", "Mary Had a Little Lamb", "Traditional (USA, 1830)", "traditional melody", 100, 4,
            "E4:1 D4:1 C4:1 D4:1 | E4:1 E4:1 E4:2 | D4:1 D4:1 D4:2 | E4:1 G4:1 G4:2 | " +
                "E4:1 D4:1 C4:1 D4:1 | E4:1 E4:1 E4:1 E4:1 | D4:1 D4:1 E4:1 D4:1 | C4:4",
            "C . . . | C . . . | G . . . | C . . . | C . . . | C . . . | G . . . | C . . .",
        ),
        BookSong(
            "london_bridge", "London Bridge Is Falling Down", "Traditional (England)", "traditional melody", 100, 4,
            "G4:1.5 A4:0.5 G4:1 F4:1 | E4:1 F4:1 G4:2 | D4:1 E4:1 F4:2 | E4:1 F4:1 G4:2 | " +
                "G4:1.5 A4:0.5 G4:1 F4:1 | E4:1 F4:1 G4:2 | D4:2 G4:2 | E4:1 C4:3",
            "C . . . | C . . . | G . . . | C . . . | C . . . | C . . . | G . . . | C . . .",
        ),
        BookSong(
            "drunken_sailor", "Drunken Sailor", "Traditional (sea shanty, 19th century)", "traditional melody", 120, 4,
            "A4:1 A4:0.5 A4:0.5 A4:1 A4:0.5 A4:0.5 | A4:1 D4:1 F4:1 A4:1 | G4:1 G4:0.5 G4:0.5 G4:1 G4:0.5 G4:0.5 | G4:1 C4:1 E4:1 G4:1 | " +
                "A4:1 A4:0.5 A4:0.5 A4:1 A4:0.5 A4:0.5 | A4:1 B4:1 C5:1 D5:1 | C5:1 A4:1 G4:1 E4:1 | D4:2 D4:2",
            "Dm . . . | Dm . . . | C . . . | C . . . | Dm . . . | Dm . . . | C . . . | Dm . . .",
        ),
        BookSong(
            "minuet_in_g", "Minuet in G", "Christian Petzold (about 1725)", "Petzold died in 1733", 100, 3,
            "D4:1 G3:0.5 A3:0.5 B3:0.5 C4:0.5 | D4:1 G3:1 G3:1 | E4:1 C4:0.5 D4:0.5 E4:0.5 F#4:0.5 | G4:1 G3:1 G3:1 | " +
                "C4:1 D4:0.5 C4:0.5 B3:0.5 A3:0.5 | B3:1 C4:0.5 B3:0.5 A3:0.5 G3:0.5 | F#3:1 G3:0.5 A3:0.5 B3:0.5 G3:0.5 | A3:3 | " +
                "D4:1 G3:0.5 A3:0.5 B3:0.5 C4:0.5 | D4:1 G3:1 G3:1 | E4:1 C4:0.5 D4:0.5 E4:0.5 F#4:0.5 | G4:1 G3:1 G3:1 | " +
                "C4:1 D4:0.5 C4:0.5 B3:0.5 A3:0.5 | B3:1 C4:0.5 B3:0.5 A3:0.5 G3:0.5 | A3:1 B3:0.5 A3:0.5 G3:0.5 F#3:0.5 | G3:3",
            "G . . | G . . | C . . | G . . | C . . | G . . | D . . | D . . | " +
                "G . . | G . . | C . . | G . . | C . . | G . . | D . . | G . .",
        ),
        BookSong(
            "fur_elise", "Für Elise (theme)", "Ludwig van Beethoven (1810)", "Beethoven died in 1827", 140, 3,
            "r:2 E4:0.5 D#4:0.5 | E4:0.5 D#4:0.5 E4:0.5 B3:0.5 D4:0.5 C4:0.5 | A3:1 r:0.5 C3:0.5 E3:0.5 A3:0.5 | " +
                "B3:1 r:0.5 E3:0.5 G#3:0.5 B3:0.5 | C4:1 r:0.5 E3:0.5 E4:0.5 D#4:0.5 | E4:0.5 D#4:0.5 E4:0.5 B3:0.5 D4:0.5 C4:0.5 | " +
                "A3:1 r:0.5 C3:0.5 E3:0.5 A3:0.5 | B3:1 r:0.5 E3:0.5 C4:0.5 B3:0.5 | A3:3",
            "Am . . | E . . | Am . . | E . . | Am . . | E . . | Am . . | E . . | Am . .",
        ),
        BookSong(
            "yankee_doodle", "Yankee Doodle", "Traditional (USA, 18th century)", "traditional melody", 120, 4,
            "G4:1 G4:1 A4:1 B4:1 | G4:1 B4:1 A4:1 D4:1 | G4:1 G4:1 A4:1 B4:1 | G4:2 F#4:2 | " +
                "G4:1 G4:1 A4:1 B4:1 | C5:1 B4:1 A4:1 G4:1 | F#4:1 D4:1 E4:1 F#4:1 | G4:2 G4:2",
            "G . . . | G . D . | G . . . | G . D . | G . . . | C . . . | D . . . | G . . .",
        ),
        BookSong(
            "canon_in_d", "Canon in D (chords and top line)", "Johann Pachelbel (about 1700)", "Pachelbel died in 1706", 70, 4,
            "F#4:2 E4:2 | D4:2 C#4:2 | B3:2 A3:2 | B3:2 C#4:2 | D4:2 C#4:2 | B3:2 A3:2 | G3:2 F#3:2 | G3:2 E3:2",
            "D . A . | Bm . F#m . | G . D . | G . A . | D . A . | Bm . F#m . | G . D . | G . A .",
        ),
        BookSong(
            "hot_cross_buns", "Hot Cross Buns", "Traditional (England)", "traditional melody", 100, 4,
            "E4:1 D4:1 C4:2 | E4:1 D4:1 C4:2 | C4:0.5 C4:0.5 C4:0.5 C4:0.5 D4:0.5 D4:0.5 D4:0.5 D4:0.5 | E4:1 D4:1 C4:2",
            "C G C . | C G C . | C . G . | C G C .",
        ),
        BookSong(
            "korobeiniki", "Korobeiniki", "Traditional (Russia, 1861)", "traditional melody", 120, 4,
            "E4:1 B3:0.5 C4:0.5 D4:1 C4:0.5 B3:0.5 | A3:1 A3:0.5 C4:0.5 E4:1 D4:0.5 C4:0.5 | B3:1.5 C4:0.5 D4:1 E4:1 | C4:1 A3:1 A3:2 | " +
                "r:0.5 D4:1 F4:0.5 A4:1 G4:0.5 F4:0.5 | E4:1.5 C4:0.5 E4:1 D4:0.5 C4:0.5 | B3:1 B3:0.5 C4:0.5 D4:1 E4:1 | C4:1 A3:1 A3:2",
            "Am . . . | Am . . . | E . . . | Am . . . | Dm . . . | C . . . | E . . . | Am . . .",
        ),
        BookSong(
            "old_macdonald", "Old MacDonald Had a Farm", "Traditional (USA, published 1917)", "traditional melody, published 1917", 110, 4,
            "G4:1 G4:1 G4:1 D4:1 | E4:1 E4:1 D4:2 | B4:1 B4:1 A4:1 A4:1 | G4:3 D4:1 | " +
                "G4:1 G4:1 G4:1 D4:1 | E4:1 E4:1 D4:2 | B4:1 B4:1 A4:1 A4:1 | G4:4",
            "G . . . | C . G . | G . D . | G . . . | G . . . | C . G . | G . D . | G . . .",
        ),
        BookSong(
            "lightly_row", "Lightly Row", "Traditional (Germany)", "traditional melody", 100, 4,
            "G4:1 E4:1 E4:2 | F4:1 D4:1 D4:2 | C4:1 D4:1 E4:1 F4:1 | G4:1 G4:1 G4:2 | " +
                "G4:1 E4:1 E4:2 | F4:1 D4:1 D4:2 | C4:1 E4:1 G4:1 G4:1 | E4:4 | " +
                "D4:1 D4:1 D4:1 D4:1 | D4:1 E4:1 F4:2 | E4:1 E4:1 E4:1 E4:1 | E4:1 F4:1 G4:2 | " +
                "G4:1 E4:1 E4:2 | F4:1 D4:1 D4:2 | C4:1 E4:1 G4:1 G4:1 | C4:4",
            "C . . . | G . . . | C . . . | C . . . | C . . . | G . . . | C . . . | C . . . | " +
                "G . . . | G . . . | C . . . | C . . . | C . . . | G . . . | C . . . | C . . .",
        ),
    )
}
