CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash TEXT NOT NULL,
    roles TEXT[] NOT NULL,
    enabled BOOLEAN NOT NULL,
    session_version BIGINT NOT NULL DEFAULT 0,
    customer_id UUID UNIQUE REFERENCES customers(id)
);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES user_account(id),
    token_hash CHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    family_expires_at TIMESTAMPTZ NOT NULL,
    rotated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX refresh_token_family_id_idx ON refresh_token(family_id);
CREATE INDEX refresh_token_user_id_idx ON refresh_token(user_id);
