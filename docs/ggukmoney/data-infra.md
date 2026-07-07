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
- 짧은 TTL의 분산 락 또는 중복 방지 값

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

## Migration 계획

현재 확장 A 도메인을 생성하는 `V1010__create_a_domain_schema.sql`은 13개 테이블과 UUID 사용자 명세에 맞지 않는다.

권장 방식:

1. 브랜치 Migration이 공유 DB에 적용되지 않았다면 `V1010`을 13개 테이블 기준으로 교체한다.
2. `app_user.id UUID`를 가장 먼저 생성하고 모든 `user_id UUID` FK를 연결한다.
3. 이미 공유 DB에 적용됐다면 기존 Migration을 수정하지 않고 신규 Migration으로 UUID 전환과 불필요 테이블 정리를 수행한다.
4. 실제 운영 데이터가 있는 BIGINT→UUID 전환은 매핑 컬럼 추가, FK 이관, 제약 교체 순서의 별도 Migration이 필요하다.
5. 테스트 DB는 PostgreSQL Testcontainers로 빈 DB부터 전체 Migration을 검증한다.

## 트랜잭션 경계

### 로그인 신규 사용자 생성

하나의 PostgreSQL 트랜잭션:

```text
app_user
+ auth_identity
+ point_account
+ keycap_box_account
+ point_ledger 온보딩 보상
+ user_keycap 온보딩 완성 키캡
```

Redis Session은 DB 커밋 뒤 저장한다. Redis 실패 시 성공 응답을 반환하지 않으며 재시도는 DB Unique와 원장 멱등 키로 안전해야 한다.

### 탭 배치

```text
tap_batch
+ user_tap_daily
+ point_account
+ point_ledger
+ keycap_box_account
```

### 상자 개봉

```text
keycap_box_account
+ user_keycap
+ keycap_box_open
```

### 출금 요청

```text
point_account
+ point_ledger(CASHOUT)
+ cashout_request
```

### 회원 탈퇴

외부 Toss unlink 호출은 DB 트랜잭션 밖에서 먼저 성공시킨다. 이후 로컬 트랜잭션에서 다음을 처리한다.

```text
app_user.status = WITHDRAWN
app_user.withdrawn_at = now
개인정보 익명화
금액성 테이블 접근 차단
```

그 뒤 Redis 전체 Session을 폐기한다. 로컬 처리 실패는 Toss Webhook이 같은 처리를 멱등하게 수행해 수렴시킨다.

## 동시성

- `point_account`, `keycap_box_account`, `user_tap_daily`, `cashout_request`는 `@Version` 또는 명시적 행 잠금을 사용한다.
- 로그인 Identity 생성 경쟁은 `(provider, provider_user_id)` Unique로 해결한다.
- 온보딩 보상은 `onboarding_reward_claimed` 조건부 갱신과 `point_ledger` 멱등 제약을 함께 사용한다.
- Refresh Rotation은 Redis Lua CAS를 사용한다.
- logout-all과 Session 저장 경쟁은 사용자 revoke marker를 Session 저장 Script 안에서 확인해 차단한다.

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
