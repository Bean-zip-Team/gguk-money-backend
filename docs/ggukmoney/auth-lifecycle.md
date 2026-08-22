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
POST /api/auth/toss/login
```

### Request

현재 구현 DTO 기준 Request:

```json
{
  "authorizationCode": "one-time-code",
  "referrer": "DEFAULT"
}
```

온보딩 보상 귀속 계약:

```json
{
  "authorizationCode": "one-time-code",
  "referrer": "DEFAULT",
  "onboardingAttemptId": "uuid"
}
```

현재 `TossLoginRequest`에는 `onboardingAttemptId` 선택 필드가 있으며, 별도 로그인 후 Claim API 없이 신규 Toss 가입 트랜잭션에서 온보딩 보상을 귀속한다.

### 처리 순서

1. 입력 검증
2. Toss `generate-token`
3. Toss `login-me`
4. `auth_identity(provider=TOSS, provider_user_id=String.valueOf(userKey))` 조회
5. 신규 사용자 DB 생성, 기존 활성 사용자 갱신 또는 기존 탈퇴 사용자 재활성화
6. 신규 사용자이고 `onboardingAttemptId`가 있으면 온보딩 보상 멱등 처리
7. Toss `login-me.agreedTerms`의 약관 태그를 동의 이력으로 기록
8. 꾹머니 Access/Refresh JWT 발급
9. Redis Auth Session 저장

### 보안 규칙

- `authorizationCode`, Toss Access Token, Toss Refresh Token, 전체 Toss 응답은 로그에 남기지 않는다.
- Toss Token은 요청 처리 범위를 벗어나 저장하지 않는다.
- `provider_user_id`는 식별정보로 취급하고 애플리케이션 로그에 출력하지 않는다.
- `WITHDRAWN` 사용자 Identity가 발견되면 신규 사용자로 만들지 않고, Toss 약관 재동의 뒤 정상 OAuth 로그인이 완료된 경우 기존 계정을 재활성화한다.
- Toss 로그인 약관 이력(`toss_login_consent_history`)에는 `agreedTerms`의 태그, 동의/철회 상태, 발생 시각, 이벤트 출처(`event_source`)만 저장한다. 약관 본문, Toss 토큰, 전체 응답, 추가 개인정보는 저장하지 않는다.
- 로그인 동의 이력의 `event_source`는 `LOGIN`이다. 동일 로그인 응답에 중복 태그가 있어도 한 번만 기록한다.
- 이미 활성 상태인 태그는 일반 재로그인에서 중복 `AGREED` 이력을 만들지 않는다. 태그 변경 여부가 약관 본문의 버전까지 보장하지 않으므로, 약관 개정 시 Console의 약관 태그도 버전별로 관리한다.

## 2. Refresh

```http
POST /api/auth/refresh
```

- Refresh Token은 Header가 아니라 Request Body의 `refreshToken`으로 전달한다.

```json
{
  "refreshToken": "refresh-jwt"
}
```

- Refresh JWT 서명, `type=REFRESH`, `sub`, `sid`, `jti`, 만료를 검증한다.
- Redis Session의 사용자 UUID와 Token hash가 JWT와 일치해야 한다.
- Lua CAS로 현재 JTI와 hash를 새 값으로 교체한다.
- 동시에 들어온 동일 Refresh 요청은 `409 AUTH_REFRESH_CONFLICT`다.
- 이미 Rotation된 과거 Token 재사용은 `401 AUTH_REFRESH_REUSED`이며 해당 Session을 폐기한다.

## 3. 현재 Session 로그아웃

```http
POST /api/auth/logout
Authorization: Bearer {accessToken}
```

Request Body는 선택 사항이다.

```json
{
  "refreshToken": "optional-refresh-jwt"
}
```

Body 없이도 호출할 수 있다.

- Access Token의 `sid`를 기준으로 현재 Redis Session을 종료한다.
- Refresh Token이 전달되면 같은 사용자와 Session인지 추가 검증한다.
- 현재 Access `jti`를 남은 TTL만큼 denylist에 추가한다.
- 현재 구현은 Redis Session이 없으면 `AUTH_SESSION_NOT_FOUND`를 반환한다. 이미 종료된 Session 로그아웃을 성공으로 정규화할지는 별도 결정이 필요하다.
- Toss 연결과 `auth_identity`는 유지한다.
- 응답은 `loggedOut`을 포함한다.

## 4. 전체 로그아웃

```http
POST /api/auth/logout-all
Authorization: Bearer {accessToken}
```

- 사용자 UUID의 모든 Redis Session을 제거한다.
- `auth:revoke:user:{userId}`에 revoke 시각을 기록한다.
- revoke 이전에 발급된 Access Token은 즉시 거절한다.
- 응답에는 `loggedOutAll`, `revokedSessionCount`를 구분한다.

## 5. 회원 탈퇴

### Endpoint

```http
POST /api/members/me/withdrawal
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
5. 활성 Toss 로그인 약관 태그의 `WITHDRAWN + DIRECT_WITHDRAWAL` 이력 기록
6. 로컬 사용자 탈퇴 처리
7. 모든 Redis Session과 Access Token 폐기

### 로컬 탈퇴 처리

- `app_user.status = WITHDRAWN`
- `app_user.withdrawn_at = now()`
- `nickname`, `nickname_normalized`, `profile_image_url` 익명화 또는 null 처리
- 포인트 잔액은 더 이상 사용할 수 없도록 계정 접근 차단
- `point_ledger`, `cashout_request`, `keycap_box_open` 등 회계·분쟁 근거는 보존
- `auth_identity`는 중복 보상 방지와 Webhook 멱등성을 위해 MVP에서는 유지하며 접근을 제한
- Toss 약관 철회로 `WITHDRAWN` 된 사용자가 다시 약관에 동의하고 정상 OAuth 로그인을 완료하면, 동일 `auth_identity`와 `app_user.id`를 유지한 채 `ACTIVE`로 재활성화한다.
- 재활성화 시 `withdrawn_at`을 초기화하고 Toss 로그인 응답의 nickname, nickname_normalized, profile image, last login 정보를 복구한다. 기존 포인트, 키캡, 원장, 랭킹 데이터는 초기화하거나 새로 만들지 않으며 신규 온보딩 보상도 지급하지 않는다.

외부 unlink 실패 시 로컬 상태를 `WITHDRAWN`으로 바꾸지 않는다. 외부 unlink 성공 뒤 로컬 처리 실패는 Webhook 재처리로 수렴하는 것을 목표로 한다. 사용자 요청 탈퇴와 Webhook이 동시에 들어오는 경우에도 상태 변경과 개인정보 익명화가 멱등하게 수렴하는지 추가 검증이 필요하다.

## 6. Toss unlink Webhook

```http
POST /api/auth/toss/unlink-webhook
Authorization: Basic {base64(secret)}
```

Request:

```json
{
  "userKey": "toss-user-key",
  "referrer": "UNLINK"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "processed": true,
    "referrer": "UNLINK"
  }
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
- 존재하면 회원 탈퇴와 같은 로컬 상태 전환, 개인정보 익명화, Session 전체 폐기를 수행한다. 현재 정책에서는 `UNLINK`, `WITHDRAWAL_TERMS`, `WITHDRAWAL_TOSS` 모두 이 경로를 사용한다.
- 존재하면 현재 활성 Toss 로그인 약관 태그에 `WITHDRAWN` 이력을 추가하고 `event_source`에는 수신한 원본 `referrer` 값(`UNLINK`, `WITHDRAWAL_TERMS`, `WITHDRAWAL_TOSS`)을 저장한다. 이미 철회된 태그에는 중복 이력을 만들지 않는다.
- Webhook에서는 다시 Toss unlink API를 호출하지 않는다.
- `WITHDRAWAL_TERMS`는 Toss가 안내한 “토스 로그인 > 동의 철회하기” 경로의 콜백 이벤트다. [Toss TechChat 안내](https://techchat-apps-in-toss.toss.im/t/withdrawal-terms/736)

### 운영 확인

Webhook 처리 결과는 Toss 식별정보 없이 다음 구조화 로그로 확인한다.

```bash
sudo journalctl -fu clickmoney@8081.service   # 활성 포트는 ./switch.sh --status 로 확인 \
  | grep --line-buffered 'TOSS_UNLINK_WEBHOOK_PROCESSED'
```

`WITHDRAWAL_TOSS`만 확인하려면 `eventType=WITHDRAWAL_TOSS`로 추가 필터링한다. 동의 이력은 아래처럼 사용자 ID 기준으로 조회한다.

```sql
SELECT status, event_source, term_tag, occurred_at
FROM toss_login_consent_history
WHERE user_id = '<user-id>'
ORDER BY occurred_at, id;
```

## 7. 오류 코드

| 코드 | HTTP | 의미 |
|---|---:|---|
| `TOSS_INVALID_GRANT` | 401 | Toss 인가 코드가 유효하지 않음 |
| `TOSS_SERVER_ERROR` | 502 | Toss API, mTLS, Timeout 오류 |
| `TOSS_USER_KEY_MISSING` | 502 | `login-me` 응답에 userKey 없음 |
| `TOSS_USER_MISMATCH` | 403 | 탈퇴 요청의 Toss 사용자가 현재 로그인 사용자와 다름 |
| `ACCOUNT_WITHDRAWN` | 403 | 탈퇴 상태에서 보호된 회원 API에 접근 |
| `AUTH_SESSION_NOT_FOUND` | 401 | 활성 Session 없음 |
| `AUTH_REFRESH_CONFLICT` | 409 | 동시 Refresh 충돌 |
| `AUTH_REFRESH_REUSED` | 401 | Rotation된 Refresh Token 재사용 |
| `TOSS_WEBHOOK_UNAUTHORIZED` | 401 | Webhook Secret 불일치 |
