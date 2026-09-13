# Nokia E51 — the full client works, one keypress at a time

This page records the physical-device report in [issue #30](https://github.com/smbdsbrain/J2MEgram/issues/30),
submitted on 2026-09-12. The exact firmware and J2MEgram version were not
reported, and no Probe output was captured, so the observations below describe
that run rather than every E51.

| | |
|---|---|
| Handset | Nokia E51 |
| Build | Normal J2MEgram build; exact version not reported |
| Installation | JAR copied over Bluetooth |
| Network | Wi-Fi, MTProxy on port 3128 |
| Input | Physical navigation keys |

## End-to-end result

The handset connected through MTProxy and completed sign-in to an existing
Telegram account, including its 2FA password. The report confirmed these paths
on the physical phone:

- loading the chat list and receiving live updates;
- opening a conversation and sending a text message;
- reacting to a message;
- opening a photo.

As on the Nokia 5800 report, the unsigned application required an MTProxy on a
port above 1024. Port 3128 was the route used for this run.

## Slow navigation has a failure window

The application is very slow on this handset. Moving the chat-list selection
can take almost a second after a keypress. Waiting for the highlight to reach
the intended row and then pressing the centre key works.

Pressing Down and then pressing the centre key before the highlight visibly
moves can instead open the chat with an `IOException`. The report therefore
captures more than poor animation: an action accepted during the delayed
selection transition can reach a failure path.

The supplied log also contained many repetitions of:

```text
W worker busy with RMS dialog live reconciliation, refused
RMS dialog index dirty
```

and this command failure:

```text
491.209 E command failed | Error: 136
```

The report does not establish whether error 136 belongs to the early chat open,
the Exit failure below, or another command, so it is preserved without assigning
it a cause.

## Exit is unreliable

Selecting **Exit** sometimes crashes instead of closing the MIDlet. It did not
happen on every attempt. This remains a separate open defect; a successful
Telegram session does not make lifecycle teardown a pass.

The result is therefore working with caveats: sign-in, updates, chats, sending,
reactions and photos all reached Telegram successfully, while responsiveness,
the selection/action race and application shutdown still need investigation.
Issue #30 stays open as the source and follow-up location for those defects.

## Still unknown

- Exact handset firmware and J2MEgram artifact version.
- Measured heap, RMS, socket, display and cryptographic behaviour from
  J2MEgram Probe.
- A crash-log entry tied to the intermittent Exit failure.
- Whether the selection/action failure originates in the platform event queue,
  J2MEgram's dialog selection state, or the concurrent RMS reconciliation.
