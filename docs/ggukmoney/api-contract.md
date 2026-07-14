# 꾹머니 13개 테이블 MVP API 계약

이 문서는 13개 테이블 Persistence MVP가 지원할 HTTP API 범위를 정의한다. 인증 세부 흐름은 [auth-lifecycle.md](auth-lifecycle.md)를 따르고, 프론트 연동용 상세 Request/Response와 JSON 예시는 [frontend-api-guide.md](frontend-api-guide.md)를 따른다.

> 최신 프론트 연동 계약은 [frontend-api-guide.md](frontend-api-guide.md)를 따른다. 이 문서는 전체 Endpoint와 상위 멱등성 계약의 기준이다.

## 공통 규칙

- API Prefix: `/api/v1`
- 인증: `Authorization: Bearer {accessToken}`
- 사용자 식별자: `app_user.id` UUID를 그대로 사용
- 다른 외부 리소스 식별자: UUID `public_id` 또는 안정 코드
- BIGINT 내부 PK는 API에 노출하지 않음
- 상태 변경 요청은 필요한 경우에만 `Idempotency-Key`를 사용한다. 현재 MVP 계약에서 Header 기반 멱등키는 상자 개봉과 출금 요청에 한정한다.
- 성공 응답 Body는 `success`, `data`만 사용하고 실패 응답 Body는 `success`, `error(code,message)`만 사용한다. 요청 추적 ID는 `X-Request-Id` 헤더와 서버 로그에만 남긴다.

## 구현 상태 요약

| 상태 | API |
|---|---|
| 구현 확인 | `POST /api/v1/auth/toss/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all`, `POST /api/v1/auth/toss/unlink-webhook`, `GET /api/v1/members/me`, `PATCH /api/v1/members/me`, `POST /api/v1/members/me/withdrawal`, `GET /api/v1/app-config`, `GET /api/v1/keycaps`, `GET /api/v1/keycaps/me`, `PUT /api/v1/keycaps/{keycapId}/equip`, `GET /api/v1/keycap-boxes/status`, `POST /api/v1/tap/batches`, `POST /api/v1/boosters/activate`, `GET /api/v1/boosters/current` |
| 계약 초안 | 위 구현 확인 API를 제외한 MVP API |

- Toss 로그인 API 자체는 구현 확인 상태다. 신규 사용자 온보딩 정산은 `onboardingAttemptId`를 로그인 요청에 포함하는 방향이 MVP 권장안이지만, 현재 `TossLoginRequest`에는 `authorizationCode`, `referrer`만 있어 구현 전 계약 확정이 필요하다.
- 탭 배치 API는 현재 코드에서 `/api/v1/tap/batches`로 구현되어 있다. 기존 문서의 `/api/v1/taps/batches` 표기는 후속 정합화가 필요하다.
- 앱 설정 API는 Access JWT 필수이며 원본 `app_config` JSON이 아니라 공개 typed DTO만 반환한다.
- 키캡 장착 API는 Access JWT 필수이며 Request Body 없이 `Keycap.publicId`를 Path Variable로 사용한다. 완성 키캡만 장착 가능하고 기존 장착 키캡은 같은 트랜잭션에서 해제한다.
- 키캡 상자 상태 API는 Access JWT 필수이며 `boxBalance`, `freeOpenTicketCount`, `boxProgressTapCount`, `nextBoxRequiredTapCount` 4개 필드만 반환한다. 상자 잔액과 무료권 수량은 `keycap_box_account`, 상자 진행도는 `user_tap_progress`를 원본으로 사용한다.
- `IDEMPOTENCY_KEY_REUSED`는 계약상 예정된 `409` 에러지만 현재 공통 `ErrorCode` 구현이 필요하다.
- 목록 API의 `page/size` 방식은 프론트 Mock과 타입 설계를 위한 초안이며 cursor 방식과 최종 선택이 필요하다.

## 인증과 회원 API

| Method | Path | 인증 | 핵심 저장소 | 설명 |
|---|---|---|---|---|
| POST | `/api/v1/auth/toss/login` | 불필요 | `app_user`, `auth_identity`, Redis | Toss 로그인, 회원 생성, JWT 발급. 온보딩 보상 연동은 `onboardingAttemptId` 전달 권장안이나 현재 미구현 |
| POST | `/api/v1/auth/refresh` | Refresh JWT | Redis | Access/Refresh Rotation |
| POST | `/api/v1/auth/logout` | Access JWT | Redis | 현재 Session 로그아웃 |
| POST | `/api/v1/auth/logout-all` | Access JWT | Redis | 모든 Session 로그아웃 |
| POST | `/api/v1/auth/toss/unlink-webhook` | Basic Secret | `app_user`, `auth_identity`, Redis | Toss 연결 해제·탈퇴 Webhook |
| GET | `/api/v1/members/me` | Access JWT | `app_user`, `user_keycap`, `point_account` | 내 정보 조회 |
| PATCH | `/api/v1/members/me` | Access JWT | `app_user` | 닉네임 등 프로필 수정 |
| POST | `/api/v1/members/me/withdrawal` | Access JWT + 새 Toss Code | `app_user`, `auth_identity`, Redis | Toss 연결 해제 후 회원 탈퇴 |

## 기능 API

| 영역 | Method | Path | 핵심 테이블 |
|---|---|---|---|
| 설정 | GET | `/api/v1/app-config` | `app_config` |
| 키캡 | GET | `/api/v1/keycaps` | `keycap` |
| 키캡 | GET | `/api/v1/keycaps/me` | `user_keycap`, `keycap` |
| 키캡 | PUT | `/api/v1/keycaps/{keycapId}/equip` | `user_keycap` |
| 상자 | GET | `/api/v1/keycap-boxes/status` | `keycap_box_account`, `user_tap_progress` |
| 상자 | POST | `/api/v1/keycap-boxes/open` | `keycap_box_account`, `keycap_box_open`, `user_keycap`, `keycap` |
| 상자 | GET | `/api/v1/keycap-boxes/history` | `keycap_box_open`, `keycap` |
| 탭 | POST | `/api/v1/taps/batches` | `tap_batch`, `user_tap_daily`, `point_account`, `point_ledger`, `keycap_box_account`, `booster_grant` |
| 탭 | GET | `/api/v1/taps/today` | `user_tap_daily`, `booster_grant` |
| 포인트 | GET | `/api/v1/points/me` | `point_account` |
| 포인트 | GET | `/api/v1/points/ledger` | `point_ledger` |
| 출금 | GET | `/api/v1/cashouts/quote` | `app_config`, `point_account` |
| 출금 | POST | `/api/v1/cashouts` | `cashout_request`, `point_account`, `point_ledger` |
| 출금 | GET | `/api/v1/cashouts` | `cashout_request` |
| 출금 | GET | `/api/v1/cashouts/{cashoutId}` | `cashout_request` |
| 부스터 | POST | `/api/v1/boosters/activate` | `booster_grant` |
| 부스터 | GET | `/api/v1/boosters/current` | `booster_grant` |

## 로그인 응답 사용자 ID

```json
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "accessToken": "jwt",
    "refreshToken": "jwt",
    "tokenType": "Bearer",
    "accessTokenExpiresAt": "2026-07-06T12:00:00Z",
    "refreshTokenExpiresAt": "2026-08-05T12:00:00Z",
    "newUser": true
  }
}
```

`userPublicId` 대신 `userId`를 사용한다. 이 값은 UUID `app_user.id`다.

## 주요 멱등성 계약

Header 기반 멱등성과 업무 키 기반 멱등성을 구분한다. 자연 멱등키가 없는 상자 개봉과 출금 요청은 `Idempotency-Key` Header를 사용하고, 탭 배치와 부스터는 각각 업무 식별자를 사용한다. 조회 API, `PUT` 장착 API, 인증 API에는 범용 `Idempotency-Key` Header를 추가하지 않는다.

### Toss 로그인 사용자와 Identity 중복 방지

- Toss Identity는 `(provider, provider_user_id)`로 중복을 막는다.
- 로그인 응답 유실 후 재요청해도 같은 Toss Identity로 새 사용자를 만들지 않는다.

> 구현 전 계약 확정 필요: 신규 사용자 온보딩 정산은 로그인 요청에 `onboardingAttemptId`를 포함하는 방향이 MVP 권장안이다. 현재 `TossLoginRequest` DTO에는 해당 필드가 없으므로 구현 시 Request/Response 필드를 확정해야 한다. 프론트가 `keycapId`, `shardCount`, `completed`, `tapCount`, `rewardPoint`, 상자 개봉 결과 전체를 보상 원본으로 전달하는 구조는 사용하지 않는다.

### 목표 온보딩 보상 멱등 계약

상태: MVP 권장안 · 현재 미구현

- 회원가입 전 온보딩 45탭을 서버가 검증한 뒤 별도 온보딩 키캡 상자 개봉 흐름에서 보상 결과를 생성하고, 서버 저장 기록에 연결된 `onboardingAttemptId`만 프론트에 반환한다.
- 온보딩 상자 개봉 API의 최종 Path는 아직 확정하지 않는다. 예시는 `POST /api/v1/onboarding/keycap-boxes/open` 또는 프로젝트 네이밍 규칙에 맞는 별도 온보딩 경로다.
- 프론트는 Toss 로그인 요청에 `onboardingAttemptId`만 전달한다. 서버는 이 식별자로 서버 기준 45탭 완료 여부, 서버가 결정한 키캡 보상 결과, 만료 여부, claimed 여부, 신규 사용자 귀속 가능 상태를 확인한다.
- `app_user.onboarding_reward_claimed=false`인 신규 사용자에게만 온보딩 보상을 지급한다.
- 포인트 원장은 `onboardingAttemptId`를 안정적인 reference와 idempotency key로 사용한다.
- 온보딩 키캡은 최종 지급 시 고정 키캡을 `shard_count=required_shard_count`, `status=COMPLETED`, `completed_at=now` 상태로 지급하며 `(user_id, keycap_id)` Unique로 중복 생성하지 않는다.
- 로그인 응답 유실 후 재요청해도 보상을 다시 지급하지 않는다.
- 같은 `onboardingAttemptId`는 한 사용자에게 한 번만 귀속할 수 있고, claimed된 attempt는 다른 신규 사용자에게 사용할 수 없다.
- 온보딩 키캡 지급, 포인트 지급, 사용자 온보딩 상태 변경, attempt claimed 처리는 가능한 범위에서 하나의 PostgreSQL 트랜잭션으로 처리한다. Redis Session 저장과 PostgreSQL 커밋의 원자성은 별도 검토 항목이다.

### 회원 탈퇴와 Webhook

- 이미 `WITHDRAWN` 상태면 같은 성공 결과로 정규화한다.
- 사용자 요청 탈퇴에서는 새 Toss `userKey`가 현재 Identity와 일치해야 한다.
- Webhook의 미등록 `userKey`는 `200 processed=true`로 처리한다.

### 탭 배치

- 멱등 기준은 `(user_id, tap_session_id, sequence)`다.
- 같은 키와 같은 `request_hash`는 이전 결과를 반환한다.
- 같은 키에 다른 `request_hash`가 들어오면 계약상 `409 IDEMPOTENCY_KEY_REUSED`다.
- `IDEMPOTENCY_KEY_REUSED`는 현재 공통 `ErrorCode`에 없어 구현이 필요하다.
- 별도 `Idempotency-Key` Header는 사용하지 않는다.

### 상자 개봉

- `Idempotency-Key` Header를 사용한다.
- 멱등성 원본은 PostgreSQL `keycap_box_open`의 `(user_id, idempotency_key)` Unique 제약이다. Redis를 상자 개봉 멱등성 원본으로 사용하지 않는다.
- 서버는 `openMethod`, `adRewardId`를 정규화해 `request_hash`를 저장한다.
- 같은 `(user_id, idempotency_key)`와 같은 `request_hash`는 상태를 다시 변경하지 않고 기존 개봉 결과를 반환한다.
- 같은 `(user_id, idempotency_key)`에 다른 `request_hash`가 들어오면 `409 IDEMPOTENCY_KEY_REUSED`다.
- 모든 성공 개봉은 `box_balance`를 1 차감한다.
- `FREE`는 성공 시 `box_balance`와 함께 `free_open_ticket_count`를 1 차감한다.
- `ADVERTISEMENT`는 성공 시 `box_balance`를 1 차감하고 검증된 `ad_reward_id`를 소비한다. 광고 검증 Service가 구현되기 전에는 미지원 오류로 처리하며 자원을 차감하지 않는다.
- 광고 검증이 구현된 뒤에는 `ad_reward_id`가 필수이며, `ad_reward_id` 중복 사용을 금지한다.
- 키캡 보상 후보는 `keycap.active=true` 키캡 중 현재 사용자가 아직 완성하지 않은 키캡으로 제한한다. `UserKeycap`이 없거나 `status=IN_PROGRESS`이면 후보에 포함하고, `status=COMPLETED`인 키캡과 온보딩으로 완성 지급된 키캡은 후보에서 제외한다.
- MVP에서는 후보 중 균등 랜덤으로 하나를 선택하고 기본 지급 조각 수는 1개다.
- 후보가 없으면 `409 KEYCAP_REWARD_NOT_AVAILABLE`을 반환하고 `box_balance`, `free_open_ticket_count`, `ad_reward_id`를 소비하지 않으며 `keycap_box_open` 이력도 생성하지 않는다.
- 조각 수는 `required_shard_count`를 초과 저장하지 않고, 완성 전환 시 `status=COMPLETED`, `completed_at=now`를 기록한다.
- 완성 키캡을 포인트나 다른 재화로 변환하는 중복 보상 정책은 MVP에서 제공하지 않는다.
- 현재 부스터는 포인트 적립 전용이며 상자 개봉 조각 수에는 적용하지 않는다.

트랜잭션 순서:

1. `Idempotency-Key`와 `request_hash`를 확인한다. 같은 요청이면 기존 결과를 반환하고, 다른 요청이면 `IDEMPOTENCY_KEY_REUSED`를 반환한다.
2. `keycap_box_account`를 잠근다.
3. 개봉 방식별 자원 보유 여부를 검증한다.
4. 미완성 활성 키캡 후보를 조회한다.
5. 후보가 없으면 `KEYCAP_REWARD_NOT_AVAILABLE`을 반환한다.
6. 후보 중 하나를 균등 랜덤으로 선택한다.
7. 상자, 무료권, 광고 보상 등 필요한 자원을 차감한다.
8. `UserKeycap`을 생성하거나 조각을 증가시키고, `required_shard_count` 도달 시 `COMPLETED` 전환과 `completed_at`을 기록한다.
9. `KeycapBoxOpen`을 저장한다.

보상 후보 존재를 확인하기 전에 자원을 차감하지 않는다. 후보 없음 오류와 트랜잭션 전체 실패에서는 자원 차감, 개봉 이력 생성, 조각 지급의 부분 반영이 없어야 한다.

### 키캡 장착

- 별도 `Idempotency-Key` Header는 사용하지 않는다.
- 같은 사용자가 이미 장착한 키캡을 다시 장착하면 같은 성공 결과를 반환한다.
- 다른 키캡을 장착하면 같은 트랜잭션에서 기존 장착 키캡을 해제하고 요청 키캡을 장착한다.
- 미완성 키캡은 `KEYCAP_NOT_COMPLETED`, 현재 사용자의 보유 키캡으로 확인되지 않는 요청은 `USER_KEYCAP_NOT_FOUND`로 처리한다.

### 출금

- `Idempotency-Key` Header를 사용한다.
- `(user_id, idempotency_key)`가 동일하면 기존 출금 결과를 반환한다.
- 외부 지급 ID `provider_transfer_id`가 존재하면 중복 저장하지 않는다.
- 포인트 차감과 `cashout_request` 생성은 하나의 트랜잭션이다.

### 부스터

- `ad_reward_id` 하나로 부스터를 두 번 생성하지 않는다.
- `(user_id, grant_date, daily_sequence)`로 당일 순번 중복을 막는다.
- 별도 `Idempotency-Key` Header는 사용하지 않는다.

## 이번 MVP에서 제공하지 않는 API

- 지역 조회와 지역 변경
- 랭킹 조회와 정산
- 알림 설정과 Push Device
- 별도 기록 화면 Projection
- 친구 초대
- 서버 분석 이벤트 수집
- 법적 문서 버전과 동의 이력 조회
