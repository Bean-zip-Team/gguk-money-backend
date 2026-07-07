# 꾹머니 13개 테이블 MVP 설계

이 디렉터리는 꾹머니 1차 MVP 백엔드의 현재 Source of Truth다.

이전 46개 테이블 확장 설계 문서는 repo 밖 백업 루트 `C:\Users\mjake\IdeaProjects\gguk_old\gguk-money-backend\feat-1-a-domain-persistence-20260707-232119\`로 이동했다. 현재 repo의 Source of Truth는 아래 13개 MVP 테이블 문서만이다.

## 핵심 결정

- `app_user.id`는 `UUID` PK다.
- 사용자 외부 ID와 내부 ID를 이중으로 두지 않는다. API, JWT `sub`, Redis Session, 모든 `user_id` FK는 동일한 사용자 UUID를 사용한다.
- 나머지 업무 테이블은 기존 13개 ERD처럼 `BIGINT` 내부 PK와 필요한 경우 `public_id UUID`를 유지한다.
- Toss Provider 사용자 식별자는 `auth_identity(provider, provider_user_id)`로 분리한다.
- 꾹머니 Access/Refresh JWT Session은 Redis가 원본이며 PostgreSQL Session 테이블은 이번 MVP에 두지 않는다.
- Toss 로그인 과정에서 받은 Toss Access/Refresh Token은 장기 저장하지 않는다.
- 로그아웃은 꾹머니 Session만 종료하며 Toss 연결을 해제하지 않는다.
- 회원 탈퇴는 새 Toss `authorizationCode`로 현재 사용자를 재확인한 뒤 Toss 연결 해제와 로컬 탈퇴를 수행한다.
- 포인트·출금 원장 때문에 회원 탈퇴 시 사용자 행과 금액성 기록을 물리 삭제하지 않는다. `WITHDRAWN` 상태와 개인정보 익명화를 사용한다.

## MVP 목표

```text
Toss 로그인
→ 온보딩 완료와 보상 처리
→ 탭 배치 검증
→ 포인트 적립
→ 상자 획득과 키캡 조각 완성
→ 광고 기반 상자 개봉과 2배 부스터
→ 포인트 출금
→ 로그아웃과 회원 탈퇴
```

## 현재 테이블 13개

| 영역 | 테이블 |
|---|---|
| 사용자와 인증 | `app_user`, `auth_identity` |
| 운영 설정 | `app_config` |
| 키캡과 상자 | `keycap`, `user_keycap`, `keycap_box_account`, `keycap_box_open` |
| 탭 | `tap_batch`, `user_tap_daily` |
| 포인트 | `point_account`, `point_ledger` |
| 출금 | `cashout_request` |
| 부스터 | `booster_grant` |

## 빵도감 참고 범위

기준 저장소와 커밋:

```text
Bean-zip-Team/bread-diary-backend
main HEAD: e9a6abb73320e61869f91b14293e5da3d1fbe4f2
```

참고한 구현:

- `domain/user/entity/User.java`: `UUID` 사용자 PK와 JPA UUID 생성
- `domain/auth/controller/AuthController.java`: Toss 로그인, Refresh, 로그아웃, Toss Webhook API 구분
- `domain/auth/service/AuthService.java`: 사용자·Session UUID, Refresh Rotation, 현재 Session 로그아웃
- `domain/user/service/UserWithdrawalService.java`: Toss 연결 해제, Webhook 인증, 사용자 데이터 정리
- `domain/auth/client/TossAuthClient.java`: `generate-token`, `login-me`, `refresh-token`, `remove-by-user-key`

꾹머니는 구조만 참고하고 아래 부분은 그대로 복사하지 않는다.

- 빵도감처럼 Toss Access/Refresh Token을 사용자 테이블에 저장하지 않는다.
- 포인트와 출금 원장이 있으므로 전체 사용자 데이터를 Hard Delete하지 않는다.
- 현재 ggukmoney Redis Refresh Session과 logout-all 구조를 유지한다.

## 이번 MVP에서 제외한 영역

- 지역과 지역 변경
- 랭킹 전체
- 알림과 Push 발송 기록
- 별도 사용자 기록 Projection
- 친구 초대와 분석 이벤트
- 법적 문서와 동의 이력 테이블

- Event Inbox와 Event Outbox
- 개별 탭 이벤트 저장
- 키캡 드롭 테이블과 상자 원장
- Toss 송금 결과 전용 테이블

제외한 기능은 영구 삭제가 아니라 이번 Persistence MVP에서 뒤로 미룬 것이다.

## 문서 구조

| 문서 | 역할 |
|---|---|
| [README.md](README.md) | MVP 범위와 핵심 결정 |
| [architecture.md](architecture.md) | 13개 테이블 구조와 트랜잭션 경계 |
| [table-spec.md](table-spec.md) | 컬럼, 제약, 인덱스 Source of Truth |
| [api-contract.md](api-contract.md) | MVP API와 테이블 연결 |
| [auth-lifecycle.md](auth-lifecycle.md) | 로그인, Refresh, 로그아웃, 탈퇴, Webhook 상세 흐름 |
| [data-infra.md](data-infra.md) | PostgreSQL, Redis, 멱등성, 동시성 원칙 |
| [test-plan.md](test-plan.md) | Migration, Entity, Repository, 인증 생명주기 검증 계획 |
| [CHANGELOG.md](CHANGELOG.md) | 설계 변경 이력 |

## 구현 상태 표기

- `NOT_STARTED`: 코드가 없음
- `IN_PROGRESS`: 일부 코드가 있으나 현재 명세와 정합화가 필요함
- `IMPLEMENTED`: Migration, Entity, Repository, 인증 흐름, 필수 테스트가 현재 문서와 일치함
- `DEFERRED`: 이번 MVP에서 제외하고 후속 단계로 이동함
