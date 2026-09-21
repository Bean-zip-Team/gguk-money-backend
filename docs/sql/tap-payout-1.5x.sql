-- Tap payout 1.5x: raise the earn rate and the daily cap together (BEA-283).
--
-- app_config is append-only. Selection is
--   SELECT DISTINCT ON (config_key) * ... WHERE effective_at <= now
--   ORDER BY config_key, effective_at DESC, id DESC
-- so inserting a row with a later effective_at IS the update. Nothing is
-- overwritten and the previous value stays as history.
--
-- config_value is jsonb. Numbers go in bare (13), not quoted ("13") --
-- the loader hands the raw jsonb text to Integer.parseInt, so quotes break it.
--
-- No deploy needed. TapPolicyConfig refreshes on a 60s schedule, so this takes
-- effect within a minute of committing.
--
--
-- WHY BOTH VALUES MOVE TOGETHER
--
-- Raising only the rate spends nothing extra: the 150P daily cap binds first,
-- so users just reach it sooner and close the app earlier. That costs ad
-- impressions (~30 KRW each on rewarded) while daily payout stays at 150P.
--
-- Today the curve and the cap are matched: 3,000 valid taps / 20 taps-per-point
-- = 150 payouts = exactly the 150P cap. Keeping that alignment is the point.
--
--   base 20 -> 13     draw range 15~25 -> 10~16 taps per 1P (mean 20 -> 13)
--   cap  150 -> 231   3,000 / 13 = 230.8, so 231 lands on the same 3,000 taps
--
-- Simulated over 100k days, the last draw usually overshoots 3,000 and the cap
-- is approached rather than hit exactly -- which is already true today:
--
--   base 20, cap 150  ->  149.0P earned per full day
--   base 13, cap 231  ->  229.7P            (1.54x, and the same near-miss shape)
--   base 14, cap 231  ->  206.4P            (24.6P short -- cap never in reach)
--
-- tap.validity.maxPerDay stays at 3,000. Raising it would not increase the
-- reward, only how long a user has to grind for it.
--
--
-- WHY 13 AND NOT 13.3
--
-- curveGeneralBase is read with getInt, so 20 / 1.5 = 13.33 cannot be stored.
-- 14 is worse than it looks: drawUniform rounds the bounds, and
-- round(14 * 0.75) = 11 with round(14 * 1.25) = 18 gives a 11~18 range whose
-- mean is 14.5, not 14 -- that yields ~207P over 3,000 taps and the user never
-- reaches the cap. base 13 rounds cleanly to 10~16 with a mean of exactly 13.

BEGIN;

INSERT INTO app_config (public_id, config_key, config_value, effective_at, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'tap.curve.general.base', '13'::jsonb,  now(), now(), now()),
    (gen_random_uuid(), 'tap.point.dailyCap',     '231'::jsonb, now(), now(), now());

COMMIT;

-- Verification -- run after the insert. Expect base=13 and dailyCap=231.
--   SELECT DISTINCT ON (config_key) config_key, config_value, effective_at
--     FROM app_config
--    WHERE config_key IN ('tap.curve.general.base', 'tap.point.dailyCap', 'tap.validity.maxPerDay')
--      AND effective_at <= now()
--    ORDER BY config_key, effective_at DESC, id DESC;
--
-- Confirm it reached the application (within 60s):
--   GET /api/tap/today  -- remainingTapsToNextPoint should now fall in 1..16
--
--
-- ROLLBACK -- the insert above is already committed, so revert by appending the
-- old values again rather than deleting rows.
--   INSERT INTO app_config (public_id, config_key, config_value, effective_at, created_at, updated_at)
--   VALUES
--       (gen_random_uuid(), 'tap.curve.general.base', '20'::jsonb,  now(), now(), now()),
--       (gen_random_uuid(), 'tap.point.dailyCap',     '150'::jsonb, now(), now(), now());
--
-- Users who already earned past 150P today keep those points. The cap is
-- compared against a running daily counter, so lowering it stops further
-- payouts for the rest of the day rather than clawing anything back.
