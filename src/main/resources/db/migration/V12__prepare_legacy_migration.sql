-- Step 18e: marks for the one-off legacy data migration (LegacyDataMigrationJob).
-- A row with migrated_at set has been handled; a second run skips it.
ALTER TABLE voice_notes  ADD COLUMN migrated_at TIMESTAMP;
ALTER TABLE voice_shares ADD COLUMN migrated_at TIMESTAMP;
