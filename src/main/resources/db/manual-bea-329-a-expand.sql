-- BEA-329 키캡 경제 재설계 — (A/3) 배포 전 확장
-- Flyway is not used in this repository, so this DDL is applied by hand.
-- Production runs ddl-auto=validate.
--
-- 추가 전용이다. 구 코드와 공존하며, 반복 실행해도 안전하다. 배포 전 아무 때나 실행한다.
-- B(manual-bea-329-b-migrate.sql)는 구 코드를 멈춘 뒤에만 실행한다. 이 파일과 B 를 한 번에 돌리지 말 것.

BEGIN;
-- 구 코드가 탭 배치마다 keycap_box_account 를 갱신한다. ALTER 가 긴 쿼리 뒤에서 잠금을 기다리면
-- 뒤따르는 탭 배치가 모두 줄을 선다. 3초 안에 못 잡으면 포기하고 다시 시도한다.
SET LOCAL lock_timeout = '3s';

ALTER TABLE user_keycap
    ADD COLUMN IF NOT EXISTS level INTEGER NOT NULL DEFAULT 1;
ALTER TABLE user_keycap
    ALTER COLUMN shard_count SET DEFAULT 0;

ALTER TABLE keycap_box_account
    ADD COLUMN IF NOT EXISTS shard_balance INTEGER NOT NULL DEFAULT 0;
-- 새 코드는 아래 네 컬럼을 매핑하지 않는다. DEFAULT 가 없으면 신규 가입 INSERT 가 깨진다.
ALTER TABLE keycap_box_account
    ALTER COLUMN box_balance SET DEFAULT 0,
    ALTER COLUMN free_open_used_count SET DEFAULT 0,
    ALTER COLUMN ad_open_used_count SET DEFAULT 0,
    ALTER COLUMN open_cycle_started_at SET DEFAULT now();

CREATE TABLE IF NOT EXISTS keycap_draw (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES app_user(id),
    keycap_id BIGINT NOT NULL REFERENCES keycap(id),
    shards_spent INTEGER NOT NULL CHECK (shards_spent > 0),
    level_after INTEGER NOT NULL CHECK (level_after > 0),
    newly_acquired BOOLEAN NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    drawn_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_keycap_draw_user_idempotency UNIQUE (user_id, idempotency_key)
);

COMMIT;

SELECT column_name, data_type, column_default, is_nullable
FROM information_schema.columns
WHERE table_name IN ('keycap_box_account', 'user_keycap', 'keycap_draw')
ORDER BY table_name, ordinal_position;
