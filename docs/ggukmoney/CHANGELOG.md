# 수정 내역

## 2026-07-04 인증/API hardening과 CI

- 인증 API 실제 경로를 `/api/v1/auth/**`로 통일하고 기존 `/auth/**`가 성공 API로 동작하지 않도록 정리했다.
- 빵도감 공통 응답 구조를 참고하되 꾹머니 `traceId`를 유지하고 `error:null`, `data:null`, `error.details`를 사용하지 않는 형식으로 문서를 맞췄다.
- JWT Secret 기본값을 제거하고 `APP_AUTH_JWT_SECRET` 또는 `app.auth.jwt.secret` 명시 주입, blank/짧은 값/과거 로컬 기본 문자열 거절 기준을 추가했다.
- 현재 기기 logout은 Access Token의 현재 Session을 대상으로 하고, optional Refresh Token은 동일 Session 검증용으로만 사용하도록 보강했다.
- logout-all은 Redis Lua Script 한 번으로 만료 Session 정리, 활성 Refresh Session 삭제, 사용자 revoke marker, 현재 access denylist 저장을 처리하도록 정리했다.
- QueryDSL을 OpenFeign `io.github.openfeign.querydsl` 7.4.0으로 전환하고 정렬 allowlist/projection 보안 규칙을 유지했다.
- 감사 로그 UUID scalar는 null/blank만 nullable로 허용하고 non-blank invalid UUID는 조용히 null row로 저장하지 않도록 정리했다.
- V1000 `auth_session_log`의 DB DEFAULT 없음, `result` CHECK만 존재하는 실제 SQL 기준을 table-spec에 반영했다.
- GitHub Actions CI workflow를 추가하고 `CI_APP_AUTH_JWT_SECRET` 수동 설정 절차를 문서화했다.
- 게스트 생성, Toss 승격/병합, 랭킹, 온보딩, 키캡/상자, 포인트/출금, 운영 배포는 구현하지 않았다.

## 2026-07-04 최신 랭킹·온보딩 와이어프레임 문서 정합화

- 최신 전체 랭킹 와이어프레임을 반영해 지역/전국 랭킹을 전체 유저 단일 랭킹으로 변경했다.
- 별도 랭킹 참가 API를 `DEPRECATED`로 정리하고 사용자 자동 시즌 포함을 문서화했다.
- 7일 회차, 남은 시간, 내 행 강조, 상위 순위와 내 주변 순위, 이전 회차 기록 요구사항을 반영했다.
- ranking API에서 현재 계약의 scope/region 필드를 제거하고 `/api/v1/rankings/current`의 `myRank`로 `/api/v1/rankings/me`를 통합했다.
- `ranking_participation`과 `ranking_snapshot`의 지역 연결을 제거하고 `ranking_snapshot`을 이전 회차 기록의 Source of Truth로 정리했다.
- Redis 랭킹 key를 `rank:overall:{seasonId}`와 `rank:reached:{seasonId}` 기준으로 변경했다.
- 주간 12,000 한도와 최신 UI 목업 점수 충돌은 `Decision Required`로 남겼다.
- 최신 온보딩 15/30/45 milestone을 반영했다.
- 온보딩 총 2P와 45탭 완성 키캡 1개 보상을 명시했다.
- 온보딩 자동 개봉과 완료 후 Toss 로그인 gate를 반영했다.
- 일반 상자/경제 정책과 온보딩 전용 정책을 분리했다.
- B DRAFT `user_onboarding_progress` 명세를 추가했다.
- B에서 A 키캡 지급을 동기 호출하는 `OnboardingKeycapGrantUseCase` 계약을 추가했다.
- 랭킹/온보딩 이벤트 카탈로그를 최신 화면 기준으로 갱신했다.
- Java, SQL, Gradle, 테스트 코드는 수정하지 않았다.

### 후속 문서 정합성 보정

- 구현 상태 정의에 `BLOCKED`를 추가하고 `CONFIRMED`와 `IMPLEMENTED`를 분리했다.
- 개인 로컬 저장소 절대경로를 공유 문서에서 제거했다.
- architecture의 Redis Session/Lua CAS 통합 테스트 상태를 실제 Testcontainers 결과에 맞췄다.
- Flyway 검증 설명을 `FlywayMigrationIntegrationTest`의 PostgreSQL Testcontainers 실제 검증 기준으로 수정했다.
- 온보딩 상태 전이를 `IN_PROGRESS` -> `LOGIN_REQUIRED` -> `COMPLETED`로 명확화했다.
- `GET /api/v1/home`의 `milestonesGranted` 필드와 누적 milestone 의미를 정리했다.
- 온보딩 완성 키캡 지급 모델에 `grant_mode=COMPLETE_KEYCAP`과 `userKeycapId` 의미를 보강했다.
- 12,000 랭킹 상한 미확정 응답을 `weeklyRankingLimit=null` 기준으로 정리했다.
- Java/SQL/Gradle/테스트 코드는 수정하지 않았다.

### 후속 문서 정합성 최종 보정

- 공유 문서에 남은 개인 절대경로를 제거했다.
- `auth_session_log`의 현재 구현 완료 범위와 감사 로그 저장 실패 재처리 상태를 분리했다.
- Java/SQL/Gradle/테스트 코드는 수정하지 않았다.

### API 경로와 인증 세션 후속 과제 정합화

- Production API Origin과 API Prefix를 분리하고 HTTP endpoint 경로를 `/api/v1/...` 전체 경로로 통일했다.
- 공유 문서의 남은 상대 API 경로를 최신 계약 기준으로 정리했다.
- GitHub Actions CI의 `build` job `check bootJar` 성공 상태를 문서에 반영했다.
- logout-all Lua 내부 원자 처리와 Session save/logout-all race 방지 상태를 분리했다.
- Session save Lua 전환, 사용자 revoke marker 확인, Refresh Rotation revoke marker 연동을 후속 과제로 문서화했다.
- `V1010__create_user_auth.sql`, `POST /api/v1/guests`, Toss 승격/병합은 후속 phase로 유지했다.
- Java/Test/SQL/Gradle/CI workflow는 수정하지 않았다.

## 2026-07-02 빵도감 main HEAD 기반 인증/로그 구현 반영

- 빵도감 기준 원본을 `develop`에서 `main` HEAD `e9a6abb73320e61869f91b14293e5da3d1fbe4f2`로 변경했습니다. 원격 `develop` 브랜치는 확인되지 않았습니다.
- 현재 꾹머니 저장소 상태가 사실상 빈 Java 구현에서 인증/로그 기반 `IN_PROGRESS` 구현으로 바뀐 점을 README와 설계 문서에 반영했습니다.
- 빵도감 `global/filter/AccessLogFilter.java` -> 꾹머니 `global/filter/AccessLogFilter.java`: 기존 요청 식별자를 `traceId`로 변경하고 `userPublicId`, `sessionIdHash`, `devicePublicId`, `clientIpMasked`, `errorCode` 필드를 반영했습니다.
- 빵도감 `global/logging/RequestLogContext.java` -> 꾹머니 `global/logging/RequestLogContext.java`: 기존 요청 ID 헤더 대신 `X-Trace-Id`를 사용하도록 변경했습니다.
- 빵도감 `global/common/ApiResponse.java`, `ApiError.java`, `ApiErrorResponse.java`, `GlobalExceptionHandler.java` -> 꾹머니 공통 응답/예외: 응답 wrapper에 `traceId`를 포함했습니다.
- 빵도감 `domain/auth/service/JwtTokenProvider.java` -> 꾹머니 `domain/auth/service/JwtTokenProvider.java`: `sub=app_user.public_id`, `type=ACCESS/REFRESH`, `issuedAtMillis`를 포함하도록 변경했습니다.
- 빵도감 `AuthController`, `AuthService`, `AuthInterceptor` 구조를 참고하되 JPA `UserSession`, `UserSessionRepository`, row lock 기반 `UserSessionService`는 복사하지 않았습니다.
- 꾹머니 신규 구현으로 `AuthSession` Redis 모델, `RedisAuthSessionRepository`, Redis Lua CAS, Access Token denylist, 사용자 revoke timestamp, Auth Audit Log Entity/Repository/Service를 추가했습니다.
- Refresh Rotation은 별도 Redis refresh lock key 없이 Lua CAS로 구현했습니다.
- 거의 동시 Refresh 충돌은 `AUTH_REFRESH_CONFLICT`로 처리하고 Session을 폐기하지 않으며, 실제 과거 Refresh 재사용은 `AUTH_REFRESH_REUSED`와 `REFRESH_REUSE_DETECTED` 감사 로그 후 Session을 폐기하도록 문서화/구현했습니다.
- `auth_session_log` 최소 Flyway SQL을 `V1000__create_auth_session_log.sql`로 정리했고, `id BIGINT identity`, `public_id/user_public_id/device_public_id UUID`, `metadata JSONB`, `created_at/updated_at` 기준으로 맞췄습니다.
- Toss Access Token 없는 일반 로그인은 device 요청 계약 미확정으로 `TOSS_DEVICE_CONTRACT_REQUIRED` Blocking Issue로 남겼습니다.
- 한글 경로에서 발생했던 Gradle/JUnit worker 테스트 클래스 로딩 문제는 영문 경로 이전 후 재발하지 않았고, temp build/test working dir 우회 설정은 제거했습니다.
- Git commit, push, branch 생성은 하지 않았습니다.

## 2026-07-02 와이어프레임 교차 검토 보강

- 34페이지 와이어프레임 PDF를 다시 확인하고 출금 단위, 탭 배치/어뷰징 기준, 지역 랭킹 콜드스타트 기준을 `Decision Required` 항목으로 분리했습니다.
- 와이어프레임의 상자 반복/일괄 개봉 UI와 주간 보상 받기 버튼은 MVP 백엔드 정책과 의도적으로 다른 표현임을 명시했습니다.
- `keycap.code`를 API 노출용 안정 코드로 추가하고 `GET /api/v1/keycaps` 응답과 테이블 명세를 정합화했습니다.
- 키캡 수집 상태를 DB/API 모두 `IN_PROGRESS`, `COMPLETED`로 통일하고 `GET /api/v1/keycaps` 페이지 응답을 공통 마스터 데이터 규칙과 맞췄습니다.
- 닉네임 중복 방지를 위해 `nickname_normalized`와 ACTIVE 사용자 partial unique 정책을 문서화했습니다.
- `204 No Content` API는 공통 response body를 사용하지 않고 `X-Trace-Id` 헤더로 추적 id를 제공할 수 있도록 예외를 명시했습니다.
- 사용자 전체 revoke 비교 기준을 `issuedAtMillis <= revokedAtMillis`로 바꾸고 같은 초 발급 토큰 경계 테스트를 추가했습니다.
- Toss 로그인 인증 조건을 일반 로그인, 게스트 승격/병합, 회원 재연결 범위로 분리해 명시했습니다.
- 랭킹 결과 API 응답 스키마와 분석 이벤트 카탈로그/스키마 버전/source 정책을 보강했습니다.

## 2026-07-02 최신 후보 문서 반영

- A 전체 API 상세 계약을 보완하고 각 API에 Owner/Status/인증/멱등성/관련 저장소를 명시했습니다.
- B 전체 API를 `PROPOSED` 상세 계약으로 작성했습니다.
- B 14개 테이블을 컬럼·타입·제약·인덱스 수준으로 상세화했습니다.
- 전체 로그아웃 시 사용자 revoke 시각으로 모든 Access Token을 즉시 차단하도록 보완했습니다.
- `auth:user-sessions` Sorted Set 만료 member 정리 규칙을 추가했습니다.
- ERD의 `APP_USER -> DEVICE` 직접 관계를 제거하고 B Aggregate를 추가했습니다.
- Refresh 동시 충돌과 실제 과거 Token 재사용 테스트를 분리했습니다.
- B 탭/포인트/출금/광고/부스터/초대/API 계약 테스트를 추가했습니다.

- logout-all은 auth:revoke:user:{userPublicId} reasoned marker, auth:user-sessions:{userPublicId} 전체 삭제, 모든 auth:refresh:{sessionId} 삭제, 현재 access denylist 등록까지 구현했습니다.

## 2026-07-03 인증/로그 Testcontainers 통합 테스트 마무리

- `FullStackIntegrationTestSupport`에 클래스 종료 후 Spring Context 폐기 정책을 적용해 전체 테스트 실행 중 종료된 Redis/PostgreSQL Testcontainer 포트가 재사용되는 문제를 해결했다.
- `RedisAuthSessionRepositoryTest`의 Lua CAS Mockito expectation을 production `redisTemplate.execute(...)` 인자 순서와 개수에 맞췄다.
- Redis/PostgreSQL/API 통합 테스트를 실제 Testcontainers로 검증했다.
- 최종 `clean test` 결과는 25 tests, failures 0, errors 0, skipped 0이다.
- `check`와 `bootJar`가 성공했고 실행 jar는 `build/libs/ggukmoney-backend-0.0.1-SNAPSHOT.jar`에 생성된다.
## 2026-07-04 Java 26 전환

- Java/Javac 26.0.1, Gradle JVM 26, Java Toolchain 26을 기준으로 전환했다.
- `build.gradle`의 Java toolchain을 26으로 변경했다.
- `TestEnvironmentSmokeTest`에 Java feature version 26 검증을 추가했다.
- Preview feature는 사용하지 않는다.
- QueryDSL 5.1.0은 현재 Java 코드 직접 사용처가 없지만 향후 동적 조회 계획 때문에 유지한다. 임의 버전 변경과 취약점 suppression은 하지 않는다.
- Redis/PostgreSQL Testcontainers 기반 인증/로그 통합 테스트를 Java 26에서 재실행했다.
- 전체 테스트 결과: 26 tests, failures 0, errors 0, skipped 0.
- B API는 PROPOSED, B 테이블은 DRAFT로 유지한다. A 담당자는 민재, B 담당자는 은창이다.
