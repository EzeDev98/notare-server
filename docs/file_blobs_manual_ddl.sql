-- Manual DDL for the file_blobs table — PRODUCTION ONLY, Postgres.
--
-- Same situation docs/voice_shares_manual_ddl.sql already documents: this
-- project has no Flyway/Liquibase yet, and prod runs hibernate.ddl-auto=
-- validate, which refuses to start if the schema doesn't already match the
-- entities. file_blobs is the backing table for PostgresFileStorageService
-- (notare.storage.backend=postgres) — it must exist BEFORE you deploy with
-- that backend selected, or the app will fail to boot.
--
-- This is a STOPGAP. Once Flyway is added (planned next), this table gets
-- folded into the regular migration set and this file can be deleted.
--
-- Run this once against the Neon database, before deploying.

CREATE TABLE file_blobs (
    storage_key   VARCHAR(100) PRIMARY KEY,
    content       BYTEA        NOT NULL,
    content_type  VARCHAR(255),
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- No index beyond the primary key: every read/delete is a point lookup by
-- storage_key (the same access pattern LocalFileStorageService and
-- R2FileStorageService use against their own backends), so nothing else is
-- queried on. No foreign key to voice_notes.storage_key, matching the rest
-- of the schema's modulith convention — cross-module/cross-concern
-- references stay plain columns, not FK constraints.
