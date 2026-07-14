# 꾹머니 13개 테이블 MVP 아키텍처

## 구조 원칙

꾹머니는 하나의 Spring Boot 애플리케이션과 하나의 PostgreSQL을 사용하는 모듈형 모놀리스로 시작한다.

- PostgreSQL은 사용자, 포인트, 상자, 키캡, 탭, 출금 상태의 Source of Truth다.
- Redis는 꾹머니 Refresh Session, 사용자별 Session 목록, Access denylist, 사용자 revoke 시각에 사용한다.
- `app_user.id`는 UUID PK이며 API, JWT, Redis, FK에서 같은 값을 사용한다.
- 핵심 잔액 변경은 동일 PostgreSQL 트랜잭션 안에서 처리한다.
- 이번 13개 테이블 범위에서는 Event Inbox와 Event Outbox를 사용하지 않는다.
- 외부 Toss 포인트 지급 결과는 `cashout_request`에 함께 저장한다.
- 개별 탭은 저장하지 않고 `tap_batch`와 `user_tap_daily`에 집계한다.
- Toss OAuth Token은 로그인과 탈퇴 요청 처리 중에만 사용하고 PostgreSQL과 Redis에 장기 저장하지 않는다.

## 식별자 전략

### 사용자

```text
app_user.id: UUID PK
JWT sub: app_user.id 문자열
Redis auth:user-sessions:{userId}: 같은 UUID 문자열
모든 user_id FK: UUID
```

사용자에는 별도 `public_id`를 두지 않는다. `id` 자체가 추측하기 어려운 UUID이며 빵도감의 사용자 식별자 전략과 일치한다.

### 나머지 리소스

- 업무 테이블은 기본적으로 `BIGINT` 내부 PK를 유지한다.
- API에서 직접 노출되는 리소스는 `public_id UUID` 또는 `code`를 사용한다.
- API에 BIGINT PK를 노출하지 않는다.

## 도메인 구성

```mermaid
flowchart LR
    Client[Apps-in-Toss Client]
    API[Spring Boot API]
    User[User/Auth]
    Tap[Tap Validation]
    Point[Point/Cashout]
    Keycap[Keycap/Box]
    Booster[Booster]
    PG[(PostgreSQL)]
    Redis[(Redis Auth Session)]
    Toss[Toss OAuth and Point API]

    Client --> API
    API --> User
    API --> Tap
    API --> Point
    API --> Keycap
    API --> Booster

    User --> PG
    Tap --> PG
    Point --> PG
    Keycap --> PG
    Booster --> PG

    User --> Redis
    User --> Toss
    Point --> Toss
```

## 13개 테이블 ERD

```mermaid
erDiagram
    APP_USER ||--o{ AUTH_IDENTITY : has
    APP_USER ||--o{ USER_KEYCAP : owns
    KEYCAP ||--o{ USER_KEYCAP : progresses

    APP_USER ||--|| KEYCAP_BOX_ACCOUNT : has
    APP_USER ||--o{ KEYCAP_BOX_OPEN : opens
    KEYCAP ||--o{ KEYCAP_BOX_OPEN : rewards

    APP_USER ||--o{ TAP_BATCH : submits
    APP_USER ||--o{ USER_TAP_DAILY : aggregates

    APP_USER ||--|| POINT_ACCOUNT : has
    POINT_ACCOUNT ||--o{ POINT_LEDGER : records
    APP_USER ||--o{ POINT_LEDGER : owns
    APP_USER ||--o{ CASHOUT_REQUEST : requests

    APP_USER ||--o{ BOOSTER_GRANT : activates
```

`app_config`는 FK를 연결하지 않는 운영 정책 저장소다.

## Toss 로그인과 온보딩

### 현재 구현 흐름

1. 프론트가 Toss `appLogin()`으로 `authorizationCode`, `referrer`를 얻는다.
2. 서버가 mTLS로 Toss `generate-token`을 호출한다.
3. 발급된 Toss Access Token으로 `login-me`를 호출한다.
4. `login-me.userKey`를 문자열로 변환해 `auth_identity(provider=TOSS, provider_user_id)`를 조회한다.
5. 기존 Identity가 연결된 `app_user.status=WITHDRAWN`이면 자동 재가입하지 않고 `ACCOUNT_WITHDRAWN`을 반환한다.
6. 신규 사용자면 UUID `app_user`, `auth_identity`, `point_account`, `keycap_box_account`, `user_tap_progress`를 생성한다.
7. 꾹머니 Access/Refresh JWT를 생성하고 Redis Session을 저장한다.
8. Toss Access/Refresh Token은 저장하지 않는다.

현재 구현에서는 Redis Session 저장이 `@Transactional` 로그인 메서드 안에서 수행된다. Redis 저장 실패 시 로그인 성공으로 응답하지 않지만, DB 커밋 이후 저장하는 구조는 아니다. 이미 생성된 DB 사용자와 Identity는 Unique 제약으로 보호되므로 재시도 시 중복 생성을 막는다.

### 온보딩 키캡 상자 계약 · MVP 권장안

현재 `TossLoginRequest`에는 `onboardingAttemptId` 필드가 없으므로 구현 전 DTO 확정이 필요하다. MVP 권장안은 회원가입 전 온보딩 키캡 상자를 개봉하고, Toss 로그인 요청에는 서버 저장 기록과 연결된 불투명한 `onboardingAttemptId`만 전달하는 방식이다. 로그인 후 별도 Claim API 권장안은 사용하지 않는다.

온보딩 키캡 상자는 일반 키캡 상자와 다른 흐름이다.

1. 회원가입 전 온보딩을 시작한다.
2. 온보딩 45탭을 수행한다.
3. 서버가 45탭 완료를 검증한다.
4. 온보딩 키캡 상자 개봉 API를 호출한다.
5. 서버가 온보딩 보상 결과를 생성하고 `onboardingAttemptId`에 연결해 임시 저장한다.
6. 프론트는 `onboardingAttemptId`만 보관한다.
7. Toss 로그인 요청에 `onboardingAttemptId`를 포함한다.
8. 신규 사용자 생성 시 서버가 해당 attempt를 재검증한다.
9. 온보딩 포인트와 완성 키캡을 신규 사용자에게 귀속한다.
10. attempt를 claimed 상태로 전환해 재사용을 방지한다.

온보딩 결과는 사용자 생성 전에는 `UserKeycap`으로 바로 저장하지 않는다. 신규 회원가입이 성공할 때 고정 키캡을 `shard_count=required_shard_count`, `status=COMPLETED`, `completed_at=now` 상태로 지급한다. 기존 사용자의 일반 로그인에서는 온보딩 보상을 다시 지급하지 않는다.

프론트가 `keycapId`, `shardCount`, `completed`, `tapCount=45`, `rewardPoint`, 상자 개봉 결과 전체를 보상의 Source of Truth로 전달하는 구조는 금지한다. 서버는 `onboardingAttemptId`를 통해 서버 기준 45탭 완료 여부, 서버가 결정한 키캡 보상 결과, attempt 만료 여부, claimed 여부, 신규 사용자 귀속 가능 상태를 확인한다.

팀 확인이 필요한 항목은 온보딩 상자 지급 키캡의 고정/랜덤 여부, attempt 생성 API 필요 여부, 45탭 제출과 검증 API 형태, `onboardingAttemptId` 만료 시간, 기존 사용자 로그인에 attempt가 전달된 경우 처리, 유효하지 않은 attempt의 신규 가입 허용 여부, 온보딩 포인트 수량, 지급 키캡 자동 장착 여부, 온보딩 상자 개봉 API 최종 Path, 로그인 Request/Response 최종 DTO, 세부 ErrorCode와 HTTP Status다.

## Refresh와 로그아웃

- Refresh는 Redis Session의 현재 Refresh JTI와 hash를 검증하고 Lua CAS로 Rotation한다.
- 현재 Session 로그아웃은 `sid` Session 삭제 또는 revoke, 현재 Access `jti` denylist를 수행한다.
- 로그아웃 요청에 Refresh Token이 전달되면 동일 Session Token인지 추가 검증한다.
- logout-all은 사용자 UUID 기준 활성 Session을 전부 폐기하고 사용자 revoke 시각을 갱신한다.
- 로그아웃은 Toss 연결과 `auth_identity`를 변경하지 않는다.

## 회원 탈퇴

1. 인증된 사용자가 `POST /api/v1/members/me/withdrawal`을 호출한다.
2. Request Body의 새 Toss `authorizationCode`, `referrer`를 `generate-token`과 `login-me`로 검증한다.
3. 응답 `userKey`가 현재 사용자의 `auth_identity.provider_user_id`와 같아야 한다.
4. 같은 Toss Access Token으로 `remove-by-user-key`를 호출한다.
5. 외부 연결 해제가 성공하면 로컬 트랜잭션에서 `app_user.status=WITHDRAWN`, `withdrawn_at` 설정, 닉네임과 프로필 이미지 익명화를 수행한다.
6. 포인트 원장, 출금 이력, 상자 개봉 이력은 삭제하지 않는다.
7. Redis의 모든 Session과 Access Token을 즉시 폐기한다.

현재 구현은 `@Transactional` 탈퇴 메서드 안에서 Toss unlink 외부 호출 뒤 로컬 탈퇴와 Redis Session 폐기를 수행한다. 외부 Toss 연결 해제 성공 뒤 로컬 처리에 실패할 수 있으므로 Toss unlink Webhook은 같은 탈퇴 처리를 멱등하게 재실행하는 보완 흐름이다.

## 탭 배치

한 번의 탭 배치 반영은 아래 데이터를 하나의 트랜잭션으로 처리한다.

```text
tap_batch
+ user_tap_daily
+ user_tap_progress
+ point_account
+ point_ledger
+ keycap_box_account
```

- `(user_id, tap_session_id, sequence)`로 중복을 차단한다.
- `request_hash`가 다르면 동일 순번 재사용으로 판단한다.
- 유효 탭만 포인트와 상자 진행도에 반영한다.
- 상자 진행도는 `user_tap_progress.cumulative_valid_tap_count`와 `user_tap_progress.next_box_target`을 원본으로 사용하고, 목표 도달 시 `keycap_box_account.box_balance`만 증가시킨다.
- 개별 탭 간격 원문은 기본 저장하지 않고 `interval_stats` JSONB에 통계만 저장한다.

## 상자 개봉과 키캡 조각

상자 개봉 API는 구현됐으며 현재 트랜잭션 계약은 아래 순서다.

1. `Idempotency-Key`를 검증하고 `openMethod`, `adRewardId` 기반 `request_hash`를 계산한다.
2. `(user_id, idempotency_key)` 기존 개봉 이력이 있으면 `request_hash`를 비교한다. 같으면 기존 결과를 반환하고, 다르면 `IDEMPOTENCY_KEY_REUSED`를 반환한다.
3. `keycap_box_account`를 잠근다.
4. 개봉 방식별 자원 보유 여부를 검증한다.
5. `keycap.active=true` 키캡 중 현재 사용자가 아직 완성하지 않은 키캡을 후보로 조회한다. `UserKeycap`이 없거나 `status=IN_PROGRESS`이면 후보에 포함하고, `status=COMPLETED`인 키캡과 온보딩으로 완성 지급된 키캡은 제외한다.
6. 후보가 없으면 `KEYCAP_REWARD_NOT_AVAILABLE`을 반환하고 어떤 자원도 차감하지 않으며 개봉 이력도 생성하지 않는다.
7. 후보 중 하나를 균등 랜덤으로 선택한다. MVP 기본 지급 조각 수는 1개다.
8. 성공 개봉으로 확정된 뒤 `box_balance`를 1 차감한다. `FREE`는 추가로 `free_open_ticket_count`를 1 차감한다. `ADVERTISEMENT`는 검증된 `ad_reward_id`를 소비하며, 광고 검증 Service가 구현되기 전에는 미지원 오류로 처리하고 자원을 차감하지 않는다.
9. `user_keycap`이 없으면 생성하고, 있으면 `shard_count`를 증가시킨다. 조각 수는 `required_shard_count`를 초과 저장하지 않는다.
10. 필요 조각 수 이상이면 `status=COMPLETED`, `completed_at`을 설정한다.
11. `keycap_box_open`에 개봉 방식, 멱등키, 요청 해시, 보상 결과를 한 행으로 저장한다.

보상 후보 존재를 확인하기 전에 자원을 차감하지 않는다. 후보 없음 오류와 트랜잭션 전체 실패에서는 자원 차감, 조각 지급, 개봉 이력 생성의 부분 반영이 없어야 한다. 완성 키캡을 포인트나 다른 재화로 변환하는 중복 보상 정책은 MVP에서 제공하지 않는다.

현재 부스터는 포인트 적립 전용이므로 상자 개봉 조각 수에 적용하지 않는다.

현재 구현은 광고 검증 Service가 없으므로 `ADVERTISEMENT`를 `ADVERTISEMENT_OPEN_NOT_SUPPORTED`로 차단한다. 무료권 자동 충전과 온보딩 상자 개봉은 이 API 범위에 포함하지 않는다.

상자 개봉 이력 조회는 `keycap_box_open`을 읽기 전용으로 조회한다. `KeycapBoxHistoryService`는 `cursor`와 `size`를 검증하고, `KeycapBoxOpenRepository`가 현재 사용자 UUID 조건과 `opened_at DESC, id DESC` cursor 조건을 DB 쿼리에 포함한다. 응답 조립은 `KeycapBoxMapper`가 담당하며 Controller와 Service는 내부 BIGINT ID, 멱등키, 요청 해시, 광고 보상 ID를 직접 노출하지 않는다.

## 포인트 출금

1. `point_account`를 잠근다.
2. 출금 가능한 잔액과 최소 단위를 확인한다.
3. `point_ledger`에 차감 원장을 추가한다.
4. `cashout_request`를 `PENDING` 또는 `PROCESSING`으로 생성한다.
5. 외부 Toss 지급 성공 시 `SUCCEEDED`, 실패 시 `FAILED`로 전환한다.
6. 복구 가능한 실패면 `CASHOUT_REFUND` 원장으로 포인트를 되돌린다.

## 후속 확장 원칙

랭킹, 알림, 기록, 원장 세분화, Outbox는 실제 요구가 확정될 때 별도 Migration으로 추가한다. 이전 확장 설계의 테이블을 한 번에 복구하지 않는다.

## 코드 검토 필요 항목

- 로그인에서 JWT 생성과 Redis Session 저장은 DB 커밋 이전에 수행된다. Redis Session 저장 이후 응답 생성 또는 트랜잭션 커밋 실패 시 정리 전략을 확인해야 한다.
- logout-all은 사용자 revoke marker를 저장하지만 현재 Session 저장 경로가 marker를 확인하지 않는다. logout-all과 신규 로그인 Session 저장 경쟁 차단은 아직 보장되지 않는다.
- 사용자 요청 탈퇴와 Toss unlink Webhook이 동시에 처리될 때 상태 변경, 개인정보 익명화, Redis Session 폐기가 멱등하게 수렴하는지 동시성 검증이 필요하다.
- 출금, 탭, 상자, 부스터는 Persistence schema가 있으나 Controller/Service 트랜잭션 구현은 아직 없으므로 위 처리 흐름은 목표 계약이다.
