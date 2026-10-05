-- BEA-325: 키캡 5종 모으기 지급액을 25 로 둔다.
-- 행이 없으면 코드 기본값 500 으로 토스에 요청한다. 콘솔의 1회 한도(25)와 어긋나 지급이 실패할 수 있다.
-- 운영·알파 모두 2026-10-05 에 반영했다(changed_by=claude). 기록용이며 다시 실행해도 안전하다.
INSERT INTO app_config (public_id, config_key, config_value, effective_at, created_at, updated_at, changed_by, change_reason)
SELECT gen_random_uuid(), 'promotion.keycapFive.amount', '25'::jsonb, now(), now(), now(), 'claude', 'BEA-325 지급액 500 → 25'
WHERE NOT EXISTS (SELECT 1 FROM app_config WHERE config_key = 'promotion.keycapFive.amount');
