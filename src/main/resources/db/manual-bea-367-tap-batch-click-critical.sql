-- BEA-367: apply before deploying this branch (ddl-auto=validate), on alpha and production alike.
-- Nullable columns without defaults: ADD COLUMN only touches the catalog, but still takes ACCESS EXCLUSIVE briefly.
-- Repeatable: IF NOT EXISTS makes a second run a no-op.
-- Rows written before BEA-367 stay NULL: their click criticals were random draws and cannot be recomputed.
-- After new code has written batches: keep these columns (forward-fix). Old code ignores them, so rollback is safe.
BEGIN;
SET LOCAL lock_timeout = '3s';

ALTER TABLE tap_batch ADD COLUMN IF NOT EXISTS start_tap_number BIGINT,
    ADD COLUMN IF NOT EXISTS equipped_keycap_code VARCHAR(255),
    ADD COLUMN IF NOT EXISTS click_critical_probability DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS click_critical_multiplier INTEGER,
    ADD COLUMN IF NOT EXISTS click_critical_count INTEGER;

COMMIT;
