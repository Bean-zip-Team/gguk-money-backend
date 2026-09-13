-- keycap.acquisition_type: keeps event keycaps out of the box reward pool (BEA-285).
--
-- Flyway is not used in this repository, so this DDL is applied by hand.
-- Production runs ddl-auto=validate: apply this BEFORE deploying the entity,
-- otherwise the application fails to boot.
--
-- Why a new column instead of reusing keycap.season: season already means the
-- catalog season ("season 1, 24 keycaps" in the Figma spec) and is exposed by the
-- catalog API. Overloading it with "is this an event keycap" would collide with a
-- future season-2 regular catalog, and filtering by a runtime "current season"
-- setting would let one wrong config value swap every user's box pool at once.
--
-- What reads this column:
--   BOX    box draws (single and bulk), onboarding bonus draw, all-complete bonus
--   EVENT  excluded from all of the above and granted only through an event path
--          (BEA-287). Still counted toward the keycap-five mission, and still listed
--          by the catalog API with acquisitionType = EVENT.
--
-- DEFAULT 'BOX' keeps the existing catalog rows in the box pool with no backfill.

BEGIN;

ALTER TABLE keycap
    ADD COLUMN acquisition_type VARCHAR(20) NOT NULL DEFAULT 'BOX';

ALTER TABLE keycap
    ADD CONSTRAINT ck_keycap_acquisition_type CHECK (acquisition_type IN ('BOX', 'EVENT'));

COMMIT;

-- Verification after applying:
--   SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
--     FROM information_schema.columns
--    WHERE table_name = 'keycap' AND column_name = 'acquisition_type';
--   SELECT acquisition_type, count(*) FROM keycap GROUP BY acquisition_type;   -- expect only BOX
