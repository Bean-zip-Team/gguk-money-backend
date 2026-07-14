# 꾹머니 13개 테이블 데이터와 인프라 원칙

## 저장소 역할

### PostgreSQL

다음 데이터의 최종 원본이다.

- UUID 사용자와 Toss Identity
- 운영 정책 버전
- 키캡 마스터와 사용자 조각 상태
- 상자 잔액과 개봉 이력
- 탭 배치와 일일 집계
- 포인트 잔액과 원장
- 출금 상태와 외부 지급 결과
- 부스터 발동 이력

### Redis

13개 PostgreSQL 테이블과 별개로 꾹머니 인증 세션에 사용한다.

- `auth:refresh:{sessionId}`: Refresh Session hash
- `auth:user-sessions:{userId}`: 사용자 UUID별 활성 Session 목록
- `auth:deny:access:{jti}`: Access Token denylist
- `auth:revoke:user:{userId}`: 전체 로그아웃과 탈퇴 revoke 시각

Redis 장애 시 포인트, 상자, 출금의 PostgreSQL 데이터를 Redis 값으로 대체하지 않는다.

## UUID 사용자 전환

`feat/1-a-domain-persistence`의 기존 `AppUser`는 `Long id + UUID publicId`다. MVP에서는 아래처럼 변경한다.

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
@Column(name = "id", nullable = false, updatable = false)
private UUID id;
```

- `publicId` 필드는 제거한다.
- 모든 Entity의 `user_id` DB 타입은 UUID다.
- `JpaRepository<AppUser, UUID>`를 사용한다.
- JWT Provider의 `userPublicId String` 인자는 `UUID userId` 또는 검증된 UUID 문자열로 변경한다.
- Redis Key와 로그 필드 명칭은 `userId`로 통일한다.

## Toss Token 저장 정책

빵도감은 사용자 요청 탈퇴를 위해 Toss Access/Refresh Token을 사용자 테이블에 저장하는 구현을 보유한다. 꾹머니는 다음 이유로 저장하지 않는다.

- 현재 13개 테이블에 Provider Token 암호화와 Rotation 저장 구조가 없음
- 토큰 유출 시 영향 범위가 큼
- 탈퇴 화면에서 `appLogin()`을 다시 호출해 새 authorizationCode를 받을 수 있음

따라서 회원 탈퇴 Request가 새 `authorizationCode`, `referrer`를 전달하고 서버가 즉시 `generate-token → login-me → remove-by-user-key`를 수행한다.

## Migration 기준

현재 저장소에는 Flyway 또는 Liquibase 적용 체계와 `src/main/resources/db/migration` 파일이 없다. 현재 테스트 환경은 Hibernate `create-drop`으로 Entity 정합성을 검증한다.

- 과거 문서의 `V1010__create_a_domain_schema.sql`, `V1020__drop_auth_session_log.sql` 기준 설명은 현재 저장소 소스에는 존재하지 않는다.
- `app_user.id`는 UUID PK이며 `app_user.public_id`는 없다.
- 나머지 도메인 테이블은 BIGINT PK와 `public_id UUID UNIQUE`를 사용한다.
- `keycap_box_open.request_hash`, `completed`, `opened_at`, `idempotency_key NOT NULL`과 관련 unique/index는 Entity에 정합화됐다.
- 공유/개발 DB에는 `(user_id, idempotency_key)` unique, `ad_reward_id` partial unique, `opened_at` 조회 index, 음수 방지 CHECK 등 실제 제약 적용 여부를 merge 전 확인해야 한다.
- 키캡 상자 개봉 이력 조회는 추가 Entity 컬럼 없이 `keycap_box_open.user_id`, `opened_at`, 내부 PK 정렬과 `keycap_id` join을 사용한다.

## 트랜잭션 경계

### 로그인 신규 사용자 생성

현재 구현 기준 핵심 트랜잭션:

```text
app_user
+ auth_identity
+ point_account
+ keycap_box_account
+ user_tap_progress
```

MVP 권장안의 신규 사용자 온보딩 정산을 로그인에 포함할 경우 같은 PostgreSQL 트랜잭션에 추가할 항목:

```text
+ point_ledger 온보딩 보상
+ user_keycap 온보딩 완성 키캡
```

현재 구현은 JWT 생성과 Redis Session 저장을 `@Transactional` 로그인 메서드 안에서 수행한다. Redis 실패 시 성공 응답을 반환하지 않지만, DB 커밋 뒤 Redis를 저장하는 구조는 아니다. Redis Session 저장 이후 트랜잭션 커밋 또는 응답 생성이 실패할 때의 정리 전략은 코드 검토 항목이다.

> 구현 전 계약 확정 필요: 현재 `TossLoginRequest`에는 `onboardingAttemptId` 필드가 없다. MVP 권장안은 회원가입 전 온보딩 키캡 상자 개봉 결과를 서버 저장 기록에 연결하고, Toss 로그인 요청에 `onboardingAttemptId`만 전달해 신규 사용자 생성 트랜잭션에서 포인트 원장과 고정 키캡 지급을 함께 처리하는 방식이다. 로그인 후 별도 Claim API 권장안은 사용하지 않는다.

### 탭 배치

```text
tap_batch
+ user_tap_daily
+ point_account
+ point_ledger
+ keycap_box_account
+ user_tap_progress
```

상자 상태 조회의 잔액과 무료권 수량은 `keycap_box_account`를 읽고, 상자 진행도는 `user_tap_progress`를 읽어 조합한다. 조회 API는 두 Entity를 생성하거나 저장하지 않는다.

### 상자 개봉

```text
keycap_box_account
+ user_keycap
+ keycap_box_open
```

상자 개봉은 구현됐으며 PostgreSQL `keycap_box_open`을 멱등성 Source of Truth로 사용한다.

- 모든 성공 개봉은 `keycap_box_account.box_balance`를 1 차감한다.
- `FREE`는 추가로 `keycap_box_account.free_open_ticket_count`를 1 차감한다.
- `ADVERTISEMENT`는 광고 검증 Service가 구현되기 전에는 미지원 오류로 처리하고 자원을 차감하지 않는다. 검증 구현 후에는 `ad_reward_id`를 필수로 받고 전역 중복 사용을 금지한다.
- 보상 후보는 `keycap.active=true` 키캡 중 현재 사용자가 아직 완성하지 않은 키캡이다. `UserKeycap`이 없거나 `status=IN_PROGRESS`이면 후보에 포함하고, `status=COMPLETED`인 키캡과 온보딩으로 완성 지급된 키캡은 제외한다.
- MVP에서는 후보 중 하나를 균등 랜덤으로 선택한다. 시즌 필터와 가중치는 저장 원본이 없어 후속 결정 사항이다.
- 후보가 없으면 `KEYCAP_REWARD_NOT_AVAILABLE`을 반환하고 `box_balance`, `free_open_ticket_count`, `ad_reward_id`를 소비하지 않으며 `keycap_box_open`도 생성하지 않는다.
- 기본 지급 조각 수는 1개이며, `user_keycap.shard_count`는 `required_shard_count`를 초과 저장하지 않는다.
- 현재 부스터는 포인트 적립 전용이므로 상자 개봉 조각 수에 적용하지 않는다.
- 현재 구현은 `TransactionTemplate` 경계 안에서 상자 계정을 비관적 쓰기 잠금으로 조회하고, 잠금 후 같은 멱등키를 재조회한다.
- `DataIntegrityViolationException`이 발생하면 롤백된 뒤 기존 row를 재조회해 같은 요청이면 기존 응답으로 복구한다.

### 키캡 장착

```text
user_keycap
```

현재 구현은 `KeycapService.equipKeycap(...)`의 단일 `@Transactional` 경계 안에서 처리한다. 현재 인증 사용자 UUID와 `Keycap.publicId`로 사용자 보유 키캡을 조회하고, 미완성 키캡은 차단한다. 같은 사용자의 기존 장착 키캡이 있으면 `equipped=false`로 해제한 뒤 요청 키캡을 `equipped=true`로 변경한다.

### 출금 요청

```text
point_account
+ point_ledger(CASHOUT)
+ cashout_request
```

### 회원 탈퇴

현재 구현은 `@Transactional` 메서드 안에서 외부 Toss unlink 호출 뒤 다음 로컬 처리를 수행한다.

```text
app_user.status = WITHDRAWN
app_user.withdrawn_at = now
개인정보 익명화
금액성 테이블 접근 차단
```

그 뒤 Redis 전체 Session을 폐기한다. 외부 unlink 성공 뒤 로컬 처리 실패는 Toss Webhook이 같은 처리를 멱등하게 수행해 수렴시키는 것을 목표로 하지만, 사용자 요청 탈퇴와 Webhook 동시 처리 검증은 추가로 필요하다.

## 동시성

- `point_account`, `keycap_box_account`, `user_tap_daily`, `cashout_request`는 `@Version` 또는 명시적 행 잠금을 사용한다.
- 키캡 장착은 같은 사용자의 `user_keycap` 행을 비관적 잠금으로 조회한 뒤 기존 장착 해제와 신규 장착을 같은 트랜잭션에서 수행한다.
- 로그인 Identity 생성 경쟁은 `(provider, provider_user_id)` Unique로 해결한다.
- 목표 온보딩 계약은 `onboarding_reward_claimed` 조건부 갱신과 `point_ledger` 멱등 제약을 함께 사용한다. 현재 로그인 DTO에 `onboardingAttemptId` 필드는 없다. 같은 `onboardingAttemptId`는 한 사용자에게 한 번만 귀속하고, claimed된 attempt는 다른 신규 사용자가 재사용할 수 없어야 한다. 동일 Toss 사용자의 동시 신규 가입 요청에서도 온보딩 포인트와 고정 키캡은 한 번만 지급해야 한다.
- Refresh Rotation은 Redis Lua CAS를 사용한다.
- logout-all은 사용자 revoke marker를 저장한다. 현재 Session 저장 경로가 revoke marker를 확인해 신규 Session 저장 경쟁을 차단하지는 않으므로 코드 검토가 필요하다.

## 멱등성 충돌 처리

MVP에서는 공통 멱등성 테이블, 공통 AOP, Redis 멱등 캐시, 공통 Response 저장 프레임워크를 만들지 않는다. 각 도메인 테이블의 Unique 제약을 Source of Truth로 사용하고, Redis를 포인트, 상자, 출금의 멱등성 원본으로 사용하지 않는다.

- 상자 개봉은 `(user_id, idempotency_key)`로 같은 사용자 요청의 중복 개봉을 막는다.
- 출금 요청은 `(user_id, idempotency_key)`로 같은 사용자 요청의 중복 출금을 막는다.
- 탭 배치는 `(user_id, tap_session_id, sequence)`로 같은 배치 재전송을 식별한다.
- 부스터 활성화는 `ad_reward_id`와 `(user_id, grant_date, daily_sequence)`로 중복 사용을 막는다.
- 같은 멱등 기준과 같은 Request Body 또는 같은 `request_hash`는 상태를 다시 변경하지 않고 기존 결과를 반환한다.
- 같은 멱등 기준에 다른 Request Body 또는 다른 `request_hash`가 들어오면 계약상 `409 IDEMPOTENCY_KEY_REUSED`로 처리한다.
- `IDEMPOTENCY_KEY_REUSED`는 공통 `ErrorCode`에 구현되어 있으며 `409`로 반환한다.

상자 개봉의 `request_hash`는 `openMethod`, `adRewardId`를 정규화한 값의 SHA-256 Base64URL이다. 같은 `(user_id, idempotency_key)`와 같은 `request_hash`는 기존 결과를 반환하고, 다른 `request_hash`는 `409 IDEMPOTENCY_KEY_REUSED`로 처리한다. 동시 동일 요청으로 Unique 충돌이 발생하면 기존 row를 재조회해 같은 응답으로 복구한다.

상자 개봉 이력 조회는 상태 변경 API가 아니므로 `Idempotency-Key` Header를 사용하지 않는다. cursor는 `opened_at DESC, id DESC` 위치를 Base64URL opaque 문자열로 인코딩하며 전체 개수 조회 없이 `size + 1` 방식으로 `hasNext`를 판단한다.

## 코드 검토 필요 항목

| 우선순위 | 항목 | 문제 | 권장 방향 |
|---|---|---|---|
| HIGH | 로그인 Redis Session 정리 | Redis Session 저장 이후 JWT 응답 생성 또는 DB 커밋 실패 시 저장된 Session 정리 여부가 명확하지 않다. | after-commit 저장, 보상 삭제, 또는 트랜잭션 이벤트 기반 발급 흐름 검토 |
| HIGH | logout-all과 신규 Session 경쟁 | `auth:revoke:user:{userId}`는 저장되지만 `save()`가 marker를 확인하지 않는다. | Session 저장 Lua/script 또는 서비스 레벨에서 revoke 시각 확인 |
| HIGH | 탈퇴 외부 호출과 트랜잭션 | Toss unlink 외부 호출이 DB 트랜잭션 안에서 수행된다. | 외부 호출과 로컬 상태 변경의 실패 복구 정책 명확화 |
| HIGH | 탈퇴와 Webhook 동시 처리 | 사용자 요청 탈퇴와 Webhook이 동시에 들어올 때 개인정보 익명화와 Session 폐기가 멱등하게 수렴하는지 확인이 필요하다. | 동시성 테스트와 상태 전환 idempotent update 추가 |
| HIGH | 출금 중복 지급 복구 | `cashout_request` schema만 있고 실제 지급 서비스와 복구 흐름이 없다. | provider transfer idempotency와 장애 복구 설계 구현 |
| MEDIUM | 원장 사용자 정합성 | `point_ledger.user_id`와 `point_account_id`가 같은 사용자임을 DB 제약으로 보장하지 않는다. | 서비스 검증 또는 복합 FK/제약 검토 |
| MEDIUM | 활성 부스터 제한 | `booster_grant`에 사용자별 활성 부스터 1개 제한이 없다. | Partial Unique 또는 서비스 잠금 검토 |
| MEDIUM | 키캡 장착 상태 | `user_keycap.equipped=true`가 `COMPLETED`일 때만 가능하다는 DB 제약이 없다. | CHECK/서비스 검증 검토 |
| MEDIUM | 키캡 장착 유일성 | 문서 명세에는 `UNIQUE (user_id) WHERE equipped=true`가 있으나 현재 작업 환경에서는 실제 공유/개발 DB 제약 존재 여부를 확인하지 못했다. | merge 전 실제 DB partial unique index 확인 |
| MEDIUM | app_config 공개 범위 | Repository에는 `config_key/effective_at` 기준 선택 메서드가 있고 앱 설정 조회 API는 `TapPolicyConfig`의 공개 typed DTO만 반환한다. 원본 JSON과 내부 검증값은 외부에 노출하지 않는다. | 신규 운영 정책 키 추가 시 공개 DTO 반영 여부를 별도 검토 |

## 민감정보 로그 정책

기록 금지:

- Toss authorizationCode
- Toss Access Token과 Refresh Token
- 꾹머니 Access Token과 Refresh Token 원문
- Toss userKey 원문
- Webhook Basic Secret
- 전체 Toss Request와 Response Body

허용 로그:

```text
action
result
requestId
userId(UUID)
sessionId(UUID)
status
errorCode
exceptionType
durationMs
```

## 탈퇴 데이터 보존

- `app_user`는 `WITHDRAWN` 상태로 유지한다.
- 개인정보 컬럼은 익명화한다.
- `auth_identity`는 MVP 보관 정책 동안 접근 제한 상태로 유지한다.
- `point_ledger`, `cashout_request`, `keycap_box_open`은 금액·분쟁·부정 사용 근거로 보존한다.
- 구체적인 보관 기간과 완전 삭제 Batch는 법적 정책 확정 뒤 별도 문서와 Migration으로 추가한다.
