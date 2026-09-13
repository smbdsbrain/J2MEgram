# Device compatibility

J2MEgram targets MIDP 2.0 / CLDC 1.1 phones, but those labels do not describe
heap limits, JAR limits, socket policy, image decoding or vendor VM behaviour.
The table below records only observations made on physical devices.

## Requirements

| Requirement | Practical meaning |
|---|---|
| MIDP 2.0 and CLDC 1.1 | Both are declared by the MIDlet. CLDC 1.0 cannot run it. |
| Enough free Java heap | About 1.5 MB is the practical lower edge for useful operation. More headroom improves photos and cached decoration. |
| A sufficient JAR limit | Try the normal build first and the minified build if installation is rejected for size. |
| A usable network route | Raw TCP is preferred. MTProxy/FakeTLS or HTTP fallback can help when direct sockets are refused. |
| Correct date and time | Especially important for FakeTLS, whose handshake includes the client's time. |

Late feature phones from roughly 2008 onwards are the most plausible targets,
but year and model family are not guarantees. Firmware and operator policy can
matter as much as the hardware.

## Physical-device evidence

| Device | Runtime and route | Recorded result | Important caveat |
|---|---|---|---|
| [Alcatel OT-810D](hardware/alcatel-ot810d.md) | MIDP 2.0 / CLDC 1.1, about 5 MB heap, GPRS | Original end-to-end run reached sign-in, dialogs and a sent text message. Probe measurements cover entropy and runtime behaviour. | The published hardware page concentrates on RNG evidence rather than a repeatable current-release checklist. |
| [Samsung GT-C3592](hardware/samsung-gt-c3592.md) | MIDP 2.0 / CLDC 1.1, about 5 MB heap, mobile data through MTProxy | Production auth-key creation, sign-in and stored-session resume were captured; chat and photo work exposed and drove fixes. | Native JPEG decode and holding a second socket both fail, so J2MEgram uses its decoder and a single-socket media path. |
| [Nokia C3-00](hardware/nokia-c3-00.md) | MIDP 2.1 / CLDC 1.1, 2 MB heap, Wi-Fi through MTProxy | Working production session, chats, persistence and upgrade/interaction testing in the smallest fully exercised heap. | The clock resets after power loss and unsigned port policy requires an allowed proxy port. |
| [Nokia 5800 XpressMusic](hardware/nokia-5800-xpressmusic.md) | Normal build, Wi-Fi through padded MTProxy on port 3128 | 2FA sign-in, chats, avatars, reactions, photos, zoom and touch-only navigation worked on the physical handset. | Live updates worked on the first launch but remained stopped after later launches; manual refresh still worked. Exact firmware and build version were not reported. |
| [Nokia E51](hardware/nokia-e51.md) | Normal build, Wi-Fi through MTProxy on port 3128 | 2FA sign-in, live updates, chats, text sending, reactions and photos worked on the physical handset. | UI response can lag by almost a second; acting before the selection catches up can produce an `IOException`, and Exit sometimes crashes. |
| [Nokia E6-00](hardware/nokia-e6-00.md) | MIDP 2.1 / CLDC 1.1, at least 8 MB heap, FakeTLS MTProxy | Sign-in, session resume, dialogs, history, text sending, reactions, photos and auxiliary data-centre setup were observed. | Old firmware labels Moscow as GMT+4; selecting the correct GMT+3 offset is required for FakeTLS. |
| [Fly E190 Wi-Fi](hardware/fly-e190-wifi.md) | MIDP 2.0 / CLDC 1.1, about 2.5 MB heap, Wi-Fi through FakeTLS MTProxy | Resumed a stored key, corrected salt, loaded configuration, dialogs and an avatar; tap, hold and swipe work. | The Java network profile must be selected separately. Chat history and sending have not yet been recorded, and drag rendering is slow. |

Evidence for one firmware does not establish compatibility for every phone with
the same model name. When reporting a result, include the exact platform string
and firmware when the device exposes them.

## Optional compatibility probe

**J2MEgram Probe 1.4.0** measures the runtime without signing in. It reports
platform strings, heap, RMS storage, socket availability, key codes, image
decoding, entropy estimates and cryptographic test vectors/benchmarks.

The Probe is a separate, older diagnostic release and is not bundled with the
current J2MEgram download. Copy its matching pair to the phone exactly like the
main application:

- [J2MEgram-Probe-1.4.0.jar](https://github.com/smbdsbrain/J2MEgram/releases/download/v1.4.0/J2MEgram-Probe-1.4.0.jar) — 174,958 bytes
- [J2MEgram-Probe-1.4.0.jad](https://github.com/smbdsbrain/J2MEgram/releases/download/v1.4.0/J2MEgram-Probe-1.4.0.jad)

Installing Probe is optional. A report without Probe output is still useful.

## Report a device

Use the [device report form](https://github.com/smbdsbrain/J2MEgram/issues/new?template=device-report.yml)
whether the client works or fails. Never include your phone number, `api_id`,
`api_hash` or authorization key in a public report.
