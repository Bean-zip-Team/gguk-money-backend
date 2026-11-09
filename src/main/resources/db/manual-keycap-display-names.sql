-- 키캡 이름을 화면마다 같게 맞춘다. 앱 도감은 collectionName() 으로 main 을 '기본', cheer 를 '응원'으로 바꿔
-- 보여 주지만, 온보딩 보상·홈 자동 클릭 말풍선·스킨 화면은 keycap.name 을 그대로 써서 이름이 달랐다.
-- lucky 는 키캡 이미지 문구('행운')에 맞춘다.
-- KeycapCatalogSeeder 는 행이 없을 때만 넣으므로 이미 있는 행은 이 UPDATE 로 바꾼다.
-- 운영·알파 모두 반영했다(cheer 2026-11-09 10:58, main·lucky 11:10 KST, 각 1행). 기록용이며 다시 실행해도 안전하다.
UPDATE keycap SET name = '응원 키캡', updated_at = now() WHERE code = 'cheer' AND name = '치어 키캡';
UPDATE keycap SET name = '기본 키캡', updated_at = now() WHERE code = 'main' AND name = '메인 키캡';
UPDATE keycap SET name = '행운 키캡', updated_at = now() WHERE code = 'lucky' AND name = '럭키 키캡';
