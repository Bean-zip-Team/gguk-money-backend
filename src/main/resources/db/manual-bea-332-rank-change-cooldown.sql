-- BEA-332: 랭킹 변동 알림 쿨다운을 설정 파일(app.smart-message.rank-change.cooldown=3h)에서 app_config 로 옮긴다.
-- 행이 없어도 코드 기본값(180분)으로 같게 동작한다. 테이블에서 값이 보이도록 넣어 둔다.
-- 바꾸려면 이 키로 새 행을 추가한다(분 단위, 0 이면 쿨다운 없음). 1분 안에 반영된다.
INSERT INTO app_config (public_id, config_key, config_value, effective_at, created_at, updated_at)
SELECT gen_random_uuid(), 'notification.rankChange.cooldownMinutes', '180'::jsonb, now(), now(), now()
WHERE NOT EXISTS (SELECT 1 FROM app_config WHERE config_key = 'notification.rankChange.cooldownMinutes');
