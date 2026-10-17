# Staging run (step 19f)

Staging is a deploy that behaves like production: prod profile, Postgres via Flyway, R2 storage, TLS Redis.
Use two real browsers or devices (A and B) and two accounts (one and two). Tick each line as it passes
and note the date and the build.

Run date: ____  Server build: ____  Client build: ____

## Setup
- [ ] Server started on the prod profile against an empty database. Flyway applied every migration, startup reached "Started" (19a).
- [ ] `STORAGE_BACKEND=r2` and the R2 settings are set (19b).
- [ ] `REDIS_URL` is a `rediss://` URL with a password (19c).
- [ ] `APP_CORS_ALLOWED_ORIGIN_PATTERNS` holds the staging frontend origin (19c).
- [ ] `ADMIN_WIPE_SECRET` is NOT set; a wipe call returns 401 (19d).
- [ ] `ADMIN_MIGRATION_SECRET` is not set (the migration endpoints are off).

## Account
- [ ] Account one signs up.
- [ ] The verification email arrives and the link or code verifies the account.
- [ ] Sign-in works on device A, and a second sign-in on device B works too (17h).

## Notes and clips
- [ ] Create a note on A. It appears on B after sync.
- [ ] Record a voice clip on A, reload the page, and play it back.
- [ ] The clip appears on B and plays there.
- [ ] Delete the note, then delete it from Trash. The clip's object is gone from the R2 bucket (19b).

## Sharing and live editing
- [ ] Account one and account two connect.
- [ ] Account one shares a note with account two as Editor. Account two receives the notification (17i).
- [ ] Both edit the same note from two devices at once. Both see each other's text, with no duplicated text.
- [ ] Leave both idle for 5 minutes, then type on one. The other updates with no reconnect (19c).
- [ ] Change account two to Viewer. Its typing is refused and the notice appears.
- [ ] Remove account two. Its editor closes, and `COLLABORATION_REMOVED` arrives (17i).

## Operations
- [ ] The log shows one `sync pull` / `sync push` line per call and the socket open and close lines, with no note text (19e).
- [ ] Alerts exist for 5xx rate, `Collab socket closed with 1011`, and job failures (19e).
- [ ] Daily backups are on, and one restore from the provider's own backup started the app (19e).
- [ ] A rate-limited call returns 429 with `Retry-After`, and the browser can read that header (17j).
- [ ] An old client version gets 426 `CLIENT_TOO_OLD` (17k).

## Account deletion
- [ ] Account one deletes its account. Sign-in fails afterwards.
- [ ] Its notes, clips and the R2 objects are gone. Account two no longer sees its shared notes.

## Result
- [ ] Every line above passed. Failures and fixes: ____
