-- Step 18f: extra checks for AFTER the job ran (needs V12). PostgreSQL.
-- Run: psql "$STAGING_URL" -f docs/migration-counts-after.sql
SELECT * FROM (
  SELECT 'new notes from standalone clips' AS check_name, count(*) AS n FROM notes WHERE client_id LIKE 'legacy-clip-%'
  UNION ALL SELECT 'new notes from shared attached clips', count(*) FROM notes WHERE client_id LIKE 'legacy-share-%'
  UNION ALL SELECT 'copied clips for shared attached clips', count(*) FROM voice_notes WHERE client_id LIKE 'legacy-share-clip-%'
  UNION ALL SELECT 'standalone clips left (visible, not trashed)', count(*) FROM voice_notes
             WHERE note_id IS NULL AND deleted = false AND removed = false
  UNION ALL SELECT 'voice shares migrated', count(*) FROM voice_shares WHERE migrated_at IS NOT NULL
  UNION ALL SELECT 'voice shares LEFT OVER (not migrated)', count(*) FROM voice_shares WHERE migrated_at IS NULL
) t ORDER BY check_name;
