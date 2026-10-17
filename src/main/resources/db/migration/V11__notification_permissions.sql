-- Step 17i (B-12): a permission-changed notification says what the access was and what it became,
-- so the client can write "Viewer" or "Editor" and tell a promotion from a demotion.
-- Null on every other type, and on rows created before this migration.
ALTER TABLE notifications ADD COLUMN previous_permission VARCHAR(20);
ALTER TABLE notifications ADD COLUMN new_permission      VARCHAR(20);
