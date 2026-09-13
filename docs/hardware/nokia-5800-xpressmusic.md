# Nokia 5800 XpressMusic — touch works, update resume does not

This page records the physical-device report in [issue #29](https://github.com/smbdsbrain/J2MEgram/issues/29),
submitted on 2026-09-11. The exact firmware and J2MEgram version were not
reported, and no Probe output was captured, so the observations below should
not be generalized beyond that run.

| | |
|---|---|
| Handset | Nokia 5800 XpressMusic |
| Build | Normal J2MEgram build; exact version not reported |
| Installation | JAR copied over Bluetooth |
| Network | Wi-Fi, padded MTProxy on port 3128 |
| Input | Touch, with the platform's on-screen keys disabled |

## End-to-end result

The handset completed sign-in to an existing Telegram account, including its
2FA password. The first signed-in launch received live updates. The report also
confirmed all of these paths on the physical phone:

- loading chats, avatars and message history;
- reacting with an emoji;
- opening a photo and changing its zoom;
- navigating with touch after disabling the platform's on-screen keys.

Because the JAR was unsigned, the phone would not allow the required connection
on the usual low ports. An MTProxy listening above port 1024 provided the usable
route; the recorded run used port 3128.

The connection diagnostics identified the selected route as `mtproxy/padded`.
One attempt failed after the server salt changed and the following attempt
connected in 1277 ms. That recovery is expected and is not the unresolved part
of the report.

## Live updates stop after a relaunch

Live updates worked on the first launch but did not resume on subsequent
launches. The header stayed at `online/stopped`. A manual refresh fetched new
chat state, but did not return the client to `online/live`.

The captured update diagnostics were:

```text
state: stopped
detail: source none, queue 0
queued: 0
pts/qts: -
date/seq: -
channels: -
```

This is a partial compatibility result rather than a clean pass: the account,
transport, touch UI, chats, reactions and photos work, while automatic update
resume after a process restart remains unexplained. Issue #29 stays open as the
source and follow-up location for that defect.

## Still unknown

- Exact handset firmware and J2MEgram artifact version.
- Measured heap, RMS, socket and image-decoder behaviour from J2MEgram Probe.
- Whether the stopped update source is specific to this firmware, to a stored
  session created by that build, or to the general relaunch path.
