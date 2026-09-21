-- Link each Notare user to its account in internal-auth-server.
-- Nullable: rows created before the IAM integration have no IAM account yet.
ALTER TABLE users ADD COLUMN internal_auth_id VARCHAR(255);

-- One Notare user per IAM account. PostgreSQL and H2 both allow multiple NULLs
-- in a unique index, so pre-integration rows do not collide.
CREATE UNIQUE INDEX uq_users_internal_auth_id ON users (internal_auth_id);
