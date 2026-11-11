CREATE TABLE password_reset_token (
    user_id UUID PRIMARY KEY REFERENCES user_account(id),
    token_hash CHAR(64) NOT NULL CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    expires_at TIMESTAMPTZ NOT NULL
);
