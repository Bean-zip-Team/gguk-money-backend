# gguk-money-backend

꾹머니 서버 저장소다.

## 배포

`develop` 기준으로 GitHub Actions의 **Deploy develop** 워크플로를 수동 실행한다.
nginx upstream 뒤에서 8080/8081을 교대로 띄우는 blue-green 방식이라 **중단이 없다.**

```bash
./switch.sh --status      # 현재 활성 포트
./rollback.sh --list      # 보관 중인 릴리스
./rollback.sh             # 직전 릴리스로 무중단 복귀
```

구조와 주의사항(스키마 마이그레이션, zram 전제 조건, 스케줄러 중복)은
[docs/ggukmoney/deployment.md](docs/ggukmoney/deployment.md)에 정리돼 있다.

## 현재 구현 기준

현재 Source of Truth는 소스 코드와 테스트다.

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

`feat/1-a-domain-persistence`의 기존 확장 A 도메인 코드는 구현 참고 자료다. 현재 13개 테이블, UUID 사용자 식별자, Toss 로그인·로그아웃·탈퇴 계약에 맞게 정리한 뒤 병합한다.
