CREATE TABLE system_settings (
    setting_key  VARCHAR(100)  PRIMARY KEY,
    value        VARCHAR(255)  NOT NULL,
    description  VARCHAR(255),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

INSERT INTO system_settings (setting_key, value, description) VALUES
    ('high_value_txn_threshold_usd', '5000', 'Withdrawals/transfers-out above this USD-equivalent trigger a high-value fee');

-- Needed to convert "amount in any currency" → USD equivalent for the threshold check.
ALTER TABLE currencies ADD COLUMN usd_exchange_rate NUMERIC(18,6) NOT NULL DEFAULT 1;
UPDATE currencies SET usd_exchange_rate = 1 WHERE currency_code = 'USD';

-- set the rest to your actual current rates, e.g.:
-- UPDATE currencies SET usd_exchange_rate = 1.08   WHERE currency_code = 'EUR';
-- UPDATE currencies SET usd_exchange_rate = 0.00025 WHERE currency_code = 'KHR';