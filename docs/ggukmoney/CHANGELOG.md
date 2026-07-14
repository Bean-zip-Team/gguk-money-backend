# 수정 내역

## 2026-07-15 회원가입 전 온보딩 키캡 상자 API 구현

- `POST /api/v1/onboarding/keycap-boxes/open`을 인증 없는 공개 API로 추가했다.
- Request는 `tapSessionId`와 45개 `tapEvents`만 사용하며, 서버가 sequence 연속성, 중복·누락, `occurredAt` 순서를 검증한다.
- `tapSessionId`와 정규화된 tap 이벤트 hash를 멱등성 기준으로 사용하고, 결과는 `onboarding_reward_attempt`에 저장한다.
- 온보딩 보상 키캡 코드, 포인트 수량, attempt TTL은 `app_config`에서 조회하며 설정 오류는 `ONBOARDING_REWARD_NOT_AVAILABLE`로 처리한다.
- 일반 상자 계정, `UserKeycap`, 포인트 원장, 일반 `keycap_box_open`은 변경하지 않는다.
- Toss 로그인 DTO 변경, 실제 포인트·키캡 지급, attempt claimed 처리는 후속 작업으로 유지했다.

## 2026-07-15 키캡 상자 개봉 이력 API 구현

- `GET /api/v1/keycap-boxes/history`를 Access JWT 필수 구현 확인 API로 추가했다.
- 이력 목록은 `cursor`/`size` 기반으로 조회하며 `openedAt DESC`, 내부 PK DESC 순서로 안정 정렬한다.
- 응답은 `content`, `nextCursor`, `hasNext`와 각 항목의 `boxOpenId`, `openMethod`, `keycapId`, `shardCount`, `completed`, `openedAt`만 노출한다.
- 내부 BIGINT ID, 사용자 ID, 멱등키, 요청 해시, 광고 보상 ID, `boostApplied`는 이력 응답에 포함하지 않는다.
- Entity와 DB 컬럼은 변경하지 않고 기존 `keycap_box_open`을 조회 원본으로 사용한다.

## 2026-07-14 멱등 키캡 상자 개봉 API 구현

- `POST /api/v1/keycap-boxes/open`을 Access JWT와 `Idempotency-Key` 필수 API로 구현했다.
- `FREE` 개봉은 상자 잔액과 무료권을 각각 1개 차감하고, 미완성 활성 키캡 후보 중 균등 랜덤으로 1조각을 지급하도록 정리했다.
- 같은 사용자와 같은 멱등키의 같은 요청은 기존 결과를 반환하고, 다른 요청 hash는 `IDEMPOTENCY_KEY_REUSED`로 차단하도록 반영했다.
- `ADVERTISEMENT`는 광고 검증 Service 부재로 `ADVERTISEMENT_OPEN_NOT_SUPPORTED`를 반환하며 자원을 차감하지 않는 상태를 유지했다.
- `keycap_box_open` Entity에 `requestHash`, `adRewardId`, `completed`, `openedAt`을 정합화했다.
- 현재 저장소에는 Flyway/Liquibase Migration 파일이 없어 실제 공유/개발 DB 제약 적용은 merge 전 확인 필요로 기록했다.

## 2026-07-14 키캡 상자 개봉 계약 확정

- `POST /api/v1/keycap-boxes/open`의 문서 계약을 현재 코드 상태와 후속 구현 범위에 맞춰 정리했다.
- 모든 성공 개봉은 상자 잔액을 1 차감하고, `FREE` 개봉은 추가로 무료 개봉권 1개를 차감하는 것으로 확정했다.
- `ADVERTISEMENT` 개봉은 광고 검증 Service 구현 전에는 미지원 오류로 처리하며 자원을 차감하지 않는다. 구현 후에는 `adRewardId` 필수와 전역 중복 방지 제약을 사용하도록 정리했다.
- 상자 개봉 멱등성은 PostgreSQL `(user_id, idempotency_key)` Unique와 `request_hash` 비교를 Source of Truth로 사용하도록 확정했다.
- 일반 상자 보상 후보는 활성 키캡 중 현재 사용자가 아직 완성하지 않은 키캡으로 제한하고, 후보가 없으면 자원 차감과 개봉 이력 생성 없이 `KEYCAP_REWARD_NOT_AVAILABLE`을 반환하도록 확정했다.
- 보상은 후보 중 균등 랜덤으로 기본 1조각을 지급하고, 초과 조각 저장 없이 완성 전환 시 `completed_at`을 기록하는 방향으로 정리했다.
- 현재 부스터는 포인트 적립 전용이므로 상자 개봉 조각 수에는 적용하지 않는 것으로 정리했다.
- 온보딩 키캡 상자는 일반 상자와 분리하고, 회원가입 전 개봉 결과를 `onboardingAttemptId`에 연결한 뒤 신규 Toss 로그인 시 서버가 재검증해 포인트와 완성 키캡을 한 번만 귀속하는 MVP 권장안으로 정리했다.

## 2026-07-14 키캡 상자 상태 조회 API 구현

- `GET /api/v1/keycap-boxes/status`를 Access JWT 필수 구현 확인 API로 추가했다.
- 응답은 현재 저장 원본이 확인되는 `boxBalance`, `freeOpenTicketCount`, `boxProgressTapCount`, `nextBoxRequiredTapCount` 4개 필드만 반환한다.
- 상자 잔액과 무료권 수량은 `KeycapBoxAccount`, 상자 진행도는 `UserTapProgress`를 원본으로 사용하도록 정리했다.
- `nextBoxRequiredTapCount`는 남은 탭 수가 아니라 누적 유효 탭 기준 다음 상자 목표값으로 기록했다.
- 무료권 충전 시각과 광고 개봉 카운트는 저장 원본과 정책이 없어 후속 이슈로 분리했다.
- `KEYCAP_BOX_ACCOUNT_NOT_FOUND` ErrorCode를 추가하고, 기존 `TAP_PROGRESS_NOT_FOUND` 진행도 없음 정책을 API 오류 응답으로 재사용하도록 정리했다.
- 키캡 상자 상태 targeted test 결과를 문서에 반영했다.

## 2026-07-14 키캡 장착 API 구현

- `PUT /api/v1/keycaps/{keycapId}/equip`를 Access JWT 필수 구현 확인 API로 추가했다.
- 장착 요청은 Request Body 없이 `Keycap.publicId`를 Path Variable로 사용하고, 응답은 `keycapId`, `equipped`만 반환하도록 정리했다.
- 완성된 사용자 보유 키캡만 장착 가능하며, 기존 장착 키캡은 같은 트랜잭션에서 자동 해제한다.
- 같은 키캡 재장착은 실패가 아니라 `200 OK` 멱등 성공으로 처리하도록 기록했다.
- `USER_KEYCAP_NOT_FOUND`, `KEYCAP_NOT_COMPLETED` ErrorCode와 키캡 장착 targeted test 결과를 문서에 반영했다.
- 실제 공유/개발 DB의 `UNIQUE (user_id) WHERE equipped=true` 제약 존재 여부는 merge 전 확인 필요 항목으로 기록했다.

## 2026-07-14 키캡 목록 조회 API 구현

- `GET /api/v1/keycaps`, `GET /api/v1/keycaps/me`를 Access JWT 필수 구현 확인 API로 추가했다.
- 키캡 목록은 `active=true` 기준으로 `sortOrder ASC, code ASC` 정렬해 반환하도록 정리했다.
- 내 키캡 목록은 현재 인증 사용자 UUID 조건으로만 조회하고 `UserKeycap`과 `Keycap`을 join fetch로 함께 조회하도록 기록했다.
- `keycap` Entity에 `season`, `imageUrl`, `soundUrl`, `sortOrder`, `active`를, `user_keycap` Entity에 `completedAt`을 현재 테이블 명세와 맞춰 반영했다.
- `Keycap.imageUrl` 추가에 맞춰 회원 조회의 장착 키캡 `imageUrl`도 실제 저장값을 반환하도록 정합화했다.
- 현재 브랜치에는 Flyway 의존성과 Migration 경로가 없어 별도 Migration 파일은 추가하지 않았다.

## 2026-07-14 앱 설정 조회 API 구현

- `GET /api/v1/app-config`를 Access JWT 필수 구현 확인 API로 추가했다.
- 응답은 원본 `app_config.config_value` JSON이 아니라 `pointPolicy`, `boxPolicy`, `boosterPolicy` typed DTO만 반환하도록 정리했다.
- 공개값은 `TapPolicyConfig` 기준으로 `pointPolicy.dailyLimit=20`, `boxPolicy.baseRequiredTapCount=200`, `boosterPolicy.durationSeconds=300`, `boosterPolicy.dailyLimit=3`을 사용한다.
- 내부 탭 검증값, 봇 판정값, 레이트리밋 값, `boxDropVariance`, 앱 버전, 점검 상태, 출금 정책, 부스터 배율은 앱 설정 응답에 포함하지 않는다.
- `AppConfigRepository`의 유효 시각 기준 조회 메서드가 존재함을 문서에 반영했다.

## 2026-07-12 회원 조회·수정 API 구현

- `GET /api/v1/members/me`, `PATCH /api/v1/members/me`를 구현 확인 상태로 갱신했다.
- 회원 조회 응답은 `app_user.id` UUID를 `userId`로 사용하고, 내부 BIGINT PK를 노출하지 않는 계약을 유지했다.
- 현재 `keycap` Entity에 `imageUrl` 필드가 없어 장착 키캡 이미지 URL은 임의 값 없이 `null`로 반환하는 것으로 기록했다.
- 회원 수정은 `nickname`, `profileImageUrl` 부분 수정을 지원하며 두 필드가 모두 없거나 닉네임이 공백이면 Validation 오류로 처리하도록 정리했다.
- 활성 사용자 닉네임 정규화 값 중복은 `NICKNAME_ALREADY_EXISTS`로 기록했다.

## 2026-07-12 A-domain persistence 리팩토링 기준 문서 정합화

- `feat/1-a-domain-persistence` 현재 구현과 `feat/1-domain-entities...HEAD` 비교를 기준으로 문서를 정합화했다.
- 로컬/원격에 `develop` 브랜치가 없어 이번 비교 기준을 `feat/1-domain-entities`로 명시했다.
- 실제 DB 구조는 Flyway Migration 기준으로 문서화하고, Entity 또는 서비스 구현과의 공백은 코드 검토 항목으로 분리했다.
- 구현 확인 API를 Toss 로그인, Refresh, 현재 Session 로그아웃, 전체 로그아웃, Toss unlink Webhook, 사용자 요청 탈퇴로 정리했다.
- Toss 로그인 API는 구현 확인 상태로 두되, 온보딩 정산 방식은 `결정 필요`로 분리했다.
- Refresh Token 전달 위치를 Request Body의 `refreshToken`으로 유지하고, Header 전달처럼 읽히는 표현을 정리했다.
- Redis Session 저장, logout-all 경쟁, 탈퇴 외부 호출과 트랜잭션 경계는 현재 코드가 보장하는 범위와 코드 검토 필요 항목을 분리했다.
- 테스트 계획은 `구현 및 통과 확인`, `코드 존재·환경 문제로 실행 미확인`, `미구현`, `기능 미구현으로 보류` 상태로 재분류했다.
- 문서에서 개인 PC 절대 경로와 현재 코드에 없는 탈퇴 서비스, Toss 토큰 갱신 API 참조를 제거했다.

## 2026-07-11 로그인 온보딩 계약 상태 정합화

- 현재 `TossLoginRequest`에 온보딩 필드가 없음을 기준으로 현재 구현 흐름과 목표 온보딩 계약을 분리했다.
- `architecture.md`, `auth-lifecycle.md`, `data-infra.md`, `test-plan.md`, `api-contract.md`에서 온보딩 보상을 구현 완료 기능처럼 표현하지 않도록 수정했다.
- 온보딩 정보를 로그인 DTO에 포함할지 별도 정산 API로 분리할지는 결정 대기 항목으로 유지했다.

## 2026-07-11 멱등성 적용 범위 정리

- `Idempotency-Key` Header는 상자 개봉과 출금 요청으로 한정했다.
- 탭 배치는 `tapSessionId + sequence`, 부스터는 `adRewardId`를 업무상 멱등키로 사용하도록 구분했다.
- 모든 POST API에 적용하는 범용 멱등성 인프라는 MVP 범위에서 제외했다.

## 2026-07-11 프론트엔드 공유용 MVP API 가이드 추가

- 프론트엔드 공유용 MVP API 가이드 `frontend-api-guide.md`를 추가했다.
- API 명세서 양식 1과 양식 2의 문서 구조를 참고해 API별 Header, Body, Response, Error Response를 작성했다.
- 현재 Controller와 DTO로 확인되는 구현 API와 아직 Controller/DTO가 없는 계약 초안 API를 구분했다.
- Toss 로그인 온보딩 계약은 현재 `TossLoginRequest`와 정합화가 필요하다고 기록했다.
- 탭 API는 현재 브랜치에 `TapController`와 탭 Request/Response DTO가 없어 계약 초안으로 기록했다.
- `Idempotency-Key` 프론트 생성, 재시도, 충돌 처리 규칙을 문서화했다.
- `IDEMPOTENCY_KEY_REUSED`는 계약상 예정이지만 현재 공통 `ErrorCode` 구현이 필요하다고 기록했다.
- 기존 상위 문서에서 프론트 API 가이드 링크를 추가했다.

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
