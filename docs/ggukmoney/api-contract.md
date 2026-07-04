# 꾹머니 API 계약

> 구현 기준: Java 26, Spring Boot 4.1.0, Jackson 3(`tools.jackson.*`). 계약/테이블 명세는 이 기준으로 해석한다.

이 문서는 전체 HTTP API, Application Port, Domain Event 계약의 Source of Truth다. A 담당자 민재의 API는 `CONFIRMED`, B 담당자 은창의 API는 팀 논의를 위한 `PROPOSED`로 구분한다. 계약 상태와 Java 구현 상태는 별도로 관리한다.

## 계약 상태

| 상태 | 의미 |
|---|---|
| `CONFIRMED` | 현재 구현 기준으로 사용한다. 변경 시 팀 합의와 문서 갱신이 필요하다. |
| `PROPOSED` | 선작성한 제안 계약이다. 구현 전 A/B/프론트 합의가 필요하다. |
| `DEPRECATED` | 신규 구현에서 사용하지 않는다. 제거 일정을 별도로 관리한다. |

구현 상태는 별도로 `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `IMPLEMENTED`로 표기한다. `CONFIRMED`는 정책/계약 확정이며 Java 코드 구현 완료를 뜻하지 않는다.

구현 상태 정의:

- `NOT_STARTED`: 구현 코드가 없음.
- `IN_PROGRESS`: 코드가 있으나 필수 흐름, 운영 보강 또는 일부 검증이 남음.
- `BLOCKED`: 외부 계약, 팀 결정 또는 환경 문제 때문에 완료할 수 없음.
- `IMPLEMENTED`: 코드와 필수 단위/통합 테스트가 통과하고 문서와 일치함.

## 담당자와 구현 상태

| 구분 | 담당자 | API 계약 상태 | Java 구현 상태 |
|---|---|---|---|
| A 인증/로그 | 민재 | CONFIRMED | Access Log, JWT, Redis Session/Lua CAS, logout-all 최소 흐름 IMPLEMENTED; 운영 보강 IN_PROGRESS; Toss 일반 로그인 BLOCKED |
| A 나머지 도메인 | 민재 | CONFIRMED | NOT_STARTED |
| B 도메인 | 은창 | PROPOSED | NOT_STARTED |

Toss 로그인은 앱인토스 auth 요청/응답, Toss user id 형식, `deviceKey/platform/appVersion` 필요 여부가 확정되기 전까지 `TOSS_DEVICE_CONTRACT_REQUIRED`로 blocking 유지한다. 로그인 전 온보딩은 프론트 로컬 체험이며 서버 사용자와 인증 Session을 만들지 않는다.

## 공통 API 규칙

| 항목 | 값 |
|---|---|
| Production API Origin | `https://api.ggukmoney.app` |
| Local API Origin | `http://localhost:8080` |
| API Prefix | `/api/v1` |
| 인증 | `Authorization: Bearer {accessToken}` |
| Content-Type | `application/json` |
| 시간 | ISO-8601 UTC 저장, 클라이언트 KST 표시 |
| 외부 ID | UUID public id 또는 code 사용, 내부 BIGINT 노출 금지 |
| 추적 ID | 응답과 Access Log에 같은 `traceId` 사용 |

Production/Local Origin 뒤에 API Prefix를 한 번만 붙인다. 이 문서의 HTTP endpoint 경로는 모두 `/api/v1/...` 전체 경로로 표기하며, Origin과 Prefix를 조합할 때 Prefix를 중복하지 않는다.

성공:

```json
{
  "success": true,
  "data": {},
  "traceId": "01J..."
}
```

204 No Content 예외:

- `204 No Content` 성공 응답은 공통 Response Body를 사용하지 않는다.
- 이 경우 `traceId`는 `X-Trace-Id` 응답 헤더로 제공할 수 있다.
- `204` 대상 API: `DELETE /api/v1/members/me`, `DELETE /api/v1/push-devices/current`, `POST /api/v1/notifications/{notificationId}/opened`.

실패:

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "요청을 처리할 수 없습니다."
  },
  "traceId": "01J..."
}
```

성공 응답에는 `error: null`을 넣지 않고, 실패 응답에는 `data: null`이나 `error.details`를 넣지 않는다. Body의 `traceId`는 응답 헤더 `X-Trace-Id`와 같은 값을 사용한다.

페이지네이션:

- 변경 이력: `cursor` + `size`, 응답은 `items`, `nextCursor`, `hasMore`.
- 마스터 데이터: `page` + `size`, 응답은 `items`, `page`, `size`, `hasNext`.
- 기본 `size=20`, 최대 `size=100`.
- 정렬 기준은 엔드포인트별로 명시하며 cursor는 불투명 문자열로 취급한다.

멱등성:

- 상태 변경 API 중 중복 요청 위험이 있는 API는 `Idempotency-Key: {uuid}`를 사용한다.
- 동일 사용자·동일 Key·동일 요청은 기존 결과를 반환한다.
- 동일 Key에 다른 요청 본문이 오면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.

## Java 26 인증 구현 기준

- Access/Refresh JWT JSON 처리는 Jackson 3의 `tools.jackson.*` 타입을 기준으로 한다.
- JWT secret은 기본값 없이 `app.auth.jwt.secret` 또는 환경변수 `APP_AUTH_JWT_SECRET`로 주입한다. blank, UTF-8 32 byte 미만, 과거 로컬 기본 문자열은 거절한다.
- Refresh Rotation은 Redis Lua CAS로 수행한다.
- 거의 동시에 들어온 동일 Refresh 요청은 `409 AUTH_REFRESH_CONFLICT`로 응답하고 정상 Session을 폐기하지 않는다.
- Rotation 완료 후 과거 Refresh Token 재사용은 `401 AUTH_REFRESH_REUSED`로 응답하고 해당 Session을 폐기하며 `REFRESH_REUSE_DETECTED`를 감사 로그에 기록한다.
- logout-all 응답은 `loggedOutAll`, `revokedSessionCount`를 분리한다.

## 인증 토큰 계약

Access Token payload:

```json
{
  "sub": "user-public-uuid",
  "sid": "session-uuid",
  "jti": "access-token-uuid",
  "type": "ACCESS",
  "iat": 1782961200,
  "issuedAtMillis": 1782961200123,
  "exp": 1782962100
}
```

Refresh Token payload:

```json
{
  "sub": "user-public-uuid",
  "sid": "session-uuid",
  "jti": "refresh-token-uuid",
  "type": "REFRESH",
  "iat": 1782961200,
  "exp": 1785553200
}
```

- Access Token 권장 만료는 15분, Refresh Token 권장 만료는 30일이다.
- Access JWT 원문은 서버에 저장하지 않는다.
- Refresh JWT 원문은 클라이언트 Secure Storage가 보관하고, 서버 Redis에는 hash와 활성 Session만 저장한다.
- 전체 기기 로그아웃·정지·탈퇴는 `auth:revoke:user:{userPublicId}`의 millisecond revoke 시각으로 기존 Access Token을 즉시 차단한다.
- JWT 표준 `iat`는 유지하되, 같은 초 안에서 revoke 전후 발급 토큰을 구분하기 위해 Access JWT에 `issuedAtMillis`를 포함한다.

## 전체 엔드포인트 인벤토리

| Owner | Status | 도메인 | Method | Path | 설명 |
|---|---|---|---|---|---|
| `A` | `CONFIRMED` | 회원/인증 | `POST` | `/api/v1/auth/toss/login` | Toss 로그인, 회원 생성/조회, 온보딩 정산 |
| `A` | `CONFIRMED` | 회원/인증 | `POST` | `/api/v1/auth/refresh` | Access/Refresh Rotation |
| `A` | `CONFIRMED` | 회원/인증 | `POST` | `/api/v1/auth/logout` | 현재 Session 로그아웃 |
| `A` | `CONFIRMED` | 회원/인증 | `POST` | `/api/v1/auth/logout-all` | 전체 기기 로그아웃 |
| `A` | `CONFIRMED` | 회원/인증 | `GET` | `/api/v1/members/me` | 내 정보 조회 |
| `A` | `CONFIRMED` | 회원/인증 | `PATCH` | `/api/v1/members/me` | 내 프로필 수정 |
| `A` | `CONFIRMED` | 회원/인증 | `DELETE` | `/api/v1/members/me` | 회원 탈퇴 |
| `A` | `CONFIRMED` | 키캡/상자 | `GET` | `/api/v1/keycaps` | 키캡 카탈로그 |
| `A` | `CONFIRMED` | 키캡/상자 | `GET` | `/api/v1/keycaps/me` | 내 키캡 |
| `A` | `CONFIRMED` | 키캡/상자 | `PUT` | `/api/v1/keycaps/{keycapId}/equip` | 키캡 장착 |
| `A` | `CONFIRMED` | 키캡/상자 | `GET` | `/api/v1/keycap-boxes/status` | 상자 상태 |
| `A` | `CONFIRMED` | 키캡/상자 | `POST` | `/api/v1/keycap-boxes/open` | 상자 1개 개봉 |
| `A` | `CONFIRMED` | 키캡/상자 | `GET` | `/api/v1/keycap-boxes/history` | 상자 이력 |
| `A` | `CONFIRMED` | 지역/랭킹 | `GET` | `/api/v1/regions` | 지역 목록 |
| `A` | `CONFIRMED` | 지역/랭킹 | `POST` | `/api/v1/regions/detect` | 좌표 기반 지역 판별 |
| `A` | `CONFIRMED` | 지역/랭킹 | `GET` | `/api/v1/members/me/region` | 내 지역 |
| `A` | `CONFIRMED` | 지역/랭킹 | `PUT` | `/api/v1/members/me/region` | 지역 설정 또는 변경 예약 |
| `A` | `DEPRECATED` | 랭킹 | `POST` | `/api/v1/rankings/participations` | 신규 프론트 미사용, 자동 포함으로 대체 |
| `A` | `CONFIRMED` | 지역/랭킹 | `GET` | `/api/v1/rankings/current` | 현재 랭킹 |
| `A` | `DEPRECATED` | 랭킹 | `GET` | `/api/v1/rankings/me` | `/api/v1/rankings/current.myRank`로 통합 |
| `A` | `CONFIRMED` | 지역/랭킹 | `GET` | `/api/v1/rankings/results/latest` | 최신 결과 |
| `A` | `CONFIRMED` | 지역/랭킹 | `GET` | `/api/v1/rankings/results` | 과거 결과 |
| `A` | `CONFIRMED` | 알림 | `PUT` | `/api/v1/push-devices/current` | Push Token 등록/갱신 |
| `A` | `CONFIRMED` | 알림 | `DELETE` | `/api/v1/push-devices/current` | Push Token 비활성화 |
| `A` | `CONFIRMED` | 알림 | `GET` | `/api/v1/notification-preferences` | 알림 설정 조회 |
| `A` | `CONFIRMED` | 알림 | `PATCH` | `/api/v1/notification-preferences` | 알림 설정 변경 |
| `A` | `CONFIRMED` | 알림 | `POST` | `/api/v1/notifications/{notificationId}/opened` | 알림 열람 |
| `A` | `CONFIRMED` | 기록/설정 | `GET` | `/api/v1/records/summary` | 누적 기록 |
| `A` | `CONFIRMED` | 기록/설정 | `GET` | `/api/v1/records/daily` | 일별 기록 |
| `A` | `CONFIRMED` | 기록/설정 | `GET` | `/api/v1/records/rewards` | 보상 기록 |
| `A` | `CONFIRMED` | 기록/설정 | `GET` | `/api/v1/app-config` | A 소유 앱 설정 |
| `A` | `CONFIRMED` | 기록/설정 | `GET` | `/api/v1/legal-documents/current` | 현재 법적 문서 |
| `B` | `PROPOSED` | 홈/탭 | `GET` | `/api/v1/home` | 홈 통합 조회 |
| `B` | `PROPOSED` | 홈/탭 | `POST` | `/api/v1/taps/batches` | 탭 배치 검증·반영 |
| `B` | `PROPOSED` | 홈/탭 | `GET` | `/api/v1/taps/today` | 오늘 탭 상태 |
| `B` | `PROPOSED` | 포인트/출금 | `GET` | `/api/v1/points/me` | 포인트 잔액 |
| `B` | `PROPOSED` | 포인트/출금 | `GET` | `/api/v1/points/ledger` | 포인트 원장 |
| `B` | `PROPOSED` | 포인트/출금 | `GET` | `/api/v1/cashouts/quote` | 출금 견적 |
| `B` | `PROPOSED` | 포인트/출금 | `POST` | `/api/v1/cashouts` | 출금 요청 |
| `B` | `PROPOSED` | 포인트/출금 | `GET` | `/api/v1/cashouts` | 출금 이력 |
| `B` | `PROPOSED` | 포인트/출금 | `GET` | `/api/v1/cashouts/{cashoutId}` | 출금 상세 |
| `B` | `PROPOSED` | 광고/부스터 | `GET` | `/api/v1/ads/placements` | 광고 슬롯 상태 |
| `B` | `PROPOSED` | 광고/부스터 | `POST` | `/api/v1/ads/views/start` | 광고 시청 시작 |
| `B` | `PROPOSED` | 광고/부스터 | `POST` | `/api/v1/ads/views/{adViewId}/complete` | 광고 완료 검증 |
| `B` | `PROPOSED` | 광고/부스터 | `POST` | `/api/v1/boosters/activate` | 부스터 활성화 |
| `B` | `PROPOSED` | 광고/부스터 | `GET` | `/api/v1/boosters/current` | 현재 부스터 상태 |
| `B` | `PROPOSED` | 친구초대 | `GET` | `/api/v1/invites/me` | 내 초대 코드·현황 |
| `B` | `PROPOSED` | 친구초대 | `POST` | `/api/v1/invites/{code}/accept` | 초대 수락 |
| `B` | `PROPOSED` | 분석 | `POST` | `/api/v1/analytics/events` | 분석 이벤트 수집 |

## A API 상세 계약

### 회원 및 인증

### POST /api/v1/auth/toss/login

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 구현 상태 | `BLOCKED` - 앱인토스 auth/device 계약 미확정. 온보딩 정산 Java 구현은 `NOT_STARTED` |
| 인증 | 불필요. public pre-auth API |
| 비로그인 허용 | 해당 없음 |
| 성공 | `200 OK` |
| 멱등성 | Toss provider identity, `onboardingAttemptId` |
| 관련 테이블 | `app_user`, `auth_identity`, `device`, `user_device`, `onboarding_settlement`, `auth_session_log` |
| Redis/Port/Event | Toss Adapter, `PreloginOnboardingSettled`, `OnboardingPointRewardGranted`, `OnboardingKeycapGranted` |

Toss 로그인으로 신규 MEMBER를 생성하거나 기존 MEMBER를 조회하고, 로그인 전 로컬 온보딩 탭을 서버 기준으로 한 번 정산한다.

인증 조건:

- Access Token 없이 호출하는 public pre-auth API다.
- 실제 Apps-in-Toss auth 요청/응답 필드, Toss user id 형식, `deviceKey`, `platform`, `appVersion` 필요 여부는 Decision Required다.
- 로그인된 회원의 Toss 재연결은 member Access Token 기반 별도 정책으로 다루며 MVP 범위에서는 이 API가 자동 재연결을 수행하지 않는다.

오류: `TOSS_DEVICE_CONTRACT_REQUIRED`, `TOSS_AUTH_FAILED`, `AUTH_SESSION_EXPIRED`, `ONBOARDING_SETTLEMENT_CONFLICT`.

Request:

```json
{
  "authorizationCode": "toss-code",
  "referrer": "ggukmoney://auth/toss",
  "deviceKey": "client-generated-stable-device-key",
  "platform": "IOS",
  "appVersion": "1.0.0",
  "onboarding": {
    "onboardingAttemptId": "uuid",
    "onboardingTapCount": 45,
    "onboardingCompleted": true
  }
}
```

`authorizationCode`, Toss 사용자 식별자, device 관련 필드는 앱인토스 실제 계약 확정 전 예시다.

Response data:

```json
{
  "userId": "member-public-uuid",
  "newMember": true,
  "sessionId": "session-uuid",
  "accessToken": "jwt",
  "refreshToken": "jwt",
  "onboardingSettlement": {
    "onboardingAttemptId": "uuid",
    "submittedTapCount": 45,
    "acceptedTapCount": 45,
    "rewardEligible": true,
    "pointRewardAmount": 2,
    "grantedUserKeycapId": "user-keycap-public-uuid",
    "status": "COMPLETED"
  }
}
```

정책:

- 로그인 전에는 서버 사용자, 인증 Session, 실제 포인트, 키캡, 일반 상자 보상을 만들지 않는다.
- `submittedTapCount`는 서버가 0..45로 clamp한다.
- `acceptedTapCount = min(submittedTapCount, 45, KST 당일 남은 유효 탭 한도)`만 `user_tap_daily`, 랭킹, 일반 탭 수학에 반영한다.
- 로그인 전 온보딩 탭에는 부스터를 소급 적용하지 않는다.
- 신규 가입자에게만 `ONBOARDING_15_TAP_POINT`, `ONBOARDING_30_TAP_POINT`로 총 2P와 고정 온보딩 키캡 1개를 한 번 지급한다.
- 기존 회원에게는 온보딩 포인트와 키캡 보상을 지급하지 않는다.
- 한 사용자와 KST 일자 기준 로그인 전 온보딩 정산은 한 번만 실제 탭에 반영한다.
- `onboardingAttemptId` 정산 결과를 저장해 응답 유실, 앱 재실행, 부분 실패 후 재시도에서도 신규/기존 판정과 보상 지급 여부를 유지한다.
- 상자 개봉과 키캡 reveal은 신규 가입 보상 미리보기 연출이며 로그인 전 실제 서버 지급을 의미하지 않는다.

### POST /api/v1/auth/refresh

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 구현 상태 | `IMPLEMENTED` - Refresh Rotation Lua CAS 구현 완료, 사용자 revoke marker 연동은 후속 보강 |
| 인증 | Refresh Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | Redis Lua CAS |
| 관련 테이블 | `auth_session_log` |
| Redis/Port/Event | `auth:refresh`, `auth:user-sessions` |

Refresh Token Rotation API다.

Request:

```json
{
  "refreshToken": "jwt"
}
```

Response data:

```json
{
  "sessionId": "session-uuid",
  "accessToken": "jwt",
  "refreshToken": "jwt"
}
```

실패 코드:

- `AUTH_REFRESH_REQUIRED`
- `AUTH_REFRESH_EXPIRED`
- `AUTH_SESSION_NOT_FOUND`
- `AUTH_REFRESH_CONFLICT`
- `AUTH_REFRESH_REUSED`
- `AUTH_REDIS_UNAVAILABLE`

Refresh 실패 의미:

- 거의 동시에 들어온 동일 Refresh 요청은 `409 AUTH_REFRESH_CONFLICT`로 처리하고 정상 Session은 폐기하지 않는다.
- Rotation 완료 후 과거 Refresh Token이 다시 사용되면 `401 AUTH_REFRESH_REUSED`로 처리하고 해당 Session을 폐기한다.
- 실제 재사용 감지는 `auth_session_log.event_type=REFRESH_REUSE_DETECTED`로 남긴다.
- 사용자 revoke marker 확인과 revoked session의 `AUTH_USER_REVOKED` 반환은 후속 보강이다.

### POST /api/v1/auth/logout

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 구현 상태 | `IMPLEMENTED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 현재 `sid + jti` |
| 관련 테이블 | `auth_session_log` |
| Redis/Port/Event | `auth:deny:access`, `auth:refresh`, `auth:user-sessions` |

현재 기기 로그아웃이다. 로그아웃 대상 Session은 항상 Access Token의 `sid`이며, Request Body의 Refresh Token은 선택적 동일 Session 검증용으로만 사용한다. Access Token jti를 denylist에 저장하고 Redis Refresh Session을 삭제한다.

Request Body 없음 또는:

```json
{
  "refreshToken": "optional-refresh-token"
}
```

검증:

- Refresh Token이 있으면 `type=REFRESH`, `sub`, `sid`, current refresh jti/hash가 현재 Access Token의 Session과 모두 일치해야 한다.
- 불일치하면 `401 AUTH_LOGOUT_SESSION_MISMATCH`를 반환하고 현재 Session과 다른 Session을 모두 삭제하지 않는다.
- Refresh Token이 없으면 Access Token의 현재 Session만 로그아웃한다.

Response data:

```json
{
  "loggedOut": true
}
```

### POST /api/v1/auth/logout-all

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 구현 상태 | `IMPLEMENTED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 사용자 revoke 시각 |
| 관련 테이블 | `auth_session_log` |
| Redis/Port/Event | `auth:revoke:user`, `auth:refresh`, `auth:user-sessions` |

사용자의 모든 Redis Refresh Session을 Lua Script 한 번으로 폐기한다. 현재 Access Token jti는 같은 Lua 실행에서 denylist에 저장한다.

Response data:

```json
{
  "loggedOutAll": true,
  "revokedSessionCount": 3
}
```

정책:

- 만료된 ZSet Session을 정리한 뒤 실제 `auth:refresh:{sessionId}`가 존재하는 활성 Refresh Session만 삭제 수에 포함한다.
- `auth:user-sessions:{userPublicId}`와 각 활성 `auth:refresh:{sessionId}`를 같은 Lua 실행에서 삭제한다.
- `auth:revoke:user:{userPublicId}`에 현재 epoch millis인 `revokedAtMillis`와 사유 `LOGOUT_ALL`을 저장한다.
- Access Token 인증 시 token `issuedAtMillis <= revokedAtMillis`이면 거절하므로 다른 기기의 기존 Access Token도 즉시 무효화된다.
- revoke key TTL은 Access Token 최대 수명보다 길게 둔다.
- Redis 장애 시 부분 성공으로 응답하지 않고 `AUTH_REDIS_UNAVAILABLE`로 실패한다.

### GET /api/v1/members/me

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `app_user`, `user_region`, `region`, `user_keycap`, `keycap` |
| Redis/Port/Event | 없음 |

Response data:

```json
{
  "userId": "user-public-uuid",
  "status": "ACTIVE",
  "nickname": "꾹꾹이",
  "region": {
    "regionId": "region-public-uuid",
    "name": "서울 강남구"
  }
}
```

### PATCH /api/v1/members/me

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 현재 사용자 |
| 관련 테이블 | `app_user` |
| Redis/Port/Event | 없음 |

닉네임 등 A 도메인 소유 프로필을 수정한다.

Request:

```json
{
  "nickname": "꾹고수"
}
```

실패 코드: `USER_NICKNAME_INVALID`, `USER_NICKNAME_DUPLICATED`.

닉네임 중복 정책:

- 표시용 `nickname`과 중복 검사용 `nicknameNormalized`를 분리한다.
- 정규화는 앞뒤 공백 제거, 연속 공백 단일화, 대소문자 fold, Unicode NFKC를 기준으로 한다.
- ACTIVE 사용자 사이에서는 `nicknameNormalized`가 중복될 수 없다.

### DELETE /api/v1/members/me

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `204 No Content` |
| 멱등성 | Idempotency-Key 권장 |
| 관련 테이블 | `app_user`, `user_device`, `auth_session_log` |
| Redis/Port/Event | `UserWithdrawalGuardPort`, `auth:revoke:user` |

회원 탈퇴 요청이다.

B에 처리 중인 출금이 있으면 `USER_WITHDRAWAL_BLOCKED_BY_CASHOUT`으로 거절한다.

### 키캡 및 상자

### GET /api/v1/keycaps

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | 선택 Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `keycap`, `user_keycap` |
| Redis/Port/Event | 없음 |

키캡 도감 목록을 조회한다.

Query:

```text
grade
status
completed
page
size
```

응답 항목:

```json
{
  "items": [
    {
      "keycapId": "uuid",
      "code": "NEON_BLUE",
      "name": "네온 블루",
      "grade": "RARE",
      "imageUrl": "https://...",
      "requiredShardCount": 5,
      "myShardCount": 3,
      "status": "IN_PROGRESS",
      "equipped": false
    }
  ],
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

상태값:

- `IN_PROGRESS`: 조각 수집 중
- `COMPLETED`: 완성됨

### GET /api/v1/keycaps/me

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `user_keycap`, `keycap` |
| Redis/Port/Event | 없음 |

내 키캡 수집 현황을 조회한다.

### PUT /api/v1/keycaps/{keycapId}/equip

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 사용자 + keycap |
| 관련 테이블 | `user_keycap` |
| Redis/Port/Event | 없음 |

완성된 키캡을 장착한다.

오류: `KEYCAP_NOT_FOUND`, `KEYCAP_NOT_COMPLETED`, `KEYCAP_ALREADY_EQUIPPED`.

한 사용자에게 장착 키캡은 하나만 허용한다. DB는 `UNIQUE(user_id) WHERE equipped = true`로 방어한다.

### GET /api/v1/keycap-boxes/status

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `keycap_box_account` |
| Redis/Port/Event | 광고 상태 조회 Port |

상자 보유량과 개봉 가능 상태를 조회한다.

Response:

```json
{
  "boxBalance": 5,
  "freeOpen": {
    "available": true,
    "nextAvailableAt": null
  },
  "advertisementOpen": {
    "usedCount": 1,
    "dailyLimit": 2,
    "remainingCount": 1
  }
}
```

### POST /api/v1/keycap-boxes/open

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 필수 `Idempotency-Key` |
| 관련 테이블 | `keycap_box_account`, `keycap_box_ledger`, `keycap_box_open`, `keycap_box_open_result`, `user_keycap` |
| Redis/Port/Event | `AdvertisementVerificationPort`, Outbox |

보유한 키캡 상자 1개를 차감하고 무료 또는 광고 개봉 자격을 검증한다.

무료 개봉 Request:

```json
{
  "method": "FREE"
}
```

광고 개봉 Request:

```json
{
  "method": "ADVERTISEMENT",
  "adViewId": "uuid"
}
```

Response data:

```json
{
  "openId": "open-public-uuid",
  "method": "FREE",
  "result": {
    "keycapId": "keycap-public-uuid",
    "keycapName": "서울 키캡",
    "grade": "RARE",
    "shardCount": 3,
    "completed": false
  },
  "remainingBoxes": 4,
  "nextFreeOpenAt": "2026-07-02T04:00:00Z"
}
```

정책:

- 요청당 1개만 개봉한다.
- 보유 상자가 없으면 실패한다.
- `ADVERTISEMENT`는 완료된 `adViewId` 하나로 상자 1개만 개봉한다.
- 동일 `adViewId`는 재사용할 수 없다.
- 온보딩 고정 키캡은 이 수동 개봉 API로 열지 않고 일반 상자 개봉 원장도 생성하지 않는다. 온보딩 지급 이력은 로그인 정산 중 `OnboardingKeycapGrantUseCase`와 `ONBOARDING_COMPLETION_KEYCAP` reason/reference로 분리한다.

### GET /api/v1/keycap-boxes/history

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `keycap_box_ledger`, `keycap_box_open`, `keycap_box_open_result` |
| Redis/Port/Event | 없음 |

상자 지급/개봉 원장 이력을 조회한다.

Query:

```text
cursor
size
```

### 지역 및 랭킹

### GET /api/v1/regions

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | 불필요 |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `region` |
| Redis/Port/Event | 없음 |

직접 지역 선택 목록을 조회한다.

Query:

```text
query
sidoCode
page
size
```

### POST /api/v1/regions/detect

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | 선택 Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | 저장 없음 |
| Redis/Port/Event | Location Provider |

GPS 좌표로 행정구역을 판별한다. 좌표는 저장하지 않는다.

Request:

```json
{
  "latitude": 37.4979,
  "longitude": 127.0276
}
```

Response:

```json
{
  "regionId": "uuid",
  "sidoName": "서울특별시",
  "sigunguName": "강남구"
}
```

### GET /api/v1/members/me/region

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `user_region`, `user_region_change`, `region` |
| Redis/Port/Event | 없음 |

현재 적용 지역과 예약 변경 상태를 조회한다.

### PUT /api/v1/members/me/region

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 사용자 + changeMonth |
| 관련 테이블 | `user_region`, `user_region_change` |
| Redis/Port/Event | 없음 |

첫 지역 설정 또는 지역 변경 예약 API다.

Request:

```json
{
  "regionId": "uuid",
  "selectionMethod": "MANUAL"
}
```

정책:

- 첫 지역 설정은 즉시 적용한다.
- 기존 지역 변경은 월 1회만 가능하다.
- `user_region_change.change_month`와 `UNIQUE(user_id, change_month)`로 월 1회 제한을 DB에서 보장한다.
- 동시에 여러 예약이 생기지 않도록 `UNIQUE(user_id) WHERE status = 'SCHEDULED'`를 둔다.
- 변경된 지역은 다음 주 월요일 00:00 KST부터 적용한다.
- 진행 중인 시즌의 참여 지역은 바뀌지 않는다.

### POST /api/v1/rankings/participations

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `DEPRECATED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | 기존 클라이언트 호환 시 `200 OK` 가능 |
| 멱등성 | season + user |
| 관련 테이블 | `ranking_participation`, `ranking_score` |
| Redis/Port/Event | 없음 |

최신 정책은 Toss 로그인 후 인정 탭이 실제 반영될 때 현재 활성 시즌에 자동 포함되므로 신규 프론트는 이 API를 호출하지 않는다.

자동 참가 후보 시점:

- Toss 로그인 후 인정 탭 반영 시 현재 활성 시즌 참가 생성.
- 신규 시즌 시작 시 ACTIVE 사용자 참가/점수 초기화 또는 lazy create.

Decision Required:

- 대량 사용자를 시즌 시작 시 eager INSERT할지, 첫 유효 탭 또는 최초 랭킹 조회 때 lazy create할지 운영 방식 확정 필요.

### GET /api/v1/rankings/current

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | 선택 Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `ranking_season`, `ranking_score`, `ranking_participation` |
| Redis/Port/Event | `rank:overall:{seasonId}`, `rank:reached:{seasonId}` |

전체 유저 단일 현재 랭킹을 조회한다. 지역/전국 scope, 지역 선택, 지역 필터는 없다. Redis로 상위 순위와 내 주변 후보를 조회하고 PostgreSQL 기준으로 동점 보정한다.

Query:

```text
limit=50
```

`limit`은 상위 순위와 내 주변 순위를 한 응답에 담기 위한 조회 크기 힌트다. 응답 `items`는 상위권과 내 주변 행을 함께 포함할 수 있으며, 프론트는 `isMe=true` 행을 강조한다.

Response data:

```json
{
  "seasonId": "season-public-uuid",
  "round": 7,
  "startsAt": "2026-06-23T00:00:00Z",
  "endsAt": "2026-06-30T00:00:00Z",
  "remainingSeconds": 345600,
  "items": [
    {
      "rank": 1,
      "userId": "user-public-uuid",
      "nickname": "꾹마스터",
      "score": 18420,
      "previousRank": null,
      "rankDelta": 0,
      "isMe": false
    },
    {
      "rank": 3,
      "userId": "my-user-public-uuid",
      "nickname": "나",
      "score": 12840,
      "previousRank": 4,
      "rankDelta": 1,
      "isMe": true
    }
  ],
  "myRank": {
    "rank": 3,
    "score": 12840,
    "remainingTapToFirst": 5580
  }
}
```

응답 규칙:

- 예시 점수는 최신 UI 목업값이며 실제 점수 한도 정책으로 확정하지 않는다.
- `rankDelta > 0`은 순위 상승, `< 0`은 하락, `0`은 변동 없음이다.
- `isMe=true`는 로그인 사용자의 행이다.
- 인증되지 않은 조회를 허용한다면 `myRank=null`, 모든 `isMe=false`로 정의한다.
- `remainingTapToFirst`는 `max(firstScore - myScore, 0)`이다.
- 1위 사용자는 `remainingTapToFirst=0`이다.

Decision Required:

- 순위 등락 비교 기준 시점: 직전 화면 조회 시점, 일일 스냅샷, 직전 점수 반영 직전, 직전 회차 최종 순위 중 확정 필요.
- 주간 랭킹 점수 12,000 한도: A안 제거, B안 유지 후 UI 목업 숫자 수정, C안 상향 중 확정 필요.

### GET /api/v1/rankings/me

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `DEPRECATED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `ranking_participation`, `ranking_score` |
| Redis/Port/Event | `rank:overall:{seasonId}` |

최신 화면은 `GET /api/v1/rankings/current` 응답의 `myRank`로 내 순위 요약을 통합한다. 신규 프론트 구현에서는 사용하지 않는다.

### GET /api/v1/rankings/results/latest

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | 선택 Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `ranking_snapshot`, `ranking_reward` |
| Redis/Port/Event | 없음 |

최신 주간 랭킹 결과·보상 모달을 조회한다. 이번 최신 랭킹 목록/이전 기록 화면과 별도 목적일 수 있으므로 바로 삭제하지 않는다. 다만 전체 랭킹 전환에 맞춰 scope, region, 지역명 기반 메시지는 사용하지 않는다.

Response data:

```json
{
  "seasonId": "season-public-uuid",
  "round": 7,
  "resultType": "FIRST_PLACE",
  "rank": 1,
  "previousRank": 3,
  "rankDelta": 2,
  "score": 128500,
  "reward": {
    "type": "LIMITED_KEYCAP",
    "pointAmount": 0,
    "boxCount": 0
  },
  "rewardGranted": true,
  "limitedKeycap": {
    "keycapId": "keycap-public-uuid",
    "code": "WEEKLY_GOLD_01",
    "name": "주간 1위 키캡"
  },
  "message": "주간 랭킹 1위!",
  "settledAt": "2026-07-06T00:00:00Z"
}
```

`resultType`: `FIRST_PLACE`, `PLACED`, `NOT_PLACED`, `RANK_CHANGED`.

Decision Required:

- 최신 MVP에서 결과·보상 모달을 유지하는지 확정 필요.

### GET /api/v1/rankings/results

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `ranking_snapshot`, `ranking_reward` |
| Redis/Port/Event | 없음 |

최신 이전 회차 기록 화면의 Source of Truth다. 회차별 내 최종 순위와 점수를 최신 회차부터 내림차순으로 조회한다. 지역 관련 필드는 없다.

Query:

```text
cursor
size
```

Response data:

```json
{
  "items": [
    {
      "seasonId": "season-public-uuid",
      "round": 7,
      "startsAt": "2026-06-23T00:00:00Z",
      "endsAt": "2026-06-30T00:00:00Z",
      "rank": 3,
      "score": 12840,
      "firstPlace": false
    },
    {
      "seasonId": "season-public-uuid",
      "round": 3,
      "startsAt": "2026-05-26T00:00:00Z",
      "endsAt": "2026-06-02T00:00:00Z",
      "rank": 1,
      "score": 15600,
      "firstPlace": true
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

정렬:

- 최신 회차 우선.
- 같은 회차 항목은 사용자당 1건.
- `firstPlace`는 `ranking_snapshot.final_rank == 1`에서 파생한다.

### 알림

### PUT /api/v1/push-devices/current

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | device + token hash |
| 관련 테이블 | `push_device`, `device` |
| Redis/Port/Event | Push Adapter |

현재 기기의 push token을 등록 또는 갱신한다.

Request:

```json
{
  "deviceId": "uuid",
  "provider": "FCM",
  "pushToken": "token"
}
```

정책:

- 한 사용자는 여러 기기를 가질 수 있다.
- 실제 발송 이력은 기기별로 `notification_log`에 저장한다.

### DELETE /api/v1/push-devices/current

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `204 No Content` |
| 멱등성 | 현재 device |
| 관련 테이블 | `push_device` |
| Redis/Port/Event | Push Adapter |

현재 기기의 push token을 비활성화한다.

### GET /api/v1/notification-preferences

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `notification_preference` |
| Redis/Port/Event | 없음 |

서버 내부 알림 설정을 조회한다.

### PATCH /api/v1/notification-preferences

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 사용자 + type |
| 관련 테이블 | `notification_preference` |
| Redis/Port/Event | 없음 |

알림 설정을 수정한다.

Request:

```json
{
  "rankingAlertEnabled": true,
  "eveningReminderEnabled": false,
  "reminderTime": "22:00"
}
```

정책:

- OS 알림 권한과 서버 내부 알림 설정은 분리한다.
- 저녁 리마인드 기본값은 22:00이다.

### POST /api/v1/notifications/{notificationId}/opened

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `204 No Content` |
| 멱등성 | notification public id |
| 관련 테이블 | `notification_log` |
| Redis/Port/Event | 없음 |

푸시 오픈 이벤트를 반영한다.

중복 기준:

- 사용자 단위 일반 푸시 중복은 Redis와 서비스 로직으로 막는다.
- 기기별 발송 중복은 `notification_log(dedupe_key, push_device_id)` unique로 막는다.
- 같은 알림은 같은 기기에 두 번 보내지 않는다.
- 같은 알림은 같은 사용자의 서로 다른 기기에는 각각 보낼 수 있다.

### 기록, 설정 및 법적 문서

### GET /api/v1/records/summary

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `user_record_summary` |
| Redis/Port/Event | Event Projection |

내 기록 요약을 조회한다.

### GET /api/v1/records/daily

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `user_record_daily` |
| Redis/Port/Event | Event Projection |

일자별 활동 기록을 조회한다.

Query:

```text
from
to
```

기본 최근 7일, 최대 90일 조회를 권장한다.

### GET /api/v1/records/rewards

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `user_record_reward` |
| Redis/Port/Event | Event Projection |

보상, 입상, 수집 히스토리를 조회한다.

Query:

```text
cursor
size
```

정책:

- 기록 API는 B 테이블을 직접 조인하지 않는다.
- B 이벤트와 A 내부 이벤트를 기반으로 `user_record_daily`, `user_record_summary`, `user_record_reward` projection을 갱신한다.
- `user_record_reward`는 `UNIQUE(user_id, reference_type, reference_id, reward_type)`로 중복을 막는다.

### GET /api/v1/app-config

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | 불필요 |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `app_config` |
| Redis/Port/Event | `app-config:active` |

A 소유 정책만 반환한다.

Response data:

```json
{
  "keycapBox": {
    "freeOpenCooldownMinutes": 60,
    "adOpenDailyLimit": 2
  },
  "regionChange": {
    "monthlyLimit": 1,
    "effectiveDay": "MONDAY"
  },
  "ranking": {
    "settlementDay": "MONDAY",
    "tieBreakers": ["score DESC", "reachedAt ASC", "userPublicId ASC"]
  },
  "notification": {
    "defaultReminderTime": "22:00"
  },
  "app": {
    "minSupportedVersion": "1.0.0",
    "maintenance": false
  }
}
```

포인트 환율, 최소 출금 포인트, 출금 단위는 B 소유 정책이다. 공통 Query Facade가 필요하면 A/B 정책을 합성한다.

### GET /api/v1/legal-documents/current

| 항목 | 내용 |
|---|---|
| Owner | `A` |
| Status | `CONFIRMED` |
| 인증 | 불필요 |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `legal_document` |
| Redis/Port/Event | 없음 |

Response data:

```json
{
  "items": [
    {
      "documentType": "TERMS",
      "version": "2026-07-01",
      "content": "..."
    }
  ]
}
```

## B API 상세 계약 — PROPOSED

아래 계약은 구현 가능한 수준으로 선작성한 제안안이다. 정책 수치와 상태 전이는 팀 합의 후 `CONFIRMED`로 승격한다.

### 홈 및 탭

### GET /api/v1/home

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | B 조회 모델 + A Query Port |
| Redis/Port/Event | 랭킹/상자/키캡 A Query Port |

로그인 후 홈 화면에 필요한 값을 한 번에 반환한다. 프론트는 이 응답을 초기 기준값으로 사용하고 탭 애니메이션은 로컬에서 즉시 반영한다. 로그인 전 온보딩 화면은 이 API를 호출하지 않는다.

Response data:

```json
{
  "user": {
    "userId": "user-public-uuid",
    "nickname": null
  },
  "tap": {
    "todayValidTapCount": 3247,
    "dailyPointEligibleTapCount": 3247,
    "dailyPointEligibleLimit": 5000,
    "weeklyRankingTapCount": 8347,
    "weeklyRankingLimit": null
  },
  "point": {
    "balance": 14,
    "progressRemainder": 247,
    "threshold": 500
  },
  "keycapBox": {
    "balance": 3,
    "progressRemainder": 247,
    "threshold": 500,
    "freeOpenAvailable": false,
    "nextFreeOpenAt": "2026-07-02T10:00:00Z",
    "remainingAdOpenCount": 2
  },
  "booster": {
    "active": false,
    "multiplier": 1,
    "remainingSeconds": 0,
    "remainingDailyCount": 3
  },
  "ranking": {
    "autoIncluded": true,
    "rank": 12,
    "weeklyScore": 8347
  },
  "equippedKeycap": {
    "keycapId": "keycap-public-uuid",
    "name": "기본 키캡",
    "imageUrl": "https://..."
  }
}
```

오류: `AUTH_REQUIRED`, `HOME_QUERY_PARTIAL_FAILURE`. A Query Port 일부가 실패하면 임의의 0으로 숨기지 않고 `unavailableSections`를 응답에 포함하는 degraded 응답을 검토한다.

홈 규칙:

- 로그인 전 온보딩 상태(`onboardingAttemptId`, 로컬 탭 수, reveal 상태)는 서버 홈 응답에 저장하거나 복구하지 않는다.
- 로그인 전 온보딩 정산 결과를 홈에 별도 노출할지는 필요 시 후속 API 계약에서 결정한다.
- `weeklyRankingLimit=null`은 현재 랭킹 상한 정책 미확정 또는 상한 없음을 뜻한다. 숫자가 내려오면 서버가 적용하는 확정 상한이다. UI 목업의 18,420 같은 점수는 서버 상한 확정 근거가 아니다.

### POST /api/v1/taps/batches

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 아니오 |
| 성공 | `200 OK` |
| 멱등성 | `tapBatchId` + `tapSessionId` + `sequence` |
| 관련 테이블 | `tap_batch`, `tap_event`, `user_tap_daily`, `abuse_signal`, `point_account`, `point_ledger` |
| Redis/Port/Event | `ValidatedTapApplyUseCase`, Record Event |

현재 PROPOSED 기준은 프론트가 50탭 또는 2초 중 먼저 도달한 시점에 배치를 전송하는 것이다. 앱이 백그라운드로 전환되면 미전송 배치를 로컬에 보존하고 다음 실행 시 같은 `tapBatchId`로 재시도한다.

와이어프레임에는 `30초 또는 100탭` 전송, 최소 간격 `80ms`, 분당 유효 탭 `420회`, 최근 `100`탭 간격 표준편차 기준이 함께 적혀 있다. 이 값들은 최종 정책 확정 전까지 PROPOSED 검토 항목이며, 서버는 클라이언트 `intervalStats`를 참고값으로만 취급하고 최종 유효 탭 수를 직접 산정한다.

로그인 전 온보딩 탭은 이 API로 전송하지 않는다. 온보딩 탭 정산은 `POST /api/v1/auth/toss/login`의 `onboarding` 입력으로만 처리한다.

Request:

```json
{
  "tapBatchId": "uuid",
  "tapSessionId": "uuid",
  "sequence": 12,
  "tapCount": 47,
  "startedAt": "2026-07-02T06:30:10.100Z",
  "endedAt": "2026-07-02T06:30:14.500Z",
  "elapsedMs": 4400,
  "intervalStats": {
    "minMs": 82,
    "avgMs": 94,
    "stddevMs": 19
  }
}
```

Response data:

```json
{
  "tapBatchId": "uuid",
  "acceptedTapCount": 47,
  "rejectedTapCount": 0,
  "rejectionReasons": [],
  "todayValidTapCount": 3294,
  "dailyPointEligibleTapCount": 3294,
  "pointProgressDelta": 47,
  "pointGranted": 0,
  "pointBalance": 14,
  "boxProgressDelta": 47,
  "boxGranted": 0,
  "boxProgressRemainder": 294,
  "rankingScoreDelta": 47,
  "weeklyRankingScore": 8394,
  "booster": {
    "active": false,
    "pointMultiplier": 1,
    "boxMultiplier": 1,
    "rankingMultiplier": 1
  },
  "risk": {
    "level": "NORMAL",
    "reviewRequired": false
  }
}
```

정책:

- 서버가 최종 유효 탭 수를 결정하며 클라이언트 계산값을 신뢰하지 않는다.
- 포인트 적립 대상 유효 탭은 일 5,000회까지다.
- 주간 랭킹 반영 한도 12,000회는 최신 UI 목업 점수와 충돌하므로 Decision Required다. 확정 전까지 예시 점수는 UI 목업값으로만 표시한다.
- 500 유효 탭마다 1P를 지급하고 나머지는 유지한다.
- 일반 상자 진행도는 기존 정책을 유지한다.
- 온보딩 15/30/45 보상은 이 API에서 지급하지 않는다.
- 온보딩 보상 reveal은 로그인 전 프론트 로컬 UI 연출이며, 실제 신규 가입 보상 정산은 Toss 로그인 API에서 처리한다.
- 부스터는 포인트/상자 진행도에 2배 적용하고 랭킹 점수에는 적용하지 않는다.
- 동일 `tapBatchId` 재요청은 기존 결과를 반환한다.
- 부스터 시작/종료 경계를 넘는 배치는 프론트가 경계에서 flush하고, 서버는 서버 시각으로 최종 검증한다.

오류: `TAP_BATCH_INVALID`, `TAP_BATCH_DUPLICATED_WITH_DIFFERENT_BODY`, `TAP_SEQUENCE_CONFLICT`, `TAP_RATE_SUSPICIOUS`, `DEVICE_BLOCKED`, `RATE_LIMITED`.

### GET /api/v1/taps/today

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `user_tap_daily`, `tap_batch` |
| Redis/Port/Event | 없음 |

Response data:

```json
{
  "date": "2026-07-02",
  "validTapCount": 3294,
  "pointEligibleTapCount": 3294,
  "pointEligibleLimit": 5000,
  "remainingPointEligibleTapCount": 1706,
  "weeklyRankingTapCount": 8394,
  "weeklyRankingLimit": null,
  "lastAcceptedSequence": 12
}
```

`weeklyRankingLimit` 규칙은 `GET /api/v1/home`과 같다. `null`이면 랭킹 상한 정책 미확정 또는 상한 없음, 숫자면 서버가 적용하는 확정 상한이다.

### 포인트 및 출금

### GET /api/v1/points/me

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `point_account` |
| Redis/Port/Event | 없음 |

```json
{
  "balance": 134,
  "lifetimeEarned": 430,
  "lifetimeSpent": 296,
  "cashoutPolicy": {
    "minimumPoint": 10,
    "unitPoint": 10,
    "pointPerUnit": 10,
    "krwPerUnit": 7
  },
  "cashoutAvailablePoint": 130,
  "estimatedKrw": 91
}
```

### GET /api/v1/points/ledger

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `point_ledger` |
| Redis/Port/Event | 없음 |

Query: `cursor`, `size`, `entryType`, `reason`, `from`, `to`.

```json
{
  "items": [
    {
      "ledgerId": "uuid",
      "entryType": "CREDIT",
      "amount": 1,
      "reason": "TAP_REWARD",
      "balanceAfter": 134,
      "occurredAt": "2026-07-02T06:30:14Z"
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

### GET /api/v1/cashouts/quote

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 아니오, MEMBER만 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `point_account` |
| Redis/Port/Event | 없음 |

Query: `pointAmount`.

```json
{
  "requestedPoint": 134,
  "executablePoint": 130,
  "remainingPoint": 4,
  "tossPointAmount": 91,
  "minimumPoint": 10,
  "unitPoint": 10,
  "rate": {
    "point": 10,
    "krw": 7
  }
}
```

### POST /api/v1/cashouts

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 아니오, MEMBER만 |
| 성공 | `202 Accepted` |
| 멱등성 | 필수 `Idempotency-Key` |
| 관련 테이블 | `point_account`, `point_ledger`, `cashout_request`, `toss_point_transfer` |
| Redis/Port/Event | Toss Point Adapter, Record Event |

Request:

```json
{
  "pointAmount": 130
}
```

Response data:

```json
{
  "cashoutId": "uuid",
  "pointAmount": 130,
  "tossPointAmount": 91,
  "status": "PENDING",
  "requestedAt": "2026-07-02T06:40:00Z"
}
```

정책:

- 최소 10P, 10P 단위만 허용한다.
- 10P를 7 토스포인트로 환산한다.
- 와이어프레임은 `134P -> 약 93 Toss 포인트`처럼 1P 단위 전환에 가까운 예시를 보여주므로, 출금 단위는 README의 Decision Required 항목에서 최종 확정한다.
- 요청 트랜잭션에서 포인트를 즉시 차감하거나 별도 hold 상태로 잠가 중복 사용을 막는다. MVP 제안은 `point_ledger DEBIT(CASHOUT_HOLD)` 즉시 차감이다.
- 외부 지급 최종 실패 시 `REVERSAL(CASHOUT_FAILED)`로 포인트를 복원한다.
- 상태: `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELED`.

오류: `MEMBER_REQUIRED`, `CASHOUT_MINIMUM_NOT_MET`, `CASHOUT_UNIT_INVALID`, `POINT_INSUFFICIENT`, `CASHOUT_ALREADY_PROCESSING`, `TOSS_TRANSFER_FAILED`.

### GET /api/v1/cashouts

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 아니오, MEMBER만 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `cashout_request` |
| Redis/Port/Event | 없음 |

Query: `cursor`, `size`, `status`.

```json
{
  "items": [
    {
      "cashoutId": "uuid",
      "pointAmount": 130,
      "tossPointAmount": 91,
      "status": "SUCCEEDED",
      "requestedAt": "2026-07-01T05:00:00Z",
      "completedAt": "2026-07-01T05:00:04Z"
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

### GET /api/v1/cashouts/{cashoutId}

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 아니오, MEMBER만 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `cashout_request`, `toss_point_transfer` |
| Redis/Port/Event | 없음 |

```json
{
  "cashoutId": "uuid",
  "pointAmount": 130,
  "tossPointAmount": 91,
  "status": "PROCESSING",
  "failureCode": null,
  "requestedAt": "2026-07-02T06:40:00Z",
  "updatedAt": "2026-07-02T06:40:02Z"
}
```

### 광고 및 부스터

### GET /api/v1/ads/placements

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `ad_placement`, `ad_view`, `booster_grant` |
| Redis/Port/Event | 광고 Provider |

Query: `screen=HOME|KEYCAP_BOX`, `purpose=BOX_OPEN|BOOSTER|BANNER`.

```json
{
  "items": [
    {
      "placementCode": "KEYCAP_BOX_OPEN",
      "purpose": "BOX_OPEN",
      "available": true,
      "remainingDailyCount": 2
    },
    {
      "placementCode": "HOME_BOOSTER",
      "purpose": "BOOSTER",
      "available": true,
      "remainingDailyCount": 3
    }
  ]
}
```

### POST /api/v1/ads/views/start

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `201 Created` |
| 멱등성 | 필수 `Idempotency-Key` |
| 관련 테이블 | `ad_view`, `ad_placement` |
| Redis/Port/Event | 광고 Provider |

```json
{
  "placementCode": "HOME_BOOSTER",
  "purpose": "BOOSTER"
}
```

```json
{
  "adViewId": "uuid",
  "status": "STARTED",
  "providerPlacementId": "provider-placement",
  "expiresAt": "2026-07-02T06:45:00Z"
}
```

### POST /api/v1/ads/views/{adViewId}/complete

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | `adViewId` |
| 관련 테이블 | `ad_view` |
| Redis/Port/Event | 광고 Provider, `AdvertisementCompleted` Event |

Request:

```json
{
  "providerProof": "opaque-proof"
}
```

Response data:

```json
{
  "adViewId": "uuid",
  "status": "COMPLETED",
  "purpose": "BOOSTER",
  "completedAt": "2026-07-02T06:42:00Z"
}
```

광고 완료만으로 BOX_OPEN 상자를 지급하거나 개봉하지 않는다. 프론트가 완료된 `adViewId`를 `POST /api/v1/keycap-boxes/open`에 전달해야 한다.

### POST /api/v1/boosters/activate

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | `adViewId` |
| 관련 테이블 | `booster_grant`, `ad_view` |
| Redis/Port/Event | 없음 |

```json
{
  "adViewId": "uuid"
}
```

```json
{
  "boosterId": "uuid",
  "active": true,
  "multiplier": 2,
  "startsAt": "2026-07-02T06:42:05Z",
  "endsAt": "2026-07-02T06:47:05Z",
  "remainingDailyCount": 2
}
```

정책: 일 3회, 1회 5분, 포인트/상자 진행도 2배, 랭킹 점수 1배. 활성 부스터가 있으면 중복 활성화하지 않는다.

### GET /api/v1/boosters/current

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예 |
| 성공 | `200 OK` |
| 멱등성 | 없음 |
| 관련 테이블 | `booster_grant` |
| Redis/Port/Event | 없음 |

```json
{
  "active": true,
  "multiplier": 2,
  "remainingSeconds": 180,
  "remainingDailyCount": 2,
  "endsAt": "2026-07-02T06:47:05Z"
}
```

### 친구초대

### GET /api/v1/invites/me

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 회원 권장 |
| 성공 | `200 OK` |
| 멱등성 | 사용자 |
| 관련 테이블 | `invite_code`, `invite_relation` |
| Redis/Port/Event | 없음 |

```json
{
  "code": "GGUK7A2B",
  "shareUrl": "ggukmoney://invite/GGUK7A2B",
  "invitedCount": 4,
  "qualifiedCount": 3,
  "rewardedCount": 3
}
```

### POST /api/v1/invites/{code}/accept

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | Access Token |
| 비로그인 허용 | 예, 보상 확정은 회원 필요 |
| 성공 | `200 OK` |
| 멱등성 | invitee user/device |
| 관련 테이블 | `invite_code`, `invite_relation` |
| Redis/Port/Event | `InvitationAccepted`, `InvitationQualified`, `KeycapBoxGrantUseCase` |

Response data:

```json
{
  "accepted": true,
  "status": "PENDING_FIRST_TAP",
  "rewardGranted": false
}
```

정책:

- 자기 초대는 불가하다.
- invitee 사용자당 한 관계만 허용한다.
- 동일 기기의 보상 중복을 막는다.
- 첫 유효 탭 이후 `QUALIFIED`가 되고 양쪽 사용자에게 상자를 지급한다.
- 상자 지급은 `KeycapBoxGrantUseCase`의 `referenceId=inviteRelationId`로 멱등 처리한다.

### 분석

### POST /api/v1/analytics/events

| 항목 | 내용 |
|---|---|
| Owner | `B` |
| Status | `PROPOSED` |
| 인증 | 선택 Access Token |
| 비로그인 허용 | 예 |
| 성공 | `202 Accepted` |
| 멱등성 | `eventId` |
| 관련 테이블 | `analytics_event` |
| Redis/Port/Event | 없음 |

Request:

```json
{
  "events": [
    {
      "eventId": "uuid",
      "eventName": "HOME_VIEWED",
      "screenName": "HOME",
      "schemaVersion": 1,
      "source": "CLIENT",
      "occurredAt": "2026-07-02T06:30:00Z",
      "properties": {
        "entry": "APP_START"
      }
    }
  ]
}
```

- 최대 50개 이벤트를 한 번에 받는다.
- `eventName`은 UPPER_SNAKE_CASE를 사용한다.
- wireframe snake_case 이벤트명은 아래 카탈로그의 매핑으로 수신하거나 클라이언트 SDK에서 변환한다.
- 허용되지 않은 이벤트명은 저장하지 않고 개별 이벤트 실패로 분리하거나, 운영 정책에 따라 `ANALYTICS_EVENT_NOT_ALLOWED`로 거절한다.
- `schemaVersion`은 이벤트 properties 스키마 변경 추적에 사용한다.
- `source`는 `CLIENT`, `SERVER` 중 하나다. `PUSH_SENT`, `REWARD_GRANTED` 같은 서버 생성 이벤트는 클라이언트 이벤트와 구분한다.
- 인증·결제·토큰·정확한 GPS 등 민감정보를 properties에 넣지 않는다.
- 분석 이벤트 실패가 핵심 비즈니스 API 성공 여부에 영향을 주지 않는다.

이벤트 카탈로그 — PROPOSED:

| Wireframe | API eventName | Source | 설명 |
|---|---|---|---|
| `splash_view` | `SPLASH_VIEWED` | CLIENT | 스플래시 노출 |
| `splash_tap_start` | `SPLASH_START_TAPPED` | CLIENT | 시작하기 탭 |
| `splash_tap_already` | `SPLASH_ALREADY_TAPPED` | CLIENT | 이미 해봤어요 탭 |
| `home_view` | `HOME_VIEWED` | CLIENT | 홈 노출 |
| `home_tap_gguk` | `HOME_GGUK_TAPPED` | CLIENT | 홈 꾹 탭 |
| `kbox_view` | `KEYCAP_BOX_VIEWED` | CLIENT | 키캡 상자 화면 노출 |
| `kbox_open` | `KEYCAP_BOX_OPENED` | CLIENT | 키캡 상자 열기 탭 |
| `box_result_view` | `KEYCAP_BOX_RESULT_VIEWED` | CLIENT | 상자 결과 노출 |
| `skin_view` | `KEYCAP_SKIN_VIEWED` | CLIENT | 키캡 스킨 화면 노출 |
| `rank_view` | `RANKING_VIEWED` | CLIENT | 랭킹 화면 노출, property `my_rank` |
| `rank_tap_history` | `RANKING_HISTORY_TAPPED` | CLIENT | 이전 기록 탭 |
| `history_view` | `RANKING_HISTORY_VIEWED` | CLIENT | 이전 기록 화면 노출, property `rounds` |
| `history_back` | `RANKING_HISTORY_BACK_TAPPED` | CLIENT | 이전 기록 뒤로가기 |
| `onb_home_view` | `ONBOARDING_HOME_VIEWED` | CLIENT | 온보딩 홈 노출, property `step` |
| `onb_tap_first` | `ONBOARDING_FIRST_TAP` | CLIENT | 온보딩 첫 탭 |
| `onb_coach_dismiss` | `ONBOARDING_COACH_DISMISSED` | CLIENT | 코치마크 닫기 |
| `onb_earn_p` | `ONBOARDING_POINT_PREVIEWED` | CLIENT | 로그인 전 온보딩 포인트 미리보기, properties `amount`, `tap_count` |
| `onb_toast_view` | `ONBOARDING_POINT_TOAST_VIEWED` | CLIENT | 로그인 전 온보딩 포인트 토스트 미리보기, property `tap_count` |
| `onb_reward_ready` | `ONBOARDING_REWARD_READY` | CLIENT | 로그인 전 고정 키캡 보상 표시 준비, property `keycap_code` |
| `onb_box_open_start` | `ONBOARDING_BOX_OPEN_STARTED` | CLIENT | 온보딩 고정 보상 상자 연출 시작 |
| `onb_box_open_end` | `ONBOARDING_BOX_OPEN_COMPLETED` | CLIENT | 온보딩 고정 보상 상자 연출 완료 |
| `onb_keycap_reveal` | `ONBOARDING_KEYCAP_REVEALED` | CLIENT | 로그인 전 온보딩 키캡 reveal 연출, property `keycap_code` |
| `onb_complete_view` | `ONBOARDING_COMPLETION_VIEWED` | CLIENT | 온보딩 완료 모달 노출 |
| `onb_login_tap` | `ONBOARDING_LOGIN_TAPPED` | CLIENT | 온보딩 로그인 CTA 탭 |
| `onb_login_success` | `ONBOARDING_LOGIN_SUCCEEDED` | CLIENT | 온보딩 로그인 성공 |
| `record_view` | `RECORD_VIEWED` | CLIENT | 기록 화면 노출 |
| `noti_perm_view` | `NOTIFICATION_PERMISSION_VIEWED` | CLIENT | 알림 권한 화면 노출 |
| `invite_view` | `INVITE_VIEWED` | CLIENT | 초대 화면 노출 |
| `push_sent` | `PUSH_SENT` | SERVER | 푸시 발송 |
| `push_open` | `PUSH_OPENED` | CLIENT | 푸시 열람 |
| `cashout_view` | `CASHOUT_VIEWED` | CLIENT | 출금 화면 노출 |
| `cashout_submit` | `CASHOUT_SUBMITTED` | CLIENT | 출금 요청 탭 |
| `boost_view` | `BOOSTER_VIEWED` | CLIENT | 부스터 화면 노출 |
| `boost_active` | `BOOSTER_ACTIVATED` | CLIENT | 부스터 활성화 |

## A/B Port와 Event 계약

| 방향 | 계약 | Status | 입력 요약 | 멱등 키 |
|---|---|---|---|---|
| B -> A | `ValidatedTapApplyUseCase` | `CONFIRMED` | `userId`, `tapBatchId`, `validTapDelta`, `boxProgressDelta`, `rankingTapDelta`, `occurredAt` | `tapBatchId` |
| B -> A | `KeycapBoxGrantUseCase` | `CONFIRMED` | `userId`, `boxCount`, `reason`, `referenceId` | `referenceId` |
| B -> A | `OnboardingKeycapGrantUseCase` | `PROPOSED` | `userId`, `settlementId`, `referenceId`, `reason`, `occurredAt` | `referenceId` |
| B -> A | `RecordEventIngestUseCase` | `CONFIRMED` | `eventId`, `eventType`, `userId`, `payload`, `occurredAt` | `eventId` |
| A -> B | `AdvertisementVerificationPort` | `CONFIRMED` | `adViewId`, `userId`, `placement` | `adViewId` |
| A -> B | `UserWithdrawalGuardPort` | `CONFIRMED` | `userId` | 없음 |
| A -> B | `HomeQueryPort` | `PROPOSED` | A 소유 상자/키캡/랭킹/지역 요약 | 조회 |

공통 이벤트 필드:

```json
{
  "eventId": "uuid",
  "eventType": "InvitationQualified",
  "userId": "user-public-uuid",
  "referenceId": "domain-reference-uuid",
  "occurredAt": "2026-07-02T06:30:00Z",
  "payload": {}
}
```

광고 상자 흐름은 `B 광고 완료 -> A가 완료된 ad_view를 Port로 조회 -> A가 기존 보유 상자 1개 개봉`이다.

`OnboardingKeycapGrantUseCase` 입력 예:

```json
{
  "userId": "user-public-uuid",
  "settlementId": "onboarding-settlement-public-uuid",
  "referenceId": "ONBOARDING_COMPLETION_KEYCAP:onboarding-settlement-public-uuid",
  "reason": "ONBOARDING_COMPLETION_KEYCAP",
  "occurredAt": "2026-07-04T00:00:00Z"
}
```

출력 예:

```json
{
  "userKeycapId": "user-keycap-public-uuid",
  "keycapId": "keycap-public-uuid",
  "code": "ONBOARDING_BLUE_SWITCH",
  "name": "청축 키캡",
  "imageUrl": "https://..."
}
```

멱등성:

- `referenceId`와 사용자당 온보딩 완료 키캡 1회 기준으로 멱등 처리한다.
- 같은 요청 재시도와 동시 요청은 동일한 `userKeycapId`와 동일 keycap 결과를 반환한다.
- `userKeycapId`는 지급된 `user_keycap.public_id`이며, `keycapId`는 카탈로그 `keycap.public_id`다.
- 즉시 키캡 결과 화면이 필요하므로 단순 비동기 Event만 사용하지 않는다. Outbox/Event는 후속 기록과 분석 전파에 사용할 수 있다.
- 분석 이벤트 실패는 보상 지급과 로그인 성공을 rollback하지 않는다.

## 주요 에러 코드

| HTTP | 코드 | 설명 |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | 요청 필드 검증 실패 |
| 400 | `IDEMPOTENCY_KEY_REUSED` | 같은 Key에 다른 요청 사용 |
| 401 | `AUTH_REQUIRED` | 인증 필요 |
| 401 | `AUTH_INVALID_TOKEN` | 토큰 형식/서명/type 오류 |
| 401 | `AUTH_ACCESS_EXPIRED` | Access Token 만료 |
| 401 | `AUTH_REFRESH_EXPIRED` | Refresh Token 만료 |
| 401 | `AUTH_SESSION_NOT_FOUND` | Redis Refresh Session 없음 |
| 401 | `AUTH_LOGOUT_SESSION_MISMATCH` | 로그아웃 요청 Refresh Token이 현재 Access Session과 불일치 |
| 401 | `AUTH_REFRESH_REUSED` | Rotation 완료된 과거 Refresh 재사용 |
| 409 | `AUTH_REFRESH_CONFLICT` | 거의 동시에 들어온 Refresh 충돌 |
| 503 | `AUTH_REDIS_UNAVAILABLE` | 인증 Redis 장애 |
| 403 | `MEMBER_REQUIRED` | 회원 전용 기능 |
| 409 | `ONBOARDING_SETTLEMENT_CONFLICT` | 온보딩 정산 멱등성 충돌 |
| 409 | `KEYCAP_NOT_COMPLETED` | 미완성 키캡 장착 시도 |
| 409 | `KEYCAP_BOX_INSUFFICIENT` | 보유 상자 부족 |
| 409 | `KEYCAP_FREE_OPEN_NOT_READY` | 무료 개봉 쿨다운 |
| 429 | `KEYCAP_AD_LIMIT_EXCEEDED` | 광고 개봉 일 한도 초과 |
| 409 | `KEYCAP_AD_NOT_VERIFIED` | 광고 완료 검증 실패 |
| 409 | `REGION_CHANGE_LIMIT_EXCEEDED` | 지역 변경 월 한도 초과 |
| 403 | `RANKING_MEMBER_REQUIRED` | 레거시 수동 참가 API 회원 필요 |
| 409 | `TAP_SEQUENCE_CONFLICT` | 탭 sequence 충돌 |
| 429 | `TAP_RATE_SUSPICIOUS` | 탭 속도 이상 |
| 409 | `POINT_INSUFFICIENT` | 포인트 부족 |
| 400 | `CASHOUT_UNIT_INVALID` | 출금 단위 오류 |
| 409 | `AD_VIEW_ALREADY_USED` | 광고 완료 건 재사용 |
| 409 | `BOOSTER_ALREADY_ACTIVE` | 이미 부스터 활성 |
| 409 | `INVITE_ALREADY_ACCEPTED` | 초대 중복 수락 |
| 502 | `EXTERNAL_SERVICE_ERROR` | Toss, 광고, Push 외부 연동 오류 |
| 503 | `SERVICE_TEMPORARILY_UNAVAILABLE` | 일시 장애 |

## 향후 API

- 운영자용 랭킹 정산 재시도/보정 API
- 운영자용 `app_config` append API
- 운영자용 부정 탭 검토 API
- 보유 상자 일괄 개봉 API는 MVP 제외

## 2026-07-04 인증 API 구현 상태

- 실제 Java API 경로는 `/api/v1` prefix를 사용한다. 기존 `/auth/**` 경로는 성공 API로 동작하지 않는다.
- `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all`은 실제 Redis/PostgreSQL Testcontainers 기반 API 통합 테스트를 통과했다.
- 응답은 공통 `traceId` 계약을 유지하며 Access Log에도 `traceId`가 기록된다.
- `logout`은 Access Token의 현재 Session을 대상으로 삼고, optional Refresh Token은 동일 Session 검증용으로만 사용한다.
- `logout-all` 응답은 `loggedOutAll`, `revokedSessionCount`를 분리한다. `revokedSessionCount`는 실제 삭제된 활성 Refresh Session 수 기준이다.
- Toss 로그인/회원 생성, 온보딩 로그인 정산, 키캡/상자, 지역/랭킹, 알림, 기록, 설정/법적 문서 API 구현 상태는 이번 작업에서 변경하지 않았다.
