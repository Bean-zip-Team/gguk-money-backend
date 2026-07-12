# gguk-money-backend

꾹머니 서버 저장소다.

## 현재 구현 기준

현재 Source of Truth는 소스 코드와 테스트이며, 문서 기준은 **사용자 UUID PK를 사용하는 13개 테이블 Persistence MVP**다.

- [MVP 문서 안내](docs/ggukmoney/README.md)
- [MVP 아키텍처](docs/ggukmoney/architecture.md)
- [MVP 테이블 명세](docs/ggukmoney/table-spec.md)
- [MVP API 계약](docs/ggukmoney/api-contract.md)
- [프론트엔드 공유용 MVP API 가이드](docs/ggukmoney/frontend-api-guide.md)
- [인증 생명주기](docs/ggukmoney/auth-lifecycle.md)
- [데이터와 인프라 원칙](docs/ggukmoney/data-infra.md)
- [테스트 계획](docs/ggukmoney/test-plan.md)
- [변경 이력](docs/ggukmoney/CHANGELOG.md)

## MVP Persistence 범위

```text
app_user
auth_identity
app_config
keycap
user_keycap
keycap_box_account
keycap_box_open
tap_batch
user_tap_daily
point_account
point_ledger
cashout_request
booster_grant
```

`app_user.id`는 빵도감 구현과 동일하게 UUID PK를 사용한다. 모든 `user_id` FK, JWT `sub`, Redis 사용자 키도 같은 UUID를 사용하며 별도 `app_user.public_id`를 두지 않는다.

현재 브랜치는 `feat/1-a-domain-persistence`이며, 마지막 문서 작업 기준 브랜치인 `feat/1-domain-entities`와 비교해 문서를 정합화한다. 로컬/원격에 `develop` 브랜치는 확인되지 않았다.

현재 구현 확인 API는 Toss 로그인, Refresh, 현재 Session 로그아웃, 전체 로그아웃, Toss unlink Webhook, 사용자 요청 탈퇴다. 탭, 상자, 출금, 부스터, 설정 조회 API는 아직 Controller/Service가 없어 계약 초안으로 유지한다.
