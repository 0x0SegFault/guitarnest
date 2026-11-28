# Privacy policy

GuitarNest has no account, no ads, no analytics and no trackers. Your songs, tabs and practice history stay on your phone.

## Permissions

| Permission | Why | When |
|---|---|---|
| Microphone (`RECORD_AUDIO`) | The tuner hears your guitar; Tab from audio records you playing; capture from other apps needs it too. | Only while the tuner is open or while you record. The sound stays in memory and is not saved. You can refuse it; the rest of the app works. |
| Foreground service, media projection (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`) | "From another app": Android shows its own consent screen and a notification while the sound of other apps is captured. | Only after you tap Start in Android's dialog, until you stop. Apps that do not allow capture give silence. |
| Internet (`INTERNET`) | Song search, free scores and your own AI connection. | Only when you tap Search, a free score, or Make. The app sends nothing on its own. |

No other permissions. CI fails if a new permission appears.

## What is sent, and to whom

| Action | Sent | To |
|---|---|---|
| Find a song | Your search words and the app name | [MusicBrainz](https://musicbrainz.org) (open music database, MetaBrainz Foundation). See their [privacy policy](https://metabrainz.org/privacy). |
| Find the title of a recording (off by default) | A fingerprint of the sound (a list of numbers, not the audio), its length and the app key | [AcoustID](https://acoustid.org) (AcoustID OÜ, Estonia, EU). See their [web service terms](https://acoustid.org/webservice). |
| Free scores | Nothing while you search: the list of free scores is in the app. When you tap a score: a download request for that score's MIDI file | [Mutopia Project](https://www.mutopiaproject.org) (volunteer project; scores in the public domain or under Creative Commons). |
| Chords (AI) on a search result | The song's title and artist, and the level | The address you set in Settings → AI connection, as for Ask AI. |
| Ask AI / Make a tab with AI | The song you selected (as a GuitarNest tab), the level and your request | The address you set in Settings → AI connection: your own computer, your own server, or the cloud service you chose. Their privacy policy applies. |
| Report a problem or idea (anonymous) | Kind, title and text you write; if you leave them on: app version, device model and system version, the last crash | GuitarNest's report service at Scaleway (Paris, EU). It never reads request headers, so it does not see or store your IP address; nothing is logged. Nothing about you is kept. The report becomes an issue in a private repository. |
| Sign in with OpenRouter | Nothing from GuitarNest; you sign in on openrouter.ai, which returns a key to the app | OpenRouter. Their privacy policy applies. The key is stored encrypted on your phone; you can delete it on openrouter.ai. |
| Search for tabs in the browser | Artist and title | Opens your browser with a DuckDuckGo search. The app itself downloads nothing. |

Audio is never sent: notes are found on this phone. Crashes are kept on the phone only; GuitarNest asks before sending one. Your AI gets only the draft tab. Your other songs, your practice history and your settings are never sent.

## iPhone

The iPhone app works the same way, with these differences: the microphone asks for permission the first time (Settings → GuitarNest → Microphone); iOS does not let apps capture other apps' sound, so "From another app" does not exist; the AI key is in the iOS Keychain (this device only); app data is excluded from iCloud backup.

## Data on your phone

- Songs (`.gnt` files), practice history and settings are in the app's private storage.
- The AI API key is encrypted with a key held by the Android Keystore.
- Android cloud backup and device transfer are off for this app. Use Export file to keep a copy of a song.

## Contact

Questions: [open an issue](https://github.com/0x0SegFault/guitarnest/issues).
