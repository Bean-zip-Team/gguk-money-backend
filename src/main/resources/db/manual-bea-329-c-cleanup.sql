-- BEA-329 키캡 경제 재설계 — (C/3) 안정화 후 정리
-- 실행 시점: 새 코드가 안정되어 구 코드로 되돌릴 가능성이 사라진 뒤 (1~2주 후).
-- 이 파일을 실행하면 B 를 다시 실행할 수 없고, 구 코드로 롤백할 수 없다.

BEGIN;
SET LOCAL lock_timeout = '3s';

ALTER TABLE keycap_box_account
    DROP COLUMN IF EXISTS box_balance,
    DROP COLUMN IF EXISTS free_open_used_count,
    DROP COLUMN IF EXISTS ad_open_used_count,
    DROP COLUMN IF EXISTS open_cycle_started_at;

ALTER TABLE user_keycap
    DROP COLUMN IF EXISTS shard_count;

COMMIT;
