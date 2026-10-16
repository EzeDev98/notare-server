-- Step 17e: the purge job (B-01, B-21, M-15).
--   notes.purge_at  when a trashed note is deleted for good. Set to deleted_at + 30 days on delete.
--                   Notes already in Recently deleted get the later of deleted_at + 30 days and
--                   release day + 7 days, so nothing vanishes on release day. This migration runs
--                   on release day, so LOCALTIMESTAMP is the release date.
--   shedlock        one row per scheduled job; the row is the lock that keeps two instances apart.
ALTER TABLE notes ADD COLUMN purge_at TIMESTAMP;

UPDATE notes
SET purge_at = GREATEST(COALESCE(deleted_at, LOCALTIMESTAMP) + INTERVAL '30' DAY,
                        LOCALTIMESTAMP + INTERVAL '7' DAY)
WHERE deleted = TRUE AND purge_at IS NULL;

CREATE INDEX idx_notes_purge_at ON notes (purge_at);

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
