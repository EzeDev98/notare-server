-- Step 17g (B-03): progress of an account deletion, so a failure halfway can continue.
-- A row exists only while a deletion is unfinished. It is removed with the user at the very end,
-- so this table never holds a deleted user's id.
CREATE TABLE account_deletions (
    user_id           BIGINT       PRIMARY KEY,
    internal_auth_id  VARCHAR(255),
    status            VARCHAR(20)  NOT NULL,
    started_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    last_error        VARCHAR(500)
);
