# 꾹머니 프론트엔드 공유용 MVP API 가이드

이 문서는 프론트엔드가 꾹머니 MVP API를 연동할 때 바로 참고할 수 있는 요청, 응답, 헤더, 에러, 재시도 규칙을 정리한다. 데이터베이스 설명보다 HTTP Method, Path, Header, Request Body, Response Body, Error Response를 우선한다.

- 상세한 프론트 연동 계약의 기준 문서는 이 문서다.
- 전체 Endpoint 범위의 상위 기준은 [api-contract.md](api-contract.md)다.
- 현재 구현된 API와 구현 예정 API를 구분한다.
- 구현되지 않은 API는 확정 구현처럼 표현하지 않고 `계약 초안`으로 표시한다.
- 계약 초안의 필드명, 성공 Status, 세부 에러 코드는 백엔드 구현 과정에서 변경될 수 있다.

> 구현 상태 기준: 현재 문서가 포함된 브랜치 기준. Controller와 DTO 변경 시 상태 집계를 다시 확인해야 한다.

## API 상태 표기

| 상태 | 의미 |
|---|---|
| `상태: 구현 확인` | 현재 Controller와 Request/Response DTO가 존재하고 실제 필드를 확인할 수 있는 API |
| `상태: 구현 확인 · 결정 필요` | 구현은 존재하지만 기존 목표 계약 중 아직 제품·API 결정이 남은 API |
| `상태: 계약 초안` | MVP 범위에는 있으나 현재 Controller 또는 DTO가 없는 API |

현재 브랜치 기준 상태 집계:

| 상태 | API 수 |
|---|---:|
| 구현 확인 | 14 |
| 구현 확인 · 결정 필요 | 0 |
| 계약 초안 | 11 |
| 전체 | 25 |

> 탭과 부스터는 현재 Controller와 DTO가 존재하므로 집계에는 구현 확인으로 반영한다. 다만 세부 문서의 경로·상태 문구는 별도 문서 정리에서 계속 정합화가 필요하다.

## 공통 규칙

### Base Path

```text
/api/v1
```

### 인증

```http
Authorization: Bearer {accessToken}
```

### Content-Type

```http
Content-Type: application/json
```

### 성공 응답

성공 응답 Body에는 `success`, `data`만 사용한다.

```json
{
  "success": true,
  "data": {}
}
```

### 실패 응답

실패 응답 Body에는 `success`, `error.code`, `error.message`만 사용한다.

```json
{
  "success": false,
  "error": {
    "code": "COMMON_VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다."
  }
}
```

### 요청 추적 ID

- `requestId`, `traceId`는 JSON Response Body에 포함하지 않는다.
- 요청 추적 ID는 `X-Request-Id` 응답 헤더와 서버 로그에서 관리한다.
- 프론트가 장애 문의용 값을 보관해야 할 경우 `X-Request-Id` 응답 헤더를 저장한다.

### 식별자

- 사용자 ID는 UUID `app_user.id`를 `userId`로 노출한다.
- 다른 외부 리소스는 UUID `public_id` 또는 안정 코드를 사용한다.
- BIGINT 내부 PK는 API에 노출하지 않는다.

### 시간

시간은 ISO 8601 UTC 문자열을 사용한다.

```text
2026-07-11T06:30:00Z
```

## Idempotency-Key 사용 규칙

### 왜 필요한가

`Idempotency-Key`는 다음 상황에서 같은 상태 변경이 여러 번 처리되는 것을 막는다.

- 네트워크 응답 유실
- 요청 타임아웃
- 버튼 중복 클릭
- 앱 자동 재전송
- 성공 여부를 확인하지 못한 상태에서 같은 요청을 다시 보내는 경우

`Idempotency-Key` 요청 헤더는 자연 멱등키가 없는 상자 개봉과 출금 요청에서만 사용한다. 탭 배치, 부스터 활성화, 키캡 장착은 아래처럼 각 API의 업무 키나 HTTP Method 특성으로 중복 실행을 제어한다.

### 프론트 생성 규칙

- 한 번의 논리적 사용자 작업마다 UUID v4 형태의 키를 새로 생성한다.
- 네트워크 문제로 같은 요청을 재시도할 때는 기존 키를 재사용한다.
- Request Body가 바뀌면 새 키를 생성한다.
- 서로 다른 API에 같은 키를 재사용하지 않는다.
- 성공 응답을 받은 후 사용자가 같은 기능을 새로 실행하면 새 키를 생성한다.

```javascript
const idempotencyKey = crypto.randomUUID();
```

### 요청 Header 예시

```http
POST /api/v1/cashouts
Authorization: Bearer {accessToken}
Content-Type: application/json
Idempotency-Key: 4b9c7f7e-d914-4c91-9d1f-6f2e57e48298
```

### 서버 처리 규칙

| 요청 상황 | 서버 처리 |
|---|---|
| 처음 들어온 키 | 요청 처리 후 결과 저장 |
| 같은 키 + 같은 요청 내용 | 상태를 다시 변경하지 않고 기존 결과 반환 |
| 같은 키 + 다른 요청 내용 | `409 IDEMPOTENCY_KEY_REUSED` |
| 새로운 키 | 새로운 작업으로 처리 |

### API별 멱등 기준

| API | 멱등 기준 |
|---|---|
| 상자 개봉 | `Idempotency-Key` Header |
| 출금 요청 | `Idempotency-Key` Header |
| 탭 배치 | `tapSessionId + sequence` |
| 부스터 활성화 | `adRewardId` 중복 사용 방지 |
| 키캡 장착 | `PUT`의 동일 최종 상태 |
| 로그인 온보딩 보상 | 계약상 `onboardingAttemptId`, 현재 로그인 DTO에는 미반영 |

`IDEMPOTENCY_KEY_REUSED`는 현재 `ErrorCode`에 없으므로 `계약상 예정 · 구현 필요` 상태다.

### 프론트 재시도 규칙

- 연결 실패, 타임아웃, 일부 5xx 응답은 같은 Body와 같은 키로 재시도할 수 있다.
- 사용자가 입력값을 수정했으면 새 키를 생성한다.
- `IDEMPOTENCY_KEY_REUSED`는 자동 무한 재시도하지 않는다.
- 탭 배치는 별도 Header가 아니라 `tapSessionId + sequence`를 유지해 재시도한다.
- 부스터 활성화는 별도 Header가 아니라 광고 보상 식별자 `adRewardId`를 중복 사용하지 않는다.

## 주요 공통 에러 코드

| HTTP | code | 상태 | 프론트 처리 |
|---:|---|---|---|
| 400 | `COMMON_VALIDATION_ERROR` | 구현 확인 | 요청값 확인 |
| 401 | `AUTH_REQUIRED` | 구현 확인 | 로그인 필요 |
| 401 | `AUTH_INVALID_TOKEN` | 구현 확인 | Refresh 또는 재로그인 |
| 401 | `AUTH_EXPIRED_TOKEN` | 구현 확인 | Refresh 수행 |
| 401 | `AUTH_SESSION_NOT_FOUND` | 구현 확인 | 로컬 토큰 제거 후 재로그인 |
| 409 | `AUTH_REFRESH_CONFLICT` | 구현 확인 | 진행 중인 Refresh 결과 대기 |
| 401 | `AUTH_REFRESH_REUSED` | 구현 확인 | 모든 인증정보 제거 |
| 401 | `AUTH_LOGOUT_SESSION_MISMATCH` | 구현 확인 | 로컬 인증정보 제거 |
| 401 | `TOSS_INVALID_GRANT` | 구현 확인 | 새 authorizationCode 발급 |
| 403 | `TOSS_USER_MISMATCH` | 구현 확인 | 탈퇴 중단 |
| 403 | `ACCOUNT_WITHDRAWN` | 구현 확인 | 탈퇴 계정 안내 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 계약상 예정 · 구현 필요 | 변경된 작업은 새 키 생성 |

## 인증과 회원

### 1. `POST /api/v1/auth/toss/login`

상태: 구현 확인

#### Description

Toss `appLogin()`으로 받은 일회성 `authorizationCode`를 서버에 전달해 꾹머니 사용자를 생성하거나 기존 사용자를 로그인시키고 Access/Refresh JWT를 발급한다.

> 결정 필요: 기존 목표 계약에는 로그인 시 온보딩 정산 정보가 포함되어 있지만 현재 `TossLoginRequest`에는 `authorizationCode`, `referrer`만 존재한다. 로그인 DTO에 온보딩 필드를 추가할지, 온보딩 정산을 별도 API로 분리할지 결정해야 한다.

현재 DTO에 없는 기존 온보딩 계약:

```json
{
  "onboarding": {
    "onboardingAttemptId": "9dc0c935-d9e2-4f96-a764-8de0b1232145",
    "onboardingTapCount": 45
  }
}
```

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Content-Type` | String | O | `application/json` |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `authorizationCode` | String | O | Toss에서 발급한 일회성 인가 코드 |
| `referrer` | String | X | Toss 진입 경로. 예: `DEFAULT` |

```json
{
  "authorizationCode": "one-time-code",
  "referrer": "DEFAULT"
}
```

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.userId` | UUID | 사용자 ID. `app_user.id` |
| `data.accessToken` | String | 꾹머니 Access JWT |
| `data.refreshToken` | String | 꾹머니 Refresh JWT |
| `data.tokenType` | String | `Bearer` |
| `data.accessTokenExpiresAt` | String | Access Token 만료 시각 |
| `data.refreshTokenExpiresAt` | String | Refresh Token 만료 시각 |
| `data.newUser` | Boolean | 신규 사용자 여부 |

```json
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "accessToken": "jwt",
    "refreshToken": "jwt",
    "tokenType": "Bearer",
    "accessTokenExpiresAt": "2026-07-11T07:00:00Z",
    "refreshTokenExpiresAt": "2026-08-10T06:30:00Z",
    "newUser": true
  }
}
```

#### Error Response

##### `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "TOSS_AUTHORIZATION_CODE_REQUIRED",
    "message": "Toss authorizationCode가 필요합니다."
  }
}
```

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "TOSS_INVALID_GRANT",
    "message": "Toss 인증 코드가 유효하지 않습니다."
  }
}
```

##### `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "ACCOUNT_WITHDRAWN",
    "message": "탈퇴한 계정입니다."
  }
}
```

### 2. `POST /api/v1/auth/refresh`

상태: 구현 확인

#### Description

Refresh Token을 Request Body로 전달해 Access/Refresh Token을 회전한다. Header가 아니라 Body의 `refreshToken`을 사용한다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Content-Type` | String | O | `application/json` |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `refreshToken` | String | O | 꾹머니 Refresh JWT |

```json
{
  "refreshToken": "refresh-jwt"
}
```

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.userId` | UUID | 사용자 ID |
| `data.accessToken` | String | 새 Access JWT |
| `data.refreshToken` | String | 새 Refresh JWT |
| `data.tokenType` | String | `Bearer` |
| `data.accessTokenExpiresAt` | String | Access Token 만료 시각 |
| `data.refreshTokenExpiresAt` | String | Refresh Token 만료 시각 |
| `data.newUser` | Boolean | Refresh에서는 일반적으로 `false` |

```json
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "accessToken": "new-access-jwt",
    "refreshToken": "new-refresh-jwt",
    "tokenType": "Bearer",
    "accessTokenExpiresAt": "2026-07-11T07:00:00Z",
    "refreshTokenExpiresAt": "2026-08-10T06:30:00Z",
    "newUser": false
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REFRESH_REUSED",
    "message": "이미 사용된 리프레시 토큰입니다."
  }
}
```

##### `409 Conflict`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REFRESH_CONFLICT",
    "message": "리프레시 토큰 갱신 충돌이 발생했습니다."
  }
}
```

### 3. `POST /api/v1/auth/logout`

상태: 구현 확인

#### Description

현재 Access Token의 `sid`를 기준으로 현재 Session을 로그아웃한다. Request Body는 선택 사항이며 Body 없이 호출할 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |
| `Content-Type` | String | X | Body를 보낼 때 `application/json` |

#### Request Body

선택 사항.

| name | type | required | description |
|---|---|---:|---|
| `refreshToken` | String | X | 같은 Session인지 추가 검증할 Refresh JWT |

```json
{
  "refreshToken": "optional-refresh-jwt"
}
```

Body 없이도 호출 가능하다.

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.loggedOut` | Boolean | 현재 Session 로그아웃 처리 여부 |

```json
{
  "success": true,
  "data": {
    "loggedOut": true
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_LOGOUT_SESSION_MISMATCH",
    "message": "로그아웃 세션이 일치하지 않습니다."
  }
}
```

### 4. `POST /api/v1/auth/logout-all`

상태: 구현 확인

#### Description

현재 사용자의 모든 Redis Session을 제거하고 기존 Access Token을 무효화한다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.loggedOutAll` | Boolean | 전체 Session 로그아웃 처리 여부 |
| `data.revokedSessionCount` | Number | 폐기한 Session 수 |

```json
{
  "success": true,
  "data": {
    "loggedOutAll": true,
    "revokedSessionCount": 3
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

### 5. `POST /api/v1/auth/toss/unlink-webhook`

상태: 구현 확인

#### Description

Toss 서버가 호출하는 연결 해제 Webhook이다. 프론트 앱이 직접 호출하는 API가 아니다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Basic {base64(secret)}` |
| `Content-Type` | String | O | `application/json` |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `userKey` | String | O | Toss 사용자 식별자 |
| `referrer` | String | O | Webhook 이벤트. 예: `UNLINK` |

```json
{
  "userKey": "toss-user-key",
  "referrer": "UNLINK"
}
```

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.processed` | Boolean | Webhook 처리 여부 |
| `data.referrer` | String | 처리한 Webhook 이벤트 |

```json
{
  "success": true,
  "data": {
    "processed": true,
    "referrer": "UNLINK"
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "TOSS_WEBHOOK_UNAUTHORIZED",
    "message": "Toss webhook 인증에 실패했습니다."
  }
}
```

### 6. `GET /api/v1/members/me`

상태: 구현 확인

#### Description

내 사용자 정보, 장착 키캡, 포인트 잔액을 조회한다.

현재 구현은 장착 키캡이 없으면 `equippedKeycap=null`을 반환한다. 현재 `keycap` Entity에는 `imageUrl` 필드가 없으므로 장착 키캡이 있어도 `equippedKeycap.imageUrl=null`을 반환하며, 임의의 고정 URL을 만들지 않는다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.userId` | UUID | 사용자 ID |
| `data.status` | String | 사용자 상태 |
| `data.nickname` | String | 닉네임 |
| `data.profileImageUrl` | String | 프로필 이미지 URL |
| `data.equippedKeycap` | Object | 장착 키캡 |
| `data.pointBalance` | Number | 포인트 잔액 |

```json
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ACTIVE",
    "nickname": "꾹머니",
    "profileImageUrl": "https://example.com/profile.png",
    "equippedKeycap": {
      "keycapId": "4e5d3a9b-02d0-4b45-b2bd-2bb30b01bb9f",
      "code": "BASIC_001",
      "name": "기본 키캡",
      "imageUrl": null
    },
    "pointBalance": 42
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

##### `403 Forbidden`

탈퇴 사용자가 조회를 시도하면 `ACCOUNT_WITHDRAWN`을 반환한다.

```json
{
  "success": false,
  "error": {
    "code": "ACCOUNT_WITHDRAWN",
    "message": "탈퇴한 계정입니다."
  }
}
```

### 7. `PATCH /api/v1/members/me`

상태: 구현 확인

#### Description

내 닉네임 등 프로필 정보를 수정한다.

부분 수정으로 처리한다. `nickname`과 `profileImageUrl` 중 최소 하나가 필요하다. `nickname`은 공백만 보낼 수 없고 최대 50자다. `profileImageUrl`은 최대 2048자이며, 공백 문자열은 현재 서버 정규화 정책에 따라 `null`로 저장된다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |
| `Content-Type` | String | O | `application/json` |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `nickname` | String | X | 변경할 닉네임 |
| `profileImageUrl` | String | X | 변경할 프로필 이미지 URL |

```json
{
  "nickname": "새닉네임",
  "profileImageUrl": "https://example.com/profile.png"
}
```

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.userId` | UUID | 사용자 ID |
| `data.nickname` | String | 변경된 닉네임 |
| `data.profileImageUrl` | String | 변경된 프로필 이미지 URL |

```json
{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "nickname": "새닉네임",
    "profileImageUrl": "https://example.com/profile.png"
  }
}
```

#### Error Response

##### `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "COMMON_VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다."
  }
}
```

##### `403 Forbidden`

탈퇴 사용자가 수정을 시도하면 `ACCOUNT_WITHDRAWN`을 반환한다.

```json
{
  "success": false,
  "error": {
    "code": "ACCOUNT_WITHDRAWN",
    "message": "탈퇴한 계정입니다."
  }
}
```

##### `409 Conflict`

활성 사용자와 닉네임 정규화 값이 중복되면 `NICKNAME_ALREADY_EXISTS`를 반환한다.

```json
{
  "success": false,
  "error": {
    "code": "NICKNAME_ALREADY_EXISTS",
    "message": "이미 사용 중인 닉네임입니다."
  }
}
```

### 8. `POST /api/v1/members/me/withdrawal`

상태: 구현 확인

#### Description

회원 탈퇴를 수행한다. 프론트는 탈퇴 직전에 Toss `appLogin()`을 다시 호출해 새로운 `authorizationCode`를 발급받아야 한다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |
| `Content-Type` | String | O | `application/json` |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `authorizationCode` | String | O | 탈퇴 직전에 새로 받은 Toss 인가 코드 |
| `referrer` | String | X | Toss 진입 경로 |

```json
{
  "authorizationCode": "fresh-one-time-code",
  "referrer": "DEFAULT"
}
```

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.withdrawn` | Boolean | 탈퇴 처리 여부 |

```json
{
  "success": true,
  "data": {
    "withdrawn": true
  }
}
```

#### Error Response

##### `403 Forbidden`

```json
{
  "success": false,
  "error": {
    "code": "TOSS_USER_MISMATCH",
    "message": "현재 사용자와 Toss 사용자가 일치하지 않습니다."
  }
}
```

## 설정과 키캡

### 9. `GET /api/v1/app-config`

상태: 구현 확인

#### Description

앱에서 표시할 공개 운영 정책을 조회한다. Access JWT가 필요한 인증 API이며, 서버 내부 검증값과 원본 `app_config.config_value` JSON은 반환하지 않는다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.pointPolicy` | Object | 포인트 정책 |
| `data.boxPolicy` | Object | 상자 정책 |
| `data.boosterPolicy` | Object | 부스터 정책 |
| `data.pointPolicy.dailyLimit` | Number | 일일 포인트 적립 한도 |
| `data.boxPolicy.baseRequiredTapCount` | Number | 누적 유효 탭 기준 상자 지급 기본 간격 |
| `data.boosterPolicy.durationSeconds` | Number | 부스터 지속 시간(초) |
| `data.boosterPolicy.dailyLimit` | Number | 일일 부스터 활성화 제한 |

```json
{
  "success": true,
  "data": {
    "pointPolicy": {
      "dailyLimit": 20
    },
    "boxPolicy": {
      "baseRequiredTapCount": 200
    },
    "boosterPolicy": {
      "durationSeconds": 300,
      "dailyLimit": 3
    }
  }
}
```

반환하지 않는 값:

- 내부 BIGINT PK, `publicId`, `effectiveAt`, 원본 `configKey`, 원본 `configValue`
- 탭 최소 간격, 분당/일일 탭 제한, 봇 판정값, 레이트리밋 값, 포인트 곡선 내부값, `boxDropVariance`
- 앱 버전, 점검 상태, 출금 환율/한도, 부스터 배율, 사용자별 다음 상자까지 남은 탭 수, 사용자별 상자 잔액

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

##### `500 Internal Server Error`

```json
{
  "success": false,
  "error": {
    "code": "COMMON_INTERNAL_SERVER_ERROR",
    "message": "서버 내부 오류가 발생했습니다."
  }
}
```

### 10. `GET /api/v1/keycaps`

상태: 구현 확인

#### Description

활성 키캡 마스터 목록을 조회한다. Access JWT가 필요하며, `active=true` 키캡만 `sortOrder ASC, code ASC` 순서로 반환한다. 목록이 없으면 `200 OK`와 빈 배열을 반환한다.


#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.keycaps` | Array | 키캡 목록 |
| `data.keycaps[].keycapId` | UUID | 키캡 식별자 |
| `data.keycaps[].code` | String | 안정 코드 |
| `data.keycaps[].name` | String | 키캡 이름 |
| `data.keycaps[].grade` | String | 등급 |
| `data.keycaps[].requiredShardCount` | Number | 완성에 필요한 조각 수 |
| `data.keycaps[].season` | Number | 시즌 |
| `data.keycaps[].imageUrl` | String | 이미지 URL |
| `data.keycaps[].soundUrl` | String | 사운드 URL |

```json
{
  "success": true,
  "data": {
    "keycaps": [
      {
        "keycapId": "4e5d3a9b-02d0-4b45-b2bd-2bb30b01bb9f",
        "code": "BASIC_001",
        "name": "기본 키캡",
        "grade": "COMMON",
        "requiredShardCount": 10,
        "season": 1,
        "imageUrl": "https://example.com/keycap.png",
        "soundUrl": "https://example.com/keycap.mp3"
      }
    ]
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

##### `500 Internal Server Error`

```json
{
  "success": false,
  "error": {
    "code": "COMMON_INTERNAL_SERVER_ERROR",
    "message": "서버 내부 오류가 발생했습니다."
  }
}
```

### 11. `GET /api/v1/keycaps/me`

상태: 구현 확인

#### Description

내가 보유한 키캡 조각, 완성 상태, 장착 여부를 조회한다. 현재 인증 사용자 UUID 조건으로만 조회하며, `UserKeycap`과 `Keycap`을 join fetch로 함께 조회해 N+1을 방지한다. 목록이 없으면 `200 OK`와 빈 배열을 반환한다.


#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

`200 OK`

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.keycaps` | Array | 내 키캡 목록 |
| `data.keycaps[].keycapId` | UUID | 키캡 식별자 |
| `data.keycaps[].code` | String | 안정 코드 |
| `data.keycaps[].name` | String | 키캡 이름 |
| `data.keycaps[].shardCount` | Number | 보유 조각 수 |
| `data.keycaps[].status` | String | `IN_PROGRESS` 또는 `COMPLETED` |
| `data.keycaps[].equipped` | Boolean | 장착 여부 |

```json
{
  "success": true,
  "data": {
    "keycaps": [
      {
        "keycapId": "4e5d3a9b-02d0-4b45-b2bd-2bb30b01bb9f",
        "code": "BASIC_001",
        "name": "기본 키캡",
        "shardCount": 10,
        "status": "COMPLETED",
        "equipped": true
      }
    ]
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

### 12. `PUT /api/v1/keycaps/{keycapId}/equip`

상태: 계약 초안

#### Description

완성한 키캡 하나를 장착한다. 사용자당 장착 키캡은 하나다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.keycapId` | UUID | 장착한 키캡 식별자 |
| `data.equipped` | Boolean | 장착 여부 |

```json
{
  "success": true,
  "data": {
    "keycapId": "4e5d3a9b-02d0-4b45-b2bd-2bb30b01bb9f",
    "equipped": true
  }
}
```

#### Error Response

##### `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "COMMON_VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다."
  }
}
```

## 키캡 상자

### 13. `GET /api/v1/keycap-boxes/status`

상태: 계약 초안

#### Description

내 키캡 상자 잔액, 무료 개봉권, 광고 개봉 카운트, 다음 상자 진행도를 조회한다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.boxBalance` | Number | 보유 상자 수 |
| `data.freeOpenTicketCount` | Number | 무료 개봉권 수 |
| `data.nextFreeTicketAt` | String | 다음 무료권 충전 시각 |
| `data.adOpenCount` | Number | 오늘 광고 개봉 횟수 |
| `data.boxProgressTapCount` | Number | 다음 상자까지 누적 탭 |
| `data.nextBoxRequiredTapCount` | Number | 다음 상자에 필요한 탭 수 |

```json
{
  "success": true,
  "data": {
    "boxBalance": 2,
    "freeOpenTicketCount": 1,
    "nextFreeTicketAt": "2026-07-11T09:00:00Z",
    "adOpenCount": 0,
    "boxProgressTapCount": 45,
    "nextBoxRequiredTapCount": 100
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

### 14. `POST /api/v1/keycap-boxes/open`

상태: 계약 초안

#### Description

상자를 개봉하고 키캡 조각을 지급한다. 이 API는 `Idempotency-Key` Header가 필수다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |
| `Content-Type` | String | O | `application/json` |
| `Idempotency-Key` | String | O | 같은 상자 개봉 중복 처리 방지 키 |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `openMethod` | String | O | `FREE` 또는 `ADVERTISEMENT` |
| `adRewardId` | String | X | 광고 개봉인 경우 광고 보상 식별자 |

```json
{
  "openMethod": "ADVERTISEMENT",
  "adRewardId": "ad-reward-20260711-001"
}
```

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.boxOpenId` | UUID | 상자 개봉 식별자 |
| `data.keycapId` | UUID | 지급된 키캡 식별자 |
| `data.shardCount` | Number | 지급 조각 수 |
| `data.boostApplied` | Boolean | 부스터 적용 여부 |
| `data.completed` | Boolean | 이번 개봉으로 완성됐는지 |
| `data.openedAt` | String | 개봉 시각 |

```json
{
  "success": true,
  "data": {
    "boxOpenId": "4f00ec0c-91d7-41db-96b1-31a305d10ef3",
    "keycapId": "4e5d3a9b-02d0-4b45-b2bd-2bb30b01bb9f",
    "shardCount": 1,
    "boostApplied": false,
    "completed": false,
    "openedAt": "2026-07-11T06:30:00Z"
  }
}
```

#### Error Response

##### `409 Conflict`

```json
{
  "success": false,
  "error": {
    "code": "IDEMPOTENCY_KEY_REUSED",
    "message": "같은 멱등키로 다른 요청을 처리할 수 없습니다."
  }
}
```

`IDEMPOTENCY_KEY_REUSED`는 계약상 예정이며 현재 `ErrorCode` 구현이 필요하다.

### 15. `GET /api/v1/keycap-boxes/history`

상태: 계약 초안

#### Description

상자 개봉 히스토리를 조회한다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.
> 페이지 방식은 계약 초안이며 `page/size`와 cursor 방식 중 최종 확정이 필요하다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

Query Parameter 초안:

```http
?page=0&size=20
```

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.content` | Array | 상자 개봉 목록 |
| `data.page` | Number | 페이지 번호 |
| `data.size` | Number | 페이지 크기 |
| `data.hasNext` | Boolean | 다음 페이지 존재 여부 |

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "boxOpenId": "4f00ec0c-91d7-41db-96b1-31a305d10ef3",
        "openMethod": "FREE",
        "keycapId": "4e5d3a9b-02d0-4b45-b2bd-2bb30b01bb9f",
        "shardCount": 1,
        "boostApplied": false,
        "completed": false,
        "openedAt": "2026-07-11T06:30:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "hasNext": false
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

## 탭

### 16. `POST /api/v1/taps/batches`

상태: 계약 초안

#### Description

프론트가 모은 탭 배치를 서버에 제출한다. 같은 `tapSessionId + sequence`는 같은 논리적 요청으로 처리한다.

> 계약 초안: 현재 브랜치에는 `TapController`와 탭 Request/Response DTO가 없어 실제 구현 Path와 최종 필드는 확인되지 않았다. 현재 MVP 상위 계약인 `/api/v1/taps/batches`를 기준으로 작성한다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |
| `Content-Type` | String | O | `application/json` |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `tapSessionId` | UUID | O | 클라이언트 탭 세션 ID |
| `sequence` | Number | O | 세션 안에서 증가하는 배치 순번 |
| `submittedCount` | Number | O | 제출 탭 수 |

```json
{
  "tapSessionId": "b3f1c2a0-1234-4a5b-9c3d-abcdef123456",
  "sequence": 5,
  "submittedCount": 87
}
```

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.acceptedCount` | Number | 인정된 탭 수 |
| `data.pointsAwarded` | Number | 지급 포인트 |
| `data.balance` | Number | 처리 후 포인트 잔액 |

```json
{
  "success": true,
  "data": {
    "acceptedCount": 87,
    "pointsAwarded": 1,
    "balance": 42
  }
}
```

#### Error Response

##### `409 Conflict`

```json
{
  "success": false,
  "error": {
    "code": "IDEMPOTENCY_KEY_REUSED",
    "message": "같은 멱등키로 다른 요청을 처리할 수 없습니다."
  }
}
```

같은 `tapSessionId + sequence`와 같은 요청은 기존 결과를 반환한다. 같은 키에 다른 요청 내용이 들어오면 계약상 `409 IDEMPOTENCY_KEY_REUSED`다. 현재 공통 `ErrorCode`에는 아직 해당 코드가 없다.

### 17. `GET /api/v1/taps/today`

상태: 계약 초안

#### Description

KST 기준 오늘 탭 집계와 현재 부스터 정보를 조회한다.

> 계약 초안: 현재 브랜치에는 `TapController`와 탭 Request/Response DTO가 없어 실제 구현 Path와 최종 필드는 확인되지 않았다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.tapDate` | String | KST 기준일 |
| `data.validTapCount` | Number | 오늘 유효 탭 수 |
| `data.pointEarnedAmount` | Number | 오늘 획득 포인트 |
| `data.boxDroppedCount` | Number | 오늘 드롭된 상자 수 |
| `data.pointProgressRemainder` | Number | 다음 포인트 적립 진행값 |
| `data.dailyPointLimitReached` | Boolean | 일일 포인트 상한 여부 |
| `data.currentBooster` | Object | 현재 부스터 |

```json
{
  "success": true,
  "data": {
    "tapDate": "2026-07-11",
    "validTapCount": 120,
    "pointEarnedAmount": 3,
    "boxDroppedCount": 1,
    "pointProgressRemainder": 20,
    "dailyPointLimitReached": false,
    "currentBooster": {
      "active": true,
      "multiplier": 2.0,
      "endsAt": "2026-07-11T07:00:00Z"
    }
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

## 포인트

### 18. `GET /api/v1/points/me`

상태: 계약 초안

#### Description

내 포인트 잔액과 누적 획득/사용 금액을 조회한다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.balance` | Number | 현재 잔액 |
| `data.lifetimeEarned` | Number | 누적 획득 |
| `data.lifetimeSpent` | Number | 누적 사용 |

```json
{
  "success": true,
  "data": {
    "balance": 42,
    "lifetimeEarned": 100,
    "lifetimeSpent": 58
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

### 19. `GET /api/v1/points/ledger`

상태: 계약 초안

#### Description

포인트 원장 목록을 조회한다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.
> 페이지 방식은 계약 초안이며 `page/size`와 cursor 방식 중 최종 확정이 필요하다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

Query Parameter 초안:

```http
?page=0&size=20
```

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.content` | Array | 원장 목록 |
| `data.page` | Number | 페이지 번호 |
| `data.size` | Number | 페이지 크기 |
| `data.hasNext` | Boolean | 다음 페이지 존재 여부 |

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "ledgerId": "e45f8603-0706-4fe7-a0ec-4a31239f1527",
        "entryType": "CREDIT",
        "amount": 1,
        "reason": "TAP_REWARD",
        "referenceType": "TAP_BATCH",
        "referenceId": "b3f1c2a0-1234-4a5b-9c3d-abcdef123456",
        "balanceAfter": 42,
        "occurredAt": "2026-07-11T06:30:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "hasNext": false
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

## 출금

### 20. `GET /api/v1/cashouts/quote`

상태: 계약 초안

#### Description

출금 가능 금액과 현재 교환비 견적을 조회한다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.balance` | Number | 현재 포인트 잔액 |
| `data.exchangeRate` | Number | 포인트 대 Toss 포인트 교환비 |
| `data.availablePointAmount` | Number | 출금 가능 포인트 |
| `data.estimatedTossPointAmount` | Number | 예상 Toss 포인트 |

```json
{
  "success": true,
  "data": {
    "balance": 10000,
    "exchangeRate": 0.7,
    "availablePointAmount": 10000,
    "estimatedTossPointAmount": 7000
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

### 21. `POST /api/v1/cashouts`

상태: 계약 초안

#### Description

포인트를 차감하고 Toss 포인트 출금 요청을 생성한다. 포인트 차감과 출금 요청 생성은 하나의 트랜잭션으로 처리해야 한다. 이 API는 `Idempotency-Key` Header가 필수다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |
| `Content-Type` | String | O | `application/json` |
| `Idempotency-Key` | String | O | 같은 출금 요청 중복 처리 방지 키 |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `pointAmount` | Number | O | 차감할 포인트 |

```json
{
  "pointAmount": 10000
}
```

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.cashoutId` | UUID | 출금 요청 식별자 |
| `data.pointAmount` | Number | 차감 포인트 |
| `data.tossPointAmount` | Number | 지급할 Toss 포인트 |
| `data.exchangeRate` | Number | 요청 당시 교환비 |
| `data.status` | String | 출금 상태 |
| `data.requestedAt` | String | 요청 시각 |

```json
{
  "success": true,
  "data": {
    "cashoutId": "8e4f7af5-7d0f-4e46-83dd-3bba0d2d7a0b",
    "pointAmount": 10000,
    "tossPointAmount": 7000,
    "exchangeRate": 0.7,
    "status": "PENDING",
    "requestedAt": "2026-07-11T06:30:00Z"
  }
}
```

#### Error Response

##### `409 Conflict`

```json
{
  "success": false,
  "error": {
    "code": "IDEMPOTENCY_KEY_REUSED",
    "message": "같은 멱등키로 다른 요청을 처리할 수 없습니다."
  }
}
```

`IDEMPOTENCY_KEY_REUSED`는 계약상 예정이며 현재 `ErrorCode` 구현이 필요하다.

### 22. `GET /api/v1/cashouts`

상태: 계약 초안

#### Description

내 출금 요청 목록을 조회한다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.
> 페이지 방식은 계약 초안이며 `page/size`와 cursor 방식 중 최종 확정이 필요하다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

Query Parameter 초안:

```http
?page=0&size=20
```

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.content` | Array | 출금 요청 목록 |
| `data.page` | Number | 페이지 번호 |
| `data.size` | Number | 페이지 크기 |
| `data.hasNext` | Boolean | 다음 페이지 존재 여부 |

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "cashoutId": "8e4f7af5-7d0f-4e46-83dd-3bba0d2d7a0b",
        "pointAmount": 10000,
        "tossPointAmount": 7000,
        "exchangeRate": 0.7,
        "status": "PENDING",
        "providerCode": null,
        "failureCode": null,
        "requestedAt": "2026-07-11T06:30:00Z",
        "completedAt": null
      }
    ],
    "page": 0,
    "size": 20,
    "hasNext": false
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

### 23. `GET /api/v1/cashouts/{cashoutId}`

상태: 계약 초안

#### Description

출금 요청 상세를 조회한다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.cashoutId` | UUID | 출금 요청 식별자 |
| `data.pointAmount` | Number | 차감 포인트 |
| `data.tossPointAmount` | Number | 지급할 Toss 포인트 |
| `data.exchangeRate` | Number | 교환비 |
| `data.status` | String | 출금 상태 |
| `data.providerCode` | String | Toss 제공 응답 코드 |
| `data.failureCode` | String | 실패 사유 |
| `data.requestedAt` | String | 요청 시각 |
| `data.completedAt` | String | 완료 시각 |

```json
{
  "success": true,
  "data": {
    "cashoutId": "8e4f7af5-7d0f-4e46-83dd-3bba0d2d7a0b",
    "pointAmount": 10000,
    "tossPointAmount": 7000,
    "exchangeRate": 0.7,
    "status": "SUCCEEDED",
    "providerCode": "OK",
    "failureCode": null,
    "requestedAt": "2026-07-11T06:30:00Z",
    "completedAt": "2026-07-11T06:31:00Z"
  }
}
```

#### Error Response

##### `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "COMMON_INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다."
  }
}
```

## 부스터

### 24. `POST /api/v1/boosters/activate`

상태: 계약 초안

#### Description

광고 보상 식별자 `adRewardId`로 2배 부스터를 활성화한다. 같은 `adRewardId`는 중복 사용할 수 없다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |
| `Content-Type` | String | O | `application/json` |

#### Request Body

| name | type | required | description |
|---|---|---:|---|
| `adRewardId` | String | O | 광고 보상 식별자 |

```json
{
  "adRewardId": "ad-reward-20260711-002"
}
```

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.boosterId` | UUID | 부스터 식별자 |
| `data.dailySequence` | Number | 당일 부스터 순번 |
| `data.multiplier` | Number | 배수 |
| `data.status` | String | 부스터 상태 |
| `data.adRewardId` | String | 광고 보상 식별자 |
| `data.startsAt` | String | 시작 시각 |
| `data.endsAt` | String | 종료 시각 |
| `data.active` | Boolean | 활성 여부 |

```json
{
  "success": true,
  "data": {
    "boosterId": "45e56c54-1f73-4a7e-a47c-7f4b66fc07a3",
    "dailySequence": 1,
    "multiplier": 2.0,
    "status": "ACTIVE",
    "adRewardId": "ad-reward-20260711-002",
    "startsAt": "2026-07-11T06:30:00Z",
    "endsAt": "2026-07-11T06:40:00Z",
    "active": true
  }
}
```

#### Error Response

##### `400 Bad Request`

```json
{
  "success": false,
  "error": {
    "code": "COMMON_VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다."
  }
}
```

### 25. `GET /api/v1/boosters/current`

상태: 계약 초안

#### Description

현재 활성 부스터를 조회한다.

> 계약 초안: 현재 Controller와 DTO가 없으므로 필드명과 에러 코드는 구현 과정에서 변경될 수 있다.

#### Request Header

| name | type | required | description |
|---|---|---:|---|
| `Authorization` | String | O | `Bearer {accessToken}` |

#### Request Body

없음.

#### Response

##### Response Code

성공 Status는 구현 시 최종 확정.

##### Response Body

| name | type | description |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `data.active` | Boolean | 활성 부스터 존재 여부 |
| `data.boosterId` | UUID | 부스터 식별자 |
| `data.dailySequence` | Number | 당일 부스터 순번 |
| `data.multiplier` | Number | 배수 |
| `data.status` | String | 부스터 상태 |
| `data.startsAt` | String | 시작 시각 |
| `data.endsAt` | String | 종료 시각 |

```json
{
  "success": true,
  "data": {
    "active": true,
    "boosterId": "45e56c54-1f73-4a7e-a47c-7f4b66fc07a3",
    "dailySequence": 1,
    "multiplier": 2.0,
    "status": "ACTIVE",
    "startsAt": "2026-07-11T06:30:00Z",
    "endsAt": "2026-07-11T06:40:00Z"
  }
}
```

#### Error Response

##### `401 Unauthorized`

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

## 프론트 구현 체크리스트

### 인증

- Access Token과 Refresh Token을 구분해 저장한다.
- 여러 API에서 동시에 401이 발생해도 Refresh 요청은 하나만 실행한다.
- 대기 중인 요청은 Refresh 결과를 공유한다.
- `AUTH_REFRESH_REUSED`, `AUTH_SESSION_NOT_FOUND`이면 로컬 인증정보를 제거한다.
- 로그아웃과 탈퇴 성공 후 로컬 인증정보를 제거한다.

### 멱등성

- 상자 개봉과 출금 시작 시 `Idempotency-Key`를 생성한다.
- 응답 대기 중 버튼 중복 클릭을 방지한다.
- 네트워크 재시도에는 같은 키를 사용한다.
- Request Body 변경 시 새 키를 사용한다.
- `IDEMPOTENCY_KEY_REUSED`는 자동 무한 재시도하지 않는다.
- 탭 배치의 `sequence`는 같은 `tapSessionId` 안에서 중복되지 않게 관리한다.
- 부스터 활성화는 `adRewardId`를 중복 사용하지 않는다.

### 응답 처리

- HTTP Status와 `success`를 함께 확인한다.
- 실패 응답에서 `data`를 기대하지 않는다.
- Body에서 `requestId`, `traceId`를 찾지 않는다.
- 필요 시 `X-Request-Id` Header를 저장한다.

## 구현 전 정합화 필요 항목

1. 현재 Toss 로그인 DTO에는 `authorizationCode`, `referrer`만 있으며 기존 온보딩 계약에는 `onboardingAttemptId`, `onboardingTapCount`가 있다.
2. 로그인 온보딩 정산을 로그인 DTO에 포함할지 별도 API로 분리할지 결정해야 한다.
3. 현재 코드에는 `TapController`와 탭 DTO가 존재하며 실제 경로는 `/api/v1/tap/batches`다. 이 문서의 탭 세부 섹션은 후속 정합화가 필요하다.
4. `IDEMPOTENCY_KEY_REUSED`는 계약 문서에는 있지만 현재 `ErrorCode`에는 없다.
5. 목록 API의 `page/size` 또는 cursor 방식 확정이 필요하다.
6. `keycaps` 목록 조회 API는 Access JWT 필수 API로 확정됐다.
7. 계약 초안 API의 도메인별 에러 코드 확정이 필요하다.
