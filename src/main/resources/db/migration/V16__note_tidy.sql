-- AI tidy: background titles and clean-up of idle notes.
ALTER TABLE notes ADD COLUMN tidied_version   BIGINT;
ALTER TABLE notes ADD COLUMN ai_title         VARCHAR(255);
ALTER TABLE notes ADD COLUMN pre_tidy_content TEXT;
ALTER TABLE notes ADD COLUMN tidy_attempts    INT NOT NULL DEFAULT 0;
ALTER TABLE notes ADD COLUMN tidy_next_at     TIMESTAMP;

-- Opt-in, off for everyone until they turn it on.
ALTER TABLE users ADD COLUMN ai_tidy_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- The picker scans live notes by how long they have been left alone.
CREATE INDEX idx_notes_tidy_candidates ON notes (updated_at) WHERE deleted = FALSE;
