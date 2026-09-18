# BEA-308 운영 및 BEA-296 통합 계약

## 현재 비활성 상태

`SystemRankingBoostRolloutGate.permits()`는 항상 `false`를 반환한다. DB 설정이 `enabled=true`여도 점수 적용/알림 발송을 활성화하지 못한다. 실제 보상 지급, 어뷰징 화이트리스트, 실제 탭 데이터 변경은 추가하지 않았다.

수동 DDL `src/main/resources/db/manual-system-ranking-boost.sql`을 **실제 런타임 DB 확인 → 스키마/기존 제약 검토 → DDL → 코드 배포** 순서로 적용해야 한다. 현재 작업에서 운영 DB에는 실행하지 않았다. 구버전 코드로 되돌리기 전에 boost 기능을 끄고, boost 적용 이력이 있는 시즌을 구버전 점수 덮어쓰기 로직으로 다시 동기화하지 않아야 한다. 감사 테이블/boost 컬럼은 삭제하지 않는다.

## 동적 설정

`app_config.config_key = ranking.systemBoost.policy`의 JSON 전체가 한 버전이다.

```json
{"enabled":false,"internalUserIds":[],"minimumLeaderScore":1000,"minIncrement":200,"maxIncrement":500}
```

1000은 잠정 예시다. 최소 실유저 점수와 가산 범위는 설정에서 읽으며 코드에 고정하지 않았다. UUID 목록은 운영자가 검증한 계정만 입력한다. 최신 `effective_at <= 현재시각` 버전을 매 실행/적용/발송 claim 시 DB에서 읽는다. 누락/잘못된 타입/범위/조회 오류는 fail-closed다. `TapConfigSeeder`에는 등록하지 않아 재시작이 운영 설정을 덮어쓰지 않는다. `enabled=false`는 신규 적용과 **아직 claim하지 않은** 발송을 중단하고 기존 점수를 되돌리지 않는다. 이미 claim했거나 HTTP 전송 중인 알림을 취소한다는 보장은 없다.

## 일별 실행과 복구

KST 매 5분 tick, 매일 [18:00, 22:00)의 5분 슬롯을 한 번 뽑아 날짜 unique 감사 행에 별도 트랜잭션으로 먼저 저장한다. 예정 시각 이후 창 안에서 한 번 적용하며 22:00 이후는 `WINDOW_EXPIRED`; 지난 날짜는 소급 적용하지 않는다. 주간 시즌 advisory lock → 일별 행 lock 순서로 rollover와 충돌을 제어한다. 실제 탭 projection과는 기존 optimistic version 충돌 시 적용 트랜잭션 전체가 롤백되고 다음 tick에서 같은 실행 시각으로 재시도한다.

실유저 leader 행은 native 조회 뒤 잠그고 점수/자격이 달라졌으면 적용 전체를 롤백한다. 커밋까지 점수를 고정해 순위 전후 count가 leader 자신을 잘못 앞선 참가자로 세지 않도록 한다. 일반 projection 전체가 시즌 lock을 공유하도록 바꾸지는 않았다. 다른 참가자의 자연스러운 점수 변화는 각 순위 조회 시점의 DB 상태에 반영된다.

선정은 ACTIVE 사내 계정 중 최저 **현재 유효 점수**(실제 점수+boost), 없는 entry는 0, 동점은 UUID 문자열 오름차순이다. 실유저 leader는 ACTIVE/양수 점수이며 현재 사내 목록과 해당 시즌 APPLIED 이력을 제외한다. 이미 leader보다 높은 후보는 내리지 않고 skip한다. 감사에는 `SYSTEM_RANKING_BOOST`, 이전/목표/실제 점수, boost 전후, 설정, 대상/leader, 시즌, 글로벌 순위 전후를 남긴다.

점수와 `notification_delivery` 준비는 함께 커밋한다. 글로벌 **1→2**에만 전용 prepare가 기존 RANK_CHANGE 동의/템플릿/SENT cooldown을 적용한다. 자연 변동의 3계단 하락/톱10 이탈 조건은 그대로다. commit 후 기존 Redis 이벤트/정기 reconciliation과 Toss 전송 경로를 사용한다. Redis 오류는 DB 점수를 되돌리지 않는다.

전송 전에 짧은 DB 트랜잭션으로 `dispatch_claimed_at`을 선점한다. claim 전 장애는 같은 날 창 안에서 다음 tick에 복구 가능하다. claim 이후 장애/불명확한 HTTP 결과는 자동 재발송하지 않는다. Toss 멱등성이 검증되지 않았으므로 아래 상태는 delivery/provider 기록과 함께 사람이 검토한다. RETRY_WAITING도 이 기능에서 자동 재시도하지 않는다.

```sql
SELECT run_date, status, skip_reason, season_id, selected_user_id, target_score,
       notification_delivery_id, dispatch_claimed_at
FROM ranking_boost_run ORDER BY run_date DESC;
```

감사 행을 삭제/재분류하여 다시 실행하거나 설정 목록 제거로 APPLIED 이력을 지우면 안 된다. 별도 개인정보 보존정책 수립 시 시즌 보상 제외 계약까지 검토한다.

## BEA-296 활성화 전 필수 체크리스트

- [ ] 보상 후보 생성 시 `RankingBoostRewardExclusions.excludedUserIds(seasonId, now)`를 호출한다. 반환한 **현재 사내 IDs ∪ 요청 시즌의 과거 APPLIED 수혜 IDs** 모두 제외한다. Optional.empty는 정상 빈 집합이 아니라 **후보 산출 중단**이다.
- [ ] 설정 목록에서 제거된 수혜자도 동일 시즌에서 제외되고 다른 시즌에는 이력이 전이되지 않는 통합 테스트를 BEA-296 보상 후보 경로에서 실행한다.
- [ ] 표시용 `final_rank`를 보상 순위로 그대로 지급하지 않고 제외 후 적격 보상 순위를 별도 산출한다. 최종 후보 저장/재실행 시에도 제외 계약을 적용한다.
- [ ] 실제 보상 마감 시각/금지 구간을 확정하고 TODO(BEA-296)를 해소한다. `outsideCloseWindow(season, now, guard)`를 `permits`에 연결한다. 정확한 cutoff에서 금지되는 경계 및 rollover 동시성 테스트를 추가한다.
- [ ] 동의 철회, 기존 cooldown, kill switch, 외부 전송 실패, Redis 장애 복구, 실제 탭/포인트/어뷰징 입력 불변을 통합 검증한다.
- [ ] 위 검증 완료 후에만 코드 rollout gate 변경을 리뷰/배포하고 설정을 활성화한다. 이 문서나 DB enabled 변경만으로 gate를 열지 않는다.
