CREATE TABLE channels (
    channel_id   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(20)   NOT NULL UNIQUE,
    name         VARCHAR(100)  NOT NULL,
    is_active    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

INSERT INTO channels (code, name) VALUES
    ('BRANCH', 'Branch'),
    ('ATM',    'ATM'),
    ('MOBILE', 'Mobile App'),
    ('WEB',    'Web Banking'),
    ('API',    'API'),
    ('POS',    'Point of Sale');

-- transactions is partitioned by month — ADD/DROP COLUMN on the parent
-- propagates to every partition automatically in Postgres, no per-partition work needed.
ALTER TABLE transactions ADD COLUMN channel_id UUID REFERENCES channels(channel_id);

UPDATE transactions t SET channel_id = c.channel_id
FROM channels c WHERE c.code = t.channel::text;

ALTER TABLE transactions DROP COLUMN channel;