# 꾹머니 13개 테이블 MVP 테스트 계획

## 목표

- 13개 테이블만 생성되는지 검증
- `app_user.id UUID`와 모든 `user_id UUID` FK 정합성 검증
- Entity, Repository, Migration 일치 검증
- Toss 로그인, Refresh, 로그아웃, logout-all, 탈퇴, Webhook 멱등성 검증
- 포인트와 상자 동시성, 탭 배치 멱등성 검증

## 1. Migration 통합 테스트

PostgreSQL Testcontainers 빈 DB에 전체 Flyway를 적용하고 아래를 확인한다.

- 정확한 13개 MVP 테이블 존재
- `app_user.id` 타입 UUID와 PK
- `app_user.public_id` 미존재
- 모든 `user_id` 컬럼 타입 UUID
- 모든 사용자 FK가 `app_user(id)` 참조
- Unique, Partial Unique, CHECK, 인덱스 존재
- 이전 지역, 랭킹, 알림, 기록, Reliability 테이블 미생성

## 2. UUID 사용자 Persistence 테스트

- `AppUser` 저장 시 UUID 자동 생성
- Repository ID Generic이 UUID
- `AuthIdentity`, `UserKeycap`, `PointAccount` 등 UUID 사용자 FK 저장과 조회
- JWT `sub`가 사용자 UUID 문자열
- 잘못된 UUID Claim 거절
- Redis Session 사용자 식별자가 같은 UUID인지 확인

## 3. Toss 로그인 테스트

### 신규 사용자

- `generate-token → login-me` 호출
- UUID 사용자와 Identity 생성
- 계정, 상자 계정 생성
- 온보딩 포인트와 키캡 한 번 지급
- Redis Session 생성과 JWT 발급
- Toss Token 미저장

### 기존 사용자

- 같은 `(TOSS, userKey)`로 기존 UUID 사용자 재사용
- 새로운 사용자나 보상 중복 생성 없음
- 프로필과 `last_login_at` 갱신

### 탈퇴 사용자

- 같은 Identity의 `app_user.status=WITHDRAWN`이면 `ACCOUNT_WITHDRAWN`
- 신규 사용자로 재생성하지 않음

### 실패

- invalid grant
- login-me userKey 누락
- mTLS/timeout/5xx
- Redis Session 저장 실패 후 로그인 성공 응답 금지
- 재시도 시 DB 중복과 보상 중복 없음

## 4. Refresh와 로그아웃 테스트

- 정상 Refresh Rotation
- 동시 동일 Refresh 중 하나만 성공
- 과거 Refresh 재사용 감지와 Session 폐기
- Access `sid` 기준 현재 Session 로그아웃
- 선택 Refresh Token이 다른 Session이면 거절
- 이미 종료된 Session 로그아웃의 멱등 응답
- logout-all 후 기존 Access Token 즉시 차단
- logout-all과 새 Session 저장 경쟁 차단
- 로그아웃이 Toss Identity나 사용자 상태를 변경하지 않는지 확인

## 5. 회원 탈퇴 테스트

### 사용자 요청 탈퇴

- 새 authorizationCode 교환
- login-me userKey와 현재 Identity 일치
- remove-by-user-key 성공
- `app_user.status=WITHDRAWN`, `withdrawn_at` 설정
- 닉네임과 프로필 익명화
- 모든 Redis Session 폐기
- 포인트 원장과 출금 이력 보존

### 보안과 실패

- 다른 Toss userKey면 `TOSS_USER_MISMATCH`
- Toss unlink 실패 시 로컬 사용자 ACTIVE 유지
- 이미 WITHDRAWN이면 멱등 성공
- 탈퇴 후 Access/Refresh Token 사용 불가

### Webhook

- 올바른 Basic Secret 허용
- 잘못된 Secret 거절
- `UNLINK`, `WITHDRAWAL_TERMS`, `WITHDRAWAL_TOSS` 처리
- 미등록 userKey는 200 멱등 성공
- 이미 WITHDRAWN인 사용자 재처리 성공
- Webhook에서 Toss unlink API를 다시 호출하지 않음

## 6. 탭과 포인트 테스트

- `(user_id, tap_session_id, sequence)` 멱등 처리
- 같은 키와 다른 request hash 충돌
- 일일 제한과 유효 탭 계산
- 포인트 계정, 원장, 상자 진행도 동일 트랜잭션
- 동시 배치에서 잔액과 누적값 유실 없음

## 7. 상자와 키캡 테스트

- 무료권과 광고 개봉 제한
- ad_reward_id 중복 방지
- 조각 증가와 완성 전환
- 사용자당 장착 키캡 하나 Partial Unique
- 동일 idempotency key 재요청 결과 동일

## 8. 출금 테스트

- 잔액 차감과 원장, 요청 생성 원자성
- 외부 지급 성공과 실패 상태 전환
- 실패 환불 원장 멱등 처리
- 동시 출금으로 잔액 음수 방지

## 9. 빌드 검증

```powershell
./gradlew.bat clean check bootJar --stacktrace
```

필수 결과:

- compileJava 성공
- compileTestJava 성공
- 전체 단위·통합 테스트 성공
- bootJar 생성
- Docker 기반 PostgreSQL/Redis Testcontainers 성공
