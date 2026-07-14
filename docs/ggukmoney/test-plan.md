# 꾹머니 13개 테이블 MVP 테스트 계획

## 목표

- 13개 테이블만 생성되는지 검증
- `app_user.id UUID`와 모든 `user_id UUID` FK 정합성 검증
- Flyway Migration, Entity, Repository의 현재 구현 일치 여부 검증
- Toss 로그인, Refresh, 로그아웃, logout-all, 탈퇴, Webhook 계약 검증
- 포인트, 상자, 탭, 출금, 부스터의 동시성과 멱등성은 기능 구현 단계에서 검증

## 상태 구분

| 상태 | 의미 |
|---|---|
| `구현 및 통과 확인` | 테스트가 존재하고 현재 실행 환경에서 통과를 확인함 |
| `코드 존재·환경 문제로 실행 미확인` | 테스트는 존재하지만 Docker/Testcontainers 등 환경 문제로 이번 검증에서 통과 여부를 확인하지 못함 |
| `미구현` | 테스트 코드가 아직 없음 |
| `기능 미구현으로 보류` | 대상 Controller/Service가 아직 없어 테스트도 후속 구현 단계로 보류함 |

테스트 코드가 존재한다는 이유만으로 완료 처리하지 않는다.

## 현재 검증 결과

`./gradlew.bat clean build`는 전체 빌드 실패로 기록한다.

- 결과: 실패
- 원인: Docker/Testcontainers 환경 미탐지로 컨테이너 기반 테스트 초기화 실패
- 영향 테스트: PostgreSQL/Redis Testcontainers를 사용하는 통합 테스트
- 최근 실행 결과: `147 tests completed, 10 failed`
- `compileJava`, `compileTestJava`, `bootJar`, `jar`, `assemble` 단계는 통과했고 `:test`에서 Docker/Testcontainers 초기화 실패로 실패했다.
- 앱 설정 관련 `AppConfigServiceTest`, `AppConfigControllerTest`, `TapPolicyConfigTest`는 targeted test로 통과 확인했다.
- 키캡 목록 및 장착 API 관련 `UserKeycapTest`, `KeycapRepositoryTest`, `KeycapMapperTest`, `KeycapServiceTest`, `KeycapControllerTest`는 targeted test로 통과 확인했다.
- 키캡 상자 상태 API 관련 `UserTapProgressServiceTest`, `KeycapBoxMapperTest`, `KeycapBoxStatusServiceTest`, `KeycapBoxControllerTest`는 targeted test로 통과 확인했다.
- 기존 탭/부스터 회귀 테스트인 `TapBatchServiceTest`, `BoosterGrantServiceTest`는 targeted test로 통과 확인했다.

## 구현 및 통과 확인

- `TestEnvironmentSmokeTest`: JUnit Platform과 Java 26 환경 확인
- `GgukmoneyBackendApplicationTests`: Spring Context 로드
- `ADomainPersistenceSmokeTest`: 13개 Entity 테이블명, PK 타입, `public_id` 보유 여부, Repository가 `JpaRepository`를 사용하는지 확인
- `RedisAuthSessionRepositoryTest`: Refresh Rotation Lua CAS 호출 구조, logout-all Lua script 구성, revoke marker 파싱 확인
- `AuthServiceLogoutAllTest`: logout-all 응답과 revoke count 반환 확인
- `AuthServiceTossLifecycleTest`: Toss 로그인 신규/기존/탈퇴 사용자, 사용자 요청 탈퇴, Toss unlink 실패, Webhook Secret, Webhook 멱등 처리 확인
- `JwtTokenProviderTest`: JWT `sub`, `sid`, `jti`, `type`, `issuedAtMillis`, 만료, secret 검증 확인
- `AccessLogFilterTest`: `X-Request-Id`, Session 로그 마스킹 확인
- `MemberMapperTest`: 회원 조회·수정 응답 변환, 장착 키캡 없음, nullable 프로필 이미지 확인
- `UserServiceTest`: 회원 조회, 포인트 잔액 0, 장착 키캡 없음, 프로필 부분 수정, 공백 닉네임, 닉네임 중복, 탈퇴 사용자 차단 확인
- `MemberControllerTest`: 인증 사용자 UUID 전달, 회원 조회·수정 성공 응답 `success/data`, Validation 실패 확인
- `UserKeycapTest`: 완료 키캡 장착, 미완성 키캡 장착 거부, 장착 해제 상태 전환 확인
- `KeycapRepositoryTest`: `active=true` 키캡 목록 정렬, 현재 사용자 보유 키캡 조건, `Keycap` join fetch 조회, 사용자 UUID와 `Keycap.publicId` 기준 보유 키캡 조회 확인
- `KeycapMapperTest`: 키캡 목록과 내 키캡 목록 DTO 변환, 장착 응답 변환, `publicId` → `keycapId`, 내부 BIGINT ID 미노출 확인
- `KeycapServiceTest`: 키캡 목록, 내 키캡 목록, 빈 배열 응답, 회원 조회에 필요한 최소 장착 키캡 요약 조회, 완료 키캡 장착, 기존 장착 자동 해제, 같은 키캡 재장착 멱등 성공, 미완성·미보유 키캡 차단 확인
- `KeycapControllerTest`: `GET /api/v1/keycaps`, `GET /api/v1/keycaps/me`, `PUT /api/v1/keycaps/{keycapId}/equip` Access JWT 필수 정책과 `success/data` 응답 확인
- `TapBatchServiceTest`: 탭 배치 처리, 포인트 적립, 상자 지급, 부스터 배율 적용, 중복 요청 재처리 방지 확인
- `BoosterGrantServiceTest`: 부스터 활성화, 중복 활성화 차단, 일일 제한, 현재 상태, 활성 배율 조회 확인
- `TapPolicyConfigTest`: `app_config` row 누락 또는 Repository 조회 실패 시 기본값 fallback 확인
- `AppConfigServiceTest`: `TapPolicyConfig` 공개값을 typed `AppConfigResponse`로 매핑하고 내부 설정 구조를 노출하지 않음 확인
- `AppConfigControllerTest`: Access JWT 필수 정책, `GET /api/v1/app-config` 성공 응답 구조, 내부 설정 키와 원본 JSON 미노출 확인

## 코드 존재·환경 문제로 실행 미확인

아래 테스트는 코드가 존재하지만 이번 `clean build`에서는 Docker 환경 부재로 초기화에 실패했다.

- `FlywayMigrationIntegrationTest`: 빈 PostgreSQL에 Flyway 적용, 13개 테이블, UUID `user_id`, 주요 제약과 인덱스 확인
- `AuthApiIntegrationTest`: Refresh, logout, logout-all API와 실제 Redis 상태 변화 확인
- `RedisAuthSessionRepositoryIntegrationTest`: Redis Session 저장, 조회, 삭제, revoke marker 확인
- `RefreshLuaCasIntegrationTest`: 실제 Redis Lua CAS, 동시 Refresh 충돌, 과거 Refresh 재사용 감지 확인
- `AuthServiceLogoutAllIntegrationTest`: 실제 Redis에서 logout-all 삭제와 revoke marker 확인
- `GlobalExceptionHandlerTest`: Testcontainers 기반 FullStack 지원 클래스에 의존해 환경 문제로 실행 미확인
- 회원 API 통합 회귀 테스트: 현재 Docker/Testcontainers 환경 부재로 전체 `clean test`, `clean build`에서 확인 필요
- 포인트/탭/부스터 통합 테스트: `PointApiIntegrationTest`, `TapApiIntegrationTest`, `BoosterApiIntegrationTest`, `TapBatchServiceRateLimitIntegrationTest`는 Docker/Testcontainers 초기화 실패로 이번 전체 검증에서 실행 결과를 확인하지 못했다.
- 출금 통합 테스트: `CashoutApiIntegrationTest`는 Docker/Testcontainers 초기화 실패로 이번 전체 검증에서 실행 결과를 확인하지 못했다.

## 기능 미구현으로 보류

아래 항목은 Entity와 Repository 또는 목표 계약은 있으나 Controller/Service 또는 외부 연동 흐름이 아직 없어 기능 테스트를 보류한다.

- 온보딩 정산: 로그인 DTO에 온보딩 필드가 없고 별도 API도 아직 없다.
- 상자 개봉 API: 상자 개봉 Controller/Service가 없다.
- 출금 요청과 Toss 지급: 출금 Controller/Service와 외부 지급 복구 흐름이 없다.
- 앱 설정 외의 운영 정책 API: 앱 버전, 점검 상태, 출금 정책 조회는 실제 설정 키와 서비스 구현이 없어 보류한다.

## 추가해야 할 테스트

- 로그인 Redis Session 저장 이후 JWT 응답 생성 또는 DB 커밋 실패 시 Session 정리 여부
- logout-all과 신규 로그인 Session 저장 경쟁
- 사용자 요청 탈퇴와 Toss unlink Webhook 동시 처리 시 상태 변경, 개인정보 익명화, Redis Session 폐기의 멱등 수렴
- `point_ledger.user_id`와 `point_account_id`의 사용자 불일치 방지
- `booster_grant` 활성 부스터 중복 생성 방지
- `user_keycap.equipped=true`와 `status=COMPLETED` 정합성은 Entity와 Service 테스트로 검증한다. 실제 DB CHECK 제약은 없으므로 서비스 검증을 유지한다.
- 상자 개봉과 출금의 같은 `Idempotency-Key` 재요청 결과 복구
- 같은 멱등 기준에 다른 Request Body가 들어왔을 때 `409 IDEMPOTENCY_KEY_REUSED` 처리
- 상자 개봉 성공 시 모든 방식에서 상자 잔액을 1 차감함
- 상자 개봉 `FREE`에서 상자 잔액과 무료권을 각각 1 차감함
- 상자 개봉 `ADVERTISEMENT`가 광고 검증 Service 부재 시 미지원 오류를 반환하고 자원을 차감하지 않음
- 상자 개봉 보상 조각 누적, 초과 조각 cap, `COMPLETED` 전환과 `completed_at` 기록
- 상자 개봉 동시 동일 요청 Unique 충돌 후 기존 결과 재조회
- 상자 개봉에서 부스터가 조각 수에 적용되지 않음

## 목표 테스트 시나리오

### Toss 로그인

- `generate-token → login-me` 호출
- UUID 사용자와 Identity 생성
- `point_account`, `keycap_box_account` 생성
- `user_tap_progress` 생성
- Redis Session 생성과 JWT 발급
- Toss Token 미저장
- 같은 `(TOSS, userKey)`로 기존 UUID 사용자 재사용
- `app_user.status=WITHDRAWN`이면 `ACCOUNT_WITHDRAWN`

### Refresh와 로그아웃

- 정상 Refresh Rotation
- 동시 동일 Refresh 중 하나만 성공
- 과거 Refresh 재사용 감지와 Session 폐기
- Access `sid` 기준 현재 Session 로그아웃
- 선택 Refresh Token이 다른 Session이면 거절
- logout-all 후 기존 Access Token 즉시 차단
- 로그아웃이 Toss Identity나 사용자 상태를 변경하지 않음

### 회원 탈퇴와 Webhook

- 새 authorizationCode 교환
- `login-me.userKey`와 현재 Identity 일치
- `remove-by-user-key` 성공 후 `app_user.status=WITHDRAWN`, `withdrawn_at`, 개인정보 익명화
- 모든 Redis Session 폐기
- 포인트 원장과 출금 이력 보존
- 다른 Toss userKey면 `TOSS_USER_MISMATCH`
- Toss unlink 실패 시 로컬 사용자 ACTIVE 유지
- Webhook에서 미등록 userKey는 200 멱등 성공
- Webhook에서 Toss unlink API를 다시 호출하지 않음

## 빌드 검증 명령

```powershell
./gradlew.bat clean build
```

종료 코드가 실패이면 전체 빌드 성공으로 표현하지 않는다. Docker 또는 외부 환경 문제는 실패 원인으로 분리해 기록한다.
