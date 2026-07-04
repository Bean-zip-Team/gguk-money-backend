# 꾹머니 데이터와 인프라 설계

> 현재 구현 기준: Java 26, Spring Boot 4.1.0, Jackson 3(`tools.jackson.*`), Gradle Wrapper 9.5.1. A 담당자는 민재, B 담당자는 은창이다.

## 테스트/빌드 환경

- Java 26, Spring Boot 4.1.0, Jackson 3(`tools.jackson.*`)를 기준으로 한다.
- 저장소 루트 기준, 기준 브랜치 `main`, Windows 환경에서 기본 Gradle `build/` 디렉터리를 사용한다.
- 한글 경로에서 사용했던 temp build/test working dir 우회 설정은 제거했다.
- Flyway 검증은 PostgreSQL Testcontainers 기반 통합 테스트로 수행한다. 현재 구현은 `V1000__create_auth_session_log.sql`을 실제 DB에 적용해 검증한다.

## Java 26/Testcontainers 기준

- Gradle JVM, compileJava, compileTestJava, test launcher는 Java 26 기준으로 통일한다.
- Testcontainers 이미지는 Redis `redis:7-alpine`, PostgreSQL `postgres:16-alpine`을 사용한다.
- Testcontainers BOM 선언은 1.21.3을 유지한다. Spring Boot 4.1.0의 `spring-boot-testcontainers` 경유 core runtime은 dependencyInsight에서 2.0.5로 resolve됨을 확인했다.
- QueryDSL은 `io.github.openfeign.querydsl:querydsl-jpa:7.4.0`, `io.github.openfeign.querydsl:querydsl-apt:7.4.0:jpa`를 사용한다. 기존 `com.querydsl` 5.1.0 좌표는 `GHSA-6q3q-6v5j-h6vg` 때문에 사용하지 않는다. Java package는 계속 `com.querydsl.*`이며, 구현 시 사용자 sort 문자열을 `PathBuilder.get()`에 직접 전달하지 않고 enum/switch allowlist와 명시적 projection을 사용한다.

## PostgreSQL 원칙

- PostgreSQL은 도메인 최종 원본 데이터다.
- Redis 인증 세션은 활성 세션의 Source of Truth다.
- PostgreSQL에는 활성 Refresh Session을 저장하지 않고 인증 감사 로그만 저장한다.
- 모든 운영 테이블은 `id BIGINT`, `public_id UUID`, `created_at`, `updated_at`을 기본으로 둔다.
- 외부 API에는 내부 `id`를 노출하지 않는다.

## PostgreSQL 테이블 Source of Truth

전체 PostgreSQL 테이블, 컬럼, 제약, 인덱스의 Source of Truth는 [table-spec.md](table-spec.md)다. 이 문서는 Redis, Flyway 계획, 트랜잭션, 동시성, 장애 복구만 담당한다.

핵심 원칙:

- 기존 Refresh Token 저장 테이블은 사용하지 않는다.
- Redis가 활성 Refresh Session의 원본이고 PostgreSQL은 `auth_session_log`만 영구 보관한다.
- A 담당 상세 컬럼과 제약은 [table-spec.md](table-spec.md)의 CONFIRMED 테이블을 따른다.
- B 담당 테이블은 [table-spec.md](table-spec.md)에 DRAFT로 표기하며 B 담당자 은창의 최종 확정 전까지 A 구현 기준으로 삼지 않는다.

## Flyway 파일 계획

현재 구현에서는 인증 감사 로그용 최소 SQL `src/main/resources/db/migration/V1000__create_auth_session_log.sql`을 생성했다. 이 파일은 `auth_session_log`만 포함하며, `app_user`, `auth_identity`, `device`, `user_device`, `user_merge_history`는 아직 `NOT_STARTED`다. 최종 구현 시 파일 순서는 아래처럼 확장한다.

| 파일 | 내용 |
|---|---|
| `V1000__create_auth_session_log.sql` | 현재 구현: `auth_session_log` |
| `V1010__create_user_auth.sql` | 최종 확장: `app_user`, `auth_identity`, `device`, `user_device`, `user_merge_history` |
| `V1100__create_config_legal.sql` | `legal_document`, `user_consent`, `app_config` |
| `V1200__create_keycap_box.sql` | 키캡, 상자, 드롭, 개봉 결과 |
| `V1300__create_region_ranking.sql` | 지역, 전체 랭킹 시즌, 자동 포함, 점수, snapshot, reward |
| `V1400__create_notification.sql` | push device, preference, notification log |
| `V1500__create_record_projection.sql` | 기록 projection |
| `V1600__create_event_reliability.sql` | outbox, inbox |
| `V1900__seed_initial_master_data.sql` | 지역, 기본 키캡, 기본 설정 seed |
| `V2000__create_tap_risk.sql` | B DRAFT 탭/어뷰징 |
| `V2050__create_user_onboarding_progress.sql` | B DRAFT 온보딩 진행 상태 |
| `V2100__create_point_cashout.sql` | B DRAFT 포인트/출금 |
| `V2200__create_ad_booster.sql` | B DRAFT 광고/부스터 |
| `V2300__create_invitation_analytics.sql` | B DRAFT 초대/분석 |

Refresh Token 저장 테이블 생성 파일은 계획하지 않는다.

## Redis Key 명세

### 인증

| Key | 자료구조 | TTL | 설명 |
|---|---|---|---|
| `auth:refresh:{sessionId}` | Hash | Refresh Token 남은 만료 시간 | 활성 Refresh Session |
| `auth:user-sessions:{userPublicId}` | Sorted Set | 세션별 만료 정리 | 사용자별 활성 session 목록 |
| `auth:deny:access:{jti}` | String | Access Token 남은 만료 시간 | 현재 Access Token 단위 차단 |
| `auth:revoke:user:{userPublicId}` | String | Access Token 최대 수명 + 여유 | 전체 로그아웃/정지/탈퇴 revoke 시각 millis |

`auth:refresh:{sessionId}` 필수 값:

- `userPublicId`
- `devicePublicId`
- `currentRefreshJtiHash`
- `refreshTokenHash`
- `tokenFamilyIdHash`
- `previousRefreshJtiHash`
- `rotatedAt`
- `issuedAt`
- `expiresAt`
- `status`

`auth:user-sessions:{userPublicId}`:

- Member: `sessionId`
- Score: `expiresAt` epoch millis
- Sorted Set member에는 개별 TTL이 없으므로 조회·추가·삭제 전 `ZREMRANGEBYSCORE key -inf nowEpochMillis`로 만료 member를 정리한다.
- 정리 후 member가 없으면 key를 삭제한다.

### 랭킹, 알림, 설정

| Key | 자료구조 | TTL | 설명 |
|---|---|---|---|
| `rank:overall:{seasonId}` | Sorted Set | 시즌 종료 후 14일 또는 정산 확인 후 삭제 | 전체 유저 단일 랭킹 후보 점수 |
| `rank:reached:{seasonId}` | Hash | 시즌 종료 후 14일 | 동점 보정용 최초 도달 시각 캐시 |
| `lock:ranking:settlement:{seasonId}` | String | 30분 권장 | 정산 중복 실행 방지 |
| `notification:daily:{userId}:{date}` | String 또는 Set | 2일 | 일반 푸시 하루 1회 방지 |
| `app-config:active` | String(JSON) 또는 Hash | 5분 권장 | A 소유 활성 설정 캐시 |

랭킹 Redis 정책:

- `rank:overall:{seasonId}` member는 `userPublicId`다.
- score는 현재 시즌 누적 랭킹 점수다.
- 동점이면 `rank:reached:{seasonId}`의 `reachedAt`이 빠른 사용자를 우선한다.
- PostgreSQL `ranking_score`가 최종 영속 데이터다.
- Redis는 현재 순위 조회 가속용이며 최종 정렬과 정산 원본은 PostgreSQL이다.
- 시즌 종료 시 PostgreSQL 기준으로 최종 정산하고 `ranking_snapshot`을 생성한다.
- 새 시즌은 점수 0에서 시작한다.
- 이전 시즌 Redis key는 정산 완료 뒤 14일 TTL로 두거나 운영 확인 후 삭제한다. 보관 기간은 운영 정책으로 조정 가능하다.

## 인증 감사 로그 처리

- Redis 인증 상태 변경이 성공한 뒤 `auth_session_log` 저장이 실패해도 성공한 인증 상태를 원복하지 않는다.
- 감사 로그 저장 실패는 Error/Infrastructure Log에 남긴다.
- 실패한 감사 로그는 outbox 또는 별도 재처리 작업 대상으로 둔다.
- `auth_session_log` 컬럼과 인덱스는 [table-spec.md](table-spec.md)를 따른다.

## Refresh Token Rotation

1. 클라이언트가 Refresh JWT를 전달한다.
2. JWT 서명, exp, type을 검증한다.
3. JWT의 `sid`로 `auth:refresh:{sessionId}`를 조회한다.
4. 전달받은 Refresh Token을 hash 처리한다.
5. 서버가 새 Access Token과 새 Refresh Token 후보를 만든다.
6. Redis Lua Script가 원자적으로 CAS를 수행한다.
7. CAS 성공 시 `auth_session_log`에 `REFRESHED`를 저장한다.
8. 새 token pair를 반환한다.

Lua Script는 다음 작업을 원자적으로 수행한다.

- `auth:refresh:{sessionId}` 존재 확인
- expected `currentRefreshJti`와 `refreshTokenHash` 비교
- 새 `currentRefreshJti`와 `refreshTokenHash` 저장
- `previousRefreshJtiHash`와 `rotatedAt` 저장
- `expiresAt`과 Redis TTL 갱신
- `auth:user-sessions:{userPublicId}` ZSet score 갱신

동시 요청과 재사용 판단:

- 거의 동시에 들어온 동일 Refresh 요청은 `409 AUTH_REFRESH_CONFLICT`로 처리한다.
- 동시 충돌은 정상 Session을 폐기하지 않는다.
- Rotation 완료 후 과거 Refresh Token이 다시 사용되면 `401 AUTH_REFRESH_REUSED`로 처리한다.
- 실제 이전 Refresh 재사용은 해당 Session을 폐기하고 `REFRESH_REUSE_DETECTED`를 기록한다.

빵도감은 JPA `findSessionForUpdate` 행 잠금으로 동시 Refresh 요청 중 하나만 성공하게 했다. 꾹머니는 같은 정책을 Redis Lua Script 기반 원자적 CAS로 구현한다. 일반 Redis 명령 조합으로 대체하는 경우에만 별도 lock key가 필요하지만, MVP 확정안에는 별도 Refresh lock key를 두지 않는다.

## 로그아웃 흐름

### 현재 기기 로그아웃

1. Access JWT를 검증한다.
2. 로그아웃 대상 Session은 항상 Access Token의 `sid`로 결정한다.
3. Request Body의 Refresh Token은 선택 값이며, 전달되면 `type=REFRESH`, `sub`, `sid`, current refresh jti/hash가 현재 Access Session과 일치하는지만 검증한다.
4. 불일치하면 `AUTH_LOGOUT_SESSION_MISMATCH`로 실패하고 현재 Session과 다른 Session을 모두 삭제하지 않는다.
5. `auth:deny:access:{jti}`에 차단 값을 Access Token 남은 만료 시간만큼 저장한다.
6. `auth:refresh:{sessionId}`를 삭제한다.
7. `auth:user-sessions:{userPublicId}`에서 `sessionId`를 제거한다.
8. `auth_session_log`에 `LOGOUT`을 저장한다.

### 전체 기기 로그아웃

1. Lua Script 한 번으로 `auth:user-sessions:{userPublicId}`의 만료 member를 정리한 뒤 활성 sessionId를 조회한다.
2. 같은 Lua 실행에서 존재하는 각 `auth:refresh:{sessionId}`를 삭제하고 실제 삭제 수를 `revokedSessionCount`로 반환한다.
3. 같은 Lua 실행에서 `auth:user-sessions:{userPublicId}`를 삭제한다.
4. 같은 Lua 실행에서 `auth:revoke:user:{userPublicId}`에 `revokedAtMillis`와 `reason`을 저장한다.
5. 현재 Access Token jti가 있으면 같은 Lua 실행에서 `auth:deny:access:{jti}`에 등록한다.
6. Redis 장애 시 부분 삭제 성공처럼 응답하지 않고 `AUTH_REDIS_UNAVAILABLE`로 실패한다.
7. `auth_session_log`에 `LOGOUT_ALL`을 저장한다.

현재 구현은 단일 Redis MVP 기준의 Lua 원자 처리를 사용한다. Redis Cluster 전환 시 `auth:refresh:{sessionId}`, `auth:user-sessions:{userPublicId}`, `auth:revoke:user:{userPublicId}`, access denylist key의 hash slot 설계를 다시 해야 한다.

Access Token 인증 시 `issuedAtMillis <= auth:revoke:user:{userPublicId}.revokedAtMillis`이면 거절한다. JWT 표준 `iat`는 유지하되 초 단위 비교에는 사용하지 않는다. 따라서 revoke 이전에 발급된 다른 기기의 Access Token은 즉시 무효화되고, 같은 초라도 revoke 이후 발급된 Access Token은 허용할 수 있다. revoke key TTL은 Access Token 최대 수명보다 길게 둔다.

정지/탈퇴는 전체 세션 폐기와 동일한 방식으로 처리하고 사유를 `USER_SUSPENDED`, `USER_WITHDRAWN`으로 남긴다.

## Redis 장애 정책

Refresh:

- Redis 세션 검증은 필수다.
- Redis 장애 시 재발급은 실패한다.
- 응답은 `503 AUTH_REDIS_UNAVAILABLE`로 처리한다.

로그아웃:

- Redis 처리는 필수다.
- 실패 여부를 숨기지 않는다.
- 클라이언트가 재시도할 수 있게 `503 AUTH_REDIS_UNAVAILABLE`로 처리한다.

Access Token 인증:

- JWT 서명과 exp는 서버에서 검증한다.
- JWT secret은 기본값이 없고 `app.auth.jwt.secret` 또는 환경변수 `APP_AUTH_JWT_SECRET`로 주입한다.
- blank, UTF-8 32 byte 미만, 과거 로컬 기본 문자열은 거절한다.
- 테스트는 운영 Secret이 아닌 `integration-test-secret-at-least-32-bytes-long` 같은 명시적 테스트 전용 값을 사용한다.
- denylist 검사가 필요한 인증 API는 Redis 조회가 필요하다.
- Redis denylist 조회 장애 시 고위험 API는 fail-closed다.
- 고위험 API: 회원 정보 변경, 출금, 지역 변경, 랭킹 보상 관련 API, 상자 개봉, Push Token 변경.
- 공개 조회 또는 인증 없이 가능한 조회는 정상 처리할 수 있다.

## 트랜잭션과 동시성

### 키캡 상자 개봉

1. `Idempotency-Key`를 확인한다.
2. 같은 `userId + Idempotency-Key`의 완료 요청이 있으면 기존 결과를 반환한다.
3. `keycap_box_account`를 잠근다.
4. 보유 상자 `balance > 0`을 확인한다.
5. `FREE`면 `next_free_open_at <= now`를 확인한다.
6. `ADVERTISEMENT`면 완료된 `ad_view` 하나로 상자 1개만 개봉하며 `ad_open_count < 2`와 `adViewId` 미사용을 확인한다.
7. 외부 광고 Provider API는 A 트랜잭션 안에서 호출하지 않는다.
8. 상자 1개를 차감하고 원장을 남긴다.
9. 서버 난수로 드롭 결과를 확정한다.
10. `user_keycap.shard_count`를 갱신한다.
11. 결과와 projection 이벤트를 저장한다.

### 랭킹 점수 반영

1. active season과 사용자 participation을 찾거나 자동 포함 정책에 따라 생성한다.
2. `ranking_score_event(source_type, source_event_id)` insert를 먼저 시도한다.
3. unique conflict면 이미 처리된 것으로 보고 반환한다.
4. `ranking_score`를 갱신한다.
5. 최초 도달 또는 더 높은 점수 도달 시 `reached_at`을 설정한다.
6. `RankingScoreChanged` outbox를 저장한다.
7. DB 트랜잭션을 commit한다.
8. commit 이후 Redis `rank:overall:{seasonId}`, `rank:reached:{seasonId}`를 갱신한다.

Redis는 PostgreSQL 트랜잭션 안에 포함하지 않고 DB commit 전에 먼저 갱신하지 않는다. Redis 실패는 PostgreSQL 기준으로 재처리한다.

### 주간 정산

1. Redis lock `lock:ranking:settlement:{seasonId}`를 획득한다.
2. `ranking_season.status`를 `SETTLING`으로 변경한다.
3. Redis 점수와 PostgreSQL `ranking_score`를 비교한다.
4. 필요하면 DB 기준으로 Redis를 재생성한다.
5. PostgreSQL 기준 `score DESC`, `reached_at ASC`, `user_public_id ASC`로 정렬한다.
6. 사용자·시즌별 전체 랭킹 snapshot을 저장한다.
7. 보상 정책이 유지되는 경우 1위 한정 키캡 보상과 기록성 보상을 자동 지급한다. 최신 MVP 보상 노출 여부는 Decision Required다.
8. 2~10위 입상 기록을 저장한다.
9. `ranking_season.status`를 `SETTLED`로 변경한다.
10. 다음 7일 시즌을 생성한다. 지역 변경 예약은 Region 기능에서 별도 처리하며 Ranking Aggregate와 연결하지 않는다.
11. Redis lock을 해제한다.

### 게스트 병합

1. Toss identity로 기존 MEMBER를 찾는다.
2. 현재 게스트를 source, 기존 회원을 target으로 `user_merge_history`를 생성한다.
3. A 도메인 자산을 병합한다.
4. B 도메인 병합은 Port/Event로 요청하고 progress를 남긴다.
5. source 게스트의 `GUEST_OWNER`를 비활성화한다.
6. target 회원의 `MEMBER_DEVICE`를 생성 또는 활성화한다.
7. source의 Redis 인증 세션 전체를 폐기한다.
8. target 기준 새 Redis 인증 세션을 생성한다.


## B 트랜잭션과 동시성 — PROPOSED

### 탭 배치

1. `tap_batch.public_id` unique insert로 멱등성을 확보한다.
2. 같은 id의 `request_hash`가 다르면 충돌로 거절한다.
3. `user_tap_daily`를 row lock/version으로 갱신한다.
4. `point_account`와 `point_ledger`를 같은 DB 트랜잭션에서 반영한다.
5. A 반영용 `tap_event`/outbox를 저장한 뒤 commit한다.
6. A의 `ValidatedTapApplyUseCase`는 `tapBatchId`로 중복을 방어한다.

### 포인트와 출금

- `point_account` row를 잠근 상태에서 잔액 확인, ledger insert, balance 갱신을 한 트랜잭션으로 처리한다.
- 출금은 `CASHOUT_HOLD` debit 원장을 먼저 남겨 중복 사용을 막는다.
- 외부 지급 성공은 `SUCCEEDED`, 최종 실패는 `REVERSAL` 원장으로 복원한다.
- Toss API 호출은 point account row lock을 잡은 채 수행하지 않는다.

### 광고와 부스터

- 광고 Provider 검증은 `ad_view` 상태 저장 전에 완료한다.
- 동일 `adViewId` 소비는 `consumed_at` 조건부 update로 한 번만 성공하게 한다.
- 부스터 활성화는 사용자 당 활성 row 존재 여부와 일 3회 unique 기준을 함께 검증한다.

### 친구초대

- invitee user unique와 기기 보상 partial unique로 중복 관계를 막는다.
- 첫 유효 탭 자격 변경과 `InvitationQualified` outbox 저장을 같은 트랜잭션으로 처리한다.
- A 상자 지급은 `inviteRelationId`를 referenceId로 사용해 멱등 처리한다.

## Outbox/Inbox

- A 내부 이벤트는 `event_outbox`에 저장한다.
- B 이벤트는 `event_inbox`에 저장하고 projection 처리 후 `processed_at`을 채운다.
- 같은 `event_id`는 중복 처리하지 않는다.
- 기록 API는 B 운영 테이블을 직접 조회하지 않는다.

## 2026-07-03 테스트 인프라 검증

- Redis 통합 테스트는 `redis:7-alpine` Testcontainer를 사용한다. 로컬 Redis 인스턴스에 의존하지 않는다.
- PostgreSQL/Flyway 통합 테스트는 `postgres:16-alpine` Testcontainer를 사용한다. 빈 DB에서 V1000 migration 적용, `flyway_schema_history`, `auth_session_log` 스키마, JSONB/UUID/identity 매핑을 검증한다.
- Spring test profile에서는 `spring.jpa.open-in-view=false`, SpringDoc test endpoint 비활성화를 명시한다.
- Mockito는 Gradle `mockitoAgent` configuration과 test JVM `-javaagent`로 정적 agent를 사용한다. 사용자별 cache 경로를 하드코딩하지 않는다.
- CI는 `.github/workflows/ci.yml`에서 push/pull_request main에 대해 Java 26, Gradle Wrapper, Docker 확인, `check bootJar`를 실행한다. `CI_APP_AUTH_JWT_SECRET` repository secret이 없으면 기본 JWT Secret으로 우회하지 않고 실패한다.
