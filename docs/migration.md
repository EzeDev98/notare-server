# Migration runbook

Covers the data migration of step 18. Backup (18a) comes first and is repeated before cutover (18g).
Never run the migration on production before a rehearsal on a restored copy (18f).

## 18a. Backup

Store every backup somewhere other than the production host (your own machine plus one cloud copy).
Name files with the UTC date so nothing is overwritten.

### Variables (production values, from Render or Neon)

    export PGHOST=<DB_HOST>
    export PGPORT=5432
    export PGUSER=<DB_USER>
    export PGPASSWORD=<DB_PASSWORD>
    export PGDATABASE=<DB_NAME>
    export PGSSLMODE=require
    export STAMP=$(date -u +%Y%m%dT%H%M%SZ)

### 1. Database dump

    pg_dump --format=custom --no-owner --no-privileges \
      --file="notare-$STAMP.dump"

    # sanity check: lists the contents, fails if the file is damaged
    pg_restore --list "notare-$STAMP.dump" | head -20

The dump includes `flyway_schema_history`, so a restored copy knows which migrations ran.

### 2. Clip storage (R2)

    export AWS_ACCESS_KEY_ID=<R2_ACCESS_KEY_ID>
    export AWS_SECRET_ACCESS_KEY=<R2_SECRET_ACCESS_KEY>
    export AWS_DEFAULT_REGION=auto

    aws s3 sync "s3://<R2_BUCKET>" "./clips-$STAMP" \
      --endpoint-url "<R2_ENDPOINT>"

    # count check: the two numbers must match
    aws s3 ls "s3://<R2_BUCKET>" --recursive --endpoint-url "<R2_ENDPOINT>" | wc -l
    find "./clips-$STAMP" -type f | wc -l

### 3. Auth database (internal-auth-server)

Notare accounts live in the shared auth database. The data migration does not change it, but back it up
the same way (own PG* variables, `auth-$STAMP.dump`) so a rollback never leaves the two out of step.

## Restore (tested in 18a)

    createdb notare_restore_test
    pg_restore --no-owner --no-privileges \
      --dbname=notare_restore_test "notare-$STAMP.dump"

    # compare counts with production
    psql notare_restore_test -c "select count(*) from notes;"
    psql notare_restore_test -c "select count(*) from voice_notes;"
    psql notare_restore_test -c "select version from flyway_schema_history order by installed_rank desc limit 1;"

Start the app against the restored copy (local Postgres, prod profile settings, `ddl-auto=validate`):

    DB_HOST=localhost DB_PORT=5432 DB_NAME=notare_restore_test DB_USER=<you> DB_PASSWORD=<pw> \
      ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

The restore passes when the app reaches "Started" with Flyway reporting the schema up to date and
`validate` raising no errors. Drop the test database afterwards: `dropdb notare_restore_test`.

Restore a few clip files from `clips-$STAMP` into a scratch bucket with
`aws s3 cp` and play one back to confirm the files are readable.

## Rollback

To roll back after a failed cutover: put the API in maintenance mode, restore the pre-cutover dump into the
production database (drop and recreate it first), restore the clip copy if clips were touched, redeploy the
previous server version, then lift maintenance mode.

## 18b. What each existing row becomes

Schema status: `notes.client_id` (V1), `voice_notes.client_id` (V6), `notes.body_mode` (V5, default LEGACY)
and `notes.purge_at` (V9) already exist. The only schema change left for the migration is `migrated_at`
on the rows the job processes (V12, step 18e).

| Existing data | Becomes | How |
|---|---|---|
| Notes with Markdown content | Same row, `body_mode = LEGACY`; the client converts on first open (18d) | No server change (V5 default) |
| Pinned, archived, trashed flags | Same flags; trashed notes get `purge_at` | Already done in V9 |
| Voice notes linked to a note | Child clips of that note | `voice_notes.client_id` exists (V6); the job fills a generated UUID where it is NULL |
| Standalone voice notes (`note_id` is NULL) | One new note per clip, titled "Voice clip, {date}", holding only that clip | Job (18e) |
| Transcripts | Attached to their clip | None; the job re-queues pending or stuck ones |
| Voice shares (`voice_shares`) | Share of a new note, per the 18c rule | Job (18e) |
| Note shares READ and WRITE | Viewer and Editor | Rename in code, not data |
| Collaborations | People (Connected, Pending) | Unchanged |
| Notifications | Kept; old types render through the client fallback | None |
| Device (Dexie) data | `version(4)` upgrade | Client, done in step 8b |

## 18g. Cut over (production)

Do this only after step 18f has passed on staging and step 19 is done. Pick a quiet time.

1. Set `MAINTENANCE_MODE=true` and `ADMIN_MIGRATION_SECRET=<new secret>` on the server, then redeploy.
   Every request except `/admin/**` now answers 503 `MAINTENANCE`.
2. Take a fresh backup (section 18a). Keep it for 30 days.
3. Run `docs/migration-counts.sql` and save the output as `counts-before.txt`.
4. Start the job: `POST /admin/migration/legacy` with `X-Admin-Secret`. Poll `/admin/migration/legacy/status`
   until it shows the counts.
5. Run `docs/migration-counts.sql` again and `docs/migration-counts-after.sql`. Compare with the 18f pass table.
   If anything fails, restore the backup (Rollback above) and stop.
6. Run the job once more. Fill, move and copy counts must be 0.
7. Smoke test with your own account: sign in, open an old note, play a clip, open a shared note.
8. Set `LEGACY_VOICE_SHARE_GONE=true`, set `MAINTENANCE_MODE=false`, redeploy. Remove `ADMIN_MIGRATION_SECRET`
   so the migration endpoints switch off.

### One release later

- Delete the voiceshare module and its tables (B-10).
- Make `voice_notes.note_id` NOT NULL (17b) in a new Flyway migration.
- The route redirects from step 7a stay for the same one release, then go.
