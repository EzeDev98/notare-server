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
