-- BEA-346: app_user.signup_referrer 에 가입할 때의 유입 경로(미니앱 실행 referrer 파라미터)를 보관한다.
-- 로그인 응답의 referrer(DEFAULT/SANDBOX)는 유입 경로가 아니라서 저장하지 않는다.
-- ddl-auto=validate 이므로 이 SQL 을 배포 전에 알파·운영 DB 에 먼저 적용한다. 컬럼은 nullable 이고 구 코드는 읽지 않으므로
-- 먼저 넣어도, 롤백해도 안전하다. 다시 실행해도 결과가 같다. 기존 가입자는 알 수 없으므로 백필하지 않는다.
-- ADD COLUMN 은 짧지만 ACCESS EXCLUSIVE 잠금을 잡는다. 긴 트랜잭션 뒤에 줄 서서 서비스 조회를 막지 않도록
-- lock_timeout 을 두고, 잠금을 못 잡으면 실패시킨 뒤 잠시 후 다시 실행한다.
BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS signup_referrer TEXT;

COMMIT;

-- 확인: 컬럼이 text 로 있어야 한다.
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'app_user' AND column_name = 'signup_referrer';
