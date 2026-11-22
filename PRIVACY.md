# Privacy policy

GuitarNest has no account, no ads, no analytics and no trackers. Your songs, tabs and practice history stay on your phone.

## Permissions

| Permission | Why | When |
|---|---|---|
| Microphone (`RECORD_AUDIO`) | The tuner hears your guitar; Tab from audio records you playing; capture from other apps needs it too. | Only while the tuner is open or while you record. The sound stays in memory and is not saved. You can refuse it; the rest of the app works. |
| Foreground service, media projection (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`) | "From another app": Android shows its own consent screen and a notification while the sound of other apps is captured. | Only after you tap Start in Android's dialog, until you stop. Apps that do not allow capture give silence. |
| Internet (`INTERNET`) | Song search and your own AI connection. | Only when you tap Search or Make. The app sends nothing on its own. |

No other permissions. CI fails if a new permission appears.

## What is sent, and to whom

| Action | Sent | To |
|---|---|---|
| Find a song | Your search words and the app name | [MusicBrainz](https://musicbrainz.org) (open music database, MetaBrainz Foundation). See their [privacy policy](https://metabrainz.org/privacy). |
| Ask AI / Make a tab with AI | The song you selected (as a GuitarNest tab), the level and your request | The address you set in Settings → AI connection: your own computer, your own server, or the cloud service you chose. Their privacy policy applies. |
| Search for tabs in the browser | Artist and title | Opens your browser with a DuckDuckGo search. The app itself downloads nothing. |

Audio is never sent: notes are found on this phone. Your AI gets only the draft tab. Your other songs, your practice history and your settings are never sent.

## Data on your phone

- Songs (`.gnt` files), practice history and settings are in the app's private storage.
- The AI API key is encrypted with a key held by the Android Keystore.
- Android cloud backup and device transfer are off for this app. Use Export file to keep a copy of a song.

## Contact

Questions: [open an issue](https://github.com/0x0SegFault/guitarnest/issues).
