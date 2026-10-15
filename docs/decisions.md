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
