-- ============================================================
-- Seeds the branch record that corresponds to Go-KYC bank_id = BANK00000002.
--
-- WHY branch_code = bank_id?
--   Go-KYC's `banks.id` (e.g. "BANK00000002") is the external identifier
--   for the bank that owns KYC records.  In CBS, `branches.branch_code` is
--   the matching internal identifier.  When CustomerService.createFromKyc()
--   receives a verified KYC record whose bank_id = "BANK00000002", it can
--   look up this branch and link it to the new Customer row.
--
-- ON CONFLICT DO NOTHING makes this migration idempotent and safe to re-run.
-- ============================================================

DO $$
DECLARE
    v_address_id UUID := gen_random_uuid();
    v_branch_id  UUID := gen_random_uuid();
BEGIN
    -- Guard: skip everything if the branch already exists
    IF EXISTS (SELECT 1 FROM branches WHERE branch_code = 'BANK00000002') THEN
        RAISE NOTICE 'Branch BANK00000002 already exists — skipping seed.';
        RETURN;
    END IF;
 
    -- 1. Address
    INSERT INTO addresses (
        address_id,
        line1,
        city,
        country_code,
        is_primary,
        created_at
    ) VALUES (
        v_address_id,
        'Head Office',
        'Phnom Penh',
        'KH',
        TRUE,
        NOW()
    );
 
    -- 2. Branch — references the address just inserted above
    INSERT INTO branches (
        branch_id,
        branch_code,
        name,
        address_id,
        is_active,
        created_at
    ) VALUES (
        v_branch_id,
        'BANK00000002',
        'Main Branch',
        v_address_id,       -- UUID captured from the address insert
        TRUE,
        NOW()
    );
 
    RAISE NOTICE 'Branch BANK00000002 seeded: branch_id=%, address_id=%',
        v_branch_id, v_address_id;
END;
$$;