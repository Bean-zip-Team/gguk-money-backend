# 수정 내역

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
- `keycap.code`를 API 노출용 안정 코드로 추가하고 `GET /keycaps` 응답과 테이블 명세를 정합화했습니다.
- 키캡 수집 상태를 DB/API 모두 `IN_PROGRESS`, `COMPLETED`로 통일하고 `GET /keycaps` 페이지 응답을 공통 마스터 데이터 규칙과 맞췄습니다.
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