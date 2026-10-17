# Notare server decisions log

The full log, including every open item, lives in the client repo at `docs/decisions.md`. Edit both in the same change when a decision touches the server.

| ID | Date | Decision | Status | Source |
|---|---|---|---|---|
| S-001 | 2026-10-09 | Transcription provider is Deepgram (commit ecdad4f). | ADOPTED | Server code |
| S-002 | 2026-10-09 | `POST /admin/database/wipe` exists only when `app.admin.wipe-enabled` is true (false by default, true in dev); the default secret is removed. | ADOPTED | Guide 1b |
| S-003 | 2026-10-09 | `NOTARE-SERVER.md` is checked against the controllers; the code wins (B-20, step 1g). | ADOPTED | Master B-20 |
| S-004 | 2026-10-09 | Voice recordings are never tracked in git; `uploads/` and `.DS_Store` are ignored. | ADOPTED | Guide 1c |

## Baseline before the overhaul (git tag `pre-overhaul`, commit ecdad4f)

Full output: `docs/baseline-test.txt`. `./mvnw test`: 822 tests, 0 failures, 6 errors: `AccountLifecycleIntegrationTest` (3), `SharingCollaborationIntegrationTest` (2), `NotareApplicationTests` (1). Anything failing that is not listed here is new.

## D-032: Collaboration runs on a Java relay inside the existing server (step 15a, B-07)
**Chosen: option B.** The Spring Boot server gets a WebSocket endpoint that stores and forwards opaque Yjs update bytes. The server never reads or merges Yjs. Option A (a Node Hocuspocus sidecar) is rejected for now.

**Why B.** One deploy unit instead of two, so no second cold start on Render and no second language to run. It reuses what already exists: the JWT from the internal auth service, the owner and share permission code, Redis (already a dependency) for one-time tickets, and Spring Modulith events for revoke and role changes. The cost is that we write the small protocol and the compaction rule ourselves (15d, 15e).

**What this commits us to.**
- **Auth.** Browsers cannot set an Authorization header on a WebSocket, so the client first calls `POST /api/v1/notes/{noteId}/collab-ticket` with its normal JWT and receives a single-use ticket (60 seconds, kept in Redis, bound to user, note and role). The socket is opened with `?ticket=` and the ticket is deleted on use (15c).
- **Storage.** `note_updates` (ordered by a per-note `seq`) and `note_snapshots` (state, `up_to_seq`, `plain_text`) in Flyway V5. A note becomes `COLLAB` when its first update arrives (15b).
- **Plain text comes from clients.** The server cannot read Yjs, so `plain_text` (used by search, notifications and sync for COLLAB notes) is whatever the last client SNAPSHOT supplied. Clients send a snapshot after a batch of updates and when a note is closed; the server deletes updates up to `upToSeq` when it stores one. A note nobody has snapshotted yet can have stale plain text. This is accepted.
- **Permissions on every frame.** A Viewer's UPDATE is rejected and not stored. Revoke closes the user's sockets with code 4403, and a role change sends a ROLE frame without reconnecting (15e).
- **Legacy writes blocked.** `PUT` and `PATCH /notes/{id}` that send content return 409 `BODY_IS_COLLABORATIVE` for a COLLAB note (15f).
- **One server instance for now.** Broadcast is in memory, so two server instances would need a Redis pub/sub fan-out before scaling out. The scheduled purge job has the same single-node limit (B-21). Revisit both before running more than one instance.
- **Idle connections.** Render closes idle sockets, so the server pings every 25 seconds and drops dead sessions.
- **Protocol document.** The protocol lives in one class (`CollabProtocol`) with a short `docs/collab-protocol.md`; the client in step 16 follows it exactly.

**Revisit if** the relay's compaction or awareness handling becomes a maintenance burden, or if we need more than one server instance. Hocuspocus then becomes worth the second deployment. Steps 15g (share-leak fixes, module test) and 15h (contract tests) are the same under either option.

## D-033: Editors can transcribe the clips of a shared note (step 17d, B-11)
**Who may ask.** The note's owner and anyone with WRITE on it may `POST /voice-notes/{id}/transcription`. A Viewer gets 403 on POST and 200 on GET; someone with no access to the note gets 404, the same as a clip that does not exist. The check asks the share module (`SharedNoteService.accessTo`) and never reads shares itself. A clip that belongs to no note is only its owner's.

**Whose job it is.** `transcriptions.owner_id` stays the clip's owner (the person who recorded it), because the audio is found through that id. The new `requested_by` is who asked. The completed and failed notices go to `requested_by` (the owner for older rows). `requested_language` is added now, default `auto`, so a language picker (O-23) needs no schema change. Nothing reads anything but `auto` yet.

**Stuck jobs.** `StuckTranscriptionRecovery` re-queues every stale PENDING or PROCESSING job whoever owns it, and the worker loads the audio as the clip's owner, so jobs for shared clips are recovered with no change.

**Cost and who pays.** The engine is Deepgram Nova-3, pre-recorded. Its published pay-as-you-go rate is about $0.0043 per minute of audio (about $0.26 an hour), billed per second, at the time of writing. Check Deepgram's pricing page before budgeting; the rate moves. Notare pays for every job, including failed ones and retries. Nothing is charged to the person and there is no per-person limit yet. Because `submit` retries a FAILED job on request, the only way to spend more than one run on a clip is to keep retrying a failing one. Add a per-person cap if that ever shows up in the bill.

## D-034: Several signed-in devices, limit set per app (step 17h, B-14)
**What.** internal-auth-server used to keep one session per user for every app. It now keeps up to `max_active_sessions` per user, set on each client application. The default is 1, so Ahorra and SyncPoint behave exactly as before. Notare is set to 5. The 6th sign-in signs out the device used least recently, and only that device's refresh token is deleted.

**Where the data lives.** `user_sessions` gets `device_label`, `last_active_at` and `authorization_id` (migration V30 in internal-auth-server). Notare passes the session list through and revokes one device with `DELETE /session/{id}`.

**Revisit if** another app needs a different limit (set it on that app) or devices should be told apart by more than the User-Agent.

## D-035: Notification payload and removed-connection type (step 17i, B-12)
**Payload.** `notifications` gets two nullable columns, `previous_permission` and `new_permission` (migration V11), filled only for the note and voice-note permission-changed types and returned in the API. Two typed columns were chosen over a JSON blob because there are exactly two values and both are the existing `SharePermission` enum.

**New type.** `COLLABORATION_REMOVED` goes to a collaborator an owner removes. It is sent only for an accepted connection (`CollaborationRemovedEvent.wasAccepted`), because withdrawing a pending invitation leaves the invitee nothing to lose. It has no target, since the connection no longer exists.

**Unknown types.** Type names stay stable and are never deleted. The client shows "Something new happened" for a type it does not know.

**Revisit if** a notification needs more than these two values, which is when a JSON payload becomes worth it.

## D-036: Rate limits, one note cap, uploads checked by content (step 17j, B-15, B-19, B-22)
**Rate limits.** `GET /sync` allows 30 a minute and `GET /user/lookup` plus `GET /user/search` together allow 20, per user, in Redis. A fixed one-minute window was chosen over a sliding one: it is one INCR per request, and a burst across a window edge is acceptable for abuse protection. If Redis is down the limiter lets requests through, because an outage in the limiter must not take the API down.

**One note cap.** `@Size(max = 20_000)` on three DTOs is replaced by `@MaxNoteLength`, driven by `notare.note.max-content-length` (100000). The server cap exists for abuse only; the product limit and counter are the client's. Shared-note responses keep their derived `title` (step 15g).

**Uploads by content.** The declared MIME type is no longer consulted. The setting that listed allowed types is removed with it, so there is one definition of what an audio upload is.

**Revisit if** real use needs different limits (change the settings) or a legitimate MP3 or AAC file starts with other bytes than the five patterns.

## D-037: Minimum client version (step 17k)
**How.** The client sends `X-Notare-Client-Version`. Below `notare.client.min-version` the server answers 426 with the problem code `CLIENT_TOO_OLD`, which the client turns into its blocking "Update required" modal (step 14e).

**No header is allowed through.** A request without the header, or with one that is not a version, is not blocked. It is a tool or a probe rather than an outdated app, and an app that has never sent the header cannot be told apart from one. The first release that sends the header is therefore the oldest one this can turn away.

**Default 0.0.0.** Nobody is blocked until the setting is raised for a release.

**Revisit if** clients that never send the header must be blocked too. That needs the header required on a path every client uses, such as login.

## D-038: Voice shares migrate to a copy of the clip, never to the owner's note (step 18c)
**Rule.** Master 9.8 says a voice share becomes a share of "the note that now holds the clip". That is safe only when the clip is standalone, because its new note holds nothing else. A clip attached to a note with text would expose the whole note to someone who was only given one recording.

**What the migration does.**
- **Standalone clip** (`voice_notes.note_id` is NULL): it becomes its own note, "Voice clip, {date}", holding only that clip. Its shares become Viewer shares of that note.
- **Clip attached to a note**: the job creates a new note "Voice clip, {date}" holding a server-side copy of the clip (the copy endpoint from 17f), and shares that note as Viewer with the same people. The owner's original note and clip are unchanged.
- **Permission.** Every migrated voice share becomes Viewer. Voice shares never carried edit rights.
- **Never silent.** If an owner's note itself should be shared, that is done only after asking that owner. The job never does it.

**Cost.** An attached shared clip is stored twice (the original and the copy). That is accepted for the privacy guarantee. The copy is deleted with the share's note like any other clip.

**Idempotence.** Each processed `voice_shares` row is marked `migrated_at` and skipped on a second run (step 18e).
