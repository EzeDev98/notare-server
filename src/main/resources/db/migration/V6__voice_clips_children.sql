-- Step 17b (B-09, P-02): a voice clip is a child of its note.
--   client_id  the clip's id on the device. Unique per owner, so a repeated upload stores one clip.
--   deleted    clips follow their note into the trash and back; they are hidden, not removed, until purge.
ALTER TABLE voice_notes ADD COLUMN client_id  VARCHAR(64);
ALTER TABLE voice_notes ADD COLUMN deleted    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE voice_notes ADD COLUMN deleted_at TIMESTAMP;

CREATE UNIQUE INDEX uq_voice_notes_owner_client_id ON voice_notes (owner_id, client_id);

-- Notes already in the trash: their clips are hidden too.
UPDATE voice_notes
   SET deleted = TRUE,
       deleted_at = (SELECT n.deleted_at FROM notes n WHERE n.id = voice_notes.note_id)
 WHERE note_id IN (SELECT id FROM notes WHERE deleted = TRUE);
