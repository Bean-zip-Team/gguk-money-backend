# 수정 내역

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
