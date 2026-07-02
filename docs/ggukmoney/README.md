# 꾹머니 백엔드 설계 문서

이 디렉터리는 꾹머니 백엔드 MVP 설계의 Source of Truth다. `CONFIRMED`는 정책/계약 확정 상태이고, Java 구현 완료를 뜻하지 않는다. 구현 상태는 별도 `NOT_STARTED`, `IN_PROGRESS`, `IMPLEMENTED`로 관리한다.

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

문서 상태:

## 기술 기준

- Java 21
- Spring Boot 4.1.0
- Jackson 3(`tools.jackson.*`)
- 실제 저장소: `C:\Users\lucy\Documents\ggukmoney` / branch `main`
- 테스트 환경: 기본 Gradle `build/` 디렉터리 사용. 한글 경로 우회용 temp build/test working dir 설정은 제거했다.


- A API/테이블: `CONFIRMED`
- B API/테이블: 팀 검토용 `PROPOSED`
- `PROPOSED`는 구현 전에 팀 합의 후 `CONFIRMED`로 승격한다.

현재 Java 구현 상태:

- Spring Boot Application 클래스와 기본 테스트: `IMPLEMENTED`
- 공통 응답/예외, `traceId`, Access Log: `IMPLEMENTED`
- JWT Provider, Redis Refresh Session Lua CAS, logout-all 서비스: `IMPLEMENTED`; refresh/logout API와 감사 로그 연동: `IN_PROGRESS`
- Auth Audit Log Entity/Repository/Service: `IN_PROGRESS`
- 게스트 생성/복구, Toss 승격/병합: `NOT_STARTED`
- 키캡/상자, 지역/랭킹, 알림, 기록, 설정/법적 문서: `NOT_STARTED`

## 서비스 개요

꾹머니는 탭을 통해 포인트와 키캡 상자를 얻고, 키캡 수집과 지역별 주간 랭킹에 참여하는 리워드형 클릭커 서비스다. PostgreSQL을 최종 원본 데이터로 사용하고, Redis는 실시간 랭킹, 인증 세션, 중복 방지, 설정 캐시에 사용한다.

## A/B 담당 범위

| 구분 | 담당 |
|---|---|
| A | 회원/인증, 키캡/상자, 지역/랭킹, 알림, 기록, 설정/법적 문서 |
| B | 탭 검증, 포인트, 출금, 광고/부스터, 친구 초대 |

A는 B의 Entity와 Repository를 직접 사용하지 않는다. A/B 연동은 [api-contract.md](api-contract.md)의 Port와 Event 계약으로만 한다.

## 확정 정책

- 게스트와 회원은 `app_user` 하나로 관리한다.
- 게스트도 랭킹 조회는 가능하지만 실제 랭킹 참가는 `MEMBER`와 지역 설정이 필요하다.
- 같은 기기의 ACTIVE 게스트가 있으면 기존 계정을 재사용하고 새 access/refresh token을 발급한다.
- 기존 Refresh Token 원문을 반환하지 않는다.
- 게스트 복구 시 `POST /guests`는 기존 guest 계정을 재사용할 수 있지만 새 Redis auth session과 token pair를 생성한다. Refresh Token 기반 세션 유지/교체는 `POST /auth/refresh`가 담당한다.
- Session 만료, Redis Session 유실, Refresh 재사용 감지, 이상 기기이면 기존 Session을 폐기하고 새 `sessionId`를 생성한다.
- 게스트 복구의 Session 유지/폐기 방식은 서버가 결정하며 클라이언트가 선택하지 않는다.
- 신규 Toss 사용자는 현재 게스트를 MEMBER로 승격하고 `GUEST_OWNER`를 `MEMBER_DEVICE`로 변경한다.
- 기존 Toss 회원 로그인은 현재 게스트를 source, 기존 회원을 target으로 병합한다.
- 기존 Toss 회원 병합 시 source guest의 `GUEST_OWNER`는 비활성화하고 target member의 `MEMBER_DEVICE`를 생성 또는 활성화한다.
- 게스트 랭킹 점수는 기존 회원의 진행 중 랭킹 점수에 합산하지 않는다.
- Access JWT 원문은 서버에 저장하지 않는다.
- Refresh JWT 원문은 클라이언트 Secure Storage가 보관한다.
- Refresh JWT의 hash와 활성 Session은 Redis에 저장한다.
- Redis가 활성 인증 세션의 Source of Truth다.
- 전체 로그아웃·정지·탈퇴는 사용자 revoke 시각으로 기존 Access Token도 즉시 차단한다.
- PostgreSQL은 인증 감사 로그만 저장하며 활성 Refresh Session 저장소로 쓰지 않는다.
- 무료 상자 개봉은 1시간마다 1회이고 무료 기회는 누적되지 않는다.
- 광고 상자 개봉은 하루 2회이며 완료된 `adViewId` 하나로 보유 상자 1개만 개봉한다.
- 상자 개봉은 MVP에서 요청당 1개만 허용하고 일괄 개봉은 향후 확장으로만 둔다.
- 상자 보유량과 개봉 조건은 분리한다.
- 상자 증감은 원장으로 기록한다.
- 상자 개봉 결과 난수는 서버가 확정하고 결과를 저장한다.
- 랭킹 보상은 정산 시 자동 지급하며 별도 수령 API는 없다.
- 지역 변경은 월 1회 가능하고 다음 주 월요일부터 적용한다.
- 현재 시즌 참여 지역은 변경하지 않는다.
- 실시간 랭킹 조회는 Redis Sorted Set을 사용한다.
- 최종 정렬과 정산 원본은 PostgreSQL이다.
- 동점 기준은 `score DESC`, `reached_at ASC`, `user_public_id ASC`다.
- 기록은 B 테이블을 직접 조인하지 않고 Event Projection으로 구성한다.
- A의 `app_config`에는 A 소유 정책만 저장하고 포인트/출금 정책은 B가 소유한다.
- 상태값은 `CANCELED`로 통일한다.
- Push Token은 암호화된 발송용 값과 검색용 hash를 분리한다.

## 미정 정책

- 실제 Toss 로그인 응답 필드와 회원 식별자 형식
- B 이벤트 payload 최종 형식
- 위치 판별 Provider와 행정구역 코드 체계
- 지역 랭킹 콜드스타트 기준 인원과 전국 랭킹 fallback 노출 조건
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

#### 지역 랭킹 콜드스타트

Decision Required:
- 와이어프레임 제안 기준: 지역 참여자 `50명` 미만이면 콜드스타트 표시 및 전국 랭킹 fallback 노출
- 현재 문서 기준: 기준 인원과 fallback 조건 미정
- 응답 후보 필드: `coldStart`, `participantCount`, `requiredParticipantCount`, `fallbackScope`, `inviteCtaVisible`

#### 와이어프레임과 의도적으로 다른 MVP 정책

- 와이어프레임에는 상자 반복/일괄 개봉 UI가 있으나 MVP 백엔드는 요청당 상자 `1개` 개봉만 지원한다. 일괄 개봉은 향후 확장이다.
- 와이어프레임에는 주간 보상 받기 버튼이 있으나 현재 백엔드 정책은 정산 시 자동 지급이다. 화면 버튼은 결과 확인 또는 닫기 동작으로 본다.
- 와이어프레임 내부에도 탭 적립, 광고 상자 개봉 시간, 출금 예시, 상자 모두 열기, 주간 보상 버튼 표현이 서로 충돌한다. 해당 문구를 새 서버 정책으로 자동 추가하지 않는다.

Redis 인증 장애 정책과 denylist 장애 정책은 [data-infra.md](data-infra.md)에서 확정안으로 둔다.

## 구현 권장 순서

1. 공통 응답, 인증, 예외, 감사 컬럼, outbox/inbox 기반
2. Redis JWT Session, Refresh Rotation, 로그아웃, 인증 감사 로그
3. 게스트 생성, 게스트 복구, Toss 승격, 기존 회원 병합
4. 키캡, 상자 계정, 상자 원장, 무료/광고 개봉
5. 지역 목록, 지역 판별 Port, 지역 설정과 변경 예약
6. 랭킹 시즌, 참여, Redis 점수 후보, PostgreSQL 정산 원본
7. 검증 탭 반영 Port, 상자 진행도, 랭킹 점수 이벤트
8. 주간 정산, snapshot, ranking_reward, 한정 키캡 자동 지급
9. 알림 preference, push_device, notification_log, fake push
10. 기록 projection, app_config, legal_document
11. B 탭/포인트/출금/광고/부스터/초대 PROPOSED 계약 팀 확정
12. Testcontainers 기반 PostgreSQL/Redis 통합 테스트

## 현재 명세 완성도

- `api-contract.md`: A 전체 API 상세 `CONFIRMED`, B 전체 API 상세 `PROPOSED`.
- `table-spec.md`: A 34개 테이블 상세 `CONFIRMED`, B 14개 테이블 상세 `PROPOSED`.
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
- Gradle test task는 Java 21 toolchain과 기본 `build/` 디렉터리를 사용
- Flyway `auth_session_log` 최소 SQL 생성
- 빵도감 저장소 변경 없음
- Git 커밋 없음
- `./gradlew compileJava compileTestJava` 성공
- 영문 경로에서 `./gradlew clean test`, `./gradlew check`, `./gradlew bootJar`, 핵심 개별 테스트가 성공했고 `ClassNotFoundException`은 재발하지 않음
