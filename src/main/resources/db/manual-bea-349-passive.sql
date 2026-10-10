-- BEA-349: run after BEA-329 migration, with old application writers stopped.
-- Apply before deploying this branch (ddl-auto=validate). No existing counters or scores are reset.
-- Repeatable: only NULL new counters are backfilled; later increments survive repeated execution.
BEGIN;
SET LOCAL lock_timeout = '3s';

ALTER TABLE user_tap_daily ADD COLUMN IF NOT EXISTS total_effective_tap_count BIGINT;
UPDATE user_tap_daily SET total_effective_tap_count = total_valid_tap_count
WHERE total_effective_tap_count IS NULL;
ALTER TABLE user_tap_daily ALTER COLUMN total_effective_tap_count SET DEFAULT 0,
    ALTER COLUMN total_effective_tap_count SET NOT NULL;

ALTER TABLE user_tap_progress ADD COLUMN IF NOT EXISTS cumulative_mission_tap_count BIGINT,
    ADD COLUMN IF NOT EXISTS cumulative_ranking_tap_count BIGINT;
UPDATE user_tap_progress SET cumulative_mission_tap_count = cumulative_valid_tap_count
WHERE cumulative_mission_tap_count IS NULL;
UPDATE user_tap_progress p
SET cumulative_ranking_tap_count = GREATEST(p.cumulative_valid_tap_count,
    COALESCE((SELECT MAX(e.score - e.ranking_boost_score) FROM ranking_entry e
              JOIN ranking_season s ON s.id=e.season_id
              WHERE e.user_id=p.user_id AND s.ranking_type='ALL_TIME'),0))
WHERE cumulative_ranking_tap_count IS NULL;
ALTER TABLE user_tap_progress ALTER COLUMN cumulative_mission_tap_count SET DEFAULT 0,
    ALTER COLUMN cumulative_mission_tap_count SET NOT NULL,
    ALTER COLUMN cumulative_ranking_tap_count SET DEFAULT 0,
    ALTER COLUMN cumulative_ranking_tap_count SET NOT NULL;

ALTER TABLE tap_batch ADD COLUMN IF NOT EXISTS result_json TEXT,
    ADD COLUMN IF NOT EXISTS effects_json TEXT;

CREATE TABLE IF NOT EXISTS keycap_passive_checkpoint (
    user_id UUID PRIMARY KEY REFERENCES app_user(id),
    last_activity_at TIMESTAMPTZ NOT NULL,
    remainder_numerator BIGINT NOT NULL CHECK (remainder_numerator >= 0 AND remainder_numerator < 86400000000000),
    equipped_keycap_code VARCHAR(255),
    equipped_level INTEGER NOT NULL,
    clicks_per_day INTEGER NOT NULL CHECK (clicks_per_day >= 0),
    effects_json TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS keycap_passive_settlement (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    idempotency_key VARCHAR(150) NOT NULL,
    result_json TEXT NOT NULL,
    settled_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_passive_settlement_user_key UNIQUE (user_id,idempotency_key)
);

-- AppConfig defaults are append-only seeded by TapConfigSeeder; existing operational rows win.
-- Activation remains false. Enable via /ops/config so enabledAt is saved atomically.
COMMIT;
