-- 운영 화면(/ops/config)에서 app_config 를 바꿀 때 누가·왜 바꿨는지 남긴다.
-- ddl-auto=validate 라 이 컬럼이 없으면 새 버전 앱이 뜨지 않는다. **배포 전에** 알파·운영 DB 에 실행한다.
-- 기존 행과 SQL·시더로 넣는 행은 비어 있다(nullable).
ALTER TABLE app_config ADD COLUMN IF NOT EXISTS changed_by varchar(50);
ALTER TABLE app_config ADD COLUMN IF NOT EXISTS change_reason varchar(200);
