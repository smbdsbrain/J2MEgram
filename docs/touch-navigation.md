# Touch navigation

The client supports keypad and pointer input at the same time.  Pointer support
does not translate taps into synthetic key codes: each Canvas calls the same
selection and activation method used by `FIRE`, while the existing key handlers
remain unchanged.

## Gesture contract

- A short tap focuses the item under the pointer and performs its `FIRE`
  action.
- A hold of at least 650 ms, released without dragging, focuses that item and
  opens its action list.  It does not also perform `FIRE`.
- Motion beyond a six-pixel slop becomes a drag.  A drag never activates an
  item or opens its action list.
- Vertical drags move lists, chat history and read-only text continuously with
  the pointer. Every motion event updates the partial-pixel offset and requests
  a non-blocking repaint. MIDP coalesces pending requests, so a slow display
  draws the newest finger position and drops stale intermediate frames. Only
  the content rectangle is invalidated, and expensive viewport/paging
  callbacks wait until release. Fully crossed rows are committed as the gesture
  continues and the remaining fraction snaps to the nearest row on release. A
  drag on a photo pans it in both axes with the same coalesced live repaint; the
  existing bounds still clamp the image.
- The MIDP Options menu stays available.  It is the fallback on unusual
  firmware and still exposes global actions that do not belong to one item.

The long-press list is a native MIDP `List`, not a second custom popup.  It has
both an application Back action and a Cancel row that dismisses only the list.
This is intentional: the Fly E190 firmware already makes native menus and lists
touchable, and routing its rows through the existing `Command` objects keeps
network and navigation behaviour in one place.

## Screen map

| Screen | Tap | Drag | Hold |
|---|---|---|---|
| Chats | Open the touched chat | Move through the chat window and trigger its existing paging margins | Open, profile, find/filter and back |
| Forum topics | Open the touched topic | Move through topics and trigger topic paging | Open, refresh, more and back |
| Conversation | Focus the touched message, then open its photo/poll or reactions exactly like `FIRE` | Scroll history; existing window reflow and prefetch run from the viewport callback | Applicable message actions: open media, reactions, reply, edit, comments, poll, reveal spoiler, full text, links, forward, delete, profile, write and back |
| Poll | Toggle the touched answer | Move through answers | Select, vote when a selection can be submitted, and back |
| Reactions | Perform the touched row action | Move through reactions/actions | Select and back |
| Photo | Cycle fit-screen, fit-width and 5x | Pan the zoomed image | Zoom or retry, and back |
| Read-only text | No activation | Scroll wrapped lines | Back |

`List`, `Form` and `TextBox` screens — search results, profiles, settings,
composer, confirmation screens and authentication — use the handset's native
pointer handling.  Their Commands remain the same as on keypad devices.

## Physical-device check

For the Fly E190 Wi-Fi, verify all of the following before calling the port
confirmed:

1. Tap a chat, return, tap a forum topic and return.
2. Swipe a long chat list and history in both directions far enough to fetch a
   page; confirm the touched row is never opened at the end of a swipe.
3. Tap an ordinary message, photo and poll.  Confirm their result matches the
   centre/FIRE action on a keypad handset.
4. Hold a message with every applicable state available (own editable text,
   link, spoiler, forwarded post, poll or comments) and exercise each row.
5. Select and vote in single- and multiple-choice polls; select and remove a
   reaction.
6. Tap through the three photo zoom modes, then pan at an edge and a corner.
7. Compose text, use search, settings and confirmations through native touch
   controls.
8. Repeat the main flow with d-pad/numeric navigation and confirm no key action
   changed.
