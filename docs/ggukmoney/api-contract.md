# 꾹머니 API 계약

## 공통 API 규칙

| 항목 | 값 |
|---|---|
| Production Base URL | `https://api.ggukmoney.app/api/v1` |
| Local path | `/api/v1` |
| 인증 | `Authorization: Bearer {accessToken}` |
| Content-Type | `application/json` |
| 시간 | ISO-8601 UTC, 클라이언트 표시는 KST |

성공:

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "01J..."
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "요청을 처리할 수 없습니다."
  },
  "traceId": "01J..."
}
```

페이지네이션:

- 변경 이력 데이터: `cursor` + `size`
- 마스터 데이터: `page` + `size`
- Cursor 대상: 키캡 상자 이력, 포인트 원장, 출금 이력, 과거 랭킹 결과, 보상 기록
- Page 대상: 지역 목록, 키캡 카탈로그, 관리자 정적 목록

## 인증 토큰 계약

Access Token:

```json
{
  "sub": "user-public-uuid",
  "sid": "session-uuid",
  "jti": "access-token-uuid",
  "type": "ACCESS",
  "iat": 1782961200,
  "exp": 1782963000
}
```

Refresh Token:

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

Access JWT 원문은 서버에 저장하지 않는다. Refresh JWT 원문은 클라이언트가 보관하고 서버 Redis에는 hash와 활성 Session만 저장한다.

## A 담당 API 빠른 보기

### 회원 및 인증

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/guests` | 게스트 최초 생성 또는 같은 기기 게스트 복구 |
| `POST` | `/auth/toss/login` | Toss 로그인, 게스트 회원 전환, 기존 회원 병합 |
| `POST` | `/auth/refresh` | Access/Refresh token rotation |
| `POST` | `/auth/logout` | 현재 기기 로그아웃 |
| `POST` | `/auth/logout-all` | 전체 기기 로그아웃 |
| `GET` | `/members/me` | 내 정보 조회 |
| `PATCH` | `/members/me` | 내 정보 수정 |
| `DELETE` | `/members/me` | 회원 탈퇴 |
| `GET` | `/members/me/merge-status` | 게스트 병합 상태 조회 |
| `POST` | `/members/me/merge-retry` | 병합 재시도 |

### 키캡 및 상자

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/keycaps` | 키캡 카탈로그 조회 |
| `GET` | `/keycaps/me` | 내 키캡 보유 현황 |
| `PUT` | `/keycaps/{keycapId}/equip` | 키캡 장착 |
| `GET` | `/keycap-boxes/status` | 상자 보유량과 개봉 조건 |
| `POST` | `/keycap-boxes/open` | 상자 1개 개봉 |
| `GET` | `/keycap-boxes/history` | 상자 원장/개봉 이력 |

### 지역 및 랭킹

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/regions` | 지역 목록 |
| `POST` | `/regions/detect` | 좌표 기반 지역 판별 |
| `GET` | `/members/me/region` | 내 지역 조회 |
| `PUT` | `/members/me/region` | 첫 지역 설정 또는 월 1회 변경 예약 |
| `POST` | `/rankings/participations` | 현재 시즌 참여 |
| `GET` | `/rankings/current` | 현재 랭킹 조회 |
| `GET` | `/rankings/me` | 내 현재 순위 조회 |
| `GET` | `/rankings/results/latest` | 최신 주간 결과 |
| `GET` | `/rankings/results` | 과거 결과 목록 |

### 알림, 기록, 설정

| Method | Path | 설명 |
|---|---|---|
| `PUT` | `/push-devices/current` | 현재 기기 Push Token 등록 |
| `DELETE` | `/push-devices/current` | 현재 기기 Push Token 비활성화 |
| `GET` | `/notification-preferences` | 알림 설정 조회 |
| `PATCH` | `/notification-preferences` | 알림 설정 변경 |
| `POST` | `/notifications/{notificationId}/opened` | 알림 열람 처리 |
| `GET` | `/records/summary` | 기록 요약 |
| `GET` | `/records/daily` | 일자별 기록 |
| `GET` | `/records/rewards` | 보상 기록 |
| `GET` | `/app-config` | A 소유 앱 정책 |
| `GET` | `/legal-documents/current` | 현재 법적 문서 |

## A API 상세 계약

### POST /guests

최초 실행 또는 게스트 세션 복구 API다.

| 항목 | 내용 |
|---|---|
| 인증 | 불필요 |
| 성공 | 새 게스트 `201 Created`, 기존 게스트 복구 `200 OK` |
| 멱등 기준 | `deviceKeyHash + GUEST_OWNER` |

Request:

```json
{
  "deviceKey": "client-generated-stable-device-key",
  "platform": "IOS",
  "appVersion": "1.0.0"
}
```

Response data:

```json
{
  "userId": "user-public-uuid",
  "accountType": "GUEST",
  "sessionId": "session-uuid",
  "accessToken": "jwt",
  "refreshToken": "jwt",
  "newGuest": true
}
```

정책:

- 같은 기기에 ACTIVE GUEST `GUEST_OWNER`가 있으면 기존 게스트를 재사용한다.
- 기존 토큰 원문을 반환하지 않는다.
- 같은 guest와 같은 device이고 기존 Redis Session이 정상이면 기존 `sessionId`를 유지한 Refresh Token Rotation으로 token pair를 교체할 수 있다.
- Session 만료, Redis Session 유실, Refresh 재사용 감지, 이상 기기이면 기존 Session을 폐기하고 새 `sessionId`를 생성한다.
- 신규 Toss 회원 승격은 guest Session 폐기 후 MEMBER Session을 생성한다.
- 기존 Toss 회원 병합은 source guest Session 전체 폐기 후 target MEMBER Session을 생성한다.
- 폐기 또는 교체 방식은 서버가 결정하며 클라이언트가 선택하지 않는다.
- `auth_session_log`에 `GUEST_CREATED` 또는 `GUEST_RECOVERED`를 기록한다.

### POST /auth/toss/login

Toss 로그인으로 게스트를 회원으로 승격하거나 기존 회원에 게스트 데이터를 병합한다.

| 항목 | 내용 |
|---|---|
| 인증 | 게스트 또는 회원 access token 권장 |
| 실패 코드 | `TOSS_AUTH_FAILED`, `USER_MERGE_CONFLICT`, `AUTH_SESSION_EXPIRED` |

Request:

```json
{
  "authorizationCode": "toss-code",
  "redirectUri": "ggukmoney://auth/toss"
}
```

Response data:

```json
{
  "userId": "member-public-uuid",
  "accountType": "MEMBER",
  "mergeRequired": false,
  "mergeStatus": "COMPLETED",
  "sessionId": "session-uuid",
  "accessToken": "jwt",
  "refreshToken": "jwt"
}
```

정책:

- 신규 Toss 사용자면 현재 게스트를 `MEMBER`로 승격한다.
- 신규 승격 시 `GUEST_OWNER`를 `MEMBER_DEVICE`로 변경한다.
- 기존 Toss 회원이면 현재 게스트의 키캡, 상자, 기록성 데이터를 target 회원으로 병합한다.
- source 게스트의 인증 세션은 모두 폐기한다.
- target 회원 기준 새 Redis 인증 세션을 생성한다.
- 게스트 랭킹 점수는 기존 회원의 진행 중 랭킹 점수에 합산하지 않는다.

### POST /auth/refresh

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

### POST /auth/logout

현재 기기 로그아웃이다. Access Token jti를 denylist에 저장하고 Redis Refresh Session을 삭제한다.

Response data:

```json
{
  "loggedOut": true
}
```

### POST /auth/logout-all

사용자의 모든 Redis Refresh Session을 폐기한다. 현재 Access Token jti는 denylist에 저장한다.

Response data:

```json
{
  "loggedOutAll": true,
  "revokedSessionCount": 3
}
```

### GET /members/me

Response data:

```json
{
  "userId": "user-public-uuid",
  "accountType": "MEMBER",
  "status": "ACTIVE",
  "nickname": "꾹꾹이",
  "region": {
    "regionId": "region-public-uuid",
    "name": "서울 강남구"
  }
}
```

### POST /keycap-boxes/open

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

### GET /rankings/current

Redis로 후보를 조회하고 PostgreSQL 기준으로 동점 보정한다.

Query:

```text
scope=REGION|NATIONAL
regionId=region-public-uuid
limit=50
```

Response data:

```json
{
  "seasonId": "season-public-uuid",
  "scope": "REGION",
  "items": [
    {
      "rank": 1,
      "userId": "user-public-uuid",
      "nickname": "꾹꾹이",
      "score": 1234,
      "reachedAt": "2026-07-02T03:00:00Z"
    }
  ],
  "coldStart": false
}
```

### GET /rankings/results/latest

최신 주간 랭킹 결과를 조회한다. 결과 화면 버튼은 보상 지급 버튼이 아니라 확인 버튼이다.

### GET /app-config

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

### GET /legal-documents/current

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

## A/B Port와 Event 계약

| 방향 | 계약 | 입력 요약 | 멱등 키 |
|---|---|---|---|
| B -> A | `ValidatedTapApplyUseCase` | `userId`, `tapBatchId`, `boxProgressDelta`, `rankingTapDelta`, `occurredAt` | `tapBatchId` |
| B -> A | `KeycapBoxGrantUseCase` | `userId`, `boxCount`, `reason`, `referenceId` | `referenceId` |
| B -> A | `RecordEventIngestUseCase` | `eventId`, `eventType`, `userId`, `payload`, `occurredAt` | `eventId` |
| A -> B | `AdvertisementVerificationPort` | `adViewId`, `userId`, `placement` | `adViewId` |
| A -> B | `UserWithdrawalGuardPort` | `userId` | 없음 |

광고 상자 흐름은 `B 광고 완료 -> A가 완료된 ad_view 로컬 조회 -> A가 기존 보유 상자 1개 개봉`이다.

## B 담당 API 인벤토리

아래 API는 B 담당 초안이다. 상세 Request/Response 계약은 B 담당자 확정이 필요하다.

| 상태 | Method | Path | 설명 |
|---|---|---|---|
| B 담당 초안 | `GET` | `/home` | 홈 요약 |
| B 담당 초안 | `POST` | `/taps/batches` | 탭 배치 검증 |
| B 담당 초안 | `GET` | `/points/me` | 포인트 잔액 |
| B 담당 초안 | `GET` | `/points/ledger` | 포인트 원장 |
| B 담당 초안 | `POST` | `/cashouts` | Toss 포인트 출금 요청 |
| B 담당 초안 | `GET` | `/cashouts` | 출금 이력 |
| B 담당 초안 | `GET` | `/ads/placements` | 광고 슬롯 |
| B 담당 초안 | `POST` | `/ads/views/start` | 광고 시청 시작 |
| B 담당 초안 | `POST` | `/ads/views/{adViewId}/complete` | 광고 완료 검증 |
| B 담당 초안 | `POST` | `/boosters/activate` | 부스터 활성화 |
| B 담당 초안 | `GET` | `/invites/me` | 내 초대 코드 |
| B 담당 초안 | `POST` | `/invites/{code}/accept` | 초대 수락 |

초대 수락 응답 정책:

```json
{
  "accepted": true,
  "status": "PENDING_FIRST_TAP",
  "rewardGranted": false
}
```

첫 유효 탭 이후 B가 `InvitationQualified` 이벤트를 만들고, inviter/invitee에게 `KeycapBoxGrantUseCase`로 상자를 지급한다.

## 주요 에러 코드

| 코드 | 설명 |
|---|---|
| `AUTH_REQUIRED` | 인증 필요 |
| `AUTH_INVALID_TOKEN` | 토큰 형식/서명 오류 |
| `AUTH_ACCESS_EXPIRED` | Access Token 만료 |
| `AUTH_REFRESH_EXPIRED` | Refresh Token 만료 |
| `AUTH_SESSION_NOT_FOUND` | Redis Refresh Session 없음 |
| `AUTH_REFRESH_CONFLICT` | 동일 Refresh Token 동시 요청 충돌 |
| `AUTH_REFRESH_REUSED` | 폐기된 Refresh Token 재사용 |
| `AUTH_REDIS_UNAVAILABLE` | 인증 Redis 장애 |
| `KEYCAP_BOX_INSUFFICIENT` | 보유 상자 부족 |
| `KEYCAP_FREE_OPEN_NOT_READY` | 무료 개봉 쿨다운 |
| `KEYCAP_AD_LIMIT_EXCEEDED` | 광고 개봉 일 한도 초과 |
| `KEYCAP_AD_NOT_VERIFIED` | 광고 완료 검증 실패 |
| `REGION_CHANGE_LIMIT_EXCEEDED` | 지역 변경 월 한도 초과 |
| `RANKING_MEMBER_REQUIRED` | 랭킹 참여 회원 필요 |
| `RANKING_REGION_REQUIRED` | 랭킹 참여 지역 필요 |
| `PUSH_TOKEN_INVALID` | Push Token 오류 |
| `EXTERNAL_SERVICE_ERROR` | Toss, 광고, 푸시 등 외부 연동 오류 |

## 향후 API

- 보유 상자 일괄 개봉 API: MVP 제외, 향후 확장 후보
- 운영자용 랭킹 정산 재시도 API
- 운영자용 app_config append API
