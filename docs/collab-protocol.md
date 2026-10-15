# Collaboration protocol

Step 15d, decision D-032. The server is a relay: it numbers, stores and forwards
Yjs update bytes and never reads them. The step 16 client follows this document
exactly. The code is `CollabProtocol`.

## Connecting

1. `POST /api/v1/notes/{noteId}/collab-ticket` with the normal JWT returns
   `{ "ticket": "...", "role": "owner" | "editor" | "viewer" }`. A note you cannot
   see is a 404.
2. Open `wss://<host>/api/v1/ws/collab?ticket=<ticket>`. A ticket works once and
   expires after 60 seconds. A refused handshake is HTTP 401 (bad or used ticket)
   or 503 (ticket store down). Ask for a new ticket on every reconnect.
3. Send JOIN. Do nothing else until SYNCED arrives.

## Frames

Every frame is binary. Byte 0 is the type. Numbers are big-endian. Maximum frame
size is 1,048,576 bytes including the type byte.

| Type | Byte | Direction | Body |
|---|---|---|---|
| JOIN | 1 | client to server | `lastSeq` (8 bytes): last seq this device holds, 0 for none |
| UPDATE | 2 | client to server | the Yjs update bytes |
| UPDATE | 2 | server to client | `seq` (8 bytes), then the Yjs update bytes |
| SNAPSHOT | 3 | client to server | `upToSeq` (8), `stateLength` (4), `state`, then plain text as UTF-8 to the end |
| SNAPSHOT | 3 | server to client | `upToSeq` (8), then the Yjs state |
| AWARENESS | 4 | both | opaque presence bytes, relayed unchanged |
| ROLE | 5 | server to client | 1 byte: 0 owner, 1 editor, 2 viewer |
| ACK | 6 | server to client | `seq` (8): the seq given to your UPDATE |
| SYNCED | 7 | server to client | `head` (8): the last seq on the server |
| ERROR | 8 | server to client | 1 byte code (below) |

## Flow

**JOIN.** If your `lastSeq` is older than the stored snapshot, the server sends
SNAPSHOT first, then every UPDATE after the snapshot's `upToSeq`. Otherwise it sends
every UPDATE after `lastSeq`. It then sends SYNCED. Apply everything in order. If
SYNCED's `head` is lower than your `lastSeq`, the server's history is behind yours
(for example a restored database): forget your seq and JOIN again with 0.

**UPDATE.** Owner and Editor only. The server stores it as the next seq, sends you
ACK, and forwards UPDATE to the other joined sessions of the note. You do not get
your own update back. If no ACK arrives before the socket drops, send the update
again after reconnecting; Yjs ignores an update it has already applied.

**SNAPSHOT.** Owner and Editor only. Send it when you want the log compacted, with
the Yjs state that includes every update up to `upToSeq` and the note's plain text.
There is no reply on success. The server deletes updates up to `upToSeq`, stores the
plain text, and also writes it into the note's `content`, which is what search, notification
labels, shared views and the sync pull read. Until the first snapshot those show the text the
note had before it became collaborative, so send a snapshot soon after the first edits. A snapshot older than the stored one
is ignored; one claiming a seq the server never issued gets ERROR 4.

**AWARENESS.** Relayed to the other joined sessions, never stored. Viewers may send
it. What a Viewer's presence shows is decided by the client (O-08: Viewers appear as
avatars, never as named cursors); the server cannot read the bytes.

**ROLE.** Sent when your role changes while connected. Switch the editor between
editable and read-only when it arrives.

## ERROR codes (the socket stays open)

| Code | Meaning |
|---|---|
| 1 | Read only: a Viewer sent UPDATE or SNAPSHOT. Nothing was stored. |
| 2 | Not joined: JOIN has not completed. |
| 3 | Already joined. |
| 4 | Bad snapshot. |
| 5 | Server error. Retry later. |

## Close codes

| Code | Meaning |
|---|---|
| 1009 | A frame over 1 MB. |
| 4400 | Malformed or unknown frame. |
| 4403 | Access removed (15e): do not reconnect, the ticket request will fail. |
| 4429 | More than 60 frames in one second. Throttle awareness. |

## Keep-alive

The server pings every 25 seconds. Browsers answer with a pong automatically. A
socket silent for 60 seconds is closed.
