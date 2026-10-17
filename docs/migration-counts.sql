-- Step 18f: counts to record BEFORE the migration and compare AFTER it. PostgreSQL.
-- Run: psql "$STAGING_URL" -f docs/migration-counts.sql > counts-before.txt
SELECT * FROM (
  SELECT 'notes: pinned=' || pinned::text || ' archived=' || archived::text || ' deleted=' || deleted::text
         || ' body_mode=' || body_mode AS check_name, count(*) AS n
    FROM notes GROUP BY pinned, archived, deleted, body_mode
  UNION ALL SELECT 'voice_notes: linked (has a note)', count(*) FROM voice_notes WHERE note_id IS NOT NULL
  UNION ALL SELECT 'voice_notes: standalone (no note)', count(*) FROM voice_notes WHERE note_id IS NULL
  UNION ALL SELECT 'note_shares: ' || permission, count(*) FROM note_shares GROUP BY permission
  UNION ALL SELECT 'voice_shares: total', count(*) FROM voice_shares
  UNION ALL SELECT 'transcriptions: ' || status, count(*) FROM transcriptions GROUP BY status
  UNION ALL SELECT 'collaborations: ' || status, count(*) FROM collaborations GROUP BY status
  UNION ALL SELECT 'notifications: total', count(*) FROM notifications
  UNION ALL SELECT 'ORPHAN clips (note id points nowhere)', count(*) FROM voice_notes v
             WHERE v.note_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM notes n WHERE n.id = v.note_id)
  UNION ALL SELECT 'DUPLICATE clip client ids', count(*) FROM (
             SELECT owner_id, client_id FROM voice_notes WHERE client_id IS NOT NULL
             GROUP BY owner_id, client_id HAVING count(*) > 1) d
  UNION ALL SELECT 'clips with no client id', count(*) FROM voice_notes WHERE client_id IS NULL
) t ORDER BY check_name;
