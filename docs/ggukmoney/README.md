# 꾹머니 백엔드 설계 문서

이 디렉터리는 꾹머니 백엔드 MVP 설계의 Source of Truth다. `CONFIRMED`는 정책/계약 확정 상태이고, Java 구현 완료를 뜻하지 않는다. 구현 상태는 별도 `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `IMPLEMENTED`로 관리한다.

## 문서 구조

| 문서 | Source of Truth |
|---|---|
| [README.md](README.md) | 정책 요약, 문서 안내, 구현 순서 |
| [architecture.md](architecture.md) | 아키텍처, ERD, A/B 경계, 설계 이유, 빵도감 인증 재사용 전략 |
| [api-contract.md](api-contract.md) | HTTP API, Port, Event 계약 |
| [table-spec.md](table-spec.md) | 전체 PostgreSQL 테이블, 컬럼, 제약, 인덱스 |
| [data-infra.md](data-infra.md) | DB, Flyway 계획, Redis, JWT Session, 트랜잭션, 동시성 |
| [test-plan.md](test-plan.md) | 테스트 전략과 구현 전 체크리스트 |
| [CHANGELOG.md](CHANGELOG.md) | 설계 변경 이력 |

동일한 정책을 여러 문서에 전문으로 반복하지 않는다. 정책은 이 README에서 요약하고, 상세 계약은 위 Source of Truth 문서로 이동한다.

## 기술 기준

- Java 26
- Spring Boot 4.1.0
- Jackson 3(tools.jackson.*)
- Gradle Wrapper 9.5.1
- 기준 브랜치: `main`
- 테스트 환경: 기본 Gradle build/ 디렉터리 사용. 한글 경로 우회용 temp build/test working dir 설정은 제거했다.

문서 상태:

- A API/테이블: CONFIRMED, 담당자 민재
- B API: 팀 검토용 PROPOSED, 담당자 은창
- B 테이블: DRAFT, 담당자 은창 최종 확정 필요
- PROPOSED는 구현 전에 팀 합의 후 CONFIRMED로 승격한다.
- 구현 상태는 `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `IMPLEMENTED` 네 가지로 표기한다.
- `CONFIRMED`는 정책/계약 확정이며 Java 구현 완료를 뜻하지 않는다.

구현 상태 정의:

- `NOT_STARTED`: 구현 코드가 없음.
- `IN_PROGRESS`: 코드가 있으나 필수 흐름, 운영 보강 또는 일부 검증이 남음.
- `BLOCKED`: 외부 계약, 팀 결정 또는 환경 문제 때문에 완료할 수 없음.
- `IMPLEMENTED`: 코드와 필수 단위/통합 테스트가 통과하고 문서와 일치함.

현재 Java 구현 상태:

- Gradle JVM, compileJava, compileTestJava, test launcher는 Java 26 기준으로 통일한다.
- Preview feature는 사용하지 않는다.
- QueryDSL은 OpenFeign `io.github.openfeign.querydsl` 7.4.0 좌표를 사용한다. 사용자 sort 문자열을 `PathBuilder.get()`에 직접 전달하지 않고 enum/switch allowlist와 명시적 projection 기준을 적용한다.
- Spring Boot Application 클래스와 기본 테스트: IMPLEMENTED
- 공통 응답/예외, traceId, Access Log: IMPLEMENTED
- JWT Provider, `/api/v1` 인증 API, Redis Refresh Session Repository/Lua CAS, 현재 Session 기준 logout, Lua 내부 원자 logout-all: IMPLEMENTED
- 감사 로그 저장 실패 재처리, Redis Session save와 logout-all 사이 race 방지, Refresh Rotation revoke marker 연동, Redis Cluster hash slot 설계 같은 운영 보강: IN_PROGRESS
- Auth Audit Log Entity/Repository/Migration/JSONB 저장 검증: IMPLEMENTED
- Toss 로그인/회원 생성, 온보딩 로그인 정산: NOT_STARTED
- Toss Access Token 없는 일반 로그인: BLOCKED
- 키캡/상자, 지역/랭킹, 온보딩, 알림, 기록, 설정/법적 문서 Java 구현: NOT_STARTED

## 최종 검증 기준

- 실제 Java/Javac: 26.0.1
- Spring Boot: 4.1.0
- Jackson: 3(tools.jackson.*)
- Gradle Wrapper: 9.5.1
- Redis Testcontainers 이미지: redis:7-alpine
- PostgreSQL Testcontainers 이미지: postgres:16-alpine
- Testcontainers BOM 선언: 1.21.3 유지. Spring Boot spring-boot-testcontainers 경유 core runtime은 2.0.5로 resolve됨을 dependencyInsight로 확인했다.
- 전체 테스트 기준: `./gradlew.bat check bootJar --stacktrace` 성공, 36 tests, failures 0, errors 0, skipped 0.
- GitHub Actions `CI` workflow의 `build` job은 Gradle 9.5.1 기준 `check bootJar` 성공을 확인했다.

## 서비스 개요

꾹머니는 탭을 통해 포인트와 키캡을 얻고, 전체 유저 단일 랭킹에서 7일 회차 순위를 겨루는 리워드형 클릭커 서비스다. PostgreSQL을 최종 원본 데이터로 사용하고, Redis는 현재 랭킹 조회 가속, 인증 세션, 중복 방지, 설정 캐시에 사용한다.

## A/B 담당 범위

| 구분 | 담당자 | 담당 |
|---|---|---|
| A | 민재 | 회원/인증, 키캡/상자, 지역/랭킹, 알림, 기록, 설정/법적 문서 |
| B | 은창 | 탭 검증, 포인트, 출금, 광고/부스터, 친구 초대 |

A는 B의 Entity와 Repository를 직접 사용하지 않는다. A/B 연동은 [api-contract.md](api-contract.md)의 Port와 Event 계약으로만 한다.

## 확정 정책

- `app_user`는 Toss 로그인 완료 사용자를 관리한다. MVP 계정 유형은 MEMBER만 둔다.
- 로그인 전 온보딩은 프론트 로컬 체험이며 서버 사용자, 인증 Session, 포인트, 키캡, 일반 상자 보상을 생성하지 않는다.
- Toss 로그인 후 실제 인정 탭이 반영된 사용자는 현재 활성 7일 랭킹 회차에 자동 포함된다.
- 기존 Refresh Token 원문을 반환하지 않는다.
- Redis Session save를 단일 Lua Script로 묶고 사용자 revoke marker를 확인해야 한다. 현재 `RedisAuthSessionRepository.save(AuthSession)`은 refresh hash 저장, TTL 설정, user-sessions ZSet 추가가 분리되어 있어 logout-all과의 race 방지가 후속 과제다.
- Session 만료, Redis Session 유실, Refresh 재사용 감지, 이상 기기이면 기존 Session을 폐기하고 새 `sessionId`를 생성한다.
- 로그인 후 기기 관계는 member-device 연결로만 관리한다.
- 신규 Toss 사용자는 새 MEMBER를 생성하고 `auth_identity`를 연결한다.
- 기존 Toss 회원 로그인은 기존 MEMBER와 `auth_identity`를 재사용한다.
- Access JWT 원문은 서버에 저장하지 않는다.
- Refresh JWT 원문은 클라이언트 Secure Storage가 보관한다.
- Refresh JWT의 hash와 활성 Session은 Redis에 저장한다.
- JWT secret은 기본값 없이 `APP_AUTH_JWT_SECRET` 또는 `app.auth.jwt.secret`로 주입한다.
- Redis가 활성 인증 세션의 Source of Truth다.
- 전체 로그아웃·정지·탈퇴는 사용자 revoke 시각으로 기존 Access Token도 즉시 차단한다.
- PostgreSQL은 인증 감사 로그만 저장하며 활성 Refresh Session 저장소로 쓰지 않는다.
- 무료 상자 개봉은 1시간마다 1회이고 무료 기회는 누적되지 않는다.
- 광고 상자 개봉은 하루 2회이며 완료된 `adViewId` 하나로 보유 상자 1개만 개봉한다.
- 상자 개봉은 MVP에서 요청당 1개만 허용하고 일괄 개봉은 향후 확장으로만 둔다.
- 상자 보유량과 개봉 조건은 분리한다.
- 상자 증감은 원장으로 기록한다.
- 상자 개봉 결과 난수는 서버가 확정하고 결과를 저장한다.
- 랭킹은 지역/전국 구분 없이 전체 유저 단일 랭킹으로 운영한다.
- 랭킹 회차는 운영 정책상 7일 단위다.
- 랭킹 보상은 정산 시 자동 지급하며 별도 수령 API는 없다.
- 지역 변경은 월 1회 가능하고 다음 주 월요일부터 적용한다. 단, 최신 랭킹 Aggregate는 지역과 연결하지 않는다.
- 실시간 랭킹 조회는 Redis Sorted Set을 사용한다.
- 최종 정렬과 정산 원본은 PostgreSQL이다.
- 동점 기준은 `score DESC`, `reached_at ASC`, `user_public_id ASC`다.
- 최신 온보딩은 일반 경제 정책과 분리한다.
- 앱인토스 온보딩은 로그인 전 프론트 로컬 체험으로 진행한다.
- 프론트는 최대 45탭과 안정적인 `onboardingAttemptId`를 로컬에 저장하고, 15/30/45 화면은 보상 미리보기와 reveal 연출만 표시한다.
- 서버 사용자와 인증 Session은 Toss 로그인 성공 후 생성한다.
- 로그인 요청은 `onboardingAttemptId`, `onboardingTapCount`, `onboardingCompleted` 후보 값을 포함할 수 있다.
- 서버는 `submittedTapCount`를 0..45로 clamp하고, `acceptedTapCount = min(submittedTapCount, 45, KST 당일 남은 유효 탭 한도)`만 `user_tap_daily`, 랭킹, 일반 탭 수학에 반영한다.
- 신규 가입자에게만 `ONBOARDING_15_TAP_POINT`, `ONBOARDING_30_TAP_POINT`로 총 2P와 고정 온보딩 키캡 1개를 한 번 지급한다.
- 기존 회원에게는 온보딩 포인트와 키캡 보상을 지급하지 않고, 인정 가능한 탭만 반영한다.
- 온보딩 상자는 일반 랜덤 상자 리소스가 아니다. 상자 개봉과 키캡 reveal은 신규 가입 보상 미리보기 연출이며 로그인 전 실제 서버 지급을 의미하지 않는다.
- 온보딩 고정 키캡 보상은 일반 상자 잔액, 무료 쿨다운, 광고 상자 횟수, 일반 드롭 테이블, 조각 지급, 상자 개봉 원장을 사용하지 않는다.
- 한 사용자와 KST 일자 기준 로그인 전 온보딩 정산은 한 번만 반영한다.
- `onboardingAttemptId` 정산 결과를 저장해 응답 유실이나 부분 실패 후 재시도에서도 신규/기존 회원 판정과 보상 지급 여부를 유지한다.
- 기록은 B 테이블을 직접 조인하지 않고 Event Projection으로 구성한다.
- A의 `app_config`에는 A 소유 정책만 저장하고 포인트/출금 정책은 B가 소유한다.
- 상태값은 `CANCELED`로 통일한다.
- Push Token은 암호화된 발송용 값과 검색용 hash를 분리한다.

## 미정 정책

- 실제 Apps-in-Toss auth 요청/응답, Toss user id 형식, `deviceKey/platform/appVersion` 필요 여부
- 기존 회원이 로그인 전 온보딩 보상 미리보기를 본 뒤 로그인할 때의 프론트 문구
- 같은 사용자/같은 KST 일자 온보딩 정산 재시도 정책
- 로그인과 A/B 보상 정산 트랜잭션 경계, 회원 생성 후 부분 실패 복구 방식
- B 이벤트 payload 최종 형식
- 위치 판별 Provider와 행정구역 코드 체계
- 주간 랭킹 점수 12,000 한도 유지/제거/상향 여부
- 결정 전 API의 `weeklyRankingLimit`은 `null`로 표현한다.
- 순위 등락 `rankDelta` 비교 기준 시점
- 최신 랭킹 결과/보상 모달 유지 여부
- 자동 랭킹 참가 row eager/lazy 생성 방식
- 키캡 드롭 확률, 등급, 조각 수, 시즌 운영 정책
- 1위 한정 키캡이 이미 있는 사용자에게 중복 지급할지 여부
- 회원 탈퇴 시 A 도메인 데이터 보관, 익명화, 삭제 범위
- outbox 전달 방식과 재시도 주기

### 와이어프레임 교차 검토 Decision Required

아래 항목은 34페이지 와이어프레임 PDF에서 확인한 표현과 현재 백엔드 MVP 문서가 다르므로 팀 결정 후 `CONFIRMED` 또는 `PROPOSED` 계약을 갱신한다. 현재 문서의 확정 정책을 이 검토만으로 임의 변경하지 않는다.

#### 출금 단위

Decision Required:
- A안: 최소 10P, 이후 1P 단위 출금
- B안: 최소 10P, 10P 단위 출금
- 현재 문서 기준: B안
- 와이어프레임 표현: A안에 가까움

근거:
- 와이어프레임은 `1P = 0.7 Toss 포인트`, 최소 출금 `10P`, `134P -> 약 93 Toss 포인트` 예시를 함께 보여준다.
- 현재 문서는 최소 `10P`, `10P` 단위, `134P` 중 `130P`만 출금, `91 Toss 포인트` 지급으로 제안한다.

#### 탭 배치와 어뷰징 기준

Decision Required:
- 배치 크기와 전송 주기: 현재 문서의 `50탭 또는 2초`와 와이어프레임의 `30초 또는 100탭` 중 최종 기준 확정 필요
- `80ms` 최소 간격, 분당 `420` 유효 탭, 최근 `100`탭 간격 표준편차 기준 적용 여부 확정 필요
- 클라이언트 `intervalStats`는 참고값이며 서버가 이를 신뢰하지 않는다는 점을 계약에 유지할지 확정 필요
- 어뷰징 기준값을 운영 설정으로 분리할지 결정 필요

근거:
- 와이어프레임은 최소 클릭 간격 `80ms`, 분당 유효 탭 `420회`, 일일 유효 탭 `12,000회`, 서버 검증 `30초/100탭 배치 전송`을 보여준다.
- 현재 문서는 `50탭 또는 2초` 전송과 서버 최종 검증을 제안한다.

#### 랭킹 화면 전환

최신 MVP 화면 기준으로 현재 랭킹은 지역/전국 구분이 없는 전체 유저 단일 랭킹이다. 지역 랭킹 콜드스타트와 전국 fallback은 현재 랭킹 계약에서 제거한다.

Decision Required:
- 주간 랭킹 점수 12,000 한도 유지/제거/상향 여부
- 순위 등락 `rankDelta` 비교 기준 시점
- 최신 결과/보상 모달 유지 여부
- 자동 랭킹 참가 row eager/lazy 생성 방식

#### 와이어프레임과 의도적으로 다른 MVP 정책

- 와이어프레임에는 상자 반복/일괄 개봉 UI가 있으나 MVP 백엔드는 요청당 상자 `1개` 개봉만 지원한다. 일괄 개봉은 향후 확장이다.
- 와이어프레임에는 주간 보상 받기 버튼이 있었으나 최신 랭킹 목록/이전 기록 화면만으로 보상 모달 유지 여부는 확정하지 않는다.
- 와이어프레임 내부에도 탭 적립, 광고 상자 개봉 시간, 출금 예시, 상자 모두 열기, 주간 보상 버튼 표현이 서로 충돌한다. 해당 문구를 새 서버 정책으로 자동 추가하지 않는다.

Redis 인증 장애 정책과 denylist 장애 정책은 [data-infra.md](data-infra.md)에서 확정안으로 둔다.

## 구현 권장 순서

1. 공통 응답, 인증, 예외, 감사 컬럼, outbox/inbox 기반
2. Redis JWT Session, Refresh Rotation, 로그아웃, 인증 감사 로그
3. Toss identity 신규/기존 회원 로그인, member-device 연결, 온보딩 로그인 정산
4. 키캡, 상자 계정, 상자 원장, 무료/광고 개봉
5. 지역 목록, 지역 판별 Port, 지역 설정과 변경 예약
6. 랭킹 시즌, 자동 포함, Redis overall 점수 후보, PostgreSQL 정산 원본
7. 검증 탭 반영 Port, 일반 상자 진행도, 온보딩 진행도, 랭킹 점수 이벤트
8. 주간 정산, snapshot, ranking_reward 유지 여부 결정, 한정 키캡 자동 지급
9. 알림 preference, push_device, notification_log, fake push
10. 기록 projection, app_config, legal_document
11. B 탭/포인트/출금/광고/부스터/초대 PROPOSED 계약 팀 확정
12. Testcontainers 기반 PostgreSQL/Redis 통합 테스트

## 현재 명세 완성도

- `api-contract.md`: A 전체 API 상세 `CONFIRMED`, B 전체 API 상세 `PROPOSED`.
- `table-spec.md`: A 34개 테이블 상세 `CONFIRMED`, B 14개 테이블 상세 `DRAFT`.
- B 정책 수치·상태·외부 Toss/광고 응답은 팀 회의에서 최종 조정한다.

## 빵도감 분석 결과 요약

이전 공유 레포인 `Bean-zip-Team/bread-diary-backend`는 원격에 `develop` 브랜치가 없어 `main` 브랜치 HEAD `e9a6abb73320e61869f91b14293e5da3d1fbe4f2`를 기준 원본으로 사용했다.

- 확인한 코드: `JwtTokenProvider`, `AuthService`, `UserSessionService`, `UserSession`, `UserSessionRepository`, `AuthController`, `AuthInterceptor`, `AccessLogFilter`, `RequestLogContext`, `RateLimitInterceptor`
- 확인한 테스트: `JwtTokenProviderTest`, `AuthServiceTest`, `AuthServiceConcurrencyTest`, `AuthRefreshMysqlIntegrationTest`, `UserSessionServiceTest`, `AuthControllerTest`, `AuthInterceptorTest`, `AccessLogFilterTest`, `RateLimitInterceptorTest`
- 확인한 빵도감 로그 패턴을 바탕으로 꾹머니 로그는 `traceId` 중심 구조화 로그로 재정의했다.
- 꾹머니 문서에서는 요청 추적 id 명칭을 `traceId`로 통일한다.
- 운영 로그 파일은 경로가 제공되지 않아 확인하지 못했다.
- 빵도감 main HEAD 코드에는 Redis Refresh Session 구현이 아니라 JPA `user_sessions` 기반 Refresh Rotation이 있다. 꾹머니는 Redis를 활성 인증 세션의 Source of Truth로 사용하므로 저장소 구현은 새로 설계하고 일부 구현했다.

## 이번 작업 검증 기준

- Java 인증/로그 기반 구현 포함
- Gradle test task는 Java 26 toolchain과 기본 `build/` 디렉터리를 사용
- Flyway `auth_session_log` 최소 SQL 생성
- 빵도감 저장소 변경 없음
- Git 커밋 없음
- `./gradlew compileJava compileTestJava` 성공
- Windows 환경에서 `./gradlew clean test`, `./gradlew check`, `./gradlew bootJar`, 핵심 개별 테스트가 성공했고 `ClassNotFoundException`은 재발하지 않음

## 2026-07-03 구현 상태 갱신

- 공통 응답/예외/traceId, Access Log, JWT Provider, Redis Refresh Session Repository, Lua CAS Refresh Rotation, logout/logout-all 최소 흐름, Auth Audit Log Entity/Repository/Migration/JSONB 저장 검증, Flyway V1000은 실제 단위/통합 테스트 통과 기준으로 `IMPLEMENTED`로 승격한다.
- refresh/logout API의 모든 장애 흐름, 감사 로그 저장 실패 재처리, Redis 장애 복구 전체 정책은 `IN_PROGRESS`로 유지한다.
- Toss Access Token 없는 일반 로그인은 device 계약 미확정으로 `BLOCKED`다.
- Toss 로그인/회원 생성, 온보딩 로그인 정산, 키캡/상자, 최신 랭킹/온보딩 Java 구현, 알림, 기록, 설정/법적 문서는 `NOT_STARTED` 상태를 유지한다.
- 통합 테스트 실행에는 Docker가 필요하며 Redis/PostgreSQL은 Testcontainers로만 기동한다.
