DROP TABLE IF EXISTS loan_payments CASCADE;
DROP TABLE IF EXISTS loans CASCADE;
DROP TYPE IF EXISTS loan_status;

CREATE TABLE loans (
    loan_id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_number              VARCHAR(30)    NOT NULL UNIQUE,
    account_id               UUID           NOT NULL REFERENCES accounts(account_id),
    disbursement_account_id  UUID           REFERENCES accounts(account_id),
    principal                NUMERIC(20,4)  NOT NULL,
    outstanding_balance      NUMERIC(20,4)  NOT NULL,
    interest_rate            NUMERIC(6,4)   NOT NULL,
    term_months              INT            NOT NULL,
    monthly_installment      NUMERIC(20,4),
    currency_code            VARCHAR(3)     NOT NULL REFERENCES currencies(currency_code),
    status                   loan_status    NOT NULL DEFAULT 'PENDING',
    approved_at              TIMESTAMPTZ,
    approved_by              UUID           REFERENCES users(user_id),
    rejected_at              TIMESTAMPTZ,
    rejection_reason         VARCHAR(255),
    disbursed_at             TIMESTAMPTZ,
    maturity_date            DATE,
    next_payment_date        DATE,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ
);

CREATE INDEX idx_loans_account      ON loans(account_id);
CREATE INDEX idx_loans_status       ON loans(status);
CREATE INDEX idx_loans_next_payment ON loans(next_payment_date);

CREATE TABLE loan_payments (
    loan_payment_id    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id            UUID          NOT NULL REFERENCES loans(loan_id),
    transaction_id     UUID          NOT NULL,
    amount             NUMERIC(20,4) NOT NULL,
    principal_portion  NUMERIC(20,4) NOT NULL,
    interest_portion   NUMERIC(20,4) NOT NULL,
    outstanding_after  NUMERIC(20,4) NOT NULL,
    paid_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_loan_payments_loan ON loan_payments(loan_id, paid_at DESC);

-- Optional cleanup: any existing LOAN-type accounts that only existed to
-- back the loans just dropped are now orphaned (loans → accounts is the FK
-- direction, so dropping `loans` doesn't cascade back onto `accounts`).
-- Only run this if you actually want them gone too — it removes every
-- LOAN-type account, not just orphaned ones, since `loans` is now empty:
-- DELETE FROM accounts
-- WHERE account_type_id = (SELECT account_type_id FROM account_types WHERE code = 'LOAN');