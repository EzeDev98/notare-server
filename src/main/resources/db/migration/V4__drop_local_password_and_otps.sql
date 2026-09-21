-- Credentials and email-verification codes now live only in internal-auth-server.
DROP TABLE IF EXISTS otps;
ALTER TABLE users DROP COLUMN password;
