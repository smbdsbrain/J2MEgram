# Fly E190 Wi-Fi — Java networking needs its own profile

The first physical-device session used the unobfuscated 1.4.0 development
artifacts from build `846e936`, compiled for production with collector label
`fly-e190-wifi`, on 2026-08-23.

Everything below was measured by that handset. It is not generalised to other
Fly models or firmware versions.

```
microedition.platform      = Fly_E190/ObigoInternetBrowser/QO3C
microedition.configuration = CLDC-1.1
microedition.profiles      = MIDP-2.0
microedition.encoding      = ISO8859_1
microedition.locale        = ru-RU
```

Optional APIs present: raw TCP, server sockets, UDP, HTTPS, RMS, JSR-75
FileConnection 1.0, JSR-135 MMAPI 1.1, JSR-120 SMS, PIM 1.0 and GameCanvas.
Absent: JSR-82 Bluetooth, JSR-179 Location and JSR-184 M3G. The runtime reports
no comm ports.

---

## Network profile: associated Wi-Fi is not enough

With Wi-Fi visibly associated but no working network profile selected for Java,
all three network paths failed:

```
Probe HTTP upload     IOException: HTTP request-body: java.io.IOException:
                      fail to send http request
Public TCP echo       tcpbin.com:4242          IOException: TCP open
Telegram socket       149.154.167.51:8443      IOException: TCP open
```

The diagnostic ring showed that both raw-socket failures occurred immediately
inside `Connector.open()`. The public echo failed in approximately 23 ms; the
literal Telegram IPv4 address failed in the same millisecond as its attempt.
They were local bearer failures, not DNS, a remote timeout, MTProxy, FakeTLS or
MTProto failures.

Selecting the correct network profile for installed Java applications fixed the
HTTP path. A complete **Upload all** then delivered all 15 standard sections,
followed by a separately uploaded Display size report. The ring records
successful single-part POST bodies from 468 through 1,946 bytes and no retries
or failures.

**Operational rule:** configure the Java application's own network profile. The
Wi-Fi status icon proves only that the native phone stack is associated. The
initial `HTTP request-body` message was not evidence of an E190 body-size or HTTP
implementation defect.

The full client later connected through FakeTLS MTProxy after correcting the
profile, which also proves that raw TCP to port 8443 works. The standalone
public echo and direct Telegram socket probes were not repeated.

---

## Memory: a usable two-megabyte class heap

```
totalMemory          = 2,523,136
totalAllocated       = 2,146,304      (2,096 KB across 262 chunks)
largestSingleAlloc   = 2,162,687      (2,111 KB)
lowestFree           = 1,536
hitOutOfMemory       = true
```

The largest single allocation is slightly larger than the total held in 8 KiB
chunks, so fragmentation is not the limiting factor. This is the same broad
heap class as the Nokia C3-00. The full client should round the 2,523,136-byte
peak down to a 2,432 KiB measured ceiling and use a reduced dynamic memory
budget, but it still needs its own run.

The emoji sprite sheet decoded as 256x160 in 51 ms and cost 189,220 bytes
(184 KB). It fits, but consumes about nine percent of the heap the allocation
probe could hold.

---

## RMS: large shared quota and 64 KiB records confirmed

```
open                 = ok
readBack             = identical
setRecord/delete     = ok
largestRecord tested = 65,536 bytes
sizeAvailable        = 42,786,736
```

Writing 131,072 bytes to a second store reduced the first store's reported
headroom by 133,072 bytes. The quota is shared across the MIDlet suite rather
than reserved independently per store.

---

## Clock, text, display and images

- `currentTimeMillis()` tick: 4 ms.
- Eight `Thread.sleep(250)` calls returned in 250–254 ms, with no early return.
- No backwards step was seen in the short monotonicity window.
- Platform encoding is lossy for non-Latin text, as expected from `ISO8859_1`;
  the client's UTF-8, RMS and report paths all passed.
- Display: 65,536 colours, approximately 16 bpp; normal Canvas 320x409 inside
  the physical 320x480 display. Full-screen Canvas size was not measured.
- Pointer press, pointer motion, key repeats and double buffering are present.
- MIDP PNG decode passed (8x8 in 4 ms). Platform JPEG decode failed with
  `IllegalArgumentException`; Telegram uses its bundled JPEG decoder instead.

---

## Crypto: correct and fast enough

All 22 packaged vectors passed: SHA-1/256/512, PBKDF2-SHA512, AES-128/192/256,
AES-IGE, `BigInteger.modPow` and deterministic RNG.

```
SHA-256 64 KiB         106 ms       603 KB/s
AES-IGE enc 16 KiB     323 ms        49 KB/s
AES-IGE dec 16 KiB     212 ms        75 KB/s
modPow 2048-bit      3,960 ms
```

A fresh auth-key exchange needs two 2048-bit exponentiations plus network and
protocol work, so its CPU floor is roughly eight seconds. That is comfortably
within the class already proven usable by the client on slower handsets.

---

## Entropy: 45-bit long-probe floor; production barrier takes 1.5 seconds

The long estimator measured a 4 ms clock, 26 samples per gather and a
correlation-discounted 1.750 bits per sample:

```
LOWER BOUND = 45 bits/gather
256 bits needs 6 gather(s) by the long estimator
NOT SUFFICIENT AS ONE GATHER
```

The actual production seeding barrier measures while it collects rather than
using that headline. On this run it chose:

```
gathers  = 11
samples  = 277
credited = 969 / 256 bits
elapsed  = 1,472 ms       (133 ms/gather)
target reached
```

Five stored launch digests were distinct. The wall clock moved backwards once;
the Probe classified it as resetting at boot and credited it with zero bits.
This history was not recorded as a controlled sequence of full cold boots, so
it establishes no digest collision in five launches, not a cold-boot bound.

---

## Upload completeness

The collector received Platform, Heap probe, RMS, Clock, Text, Display, Image
decode, Emoji sheet, Crypto vectors, Crypto benchmarks, Entropy measure,
Seeding barrier, Entropy log, Diagnostic log and Crash log. Crash log reported
`no crashes recorded`. Display size was uploaded separately.

---

## Telegram: production FakeTLS MTProxy works

A full-client Diagnostics report was received on 2026-08-24. It contains a
successful production connection through the built-in FakeTLS MTProxy:

```
state                 = online
route                 = mtproxy/faketls
Auto attempt          = mtproxy OK in 4,158 ms
help.getConfig        = 1,164 bytes
auth key seeding      = v1, measured barrier
memory sheds          = 0
```

The proxy was the first Auto candidate and succeeded, so no fallback route was
needed. The client resumed an existing production auth key, corrected its salt
after `bad_server_salt`, completed `help.getConfig`, and in an earlier launch
loaded the dialog list in 3,789 ms and one dialog avatar in 3,830 ms.

This is more than a socket-open result: FakeTLS, MTProxy framing, encrypted
MTProto, stored-key resume, salt correction, gzip decoding, the configuration
RPC, dialog parsing and one media path all ran on the physical handset. Opening
chat history and sending a marked message have not yet been recorded.

### Client memory budget in the real run

The stored probe result became exactly the expected reduced profile:

```
heap ceiling          = 2,432 KB       viable
largest block         = 1,536 KB       stored client measurement
packet / inflate      = 608 / 768 KB
photo bytes / pixels  = 304 KB / 182,400
dialogs               = 71 held / 23 per page
history               = 71 held / 17 per page
chat window           = 1 screen
current headroom      = 599 KB
```

At startup, avatar loading was paused at 98–223 KB of headroom after one decode
had already failed at zero headroom. The admission control did what it was built
to do: it retired decorative work rather than losing the session. Seven avatar
records occupying 69,904 bytes and one dialog-cache record were present in RMS.

---

## Fly lifecycle exposes a connect/pause race

The latest launch contains this ordering:

```
10.006  socket connect started
10.089  pauseApp
11.856  TCP connected
13.458  help.getConfig completed
13.878  authorization check: not connected
13.887  cached dialogs used
```

`pauseApp` calls `Telegram.pause()`, which clears the shared `client` field. The
already-running connect worker owns its local candidate and can still finish
the handshake and preflight; it then records the route as online without
restoring the shared field that `pause()` cleared. `verifyAuthorization()` runs
next, sees no client, and conservatively preserves the stored session while the
UI falls back to cached dialogs. The resulting report is internally consistent
with that race: connection diagnostics say online, while updates are stopped
and their source is `none`.

This is a client lifecycle defect triggered by the Fly AMS pausing the MIDlet
during connection, not a Telegram or proxy failure. Until fixed, returning to
the MIDlet and pressing Retry/Connect again should establish a clean foreground
session.

The RMS crash log also holds two identical `java.lang.Error` entries with
message `136`. Both follow an explicit `destroyApp` line immediately and were
recorded by the command-level catch. The available evidence narrows them to the
explicit Exit path — cleanup inside `destroyApp(true)` or the following native
`notifyDestroyed()` — but does not distinguish those two calls. They were not
raised during handshake, dialog loading or normal keepalive operation; one
preceding session exchanged successful ping/pong traffic for about 17 minutes.
