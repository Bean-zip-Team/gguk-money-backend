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
- 구현됨: 공통 응답 `traceId`, Access Log Filter, JWT Provider, Redis Refresh Session Repository/Lua CAS, logout-all Redis 전체 세션 삭제, Redis/PostgreSQL Testcontainers 인증/로그 통합 테스트
- 구분 필요: Auth Audit Log Entity/Repository/Migration/JSONB 저장 검증은 구현됨. 인증 API 연동 범위와 운영 저장 실패 재처리는 `IN_PROGRESS`
- 미구현: 게스트 생성/복구, Toss 승격/병합, 키캡/랭킹/온보딩/알림/기록/설정 도메인 Java 구현
- B API는 `PROPOSED`, B 테이블은 `DRAFT`
- Blocking Issue: Toss Access Token 없는 일반 로그인에서 필요한 `deviceKey/platform/appVersion` 요청 계약 미확정. 현재 Toss 일반 로그인은 `TOSS_DEVICE_CONTRACT_REQUIRED`로 차단한다.

## 최신 랭킹/온보딩 문서 기준

- 랭킹은 전체 유저 단일 랭킹으로 정리한다. 지역 선택, 지역 필터, 전국 필터, 별도 참가 버튼은 최신 현재 랭킹 화면 계약에서 제거한다.
- 랭킹 회차는 7일 단위이며, 가입 또는 게스트 생성 사용자는 현재 활성 회차에 자동 포함된다.
- 현재 랭킹은 상위 순위와 내 주변 순위, 내 행 강조, 남은 회차 시간, 1위까지 남은 탭 수를 함께 응답한다.
- 이전 회차 기록은 서버가 회차 종료 시 생성한 `ranking_snapshot`을 기준으로 최신 회차부터 조회한다.
- 온보딩은 일반 경제 정책과 분리한다. 신규 사용자는 15탭 1P, 30탭 추가 1P, 45탭 온보딩 완성 키캡 1개를 받는다.
- 온보딩 상자는 프론트 수동 개봉 API 없이 서버 보상 결과를 바탕으로 자동 개봉 애니메이션만 표시한다.
- 서버 게스트 계정과 세션은 앱 시작 시 `POST /guests`로 생성하며, 완료 모달의 Toss 로그인은 게스트 데이터를 MEMBER로 승격하는 흐름이다.
- 온보딩 상태는 0~44탭 `IN_PROGRESS`, 45탭 키캡 지급 후 `LOGIN_REQUIRED`, Toss 회원 승격 성공 후 `COMPLETED`로 전이한다. `COMPLETED` 전까지 보상은 회수하지 않는다.
- 최신 랭킹/온보딩은 문서 계약만 갱신됐고 Java 구현 상태는 `NOT_STARTED`다.

Decision Required:

- 주간 랭킹 점수 12,000 한도 유지/제거/상향 여부.
- 결정 전 API의 `weeklyRankingLimit`은 `null`로 표현한다.
- 순위 등락 `rankDelta` 비교 기준 시점.
- 최신 결과/보상 모달과 `ranking_reward` 노출 유지 여부.
- 자동 랭킹 참가 row를 시즌 시작 시 eager 생성할지, 첫 유효 탭 또는 최초 조회 시 lazy 생성할지 여부.

## 2026-07-04 Java 26 전환 결과

- `java -version`: `26.0.1`
- `javac -version`: `26.0.1`
- `./gradlew.bat --version`: Gradle JVM 26 확인
- `./gradlew.bat javaToolchains`: Oracle JDK 26과 Gradle provisioned JDK 26 확인
- `./gradlew.bat clean test`: 26 tests, failures 0, errors 0, skipped 0
- `./gradlew.bat check`: 성공
- `./gradlew.bat bootJar`: 성공, `build/libs/ggukmoney-backend-0.0.1-SNAPSHOT.jar` 생성
- QueryDSL 5.1.0은 현재 Java 코드에서 직접 사용하지 않지만 향후 동적 조회 계획 때문에 유지한다. 임의 버전 변경이나 취약점 suppression은 하지 않는다.
