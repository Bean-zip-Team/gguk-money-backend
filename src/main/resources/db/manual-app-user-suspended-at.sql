-- app_user.suspended_at: 정지 이력을 status 와 분리해 보존한다.
-- 배경: 정지(SUSPENDED) 계정이 토스 연결 해제로 WITHDRAWN 이 된 뒤 재로그인하면 reactivate() 가 ACTIVE 로 되돌려
--       정지가 풀렸다(2026-10-19 운영 사례). 이제 reactivate() 는 suspended_at 이 있으면 SUSPENDED 로 복구한다.
-- ddl-auto=validate 이므로 이 SQL 을 배포 전에 먼저 적용한다. 컬럼은 nullable 이고 구 코드는 읽지 않으므로
-- 먼저 넣어도, 롤백해도 안전하다. 다시 실행해도 결과가 같다.
-- ADD COLUMN 은 짧지만 ACCESS EXCLUSIVE 잠금을 잡는다. 긴 트랜잭션 뒤에 줄 서서 서비스 조회를 막지 않도록
-- lock_timeout 을 두고, 잠금을 못 잡으면 실패시킨 뒤 잠시 후 다시 실행한다.
-- 백필은 현재 SUSPENDED 행의 updated_at 을 쓴다. 운영의 정지 3건은 정지 SQL 이 updated_at=now() 로 찍어서
-- 실제 정지 시각과 같다. 알파에는 정지 행이 없다.
BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;

UPDATE app_user
SET suspended_at = COALESCE(suspended_at, updated_at)
WHERE status = 'SUSPENDED';

COMMIT;

-- 확인: SUSPENDED 행은 모두 suspended_at 이 있어야 한다(missing = 0).
SELECT count(*) AS suspended_rows,
       count(*) FILTER (WHERE suspended_at IS NULL) AS missing_suspended_at
FROM app_user
WHERE status = 'SUSPENDED';

-- 운영 처리 템플릿 (필요할 때만 실행)
-- 정지:      UPDATE app_user SET status = 'SUSPENDED', suspended_at = COALESCE(suspended_at, now()), updated_at = now() WHERE id = '<uuid>';
-- 정지 해제: UPDATE app_user SET status = 'ACTIVE', suspended_at = NULL, updated_at = now() WHERE id = '<uuid>';
