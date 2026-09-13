<p align="center">
  <img src="docs/assets/j2megram-mark.png" width="96" alt="J2MEgram pixel-style feature phone mark">
</p>

<h1 align="center">J2MEgram</h1>

<p align="center">
  <strong>Telegram for Java ME feature phones.</strong><br>
  An experimental, unofficial client that connects from the handset itself — no relay server or web wrapper required.
</p>

<p align="center">
  <a href="https://github.com/smbdsbrain/J2MEgram/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/smbdsbrain/J2MEgram?sort=semver&label=release"></a>
  <img alt="Java ME: MIDP 2.0 and CLDC 1.1" src="https://img.shields.io/badge/Java_ME-MIDP_2.0_%C2%B7_CLDC_1.1-1769aa">
  <a href="LICENSE"><img alt="License: WTFPL" src="https://img.shields.io/badge/license-WTFPL-1769aa"></a>
</p>

<p align="center">
  <strong><a href="https://github.com/smbdsbrain/J2MEgram/releases/latest">Download the latest release</a></strong>
  · <a href="docs/compatibility.md">Check compatibility</a>
  · <a href="https://github.com/smbdsbrain/J2MEgram/issues/new?template=device-report.yml">Report your phone</a>
</p>

<p align="center">
  <img src="docs/screenshots/dialog-list.png" width="30%" alt="J2MEgram chat list on a 320x240 Java ME screen">
  <img src="docs/screenshots/weekend-chat.png" width="30%" alt="J2MEgram group chat on a Java ME screen">
  <img src="docs/screenshots/j2me-club-dark.png" width="30%" alt="J2MEgram dark theme conversation">
</p>

<p align="center"><sub>Real application UI rendered at 320×240. Names and conversations are fictional.</sub></p>

> [!IMPORTANT]
> J2MEgram is experimental and has not received a security audit. It is tested
> on a small set of physical phones, but compatibility and security properties
> on other Java ME runtimes remain unknown.

## What you can do

- Sign in with a phone number, login code and optional cloud password.
- Read and send text in private chats, groups, channels and forum topics.
- Reply, edit, forward, delete, search, react and vote in polls.
- Open photos and inspect links before handing them to the phone.
- Keep recent chats readable offline and queue outgoing messages across restarts.
- Connect over direct TCP, obfuscated transport, MTProxy/FakeTLS or HTTP fallback.
- Use keypad or touchscreen controls with light, dark and high-contrast themes.

MTProto authorization, encryption and session storage run on the phone. A proxy
can help when a carrier or handset blocks direct sockets, but J2MEgram does not
require a project-operated relay service. See the complete
[feature and limitation reference](docs/features.md) for the detailed list.

## Download

Open the **[latest release](https://github.com/smbdsbrain/J2MEgram/releases/latest)**
and download both files from one row:

| Build | Files to download | When to choose it |
|---|---|---|
| Normal | `J2MEgram-<version>.jar` and matching `.jad` | **Start here.** Crash reports retain readable class and method names. |
| Minified | `J2MEgram-<version>-min.jar` and matching `.jad` | Use when the phone rejects the normal JAR as too large. Features are unchanged. |

Do not rename the files or mix normal and minified pairs. The JAD records the
exact JAR filename and byte size. `SHA256SUMS.txt` is included for download
verification.

## Install

1. Put the matching `.jar` and `.jad` in the same folder on your computer.
2. Copy that folder to the phone by USB, Bluetooth or memory card.
3. Open the `.jad` in the phone's file manager. If local JAD installation is
   refused, open the matching `.jar` instead.
4. Allow network access when prompted, then sign in.

Opening the GitHub release in the phone browser usually cannot install the app:
GitHub requires modern TLS that these handsets do not support. Copy the files
from a modern computer instead. Upgrading from the old `TelegramJ2ME` suite and
device-specific preparation are covered in the
[installation guide](docs/installing.md).

## Compatibility

J2MEgram requires **MIDP 2.0 and CLDC 1.1**. A practical handset also needs a
JAR limit large enough for the selected build, roughly 1.5 MB of free Java heap
for useful operation, and either raw TCP sockets or the HTTP fallback. In
practice this usually means a late feature phone, roughly 2008 onwards, or an
earlier high-end model.

These phones have supplied physical-device evidence:

| Phone | What has been observed |
|---|---|
| Alcatel OT-810D | Original GPRS end-to-end run: sign-in, dialogs and sending text. |
| Samsung GT-C3592 | Production sign-in and session resume through MTProxy; exposed JPEG and second-socket limitations. |
| Nokia C3-00 | Working chats and session recovery in a 2 MB heap; upgrade and interaction paths exercised. |
| Nokia 5800 XpressMusic | 2FA sign-in, chats, reactions, photos and touch-only navigation work; live updates do not resume after later launches. |
| Nokia E51 | 2FA sign-in, live updates, sending, reactions and photos work; slow selection changes expose an `IOException` window. |
| Nokia E6-00 | Production login, chats, sends, reactions and media through FakeTLS MTProxy. |
| Fly E190 Wi-Fi | Stored-session resume, dialogs and an avatar through FakeTLS; chat history and sending are not yet recorded. |

This is evidence for those exact devices and firmware, not a compatibility
promise for a model family. Read the [compatibility matrix](docs/compatibility.md)
for routes, caveats and links to every measurement report.

An optional **J2MEgram Probe** from release 1.4.0 can measure heap, sockets,
storage, image decoding and cryptographic performance without signing in. It is
not bundled with current releases. Download its matching
[JAR](https://github.com/smbdsbrain/J2MEgram/releases/download/v1.4.0/J2MEgram-Probe-1.4.0.jar)
and [JAD](https://github.com/smbdsbrain/J2MEgram/releases/download/v1.4.0/J2MEgram-Probe-1.4.0.jad)
(about 175 KB for the JAR).

## Current limitations

- Incoming photos open; files, voice, video, GIFs and stickers are labelled but
  cannot be downloaded or played.
- Outgoing content is limited to text and poll votes.
- There are no background notifications, voice/video calls or secret chats.
- Contact management, stories and Mini Apps are absent.
- The interface is English only.
- Sessions are stored in RMS, which does not encrypt data at rest. Anyone with
  physical access and the right tools may be able to extract the session.

Some unsigned MIDlets are denied sockets on ports 80 and 443. An MTProxy on an
allowed high port may work, and FakeTLS requires the phone's date, time and time
zone to be correct. More context is in [compatibility](docs/compatibility.md)
and [architecture](docs/architecture.md#security-posture-stated-honestly).

## Help improve it

- **[Report your phone](https://github.com/smbdsbrain/J2MEgram/issues/new?template=device-report.yml)** — successful reports are as useful as failures.
- **[Report a bug](https://github.com/smbdsbrain/J2MEgram/issues/new?template=bug-report.yml)** — include the Diagnostics and Log output when possible.
- **[Ask for setup help](https://github.com/smbdsbrain/J2MEgram/issues/new?template=setup-question.yml)** or **[suggest a feature](https://github.com/smbdsbrain/J2MEgram/issues/new?template=feature-request.yml)**.

Never post your phone number, `api_id`, `api_hash` or `auth_key` in a public
issue. Security problems should be reported through the private process in
[SECURITY.md](SECURITY.md).

## For developers

The build works on Windows, Linux and macOS with JDK 8, Python 3 and PowerShell 7:

```bash
git clone https://github.com/smbdsbrain/J2MEgram.git
cd J2MEgram
./tools/bootstrap.sh
./tools/build.sh -Target tg
./tools/test.sh
```

Start with the [build guide](docs/building.md), then see the
[documentation index](docs/README.md), [architecture](docs/architecture.md)
and [contribution guide](CONTRIBUTING.md).

## License and trademark

Project code is released under the [WTFPL](LICENSE). Vendored components retain
their upstream licences. J2MEgram is independent, is not affiliated with or
endorsed by Telegram, and does not use the official Telegram logo. Telegram is
a trademark of Telegram Messenger Inc.
