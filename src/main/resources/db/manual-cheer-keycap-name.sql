-- 응원 키캡 이름을 화면마다 같게 맞춘다. 앱 보관함은 cheer 를 '응원'으로 바꿔 보여 주지만,
-- 온보딩 보상·홈 자동 클릭 말풍선은 keycap.name 을 그대로 써서 '치어 키캡'으로 나왔다.
-- KeycapCatalogSeeder 는 행이 없을 때만 넣으므로 이미 있는 행은 이 UPDATE 로 바꾼다.
-- 운영·알파 모두 2026-11-09 10:58 KST 에 반영했다(각 1행). 기록용이며 다시 실행해도 안전하다.
UPDATE keycap SET name = '응원 키캡', updated_at = now()
WHERE code = 'cheer' AND name = '치어 키캡';
