-- Step 17d: Editors can transcribe the clips of a note they share (B-11).
--   requested_by         who asked for the transcript. The "ready" or "failed" notice goes to them, which
--                        is not always the clip's owner. Older rows were asked for by the owner.
--   requested_language   the language asked for. 'auto' lets the engine detect it. Nothing reads
--                        anything else yet; the column is here so a language picker (O-23) needs no migration.
ALTER TABLE transcriptions ADD COLUMN requested_by BIGINT;
ALTER TABLE transcriptions ADD COLUMN requested_language VARCHAR(16) NOT NULL DEFAULT 'auto';

UPDATE transcriptions SET requested_by = owner_id WHERE requested_by IS NULL;
