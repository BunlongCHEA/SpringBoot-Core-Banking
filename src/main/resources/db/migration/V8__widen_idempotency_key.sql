-- transactions is partitioned by month — ALTER COLUMN TYPE on the parent
-- propagates to every existing partition automatically in Postgres,
-- same as the ADD/DROP COLUMN behavior used earlier for channel_id.
ALTER TABLE transactions ALTER COLUMN idempotency_key TYPE VARCHAR(100);

-- this is the durable idempotency table added a few turns back — same value,
-- must stay wide enough to match, since it's compared/inserted alongside it.
ALTER TABLE transactions_idempotency ALTER COLUMN idempotency_key TYPE VARCHAR(100);