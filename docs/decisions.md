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
