# 꾹머니 13개 테이블 MVP API 계약

이 문서는 13개 테이블 Persistence MVP가 지원할 HTTP API 범위를 정의한다. 인증 세부 흐름은 [auth-lifecycle.md](auth-lifecycle.md)를 따른다.

## 공통 규칙

- API Prefix: `/api/v1`
- 인증: `Authorization: Bearer {accessToken}`
- 사용자 식별자: `app_user.id` UUID를 그대로 사용
- 다른 외부 리소스 식별자: UUID `public_id` 또는 안정 코드
- BIGINT 내부 PK는 API에 노출하지 않음
- 상태 변경 요청은 필요한 경우 `Idempotency-Key`를 사용
- 성공 응답 Body는 `success`, `data`만 사용하고 실패 응답 Body는 `success`, `error(code,message)`만 사용한다. 요청 추적 ID는 `X-Request-Id` 헤더와 서버 로그에만 남긴다.

## 인증과 회원 API

| Method | Path | 인증 | 핵심 저장소 | 설명 |
|---|---|---|---|---|
| POST | `/api/v1/auth/toss/login` | 불필요 | `app_user`, `auth_identity`, Redis | Toss 로그인, 회원 생성, 온보딩 보상, JWT 발급 |
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
| 상자 | GET | `/api/v1/keycap-boxes/status` | `keycap_box_account` |
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

### Toss 로그인과 온보딩 보상

- Toss Identity는 `(provider, provider_user_id)`로 중복을 막는다.
- `app_user.onboarding_reward_claimed=false`인 신규 사용자에게만 온보딩 보상을 지급한다.
- 포인트 원장은 `onboardingAttemptId`를 안정적인 reference와 idempotency key로 사용한다.
- 온보딩 키캡은 `(user_id, keycap_id)` Unique로 중복 생성하지 않는다.
- 로그인 응답 유실 후 재요청해도 새 사용자를 만들거나 보상을 다시 지급하지 않는다.

### 회원 탈퇴와 Webhook

- 이미 `WITHDRAWN` 상태면 같은 성공 결과로 정규화한다.
- 사용자 요청 탈퇴에서는 새 Toss `userKey`가 현재 Identity와 일치해야 한다.
- Webhook의 미등록 `userKey`는 `200 processed=true`로 처리한다.

### 탭 배치

- 멱등 기준은 `(user_id, tap_session_id, sequence)`다.
- 같은 키와 같은 `request_hash`는 이전 결과를 반환한다.
- 같은 키에 다른 `request_hash`가 들어오면 `409 IDEMPOTENCY_KEY_REUSED`다.

### 상자 개봉

- `(user_id, idempotency_key)`가 동일하면 같은 개봉 결과를 반환한다.
- 광고 개봉은 `ad_reward_id` 중복 사용을 금지한다.

### 출금

- `(user_id, idempotency_key)`가 동일하면 기존 출금 결과를 반환한다.
- 외부 지급 ID `provider_transfer_id`가 존재하면 중복 저장하지 않는다.
- 포인트 차감과 `cashout_request` 생성은 하나의 트랜잭션이다.

### 부스터

- `ad_reward_id` 하나로 부스터를 두 번 생성하지 않는다.
- `(user_id, grant_date, daily_sequence)`로 당일 순번 중복을 막는다.

## 이번 MVP에서 제공하지 않는 API

- 지역 조회와 지역 변경
- 랭킹 조회와 정산
- 알림 설정과 Push Device
- 별도 기록 화면 Projection
- 친구 초대
- 서버 분석 이벤트 수집
- 법적 문서 버전과 동의 이력 조회
