# gguk-money-backend
꾹머니 서버 레포입니다.

## 설계 문서

- [꾹머니 백엔드 설계 문서](docs/ggukmoney/README.md)

## Documentation Source of Truth

- [docs/ggukmoney/README.md](docs/ggukmoney/README.md): 전체 문서 안내와 정책 요약
- [docs/ggukmoney/architecture.md](docs/ggukmoney/architecture.md): 아키텍처, A/B 경계, 설계 이유
- [docs/ggukmoney/api-contract.md](docs/ggukmoney/api-contract.md): HTTP API, Port, Event 계약
- [docs/ggukmoney/table-spec.md](docs/ggukmoney/table-spec.md): 전체 PostgreSQL 테이블, 컬럼, 제약, 인덱스
- [docs/ggukmoney/data-infra.md](docs/ggukmoney/data-infra.md): Redis, Flyway, 트랜잭션, 동시성, 장애 복구
- [docs/ggukmoney/test-plan.md](docs/ggukmoney/test-plan.md): 테스트 전략과 검증 결과
- [docs/ggukmoney/CHANGELOG.md](docs/ggukmoney/CHANGELOG.md): 설계 변경 이력

## 담당자와 범위

- A 담당자: 민재. 회원/인증, 키캡/상자, 지역/랭킹, 알림, 기록, 설정/법적 문서.
- B 담당자: 은창. 탭, 포인트, 출금, 광고/부스터, 친구 초대.
- A는 B Entity/Repository를 직접 사용하지 않고 Port/Event 계약으로만 연동한다.

## 현재 구현 상태

- 기준 브랜치: `main`
- 기술 기준: Java 26, Spring Boot 4.1.0, Jackson 3(`tools.jackson.*`), Gradle Wrapper 9.5.1
- Java 런타임: Oracle JDK 26.0.1, Gradle JVM 26, Java Toolchain 26
- Preview feature: 사용하지 않음
- 테스트 환경: 기본 Gradle `build/` 디렉터리 사용, 과거 한글 경로 우회용 temp build 설정 없음
- 구현 상태 정의: `NOT_STARTED` 코드 없음, `IN_PROGRESS` 코드가 있으나 필수 흐름/운영 보강/일부 검증 남음, `BLOCKED` 외부 계약·팀 결정·환경 문제로 완료 불가, `IMPLEMENTED` 코드와 필수 단위/통합 테스트가 문서와 일치
- 구현됨: 공통 응답 `traceId`, `/api/v1` 인증 API, Access Log Filter, JWT Provider, Redis Refresh Session Repository/Lua CAS, 현재 Session 기준 logout, Lua 내부 원자 logout-all, Redis/PostgreSQL Testcontainers 인증/로그 통합 테스트
- 구분 필요: Auth Audit Log Entity/Repository/Migration/JSONB 저장 검증은 구현됨. 감사 로그 저장 실패 재처리, Redis Session save와 logout-all 사이 race 방지, Refresh Rotation revoke marker 연동은 `IN_PROGRESS`
- 미구현: Toss 로그인/회원 생성, 온보딩 로그인 정산, 키캡/랭킹/온보딩/알림/기록/설정 도메인 Java 구현
- B API는 `PROPOSED`, B 테이블은 `DRAFT`
- 앱인토스 로그인 계약: 비게임 미니앱, `appLogin()` 기반 Toss 로그인, `authorizationCode/referrer` 입력, 서버의 Toss `generate-token` -> `login-me` mTLS 호출, `login-me.userKey` 기반 회원 식별로 확정. Java 구현 상태는 `NOT_STARTED`

## 최신 랭킹/온보딩 문서 기준

- 랭킹은 전체 유저 단일 랭킹으로 정리한다. 지역 선택, 지역 필터, 전국 필터, 별도 참가 버튼은 최신 현재 랭킹 화면 계약에서 제거한다.
- 랭킹 회차는 7일 단위이며, Toss 로그인 후 실제 인정 탭이 반영된 사용자는 현재 활성 회차에 자동 포함된다.
- 현재 랭킹은 상위 순위와 내 주변 순위, 내 행 강조, 남은 회차 시간, 1위까지 남은 탭 수를 함께 응답한다.
- 이전 회차 기록은 서버가 회차 종료 시 생성한 `ranking_snapshot`을 기준으로 최신 회차부터 조회한다.
- 앱인토스 온보딩은 로그인 전 프론트 로컬 체험으로 진행한다. 서버 사용자와 인증 Session은 Toss 로그인 성공 후 생성한다.
- 로그인 요청은 최대 45회의 온보딩 탭 정산 정보를 포함할 수 있으며, 서버는 당일 남은 인정 한도 내에서만 반영한다.
- 신규 가입자에게만 2P와 고정 온보딩 키캡을 한 번 지급하고, 기존 회원에게는 온보딩 보상을 지급하지 않는다.
- 상자 개봉과 키캡 reveal은 신규 가입 보상 미리보기 연출이며 로그인 전 실제 서버 지급을 의미하지 않는다.
- Toss 로그인 요청은 `authorizationCode`, `referrer(DEFAULT|SANDBOX)`, `onboarding.onboardingAttemptId`, `onboarding.onboardingTapCount`로 확정한다.
- 최신 랭킹/온보딩은 문서 계약만 갱신됐고 Java 구현 상태는 `NOT_STARTED`다.

Decision Required:

- 주간 랭킹 점수 12,000 한도 유지/제거/상향 여부.
- 결정 전 API의 `weeklyRankingLimit`은 `null`로 표현한다.
- 순위 등락 `rankDelta` 비교 기준 시점.
- 최신 결과/보상 모달과 `ranking_reward` 노출 유지 여부.
- 자동 랭킹 참가 row를 시즌 시작 시 eager 생성할지, 첫 유효 탭 또는 최초 조회 시 lazy 생성할지 여부.
- 기존 회원이 로그인 전 온보딩 보상 미리보기를 본 뒤 로그인할 때의 프론트 문구.
- 같은 사용자/같은 KST 일자 온보딩 정산 재시도 정책과 로그인/보상 정산 트랜잭션 경계.

## 2026-07-04 Java 26 전환 결과

- `java -version`: `26.0.1`
- `javac -version`: `26.0.1`
- `./gradlew.bat --version`: Gradle JVM 26 확인
- `./gradlew.bat javaToolchains`: Oracle JDK 26과 Gradle provisioned JDK 26 확인
- `./gradlew.bat check bootJar --stacktrace`: 성공, 36 tests, failures 0, errors 0, skipped 0
- `bootJar`: 성공, `build/libs/ggukmoney-backend-0.0.1-SNAPSHOT.jar` 생성
- JWT secret은 기본값 없이 `APP_AUTH_JWT_SECRET` 또는 `app.auth.jwt.secret`로 주입한다. Git에는 운영 Secret을 저장하지 않는다.
- QueryDSL은 `io.github.openfeign.querydsl` 7.4.0 좌표로 사용한다. 사용자 sort 문자열은 QueryDSL `PathBuilder.get()`에 직접 전달하지 않고 enum/switch allowlist와 명시적 projection을 사용한다.

## GitHub Actions CI

- Workflow: `.github/workflows/ci.yml`
- Trigger: `push` main, `pull_request` main
- 실행: Java 26, Docker 확인, Gradle Wrapper `check bootJar`
- 최신 확인: GitHub Actions `CI` workflow의 `build` job이 Gradle 9.5.1로 `check bootJar`를 성공했다.
- Repository secret `CI_APP_AUTH_JWT_SECRET`을 GitHub에서 직접 등록해야 한다. 값은 UTF-8 기준 32 byte 이상의 임의 문자열이며 문서나 Git에 저장하지 않는다.
- 설정 경로: Repository `Settings` -> `Secrets and variables` -> `Actions` -> `New repository secret`
- 첫 CI 성공 후 Branch protection에서 `build` check를 필수로 설정한다.
