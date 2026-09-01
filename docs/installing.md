# Installing J2MEgram

## Choose one build pair

Every current release contains two variants of the same application:

| Build | Files | Use |
|---|---|---|
| Normal | `J2MEgram-<version>.jar` plus its matching `.jad` | Start here. Names remain readable in crash reports. |
| Minified | `J2MEgram-<version>-min.jar` plus its matching `.jad` | Use only when the phone rejects the normal JAR as too large. |

The minified build removes no user-facing features. It is optimised and
obfuscated, so stack traces contain shortened names.

Download the pair from the
[latest release](https://github.com/smbdsbrain/J2MEgram/releases/latest). Do not
rename either file or mix variants or versions: the JAD contains the exact JAR
filename and byte length. `SHA256SUMS.txt` contains checksums for verification.

## Copy and install

1. Keep both files of one pair in the same folder.
2. Copy the folder to the phone by USB, Bluetooth or memory card.
3. Open the `.jad` from the phone's file manager.
4. If local JAD installation is refused, open the matching `.jar` instead.
5. Grant network access when prompted.

Installing over the air from GitHub usually does not work because GitHub
requires modern TLS. Download on a modern computer and transfer the files
locally.

## Before connecting

- Set the phone's date, time and numeric time-zone offset. This is required for
  FakeTLS MTProxy and can be wrong even when old firmware shows the right city.
- Allow network access for the MIDlet. Some phones have a separate network
  profile for installed Java applications.
- If an unsigned MIDlet is denied sockets on ports 80 or 443, configure an
  MTProxy on an allowed high port before connecting.
- Turn off avatars and inline previews in Settings if the phone runs close to
  its memory limit.

See [device compatibility](compatibility.md) for measured phone-specific
behaviour.

## Upgrade from TelegramJ2ME 1.2.0 or earlier

J2MEgram 1.3.0 changed the MIDlet suite name to comply with the
[Telegram API Terms of Service](https://core.telegram.org/api/terms). A Java ME
suite is identified by its name and vendor, so J2MEgram installs beside an old
TelegramJ2ME suite rather than replacing it.

1. Install J2MEgram without removing TelegramJ2ME.
2. Sign in and confirm the new installation works.
3. Remove the old TelegramJ2ME suite manually.

Removing the old suite deletes its authorization key, settings, outbox, drafts
and caches. Do it only after the J2MEgram sign-in succeeds.

From J2MEgram 1.3.0 onwards, the suite name and vendor stay fixed. When the
phone offers `replace/update` and `remove`, choose the update option to preserve
RMS data.

## Optional J2MEgram Probe

The separate Probe 1.4.0 diagnostic MIDlet can inspect heap, RMS, networking,
image decoding and cryptographic performance without signing in. It is not part
of current releases and is not required before filing a device report.

Download the matching [Probe JAR](https://github.com/smbdsbrain/J2MEgram/releases/download/v1.4.0/J2MEgram-Probe-1.4.0.jar)
and [Probe JAD](https://github.com/smbdsbrain/J2MEgram/releases/download/v1.4.0/J2MEgram-Probe-1.4.0.jad)
from release 1.4.0 and install them using the same copy-and-open steps.
