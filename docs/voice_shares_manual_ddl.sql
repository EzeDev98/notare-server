-- Manual DDL for the voice_shares table — PRODUCTION ONLY.
--
-- Why this file exists: this project has no Flyway/Liquibase. Schema comes
-- from hibernate.ddl-auto, which is "update" in dev (creates this table
-- automatically the first time the app starts against a dev database) but
-- "validate" in prod (refuses to start if the schema doesn't already match
-- the entities). That means the voice_shares table introduced by VoiceShare
-- (Step 3a) must be created by hand in prod BEFORE deploying this feature,
-- or the app will fail to boot.
--
-- This is a pre-existing constraint of the project, not something specific
-- to this feature — every prior module (share, voicenote, collaboration,
-- etc.) had to clear the same bar. Documented here rather than worked around,
-- per the roadmap's Step 7 finding.
--
-- Run this against the production database ONCE, before deploying the
-- release that includes the voiceshare module. Column types match the H2/dev
-- schema Hibernate would generate from VoiceShare.java; adjust types if your
-- production database is not MySQL/MariaDB.

CREATE TABLE voice_shares (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    voice_note_id         BIGINT       NOT NULL,
    owner_id              BIGINT       NOT NULL,
    shared_with_user_id   BIGINT       NOT NULL,
    permission            VARCHAR(20)  NOT NULL DEFAULT 'READ',
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NULL,

    CONSTRAINT uq_voice_shares_voice_note_user
        UNIQUE (voice_note_id, shared_with_user_id)
);

CREATE INDEX idx_voice_shares_shared_with_user_id ON voice_shares (shared_with_user_id);
CREATE INDEX idx_voice_shares_voice_note_id       ON voice_shares (voice_note_id);
CREATE INDEX idx_voice_shares_owner_shared_with   ON voice_shares (owner_id, shared_with_user_id);

-- No foreign keys to voice_notes or users tables, matching the rest of the
-- schema's modulith convention: cross-module references are plain BIGINT
-- columns, not FK constraints, so each module's table stays independently
-- creatable/droppable and the app never depends on cross-module FK
-- enforcement at the database layer.
