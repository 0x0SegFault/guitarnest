# Security policy

## Reporting a vulnerability

**Do not open a public issue.** Report it privately:
[Report a vulnerability](https://github.com/0x0SegFault/guitarnest/security/advisories/new) (GitHub private vulnerability reporting).

Include the version, the steps to reproduce and the impact. You get a reply as soon as possible, and credit in the release notes if you want it.

## Supported versions

Only the latest release gets security fixes.

## Security design

- Network only on the player's action: song search (MusicBrainz, HTTPS) and the player's own AI connection. CI checks the permission list on every build.
- System certificates only. Plain HTTP is allowed for AI servers in a home network, with a warning in the app.
- The AI API key is encrypted with AES-256-GCM; the key is held by the Android Keystore (StrongBox when available).
- AI answers are parsed as data and checked (format, ranges) before they are saved; they are never run as code.
- Imported files (.gnt, MusicXML, .mxl) are parsed without external entities or DTD loading; .mxl unpacking is size-limited.
- The microphone is used only while the tuner is open. Audio stays in memory and is never written to a file.
- App data is excluded from Android cloud backup and device-to-device transfer.
