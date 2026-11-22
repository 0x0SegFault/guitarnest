# Security policy

## Reporting a vulnerability

**Do not open a public issue.** Report it privately:
[Report a vulnerability](https://github.com/0x0SegFault/guitarnest/security/advisories/new) (GitHub private vulnerability reporting).

Include the version, the steps to reproduce and the impact. You get a reply as soon as possible, and credit in the release notes if you want it.

## Supported versions

Only the latest release gets security fixes.

## Security design

- No `INTERNET` permission: the app cannot send data anywhere. CI checks the permission list on every build.
- The microphone is used only while the tuner is open. Audio stays in memory and is never written to a file.
- App data is excluded from Android cloud backup and device-to-device transfer.
