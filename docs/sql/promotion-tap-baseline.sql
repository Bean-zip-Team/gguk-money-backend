-- user_tap_progress.promotion_tap_baseline: tap mission cutoff snapshot (BEA-278).
--
-- Flyway is not used in this repository, so this DDL is applied by hand.
-- Production runs ddl-auto=validate: apply this BEFORE deploying the entity,
-- otherwise the application fails to boot.
--
-- Why a snapshot: taps are stored as a running total with no per-tap timestamp,
-- so "only taps after the cutoff" cannot be derived the way the keycap mission
-- does it with user_keycap.completed_at. The cumulative value at the cutoff is
-- recorded per user instead, and eligibility compares the difference.
--
-- Nullable on purpose. NULL means no baseline yet, which is not the same as 0 —
-- a user with no baseline has not been anchored to the cutoff and must not be
-- paid. Backfilling this column with 0 would make every existing user eligible
-- immediately and drain the 25,000 budget in one pass.

ALTER TABLE user_tap_progress ADD COLUMN promotion_tap_baseline BIGINT;

-- Verification after applying:
--   SELECT column_name, data_type, is_nullable
--     FROM information_schema.columns
--    WHERE table_name = 'user_tap_progress' AND column_name = 'promotion_tap_baseline';
--   SELECT count(*) FILTER (WHERE promotion_tap_baseline IS NOT NULL) AS anchored,
--          count(*) AS total
--     FROM user_tap_progress;
