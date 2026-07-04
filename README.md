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

- 실제 저장소: `C:\Users\lucy\Documents\ggukmoney`
- 브랜치: `main`
- 기술 기준: Java 26, Spring Boot 4.1.0, Jackson 3(`tools.jackson.*`), Gradle Wrapper 9.5.1
- Java 런타임: Oracle JDK 26.0.1, Gradle JVM 26, Java Toolchain 26
- Preview feature: 사용하지 않음
- 테스트 환경: 기본 Gradle `build/` 디렉터리 사용, 과거 한글 경로 우회용 temp build 설정 없음
- 구현됨: 공통 응답 `traceId`, Access Log Filter, JWT Provider, Redis Refresh Session Lua CAS, refresh/logout/logout-all API 뼈대, logout-all Redis 전체 세션 삭제, Auth Audit Log Entity/Repository/Service, Redis/PostgreSQL Testcontainers 인증/로그 통합 테스트
- 미구현: 게스트 생성/복구, Toss 승격/병합, 키캡/랭킹/알림/기록/설정 도메인
- Blocking Issue: Toss Access Token 없는 일반 로그인에서 필요한 `deviceKey/platform/appVersion` 요청 계약 미확정. 현재 Toss 일반 로그인은 `TOSS_DEVICE_CONTRACT_REQUIRED`로 차단한다.

## 2026-07-04 Java 26 전환 결과

- `java -version`: `26.0.1`
- `javac -version`: `26.0.1`
- `./gradlew.bat --version`: Gradle JVM 26 확인
- `./gradlew.bat javaToolchains`: Oracle JDK 26과 Gradle provisioned JDK 26 확인
- `./gradlew.bat clean test`: 26 tests, failures 0, errors 0, skipped 0
- `./gradlew.bat check`: 성공
- `./gradlew.bat bootJar`: 성공, `build/libs/ggukmoney-backend-0.0.1-SNAPSHOT.jar` 생성
- QueryDSL 5.1.0은 현재 Java 코드에서 직접 사용하지 않지만 향후 동적 조회 계획 때문에 유지한다. 임의 버전 변경이나 취약점 suppression은 하지 않는다.