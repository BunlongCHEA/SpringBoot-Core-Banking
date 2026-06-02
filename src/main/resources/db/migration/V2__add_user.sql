CREATE TYPE user_role AS ENUM (
    'SUPER_ADMIN', 'ADMIN', 'CUSTOMER_SERVICE', 'TELLER', 'AUDITOR', 'CUSTOMER'
);

CREATE TYPE password_policy_interval AS ENUM (
    'ONE_MONTH', 'THREE_MONTHS', 'SIX_MONTHS', 'TWELVE_MONTHS'
);

CREATE TABLE users (
    user_id                 UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    username                VARCHAR(50)                 NOT NULL UNIQUE,
    email                   VARCHAR(255)                NOT NULL UNIQUE,
    password_hash           VARCHAR(255)                NOT NULL,
    role                    user_role                   NOT NULL DEFAULT 'CUSTOMER_SERVICE',
    branch_id               UUID                        REFERENCES branches(branch_id),
    is_active               BOOLEAN                     NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN                     NOT NULL DEFAULT FALSE,
    -- first-login flag: force password change before any other operation
    must_change_password    BOOLEAN                     NOT NULL DEFAULT TRUE,
    password_changed_at     TIMESTAMPTZ,
    password_policy         password_policy_interval    NOT NULL DEFAULT 'THREE_MONTHS',
    password_expires_at     TIMESTAMPTZ,
    created_by              UUID,
    created_at              TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_role     ON users(role);
CREATE INDEX idx_users_active   ON users(is_active) WHERE NOT is_deleted;

-- Password history: keep last 5 hashes to prevent reuse
CREATE TABLE password_histories (
    history_id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pwd_history_user ON password_histories(user_id, created_at DESC);