# 꾹머니 테스트 계획

이 문서는 테스트 코드가 아니라 구현 전 테스트 케이스 목록이다.

## 빵도감에서 참고한 테스트 구조

| 빵도감 테스트 | 재사용 방향 |
|---|---|
| `JwtTokenProviderTest` | Access/Refresh payload, type, exp, signature 검증 테스트 구조 재사용 |
| `AuthServiceTest` | login, refresh, logout 성공/실패 케이스 구조 재사용 |
| `AuthServiceConcurrencyTest` | 동일 Refresh Token 동시 요청 시 하나만 성공하는 시나리오 재사용 |
| `AuthRefreshMysqlIntegrationTest` | JPA row lock 검증은 Redis Lua CAS 통합 테스트로 대체 |
| `UserSessionServiceTest` | 세션 생성/회전/폐기 테스트를 Redis Session Adapter 테스트로 대체 |
| `AuthInterceptorTest` | Access JWT 검증과 request attribute 설정 테스트 재사용 |
| `AccessLogFilterTest` | 토큰/민감 query/header가 로그에 남지 않는 테스트 재사용 |
| `RateLimitInterceptorTest` | `/auth/refresh` rate limit 테스트 후보 |

## 회원/인증 테스트

- 게스트 최초 생성 시 `app_user`, `device`, `user_device(GUEST_OWNER)`가 생성된다.
- 게스트 최초 생성 시 Access JWT와 Refresh JWT가 발급된다.
- 게스트 최초 생성 시 Redis `auth:refresh:{sessionId}`가 생성된다.
- 게스트 최초 생성 시 Redis `auth:user-sessions:{userId}`에 sessionId가 추가된다.
- 게스트 최초 생성 응답은 `201 Created`다.
- 같은 기기 게스트 복구 시 기존 게스트 계정을 재사용한다.
- 같은 기기 게스트 복구 시 새 Access/Refresh token이 발급된다.
- 같은 guest와 같은 device이고 기존 Redis Session이 정상이면 기존 `sessionId`를 유지하고 Refresh Token Rotation으로 교체할 수 있다.
- Session 만료, Redis Session 유실, Refresh 재사용 감지, 이상 기기이면 기존 Session을 폐기하고 새 `sessionId`를 생성한다.
- 게스트 복구의 Session 유지/폐기는 서버가 결정하고 클라이언트가 선택하지 않는다.
- 같은 기기 게스트 복구 응답은 `200 OK`다.
- 기존 토큰 원문을 반환하지 않는다.
- 신규 Toss 사용자는 기존 게스트 계정이 MEMBER로 승격된다.
- 신규 Toss 승격 시 `GUEST_OWNER`가 `MEMBER_DEVICE`로 변경된다.
- 신규 Toss 승격 시 게스트 세션은 폐기되고 MEMBER 세션이 새로 생성된다.
- 기존 Toss 회원 로그인 시 게스트 키캡과 상자가 target 회원에게 병합된다.
- 기존 Toss 회원 병합 시 source guest의 `GUEST_OWNER`는 비활성화된다.
- 기존 Toss 회원 병합 시 target member의 `MEMBER_DEVICE`가 생성 또는 활성화된다.
- 기존 Toss 회원 병합 시 게스트 랭킹 점수는 진행 중 랭킹 점수에 합산되지 않는다.
- 병합 실패 단계는 `user_merge_history.progress` 기준으로 재시도된다.
- 탈퇴/정지 사용자는 인증 API 접근이 차단된다.

## Redis JWT 인증 테스트

- Access JWT에는 `sub`, `sid`, `jti`, `type=ACCESS`, `iat`, `exp`가 포함된다.
- Refresh JWT에는 `sub`, `sid`, `jti`, `type=REFRESH`, `iat`, `exp`가 포함된다.
- Access API에 Refresh Token을 보내면 거절된다.
- Refresh API에 Access Token을 보내면 거절된다.
- Refresh Rotation 성공 시 새 Access/Refresh token이 반환된다.
- Refresh Rotation 성공 시 같은 `sessionId`의 `currentRefreshJti`와 `refreshTokenHash`만 교체된다.
- Lua 기반 Refresh Rotation은 session 존재 확인, expected jti/hash 비교, 새 jti/hash 저장, TTL 갱신, user session ZSet score 갱신을 원자적으로 수행한다.
- 동일 Refresh 동시 요청 중 하나만 성공한다.
- 동시 충돌 요청은 `409 AUTH_REFRESH_CONFLICT`로 실패하고 Session을 폐기하지 않는다.
- Refresh Rotation 성공 시 Redis TTL이 갱신된다.
- Refresh Rotation 성공 시 `auth:user-sessions:{userId}` score가 갱신된다.
- 실제 이전 Refresh Token 재사용 시 `401 AUTH_REFRESH_REUSED`가 반환된다.
- 실제 이전 Refresh Token 재사용 시 해당 Session을 폐기한다.
- 실제 이전 Refresh Token 재사용 시 `auth_session_log.event_type=REFRESH_REUSE_DETECTED`가 저장된다.
- 동일 Refresh Token 동시 요청은 하나만 성공한다.
- 중복 Refresh 요청 중 실패한 요청은 충돌 또는 재사용 감지로 처리된다.
- Refresh Session TTL 만료 후 Refresh는 실패한다.
- Redis Session이 없으면 Refresh는 실패한다.
- Access denylist TTL은 Access Token 남은 만료 시간과 같다.
- 현재 기기 로그아웃 시 `auth:deny:access:{jti}`가 생성된다.
- 현재 기기 로그아웃 시 `auth:refresh:{sessionId}`가 삭제된다.
- 현재 기기 로그아웃 시 `auth:user-sessions:{userId}`에서 sessionId가 제거된다.
- 전체 기기 로그아웃 시 사용자의 모든 `auth:refresh:{sessionId}`가 삭제된다.
- 전체 기기 로그아웃 시 `auth:user-sessions:{userId}`가 삭제된다.
- 회원 정지 시 전체 세션이 폐기된다.
- 회원 탈퇴 시 전체 세션이 폐기된다.
- Redis 장애 시 Refresh는 `503 AUTH_REDIS_UNAVAILABLE`로 실패한다.
- Redis 장애 시 로그아웃은 실패를 숨기지 않고 재시도 가능하게 응답한다.
- 고위험 API에서 denylist Redis 조회 실패 시 fail-closed로 거절한다.

## 인증 로그 테스트

- 로그인 성공 시 `AUTH action=... result=success` 구조화 로그가 남는다.
- 로그인 실패 시 `AUTH action=... result=fail` 로그와 `failureCode`가 남는다.
- Refresh 성공/실패 로그가 남는다.
- Refresh Token 재사용 감지 로그가 남는다.
- 로그아웃/전체 로그아웃 로그가 남는다.
- Redis 세션 없음 로그가 남는다.
- 중복 재발급 요청 로그가 남는다.
- 동일 기기 게스트 복구 로그가 남는다.
- 다른 기기 로그인 로그가 남는다.
- 강제 로그아웃 또는 회원 정지 세션 폐기 로그가 남는다.
- Access Log에는 `traceId`, `method`, `pathTemplate`, `status`, `durationMs`, `userPublicId`, `sessionIdHash`, `devicePublicId`, `clientIpMasked`, `userAgent`, `errorCode`가 남는다.
- Access Log에 `traceId`가 포함된다.
- AccessLog는 Authorization header를 남기지 않는다.
- AccessLog는 Refresh Token, Toss authorizationCode, Push Token, presigned URL query를 남기지 않는다.
- Access Log에는 Request Body 전체와 민감한 Query String이 남지 않는다.
- `auth_session_log`에는 `result`와 `failure_code`가 저장된다.
- `auth_session_log`에는 토큰 원문이 저장되지 않는다.
- `auth_session_log`에는 `session_id_hash`와 `token_family_id_hash`만 저장된다.
- 감사 로그 저장 실패가 성공한 Refresh Rotation을 원복하지 않는다.

## 키캡/상자 테스트

- 보유 상자가 없으면 개봉할 수 없다.
- 무료 개봉 시간이 아직 도래하지 않으면 개봉할 수 없다.
- 무료 개봉은 성공 후 `next_free_open_at`을 1시간 뒤로 갱신한다.
- 무료 개봉 횟수는 누적되지 않는다.
- 광고 개봉은 하루 2회를 초과할 수 없다.
- FREE 개봉 요청은 한 번에 상자 1개만 개봉한다.
- ADVERTISEMENT 개봉 요청은 완료된 `adViewId` 하나로 상자 1개만 개봉한다.
- 동일 `adViewId`는 여러 상자 개봉에 재사용할 수 없다.
- Request Body에 `count`가 들어오면 validation 실패 처리한다.
- 광고 검증 실패 시 상자가 차감되지 않는다.
- A 상자 개봉 트랜잭션 안에서는 외부 광고 Provider API를 호출하지 않는다.
- 동일 `Idempotency-Key` 재요청은 같은 결과를 반환한다.
- 여러 상자 일괄 개봉은 MVP에서 허용하지 않는다.
- 키캡 조각이 요구 개수에 도달하면 `COMPLETED`가 된다.
- 한 사용자에게 장착 키캡은 하나만 존재한다.

## 지역/랭킹 테스트

- GPS 좌표는 저장하지 않고 판별 결과 지역만 저장한다.
- 첫 지역 설정은 즉시 적용된다.
- 지역 변경은 월 1회만 가능하다.
- 같은 사용자와 같은 `change_month`로 지역 변경을 두 번 생성할 수 없다.
- 같은 사용자에게 `SCHEDULED` 지역 변경 예약이 두 개 생길 수 없다.
- 지역 변경 예약은 다음 주 월요일 00:00부터 적용된다.
- 진행 중 시즌의 참여 지역은 변경되지 않는다.
- 게스트는 랭킹 조회는 가능하지만 참여 확정은 불가능하다.
- MEMBER와 지역 설정이 있어야 랭킹 참여가 가능하다.
- 동일 시즌에 같은 사용자는 한 번만 참여할 수 있다.
- 동일 탭 배치는 랭킹 점수에 한 번만 반영된다.
- 동점 사용자는 `score DESC`, `reached_at ASC`, `user_public_id ASC` 순서로 정렬된다.
- 페이지 경계에 동점자가 있을 때 같은 점수 사용자를 추가 조회해 순위를 보정한다.
- 주간 1위는 한정 키캡 보상을 자동 지급받는다.
- 2~10위는 입상 기록으로 저장된다.
- 결과 확인 API는 보상 지급을 다시 실행하지 않는다.

## 알림/기록/설정 테스트

- OS 권한 여부와 서버 알림 preference가 분리된다.
- 저녁 리마인드 기본값은 22:00이다.
- 일반 푸시는 사용자당 하루 한 번만 생성된다.
- 같은 dedupe key는 같은 기기에 두 번 발송되지 않는다.
- 같은 dedupe key는 같은 사용자의 서로 다른 기기에는 각각 발송될 수 있다.
- 우선순위가 높은 알림 후보 하나만 선택된다.
- 비활성 push token은 발송 대상에서 제외된다.
- Push Token은 암호문과 hash로 분리 저장된다.
- 알림 오픈 이벤트는 중복 반영되지 않는다.
- B 이벤트 수신 시 daily와 summary projection이 갱신된다.
- 같은 이벤트가 재전송되어도 중복 집계되지 않는다.
- A 내부 이벤트도 기록 projection에 반영된다.
- 기록 API는 B 테이블을 직접 조회하지 않는다.
- `/app-config`는 A 소유 정책만 반환한다.
- 법적 문서 API는 `content`를 반환한다.

## 제약 테스트

- `auth_identity(provider, provider_user_id)` unique.
- `user_device(device_id) WHERE active = true AND account_role = 'GUEST_OWNER'` partial unique.
- `user_keycap(user_id, keycap_id)` unique.
- `user_keycap(user_id) WHERE equipped = true` partial unique.
- `keycap_box_open(user_id, idempotency_key)` unique.
- `keycap_box_open(ad_view_id) WHERE open_method = 'ADVERTISEMENT'` partial unique.
- `ranking_score_event(source_type, source_event_id)` unique.
- `ranking_participation(season_id, user_id)` unique.
- `ranking_snapshot` 지역/전국 partial unique.
- `notification_log.push_device_id`는 `NOT NULL`.
- `notification_log(dedupe_key, push_device_id)` unique.
- `user_region_change(user_id, change_month)` unique.
- `user_record_reward(user_id, reference_type, reference_id, reward_type)` unique.
- `event_inbox(event_id)` unique.
- [table-spec.md](table-spec.md)의 UNIQUE/CHECK/Partial Index와 테스트 계획의 제약 테스트가 일치한다.

## Redis 복구 테스트

- Redis 지역 랭킹 key 삭제 후 DB 기준으로 복원된다.
- Redis 전국 랭킹 key 삭제 후 DB 기준으로 복원된다.
- DB 점수와 Redis 점수가 다를 때 정산 전 DB 기준으로 보정된다.
- 랭킹 점수 반영은 DB commit 이후 Redis를 갱신한다.
- DB 성공 후 Redis 실패 시 PostgreSQL 기준 이벤트를 재처리해 Redis를 복구한다.
- `rank:reached`가 PostgreSQL `ranking_score.reached_at`의 캐시로 동작한다.
- 정산 lock은 중복 정산 실행을 막는다.
- `notification:daily:{userId}:{date}`는 하루 중복 발송을 막는다.
- `app-config:active` cache miss 시 DB에서 재조회한다.

## 구현 전 체크리스트

- Toss login fake adapter 응답 형식을 A/B가 합의했는가.
- B 이벤트 payload에 기록 projection에 필요한 수치가 모두 포함되는가.
- `ValidatedTapApplyUseCase`가 상자 진행도와 랭킹 점수를 같은 호출로 처리할지 확정했는가.
- Redis Refresh Session 직렬화 형식과 암호화/hash 정책을 확정했는가.
- Refresh 동시성 처리는 Redis Lua CAS 기준으로 구현하기로 공유했는가.
- IP/User-Agent 감사 로그 보관 기간을 확정했는가.
- 랭킹 동점 처리 기준을 구현 테스트로 고정했는가.
- 1위 한정 키캡 중복 보유 정책을 확정했는가.
- 지역 seed 데이터 적재 방식을 확정했는가.
- `/app-config`는 A 소유 정책만 반환하고 B/공통 정책은 Query Facade에서 합성한다는 기준을 공유했는가.
- outbox 전달 방식과 재시도 주기를 확정했는가.
- 회원 탈퇴 시 개인정보 삭제/익명화 범위를 법무 기준으로 확인했는가.
- 운영자가 랭킹 정산 실패를 재시도할 관리 방법을 정했는가.
