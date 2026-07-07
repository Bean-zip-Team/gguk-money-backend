# 꾹머니 인증 생명주기

이 문서는 13개 테이블 MVP의 Toss 로그인, Refresh, 로그아웃, logout-all, 회원 탈퇴, Toss unlink Webhook 계약을 정의한다.

## 빵도감에서 참고한 부분

기준 커밋 `e9a6abb73320e61869f91b14293e5da3d1fbe4f2`의 다음 구조를 참고한다.

- 사용자와 Session ID를 UUID로 사용
- `AuthController`에서 로그인, Refresh, 로그아웃, Webhook을 분리
- Refresh Token Rotation과 현재 Session revoke
- 사용자 요청 탈퇴에서 Toss `remove-by-user-key` 호출
- Webhook Basic 인증과 존재하지 않는 사용자에 대한 멱등 응답

꾹머니 차이:

- Toss Access/Refresh Token을 `app_user`에 저장하지 않는다.
- 탈퇴할 때 프론트가 `appLogin()`을 다시 호출하여 새 `authorizationCode`를 전달한다.
- 금액성 원장이 있으므로 사용자와 연관 데이터를 물리 삭제하지 않는다.

## 사용자와 Session 식별자

- 사용자 ID: `UUID app_user.id`
- Session ID: `UUID sid`
- JWT `sub`: 사용자 UUID 문자열
- Access Token `jti`: UUID 문자열
- Refresh Token `jti`: UUID 문자열

## 1. Toss 로그인

### Endpoint

```http
POST /api/v1/auth/toss/login
```

### Request

```json
{
  "authorizationCode": "one-time-code",
  "referrer": "DEFAULT",
  "onboarding": {
    "onboardingAttemptId": "uuid",
    "onboardingTapCount": 45
  }
}
```

### 처리 순서

1. 입력 검증
2. Toss `generate-token`
3. Toss `login-me`
4. `auth_identity(provider=TOSS, provider_user_id=String.valueOf(userKey))` 조회
5. 신규 사용자 DB 생성 또는 기존 활성 사용자 갱신
6. 온보딩 보상 멱등 처리
7. Redis Auth Session 저장
8. 꾹머니 Access/Refresh JWT 발급

### 보안 규칙

- `authorizationCode`, Toss Access Token, Toss Refresh Token, 전체 Toss 응답은 로그에 남기지 않는다.
- Toss Token은 요청 처리 범위를 벗어나 저장하지 않는다.
- `provider_user_id`는 식별정보로 취급하고 애플리케이션 로그에 출력하지 않는다.
- `WITHDRAWN` 사용자 Identity가 발견되면 신규 사용자로 만들지 않는다.

## 2. Refresh

```http
POST /api/v1/auth/refresh
```

- Refresh JWT 서명, `type=REFRESH`, `sub`, `sid`, `jti`, 만료를 검증한다.
- Redis Session의 사용자 UUID와 Token hash가 JWT와 일치해야 한다.
- Lua CAS로 현재 JTI와 hash를 새 값으로 교체한다.
- 동시에 들어온 동일 Refresh 요청은 `409 AUTH_REFRESH_CONFLICT`다.
- 이미 Rotation된 과거 Token 재사용은 `401 AUTH_REFRESH_REUSED`이며 해당 Session을 폐기한다.

## 3. 현재 Session 로그아웃

```http
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
```

Request Body는 선택 사항이다.

```json
{
  "refreshToken": "optional-refresh-jwt"
}
```

- Access Token의 `sid`를 기준으로 현재 Redis Session을 종료한다.
- Refresh Token이 전달되면 같은 사용자와 Session인지 추가 검증한다.
- 현재 Access `jti`를 남은 TTL만큼 denylist에 추가한다.
- 여러 번 호출되어 이미 Session이 없어도 보안상 성공으로 정규화할 수 있다.
- Toss 연결과 `auth_identity`는 유지한다.

## 4. 전체 로그아웃

```http
POST /api/v1/auth/logout-all
Authorization: Bearer {accessToken}
```

- 사용자 UUID의 모든 Redis Session을 제거한다.
- `auth:revoke:user:{userId}`에 revoke 시각을 기록한다.
- revoke 이전에 발급된 Access Token은 즉시 거절한다.
- 응답에는 `loggedOutAll`, `revokedSessionCount`를 구분한다.

## 5. 회원 탈퇴

### Endpoint

```http
POST /api/v1/members/me/withdrawal
Authorization: Bearer {accessToken}
```

`DELETE` 대신 `POST` Action Endpoint를 사용하는 이유는 새 Toss 인증정보를 Request Body로 안전하게 전달해야 하기 때문이다.

### Request

```json
{
  "authorizationCode": "fresh-one-time-code",
  "referrer": "DEFAULT"
}
```

### 처리 순서

1. 현재 Access Token의 사용자 UUID 확인
2. 새 Toss `authorizationCode`를 `generate-token`으로 교환
3. `login-me.userKey`와 현재 `auth_identity.provider_user_id` 일치 확인
4. Toss `remove-by-user-key` 호출
5. 로컬 사용자 탈퇴 처리
6. 모든 Redis Session과 Access Token 폐기

### 로컬 탈퇴 처리

- `app_user.status = WITHDRAWN`
- `app_user.withdrawn_at = now()`
- `nickname`, `nickname_normalized`, `profile_image_url` 익명화 또는 null 처리
- 포인트 잔액은 더 이상 사용할 수 없도록 계정 접근 차단
- `point_ledger`, `cashout_request`, `keycap_box_open` 등 회계·분쟁 근거는 보존
- `auth_identity`는 중복 보상 방지와 Webhook 멱등성을 위해 MVP에서는 유지하며 접근을 제한
- 동일 Toss 사용자의 자동 재가입은 허용하지 않고 `ACCOUNT_WITHDRAWN` 반환

외부 unlink 실패 시 로컬 상태를 `WITHDRAWN`으로 바꾸지 않는다. 외부 unlink 성공 뒤 로컬 처리 실패는 Webhook 재처리로 수렴한다.

## 6. Toss unlink Webhook

```http
POST /api/v1/auth/toss/unlink-webhook
Authorization: Basic {base64(secret)}
```

Request:

```json
{
  "userKey": "toss-user-key",
  "referrer": "UNLINK"
}
```

허용 이벤트:

```text
UNLINK
WITHDRAWAL_TERMS
WITHDRAWAL_TOSS
```

- Basic Secret을 상수 시간 비교 가능한 방식으로 검증한다.
- `(provider=TOSS, provider_user_id=userKey)`로 사용자를 찾는다.
- 사용자 미존재 또는 이미 `WITHDRAWN`이면 `200 processed=true`를 반환한다.
- 존재하면 회원 탈퇴와 같은 로컬 상태 전환, 개인정보 익명화, Session 전체 폐기를 수행한다.
- Webhook에서는 다시 Toss unlink API를 호출하지 않는다.

## 7. 오류 코드

| 코드 | HTTP | 의미 |
|---|---:|---|
| `TOSS_INVALID_GRANT` | 401 | Toss 인가 코드가 유효하지 않음 |
| `TOSS_SERVER_ERROR` | 502 | Toss API, mTLS, Timeout 오류 |
| `TOSS_USER_KEY_MISSING` | 502 | `login-me` 응답에 userKey 없음 |
| `TOSS_USER_MISMATCH` | 403 | 탈퇴 요청의 Toss 사용자가 현재 로그인 사용자와 다름 |
| `ACCOUNT_WITHDRAWN` | 403 | 탈퇴 계정의 자동 로그인 또는 자동 재가입 시도 |
| `AUTH_SESSION_NOT_FOUND` | 401 | 활성 Session 없음 |
| `AUTH_REFRESH_CONFLICT` | 409 | 동시 Refresh 충돌 |
| `AUTH_REFRESH_REUSED` | 401 | Rotation된 Refresh Token 재사용 |
| `TOSS_WEBHOOK_UNAUTHORIZED` | 401 | Webhook Secret 불일치 |
