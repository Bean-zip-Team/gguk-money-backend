# 보상 정책

포인트·상자·개봉·출금의 **현재 운영 정책**과, 그 값이 코드 어디에 있는지 정리한다.
정책 수치를 확인하거나 바꾸려 할 때 여기부터 본다.

## 0. 먼저 알아야 할 것 — 정책의 원본은 코드다

`app_config` 테이블을 보면 정책 값이 다 들어 있어서 "DB에서 바꾸면 되겠다"고 오해하기 쉽다.
**아니다.** `TapConfigSeeder`가 부팅할 때마다 DB 값을 코드의 `DEFAULT_VALUES`와 대조해서,
다르면 코드 값으로 새 행을 넣는다.

```
DB에서 값을 바꾼다  →  다음 배포까지는 적용된다  →  배포하면 코드 값으로 원복된다
```

따라서

- **임시 실험**(장애 대응, 당일 검증)이면 DB 수정으로 충분하다. 단, 다음 배포에 사라진다.
- **정식 정책 변경**이면 코드의 `DEFAULT_VALUES`를 고쳐서 배포해야 한다. 배포가 곧 정책 롤아웃이다.

## 1. 포인트

| 항목 | 값 | 설정 키 |
|---|---|---|
| 지급 간격 | 평균 20탭, 실제 **15~25탭 균등 랜덤** | `tap.curve.general.base` = 20, `tap.curve.general.variance` = 0.25 |
| 1회 지급량 | 1P | 코드 상수 (`TapBatchService`의 `creditAmount`) |
| 일일 포인트 상한 | 150P | `tap.point.dailyCap` |
| 일일 탭 보상 상한 | 3000탭 | `tap.validity.maxPerDay` |
| 온보딩 퍼널 완주 | 70P | `onboarding.reward.pointAmount` |

기준 카운터는 `user_tap_progress.cumulative_valid_tap_count`(**전 기간 누적**)다. 날짜가 바뀌어도 리셋되지 않는다.

두 상한은 서로 맞물려 있다. `3000탭 ÷ 평균 20탭 = 150P`라 정상적으로 치면 두 상한에 동시에 닿는다.
둘 중 하나라도 걸리면 그날 포인트 적립은 멈춘다.

> **지급 간격은 하루 내내 균일하다.** 하루 적립량이 일정 선을 넘으면 간격을 벌리는 "감속 커브"가
> 한때 있었으나 정책에서 빠졌고, 코드에서도 제거됐다.

## 2. 상자

| 상자 | 누적 세션 탭 | 직전 상자로부터 |
|---|---|---|
| 1개째 | 25 | 25 (`tap.box.session.step1`) |
| 2개째 | 60 | 35 (`step2`) |
| 3개째 | 110 | 50 (`step3`) |
| 4개째 | 180 | 70 (`step4`) |
| 5개째 | 280 | 100 (`step5`) |
| 6개째~ | 460, 640, … | 180 (`tailStep`) |

**랜덤이 아니라 고정값이다.** 포인트와 달리 분산이 없다.

기준 카운터는 `user_tap_session.session_valid_tap_count`(**세션 누적**)다.
세션은 시작 후 1시간(`tap.box.session.maxDurationSeconds` = 3600)이 지나면 리셋되고,
카운터가 0으로 돌아가면서 다시 25탭부터 시작한다.

**상자에는 일일 상한이 없다.** 포인트가 멈추는 3000탭 이후에도 상자는 계속 나온다.
실질 병목은 상자 재고가 아니라 아래의 개봉 주기다.

## 3. 포인트와 상자는 동시에 지급되지 않는다

자주 나오는 오해라 명시한다. 둘은 **기준 카운터도 리셋 주기도 다르다.**

| | 포인트 | 상자 |
|---|---|---|
| 기준 카운터 | 전 기간 누적 탭 | 세션 누적 탭 (1시간마다 리셋) |
| 간격 | 15~25탭 랜덤 | 25/60/110/180/280/460… 고정 |
| 일일 상한 | 있음 (150P / 3000탭) | 없음 |
| 원장 기록 | `point_ledger` + 멱등키 | 없음 (`keycap_box_account` 카운터 증가) |

같은 배치 응답에 `pointsAwarded`와 `boxesDropped`가 함께 담기지만, 둘이 같이 나오는 건 우연이다.
보장되는 건 **같은 트랜잭션이라 롤백되면 둘 다 안 나간다**는 것뿐이다.

## 4. 상자 개봉

| 항목 | 값 | 설정 키 |
|---|---|---|
| 개봉 주기 | 60초 | `keycapBox.openCycle.durationSeconds` |
| 주기당 무료 개봉 | 2회 | `keycapBox.freeOpen.limit` |
| 주기당 광고 개봉 | 2회 | `keycapBox.adOpen.limit` |
| 광고 개봉 일일 한도 | 2회 | `keycapBox.adOpen.dailyLimit` |
| 무료 개봉권 충전 | 시간당 1장 | `keycapBox.freeTicket.refillPerHour` |
| 무료 개봉권 보유 상한 | 8장 | `keycapBox.freeTicket.cap` |

## 5. 출금

| 항목 | 값 | 설정 키 |
|---|---|---|
| 최소 출금 | 50P | `cashout.minimumPoint` |
| 교환비 | 1P = 0.02원 (**50P = 1원**) | `cashout.pointToKrwRate` |

## 6. 유령 설정 키 주의

`app_config`에는 **코드가 읽지 않는 키가 남아 있을 수 있다.** 시더가 append-only라 키를 지우지 않기 때문에,
정책이 바뀌어 코드에서 키를 없애도 DB 행은 그대로 남는다. 그 값을 고쳐도 동작은 바뀌지 않는다.

부팅 시 시더가 이런 키를 찾아 경고 로그를 남긴다.

```
AppConfig 에 코드가 읽지 않는 정책 키가 남아 있습니다 (값을 바꿔도 동작에 반영되지 않습니다): [...]
```

로그에 뜨는 키는 DB에서 지워도 안전하다. 판단이 필요하므로 자동 삭제는 하지 않는다.

## 확인 방법

코드가 아는 키 전체:

```bash
grep -rhoE '"[a-z][a-zA-Z]+\.[a-zA-Z0-9.]+"' src/main/java/com/ggukmoney/beanzip/global/config/*.java \
  | tr -d '"' | sort -u
```

DB에 있는 키 전체:

```sql
SELECT DISTINCT config_key FROM app_config ORDER BY 1;
```

두 목록의 차이가 곧 유령 키다.
