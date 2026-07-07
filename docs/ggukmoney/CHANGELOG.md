# 수정 내역

## 2026-07-06 UUID 사용자와 Toss 인증 생명주기 반영

- 빵도감 `User`의 UUID PK 전략을 참고해 `app_user.id`를 UUID PK로 변경했다.
- `app_user.public_id`를 제거하고 API, JWT `sub`, Redis, 모든 `user_id` FK에 같은 UUID를 사용하도록 확정했다.
- 빵도감 Toss 로그인, Refresh, 로그아웃, 탈퇴, unlink Webhook 구현을 검토했다.
- 꾹머니는 Toss Provider Token을 저장하지 않고 탈퇴 요청에서 새 authorizationCode를 받도록 차별화했다.
- 포인트와 출금 원장을 보존하기 위해 사용자 Hard Delete 대신 `WITHDRAWN`, `withdrawn_at`, 개인정보 익명화를 사용한다.
- 현재 Session 로그아웃과 logout-all의 Redis 처리 계약을 추가했다.
- 사용자 요청 탈퇴 API와 Toss unlink Webhook API를 추가했다.
- UUID 전환, 인증 생명주기, Migration, 테스트 작업을 Codex 검토 프롬프트에 반영했다.

## 2026-07-06 13개 테이블 Persistence MVP 축소

- 이전 A 31개, B 15개 확장 설계를 참고 문서로 이동하기로 했다.
- 현재 구현 범위를 13개 테이블로 축소했다.
- 지역, 랭킹, 알림, 기록, Reliability, 키캡 드롭 세분화, 개별 탭 이벤트를 후속 단계로 이동했다.
- 상자 개봉 결과를 `keycap_box_open`에 통합했다.
- 키캡 조각과 완성 상태를 `user_keycap`에 통합했다.
- Toss 지급 결과를 `cashout_request`에 통합했다.
