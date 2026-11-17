-- Collaboration and sharing are removed from Notare.
-- Drops their tables and columns, and the notifications only they created
-- (Hibernate cannot read a row whose type is no longer an enum constant).

DELETE FROM notifications
WHERE type IN (
        'COLLABORATION_INVITED',
        'COLLABORATION_ACCEPTED',
        'COLLABORATION_REJECTED',
        'COLLABORATION_REMOVED',
        'NOTE_SHARED',
        'NOTE_SHARE_PERMISSION_CHANGED',
        'NOTE_SHARE_REVOKED',
        'VOICE_NOTE_SHARED',
        'VOICE_NOTE_SHARE_PERMISSION_CHANGED',
        'VOICE_NOTE_SHARE_REVOKED')
   OR target_type = 'COLLABORATION';

ALTER TABLE notifications DROP COLUMN IF EXISTS previous_permission;
ALTER TABLE notifications DROP COLUMN IF EXISTS new_permission;

ALTER TABLE notes DROP COLUMN IF EXISTS body_mode;

DROP TABLE IF EXISTS note_updates;
DROP TABLE IF EXISTS note_snapshots;
DROP TABLE IF EXISTS voice_shares;
DROP TABLE IF EXISTS note_shares;
DROP TABLE IF EXISTS collaborations;
