# Science and sources

GuitarNest uses published methods. This page lists them. Each source has a DOI or a stable link.

## Tuner

- **Pitch detection: YIN.** The tuner finds the period of the sound with the cumulative mean normalised difference function. This method makes fewer octave errors than plain autocorrelation. Octave errors matter on guitar, because the low strings often have a weaker fundamental than their second harmonic.
  de Cheveigné A, Kawahara H. *YIN, a fundamental frequency estimator for speech and music.* J Acoust Soc Am. 2002;111(4):1917–30. [doi:10.1121/1.1458024](https://doi.org/10.1121/1.1458024)
- **Notes and cents.** Equal temperament, A4 = 440 Hz by default (415–466 Hz adjustable). 100 cents = 1 semitone.
- **In tune.** The needle turns green within ±5 cents, a common tuner tolerance.
- **Smoothing.** A median of the last 5 readings removes single jumps (pick attack, octave slips).
- **Tests.** The tests use pure tones, tones with a weak fundamental, and plucked-string tones (Karplus & Strong 1983, [doi:10.2307/3680062](https://doi.org/10.2307/3680062); fine-tuned as in Jaffe & Smith 1983, [doi:10.2307/3680063](https://doi.org/10.2307/3680063)). Every standard string is found within 1 cent; plucked tones from C2 to C6 within 5 cents.

## Practice method (planned)

The practice tools will follow these findings:

- **Practise slowly, then speed up.** Slow practice is the most common practice strategy, at all levels. Musicians use it to reduce the load on attention, to build motor skills and to solve problems.
  Allingham E, Wöllner C. *Putting practice under the microscope: The perceived uses and limitations of slow instrumental music practice.* Psychology of Music. 2022. [doi:10.1177/03057356221129650](https://doi.org/10.1177/03057356221129650)
- **Fix errors at once, and work on the hard part.** The best pianists in this study stopped at errors, found the exact place, and repeated it correctly, often slower. How they practised predicted the result better than how long they practised.
  Duke RA, Simmons AL, Cash CD. *It's not how much; it's how.* J Res Music Educ. 2009;56(4):310–21. [doi:10.1177/0022429408328851](https://doi.org/10.1177/0022429408328851)
- **Spread practice over days.** Sessions separated by a night's sleep gave better accuracy than sessions on the same day.
  Simmons AL. *Distributed practice and procedural memory consolidation in musicians' skill learning.* J Res Music Educ. 2012;59(4):357–68. [doi:10.1177/0022429411424798](https://doi.org/10.1177/0022429411424798)

## Tabs from audio (research)

- **Notes from audio: Basic Pitch.** A small neural network (230 KB) that finds notes in polyphonic audio. Spotify, Apache-2.0.
  Bittner RM et al. *A lightweight instrument-agnostic model for polyphonic note transcription and multipitch estimation.* ICASSP 2022. [arXiv:2203.09893](https://arxiv.org/abs/2203.09893)
- **Strings and frets.** The cheapest path through all possible hand positions.
  Sayegh SI. *Fingering for string instruments with the optimum path paradigm.* Computer Music Journal. 1989. [doi:10.2307/3680014](https://doi.org/10.2307/3680014)
- Measured accuracy and limits: [research/transcription/README.md](../research/transcription/README.md).
