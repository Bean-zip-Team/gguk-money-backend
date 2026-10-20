-- app_user.suspended_at: 정지 이력을 status 와 분리해 보존한다.
-- 배경: 정지(SUSPENDED) 계정이 토스 연결 해제로 WITHDRAWN 이 된 뒤 재로그인하면 reactivate() 가 ACTIVE 로 되돌려
--       정지가 풀렸다(2026-10-19 운영 사례). 이제 reactivate() 는 suspended_at 이 있으면 SUSPENDED 로 복구한다.
-- ddl-auto=validate 이므로 이 SQL 을 배포 전에 먼저 적용한다. 컬럼은 nullable 이고 구 코드는 읽지 않으므로
-- 먼저 넣어도, 롤백해도 안전하다. 다시 실행해도 결과가 같다.
-- 정지 해제는 UPDATE app_user SET status = 'ACTIVE', suspended_at = NULL WHERE id = ... 로 한다.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;

UPDATE app_user
SET suspended_at = COALESCE(suspended_at, updated_at)
WHERE status = 'SUSPENDED';
