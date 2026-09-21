-- Run against the confirmed runtime database before deploying BEA-280 bulk-open code.
-- The existing production constraint only permits FREE and ADVERTISEMENT.

BEGIN;

ALTER TABLE keycap_box_open
    DROP CONSTRAINT IF EXISTS keycap_box_open_open_method_check;

ALTER TABLE keycap_box_open
    ADD CONSTRAINT keycap_box_open_open_method_check
    CHECK (open_method IN ('FREE', 'ADVERTISEMENT', 'BULK_REWARD'));

COMMIT;

SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'keycap_box_open'::regclass
  AND conname = 'keycap_box_open_open_method_check';
