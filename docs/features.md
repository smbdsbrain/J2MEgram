# Features and limitations

This page is the detailed capability reference for the current J2MEgram
release. The shorter [project README](../README.md) stays focused on choosing,
installing and trying the application.

## Account

- Sign in with a phone number and an SMS or in-app code.
- Complete two-step verification with a cloud password.
- Create an account, resend or cancel a code, and change the phone number.
- View and edit your profile.
- Log out locally or close other Telegram sessions.

## Chats and messages

- Browse, filter and page through the chat list, including unread and pinned
  state, avatars, Saved Messages and opening a peer by `@username`.
- Use personal folders, explicit include/exclude lists, pinned peers and the
  main/archive split. Shared folders are read-only.
- Read paged history and search within the current conversation.
- Send text; reply, edit, forward and delete eligible messages.
- See read state and live updates with gap recovery.
- Send and remove reactions, and inspect who reacted.
- Read ordinary, multiple-choice, quiz and closed polls; vote and receive live
  results.
- Read bold, italic, underline, strikethrough, inline code, preformatted text,
  block quotes and concealed spoilers.
- Inspect supported URLs, usernames, phone numbers and email addresses before
  handing an action to the phone.

## Groups, channels and forums

- Read groups and channels, open comment threads and send replies.
- Open forum supergroups as topic lists with topic-local history, unread state,
  drafts and sending.
- When the account has permission, view participants, invite or remove members,
  handle bans, promote or demote administrators, edit default permissions and
  manage invite links or join requests.
- Create, rename, close, reopen, pin, unpin and delete eligible forum topics.

Community administration and folder flows have deterministic desktop and
packaged-emulator coverage in 1.6.0. No new physical-device validation is
claimed for those additions.

## Media and interface

- Open photos with zoom and D-pad panning.
- Decode JPEG in bundled Java code on phones whose runtime cannot decode it.
- Show blurred inline thumbnails, cached avatars and a bounded emoji atlas.
- Label unsupported media in the transcript so surrounding conversation text
  remains readable.
- Adapt the Canvas UI to the measured viewport instead of assuming a screen
  size.
- Use keypad controls on every screen and touch controls on runtimes that expose
  pointer events.
- Choose light, dark or high-contrast themes.

## Unreliable networks and small heaps

- Queue outgoing messages in a persistent outbox with retry and deletion.
- Autosave per-chat drafts and open to a bounded offline cache.
- Reconnect with bounded backoff or an explicit `Reconnect now` action.
- Try direct TCP, obfuscated2, MTProxy/FakeTLS and MTProto-over-HTTP routes, then
  remember the last route that worked.
- Connect to multiple Telegram data centres and migrate transparently during
  sign-in.
- Measure heap on first launch and size packet, image, cache and paging budgets
  from the result. Decorative work is dropped before conversation state when
  memory becomes tight.

## Diagnostics

- Inspect route attempts, byte counters and retry timing.
- Read an in-app log and a crash tail that survives a MIDlet failure.
- Optionally send diagnostics to a developer-owned endpoint from explicitly
  configured development builds. Public releases do not contain collector
  credentials.
- Use the separate J2MEgram Probe 1.4.0 MIDlet to measure runtime APIs, heap,
  RMS, sockets, image decoding, entropy and cryptographic performance without
  signing in.

## Not implemented

- Background notifications of any kind.
- Secret chats, voice calls or video calls.
- Sending photos, files, voice, video or other attachments.
- Downloading or opening incoming files, voice, music, video, GIFs, stickers,
  animated stickers or custom emoji. Incoming photos are the exception.
- Creating polls, scheduled messages or stories.
- Contact management and general address-book synchronization.
- Mini Apps and bot interfaces beyond ordinary chat messages.
- Localisation; the interface is English only.

## Security boundaries

J2MEgram implements MTProto cryptography on the handset and keeps the
authorization key local. That does not make it a security-audited client.

- Entropy collection has been measured on several phones, but independence
  across consecutive gathers has not been established for every Java ME VM.
- RMS offers no encryption at rest. Physical access plus device-specific tools
  may expose the stored session.
- Secret chats are absent, so conversations use Telegram's ordinary cloud-chat
  security model.
- Old firmware, carrier policy and unsigned-MIDlet permissions can change which
  network routes are available.

See the [architecture security posture](architecture.md#security-posture-stated-honestly)
and [private vulnerability reporting policy](../SECURITY.md) before using the
client for anything sensitive.
