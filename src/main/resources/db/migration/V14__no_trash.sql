-- Deleting a note is now permanent: there is no Recently deleted and no restore.
-- Notes already in the trash are made due now, so TrashPurgeJob removes them,
-- with their clips and stored files, on its next run.
UPDATE notes SET purge_at = LOCALTIMESTAMP WHERE deleted = true;
