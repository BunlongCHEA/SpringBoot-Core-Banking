-- ============================================================
-- Core Banking System — Initial Schema
-- V1__init_core_banking_schema.sql
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── ENUM TYPES ───────────────────────────────────────────────
CREATE TYPE customer_status      AS ENUM ('ACTIVE','INACTIVE','SUSPENDED','BLOCKED');
CREATE TYPE customer_type        AS ENUM ('INDIVIDUAL','CORPORATE');
CREATE TYPE account_type         AS ENUM ('SAVINGS','CHECKING','LOAN','FIXED_DEPOSIT','CURRENT');
CREATE TYPE account_status       AS ENUM ('ACTIVE','DORMANT','CLOSED','FROZEN');
CREATE TYPE transaction_type     AS ENUM ('TRANSFER','DEPOSIT','WITHDRAWAL','PAYMENT','REVERSAL','FEE');
CREATE TYPE transaction_status   AS ENUM ('PENDING','PROCESSING','COMPLETED','FAILED','REVERSED');
CREATE TYPE transaction_channel  AS ENUM ('ATM','MOBILE','WEB','BRANCH','API','POS');
CREATE TYPE entry_type           AS ENUM ('DEBIT','CREDIT');
CREATE TYPE card_type            AS ENUM ('DEBIT','CREDIT','PREPAID');
CREATE TYPE card_status          AS ENUM ('ACTIVE','INACTIVE','BLOCKED','EXPIRED','PENDING');
CREATE TYPE loan_status          AS ENUM ('PENDING','DISBURSED','ACTIVE','CLOSED','DEFAULTED','WRITTEN_OFF');
CREATE TYPE kyc_status           AS ENUM ('PENDING','VERIFIED','REJECTED','EXPIRED');
CREATE TYPE kyc_document_type    AS ENUM ('PASSPORT','NATIONAL_ID','DRIVING_LICENSE','UTILITY_BILL','BANK_STATEMENT');
CREATE TYPE audit_action         AS ENUM ('CREATE','UPDATE','DELETE','VIEW','LOGIN','LOGOUT');
CREATE TYPE fee_type             AS ENUM ('TRANSFER_FEE','ATM_FEE','MAINTENANCE_FEE','PENALTY','REVERSAL_FEE');

-- ── CURRENCIES ───────────────────────────────────────────────
CREATE TABLE currencies (
    currency_code   VARCHAR(3)    PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    symbol          VARCHAR(5)    NOT NULL,
    decimal_places  SMALLINT      NOT NULL DEFAULT 2,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

INSERT INTO currencies (currency_code, name, symbol, decimal_places) VALUES
    ('USD', 'US Dollar',          '$',  2),
    ('EUR', 'Euro',               '€',  2),
    ('GBP', 'British Pound',      '£',  2),
    ('KHR', 'Khmer Riel',         '៛',  0),
    ('THB', 'Thai Baht',          '฿',  2),
    ('SGD', 'Singapore Dollar',   'S$', 2),
    ('JPY', 'Japanese Yen',       '¥',  0),
    ('CNY', 'Chinese Yuan',       '¥',  2);

-- ── ADDRESSES ────────────────────────────────────────────────
CREATE TABLE addresses (
    address_id      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    line1           VARCHAR(255)  NOT NULL,
    line2           VARCHAR(255),
    city            VARCHAR(100)  NOT NULL,
    state_province  VARCHAR(100),
    postal_code     VARCHAR(20),
    country_code    VARCHAR(2)    NOT NULL,
    is_primary      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

-- ── BRANCHES ─────────────────────────────────────────────────
CREATE TABLE branches (
    branch_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_code     VARCHAR(20)   NOT NULL UNIQUE,
    name            VARCHAR(255)  NOT NULL,
    address_id      UUID          REFERENCES addresses(address_id),
    swift_code      VARCHAR(11),
    phone           VARCHAR(20),
    email           VARCHAR(255),
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

-- ── CUSTOMERS ────────────────────────────────────────────────
CREATE TABLE customers (
    customer_id     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_code   VARCHAR(20)     NOT NULL UNIQUE,
    full_name       VARCHAR(255)    NOT NULL,
    date_of_birth   DATE,
    national_id     VARCHAR(50)     UNIQUE,
    email           VARCHAR(255)    UNIQUE,
    phone           VARCHAR(20)     UNIQUE,
    status          customer_status NOT NULL DEFAULT 'ACTIVE',
    customer_type   customer_type   NOT NULL DEFAULT 'INDIVIDUAL',
    branch_id       UUID            REFERENCES branches(branch_id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_customers_email       ON customers(email);
CREATE INDEX idx_customers_phone       ON customers(phone);
CREATE INDEX idx_customers_national_id ON customers(national_id);
CREATE INDEX idx_customers_status      ON customers(status);

-- ── CUSTOMER ADDRESSES ───────────────────────────────────────
CREATE TABLE customer_addresses (
    customer_id UUID NOT NULL REFERENCES customers(customer_id) ON DELETE CASCADE,
    address_id  UUID NOT NULL REFERENCES addresses(address_id)  ON DELETE CASCADE,
    PRIMARY KEY (customer_id, address_id)
);

-- ── ACCOUNTS ─────────────────────────────────────────────────
CREATE TABLE accounts (
    account_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number      VARCHAR(20)     NOT NULL UNIQUE,
    customer_id         UUID            NOT NULL REFERENCES customers(customer_id),
    account_type        account_type    NOT NULL,
    currency_code       VARCHAR(3)      NOT NULL REFERENCES currencies(currency_code),
    balance             NUMERIC(20,4)   NOT NULL DEFAULT 0 CHECK (balance >= 0),
    available_balance   NUMERIC(20,4)   NOT NULL DEFAULT 0 CHECK (available_balance >= 0),
    hold_balance        NUMERIC(20,4)   NOT NULL DEFAULT 0 CHECK (hold_balance >= 0),
    status              account_status  NOT NULL DEFAULT 'ACTIVE',
    branch_id           UUID            REFERENCES branches(branch_id),
    daily_limit         NUMERIC(20,4)   DEFAULT 50000.0000,
    interest_rate       NUMERIC(6,4)    DEFAULT 0.0000,
    opened_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT chk_balance_consistency
        CHECK (balance = available_balance + hold_balance)
);

CREATE INDEX idx_accounts_customer ON accounts(customer_id);
CREATE INDEX idx_accounts_number   ON accounts(account_number);
CREATE INDEX idx_accounts_status   ON accounts(status);
CREATE INDEX idx_accounts_currency ON accounts(currency_code);

-- ── TRANSACTIONS (partitioned) ───────────────────────────────
-- PRIMARY KEY must include the partition key column (initiated_at).
--      transaction_id alone is NOT sufficient on a partitioned table.
--      Composite PK: (transaction_id, initiated_at)
--
-- UNIQUE constraint on idempotency_key must also include initiated_at
--      for the same reason. We enforce true global uniqueness via a
--      separate unique index on the NON-partitioned idempotency lookup
--      table below (transactions_idempotency).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE transactions (
    transaction_id      UUID                 NOT NULL DEFAULT gen_random_uuid(),
    initiated_at        TIMESTAMPTZ          NOT NULL DEFAULT NOW(),   -- ← partition key

    reference_number    VARCHAR(40)          NOT NULL,
    idempotency_key     VARCHAR(64)          NOT NULL,
    debit_account_id    UUID                 REFERENCES accounts(account_id),
    credit_account_id   UUID                 REFERENCES accounts(account_id),
    transaction_type    transaction_type     NOT NULL,
    amount              NUMERIC(20,4)        NOT NULL CHECK (amount > 0),
    currency_code       VARCHAR(3)           NOT NULL REFERENCES currencies(currency_code),
    exchange_rate       NUMERIC(12,6)        NOT NULL DEFAULT 1.000000,
    base_amount         NUMERIC(20,4),
    status              transaction_status   NOT NULL DEFAULT 'PENDING',
    channel             transaction_channel,
    description         VARCHAR(500),
    initiated_by        UUID,
    completed_at        TIMESTAMPTZ,
    reversed_at         TIMESTAMPTZ,
    reversal_ref        VARCHAR(40),
    created_at          TIMESTAMPTZ          NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,

    -- ✅ Composite PK includes partition key
    PRIMARY KEY (transaction_id, initiated_at)

) PARTITION BY RANGE (initiated_at);

-- ── Global uniqueness for reference_number & idempotency_key ─
-- Because UNIQUE across all partitions requires the partition key,
-- we use a lightweight dedicated lookup table as the source of truth.
CREATE TABLE transactions_idempotency (
    idempotency_key  VARCHAR(64)  PRIMARY KEY,
    transaction_id   UUID         NOT NULL,
    initiated_at     TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions_reference (
    reference_number VARCHAR(40)  PRIMARY KEY,
    transaction_id   UUID         NOT NULL,
    initiated_at     TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Monthly partitions ────────────────────────────────────────
CREATE TABLE transactions_2026_01 PARTITION OF transactions
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE transactions_2026_02 PARTITION OF transactions
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE transactions_2026_03 PARTITION OF transactions
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE transactions_2026_04 PARTITION OF transactions
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE transactions_2026_05 PARTITION OF transactions
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE transactions_2026_06 PARTITION OF transactions
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE transactions_2026_07 PARTITION OF transactions
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE transactions_2026_08 PARTITION OF transactions
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE transactions_2026_09 PARTITION OF transactions
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE transactions_2026_10 PARTITION OF transactions
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE transactions_2026_11 PARTITION OF transactions
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE transactions_2026_12 PARTITION OF transactions
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE transactions_default  PARTITION OF transactions DEFAULT;

-- Indexes on partitioned table (applied to all partitions automatically)
CREATE INDEX idx_txn_debit_account  ON transactions(debit_account_id);
CREATE INDEX idx_txn_credit_account ON transactions(credit_account_id);
CREATE INDEX idx_txn_status_date    ON transactions(status, initiated_at DESC);
CREATE INDEX idx_txn_reference      ON transactions(reference_number);
CREATE INDEX idx_txn_type           ON transactions(transaction_type);
CREATE INDEX idx_txn_initiated_at   ON transactions(initiated_at DESC);

-- ── ACCOUNT LEDGER (Double-Entry) ────────────────────────────
-- transaction_id is a plain UUID column — no FK to transactions
--      because the transactions PK is now composite (id + initiated_at).
--      Referential integrity is enforced at the application/service layer.
CREATE TABLE account_ledgers (
    ledger_id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          UUID          NOT NULL REFERENCES accounts(account_id),
    transaction_id      UUID          NOT NULL,   -- ← no FK (partitioned table)
    entry_type          entry_type    NOT NULL,
    amount              NUMERIC(20,4) NOT NULL CHECK (amount > 0),
    balance_after       NUMERIC(20,4) NOT NULL,
    value_date          DATE          NOT NULL,
    posting_date        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    description         VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledgers_account_date ON account_ledgers(account_id, posting_date DESC);
CREATE INDEX idx_ledgers_transaction  ON account_ledgers(transaction_id);
CREATE INDEX idx_ledgers_value_date   ON account_ledgers(account_id, value_date DESC);

-- ── TRANSACTION FEES ─────────────────────────────────────────
-- transaction_id is plain UUID, no FK to partitioned table.
CREATE TABLE transaction_fees (
    fee_id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID          NOT NULL,       -- ← no FK (partitioned table)
    fee_type        fee_type      NOT NULL,
    amount          NUMERIC(20,4) NOT NULL CHECK (amount >= 0),
    currency_code   VARCHAR(3)    NOT NULL REFERENCES currencies(currency_code),
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fees_transaction ON transaction_fees(transaction_id);

-- ── CARDS ────────────────────────────────────────────────────
CREATE TABLE cards (
    card_id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id            UUID          NOT NULL REFERENCES accounts(account_id),
    card_number_hash      VARCHAR(64)   NOT NULL UNIQUE,
    card_last_four        VARCHAR(4)    NOT NULL,
    card_type             card_type     NOT NULL,
    expiry_date           DATE          NOT NULL,
    status                card_status   NOT NULL DEFAULT 'PENDING',
    daily_limit           NUMERIC(20,4) DEFAULT 5000.0000,
    contactless_enabled   BOOLEAN       NOT NULL DEFAULT TRUE,
    international_enabled BOOLEAN       NOT NULL DEFAULT FALSE,
    issued_at             TIMESTAMPTZ,
    blocked_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ
);

CREATE INDEX idx_cards_account ON cards(account_id);
CREATE INDEX idx_cards_status  ON cards(status);

-- ── LOANS ────────────────────────────────────────────────────
CREATE TABLE loans (
    loan_id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_number          VARCHAR(30)   NOT NULL UNIQUE,
    account_id           UUID          NOT NULL REFERENCES accounts(account_id),
    principal            NUMERIC(20,4) NOT NULL CHECK (principal > 0),
    outstanding_balance  NUMERIC(20,4) NOT NULL,
    interest_rate        NUMERIC(6,4)  NOT NULL,
    term_months          INTEGER       NOT NULL CHECK (term_months > 0),
    monthly_installment  NUMERIC(20,4),
    currency_code        VARCHAR(3)    NOT NULL REFERENCES currencies(currency_code),
    disbursed_at         TIMESTAMPTZ,
    maturity_date        DATE,
    next_payment_date    DATE,
    status               loan_status   NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ
);

CREATE INDEX idx_loans_account ON loans(account_id);
CREATE INDEX idx_loans_status  ON loans(status);

-- ── KYC VERIFICATIONS ────────────────────────────────────────
CREATE TABLE kyc_verifications (
    kyc_id            UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id       UUID              NOT NULL REFERENCES customers(customer_id),
    document_type     kyc_document_type NOT NULL,
    document_number   VARCHAR(100)      NOT NULL,
    document_expiry   DATE,
    status            kyc_status        NOT NULL DEFAULT 'PENDING',
    verified_by       UUID,
    verified_at       TIMESTAMPTZ,
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ
);

CREATE INDEX idx_kyc_customer ON kyc_verifications(customer_id);
CREATE INDEX idx_kyc_status   ON kyc_verifications(status);

-- ── BENEFICIARIES ────────────────────────────────────────────
CREATE TABLE beneficiaries (
    beneficiary_id    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_customer_id UUID          NOT NULL REFERENCES customers(customer_id),
    nickname          VARCHAR(100)  NOT NULL,
    account_number    VARCHAR(20)   NOT NULL,
    bank_code         VARCHAR(20),
    bank_name         VARCHAR(255),
    beneficiary_name  VARCHAR(255)  NOT NULL,
    currency_code     VARCHAR(3)    REFERENCES currencies(currency_code),
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ
);

CREATE INDEX idx_beneficiaries_owner ON beneficiaries(owner_customer_id);

-- ── AUDIT LOGS ───────────────────────────────────────────────
CREATE TABLE audit_logs (
    audit_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(100)  NOT NULL,
    entity_id       UUID          NOT NULL,
    action          audit_action  NOT NULL,
    changed_by      UUID,
    changed_by_role VARCHAR(50),
    ip_address      VARCHAR(45),
    old_value       JSONB,
    new_value       JSONB,
    metadata        JSONB,
    changed_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity     ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_changed_by ON audit_logs(changed_by);
CREATE INDEX idx_audit_changed_at ON audit_logs(changed_at DESC);