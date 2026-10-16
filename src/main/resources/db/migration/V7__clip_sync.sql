-- Step 17c: sync carries clips and transcripts.
--   removed     the person deleted this clip on its own. Unlike a clip hidden because its note is in the
--               trash, it does not come back when the note is restored.
--   updated_at  a clip's change marker. Older rows may have none: give them their upload time, so a
--               pull from any cursor sees them.
ALTER TABLE voice_notes ADD COLUMN removed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE voice_notes SET updated_at = created_at WHERE updated_at IS NULL;

-- The pull asks "this owner's clips changed since T".
CREATE INDEX idx_voice_notes_owner_updated ON voice_notes (owner_id, updated_at);
