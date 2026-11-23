-- BEA-390: 친구 초대 미션 두 개를 켠다. **새 버전을 배포한 뒤에** 적용한다.
--
-- 이전 버전은 FRIEND_INVITE 라는 미션 종류를 모른다. 이 두 행이 켜진 채로 이전 버전이 뜨면 미션 정의를
-- 읽다가 실패해서 **데일리 미션 목록 전체가 빈다**(응답은 200 이라 겉으로 드러나지 않는다).
-- 그래서 manual-bea-390-friends.sql 은 두 행을 꺼진 채로 넣고, 켜는 일은 이 파일이 한다.
--
-- 배포를 되돌려야 하면 **되돌리기 전에** 아래 '끄기'를 먼저 실행한다.
-- 켜 두어도 friends.enabled 가 꺼져 있는 동안에는 미션 목록에 나오지 않는다.
-- 서버는 미션 정의를 60초마다 다시 읽으므로 재시작은 필요 없다. 다시 실행해도 안전하다.
BEGIN;

SET LOCAL lock_timeout = '3s';

UPDATE mission_definition
SET active = TRUE, updated_at = now()
WHERE mission_type = 'FRIEND_INVITE'
  AND active = FALSE;

COMMIT;

-- 끄기(배포를 되돌리기 전에 먼저):
--   UPDATE mission_definition SET active = FALSE, updated_at = now() WHERE mission_type = 'FRIEND_INVITE';
--
-- 확인:
--   SELECT code, active FROM mission_definition WHERE mission_type = 'FRIEND_INVITE';
