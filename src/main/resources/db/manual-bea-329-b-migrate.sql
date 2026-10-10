-- BEA-329 키캡 경제 재설계 — (B/3) 데이터 이동
-- 무중단 배포용으로 두 번 실행한다. 서버를 끌 필요가 없다.
--   1차: A 적용 후, 롤링 배포 직전. 상자 약 1,014개와 진행 중 조각을 지갑으로 옮긴다.
--        구 코드는 계속 돌지만 상자가 0 이 되어 "열 상자 없음" 상태가 될 뿐 오류는 없다.
--   2차: 롤링 배포가 끝나 구 인스턴스가 모두 내려간 뒤. 롤링 구간에 구 코드가 새로 만든 상자·진행 중 행을 쓸어 담는다.
-- 새 코드는 IN_PROGRESS 행을 "미보유"로 취급하고, 뽑기에서 걸리면 보유로 바꾸며 그 행의 조각을 지갑에 더하므로
-- 공존 구간에도 깨지지 않는다 (UserKeycap#convertLegacyToOwned).
--
-- 상자와 종별 조각을 1:1 로 지갑에 합산한다. 상한 없음. 부스터 4배 버그 과지급분도 회수하지 않는다.
-- 더하는 방식이라 몇 번을 실행해도 안전하다 — 옮길 것이 없으면 0 이 더해진다.
-- completed_at 은 절대 갱신하지 않는다. 키캡 5개 미션이 completedAt > launchAt 을 보므로
-- 여기서 찍으면 기존 유저 전원에게 25원이 일괄 지급된다. 완성 키캡은 Lv1(A 의 DEFAULT)로 시작한다.

-- 사전 확인 (2026-09-28 기준 기대값: 상자 1,014 + 진행 중 조각 약 358 = 약 1,372)
SELECT COALESCE(SUM(box_balance), 0) AS boxes, COALESCE(SUM(shard_balance), 0) AS shards_already FROM keycap_box_account;
SELECT COALESCE(SUM(shard_count), 0) AS in_progress_shards, COUNT(*) AS in_progress_rows FROM user_keycap WHERE status = 'IN_PROGRESS';

BEGIN;

DO $$
DECLARE
    stranded BIGINT;
    expected BIGINT;
    actual   BIGINT;
BEGIN
    SELECT COUNT(*) INTO stranded
      FROM user_keycap uk
     WHERE uk.status = 'IN_PROGRESS'
       AND NOT EXISTS (SELECT 1 FROM keycap_box_account a WHERE a.user_id = uk.user_id);
    IF stranded > 0 THEN
        RAISE EXCEPTION 'BEA-329: % IN_PROGRESS rows have no wallet; create wallets first', stranded;
    END IF;

    SELECT COALESCE(SUM(shard_balance), 0) + COALESCE(SUM(box_balance), 0) INTO expected FROM keycap_box_account;
    SELECT expected + COALESCE(SUM(shard_count), 0) INTO expected FROM user_keycap WHERE status = 'IN_PROGRESS';

    UPDATE keycap_box_account a
       SET shard_balance = a.shard_balance + a.box_balance + COALESCE((
               SELECT SUM(uk.shard_count)
                 FROM user_keycap uk
                WHERE uk.user_id = a.user_id
                  AND uk.status = 'IN_PROGRESS'), 0),
           box_balance = 0;

    -- 진행 중 행은 조각을 지갑으로 옮겼으므로 지운다. 새 코드에서 행 존재 = 보유다.
    DELETE FROM user_keycap WHERE status = 'IN_PROGRESS';

    -- 상자 정책 키는 코드가 더 읽지 않는다. 남겨 두면 시더가 매 기동마다 잔재 경고를 낸다.
    DELETE FROM app_config WHERE config_key LIKE 'keycapBox.%';

    SELECT COALESCE(SUM(shard_balance), 0) INTO actual FROM keycap_box_account;
    IF actual <> expected THEN
        RAISE EXCEPTION 'BEA-329: shard total mismatch (expected %, actual %); rolled back', expected, actual;
    END IF;
    RAISE NOTICE 'BEA-329: migrated. total shards = %', actual;
END $$;

COMMIT;

-- 사후 확인
SELECT COALESCE(SUM(shard_balance), 0) AS shards, COALESCE(SUM(box_balance), 0) AS boxes_should_be_zero FROM keycap_box_account;
SELECT COUNT(*) AS non_completed_should_be_zero FROM user_keycap WHERE status <> 'COMPLETED';
SELECT COUNT(*) AS without_level_should_be_zero FROM user_keycap WHERE level IS NULL OR level < 1;
