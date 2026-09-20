# BEA-296 Weekly Ranking Reward Design

## Goal

매주 월요일 00:00 KST에 종료되는 주간 랭킹에서 보상 대상자를 확정하고, 사용자가 순위 변동 알림에 동의한 뒤 다음 주차 마감 전까지 직접 내부 포인트를 수령하게 한다. 마감 당시의 대상, 순위, 점수, 금액을 보존하며 재실행과 동시 요청에도 중복 지급하지 않는다.

## Product Decisions

- 보상은 내부 포인트이며 최초 정책은 1등 10,000P, 2등 5,000P, 3등 2,500P다.
- 보상 대상 수와 금액은 절대 등수 매핑으로 관리한다. 상위 비율은 사용하지 않는다.
- 보상 정책은 `app_config`의 `ranking.weeklyReward.policy` JSON 값으로 관리한다.
- 알림 동의는 마감 시점이 아니라 클레임 시점에 판정한다.
- 클레임 기한은 해당 시즌 종료 시각부터 7일이며, 다음 주차 종료 시각 미만까지다.
- BEA-103이 완료되거나 운영자가 위험을 명시적으로 승인하기 전까지 정책을 `enabled=false`로 배포한다.
- 공개 랭킹 순위와 보상 순위를 분리한다. BEA-308의 내부 계정과 시스템 부스트 수령자를 제외한 뒤 보상 순위를 다시 부여한다.

## Existing Foundations

- `RankingSeasonService`는 월요일 00:00 KST에 시즌을 `FINALIZING`으로 전환하고 10분 보정 후 최종 순위를 저장한다.
- `RankingEntry`에는 `finalRank`, `finalizedAt`, 최종 점수가 남는다.
- `RankingBoostRewardExclusions`는 내부 계정과 해당 시즌의 시스템 부스트 수령자를 보상 후보에서 제외한다.
- `NotificationPreference`의 `RANK_CHANGE` 설정은 `enabled && agreementStatus == AGREED`일 때만 전송 가능한 상태다.
- `PointLedger`는 `(user_id, idempotency_key)` 유니크 제약을 제공한다.
- 운영 DB는 `ddl-auto=validate`를 사용하므로 엔티티 배포 전에 수동 DDL을 적용해야 한다.

## Reward Policy

정책 키는 `ranking.weeklyReward.policy`이며 초기 값은 다음과 같다.

```json
{
  "enabled": false,
  "rewards": {
    "1": 10000,
    "2": 5000,
    "3": 2500
  }
}
```

정책 로더는 `AppConfigBatchLoader`와 Jackson을 사용한다. 정책은 사용할 때 최신 유효 행을 읽어 배포 없이 변경 사항을 반영한다. 다음 조건을 모두 만족해야 유효하다.

- `enabled`는 boolean이다.
- `rewards`는 비어 있지 않은 객체다.
- 등수 키는 1부터 시작하는 연속된 양의 정수다.
- 포인트 금액은 모두 양수 `long` 범위다.

정책 행이 없거나 형식이 잘못되면 현재 랭킹 조회에서는 보상 미리보기를 비활성 상태로 반환하고 핵심 랭킹 조회는 유지한다. 시즌 종료에서는 오류를 발생시켜 전체 종료 트랜잭션을 롤백하고 시즌을 `FINALIZING`에 남긴다. 명시적인 `enabled=false`만 보상 생성 없이 시즌을 정상 종료시키는 값이다.

현재 주차 화면은 조회 시점의 정책을 사용한다. 종료된 주차의 조회와 지급은 보상 행에 저장된 금액을 사용하므로 이후 정책 변경의 영향을 받지 않는다.

## Persistence Model

새 엔티티와 테이블 이름은 각각 `WeeklyRankingReward`, `weekly_ranking_reward`로 한다.

| Column | Meaning |
| --- | --- |
| `id`, `public_id` | 내부 PK와 API 공개 UUID |
| `season_id`, `user_id` | 종료 시즌과 수상 사용자 |
| `source_final_rank` | 공개 랭킹에 저장된 최종 순위 |
| `reward_rank` | 제외 대상 제거 후 다시 부여한 보상 순위 |
| `final_score` | 마감 시점 최종 점수 |
| `point_amount` | 마감 시점 정책에서 고정한 포인트 |
| `status` | `OPENED` 또는 `CLAIMED` |
| `expires_at` | `season.endsAt + 7 days` |
| `claimed_at` | 실제 수령 시각, 미수령이면 null |
| `created_at`, `updated_at` | 감사 시각 |

DB 제약은 다음을 강제한다.

- `(season_id, user_id)` 유니크: 한 사용자는 한 시즌에 하나의 보상만 가진다.
- `(season_id, reward_rank)` 유니크: 한 시즌의 보상 등수는 한 명에게만 배정된다.
- `public_id` 유니크.
- 순위, 점수, 금액은 양수다.
- `OPENED`는 `claimed_at IS NULL`, `CLAIMED`는 `claimed_at IS NOT NULL`이다.

수동 DDL은 테이블, FK, 유니크 제약, 체크 제약, 조회용 인덱스를 생성하고 정책 행이 없을 때만 비활성 초기 정책을 추가한다. 재실행 가능한 `IF NOT EXISTS` 형태로 작성한다.

## Finalization Flow

기존 `RankingSeasonService.closeFinalizingWeeklySeason` 트랜잭션 안에서 다음 순서를 지킨다.

1. 주간 탭 집계를 보정한다.
2. 기존 최종 순위 충돌을 검사하고 전체 참가자의 최종 순위를 저장한다.
3. 보상 정책을 한 번 읽는다.
4. `enabled=false`면 보상 생성 없이 시즌을 닫는다.
5. 정책이나 BEA-308 제외 목록을 읽지 못하면 예외를 발생시켜 트랜잭션을 롤백한다.
6. 활성 사용자, 양수 점수, 제외 대상이 아닌 참가자를 `score DESC, user_id DESC`로 조회한다.
7. 정책 등수 수만큼 후보를 가져와 조회 순서대로 보상 순위 1..N을 부여하고 `OPENED` 보상 행을 저장한다.
8. 시즌을 `CLOSED`로 전환한다.

참가자가 정책 등수 수보다 적으면 존재하는 후보에게만 보상을 생성한다. 후보가 없으면 0건을 생성하고 시즌을 정상 종료한다. 생성 로그에는 시즌 ID, 정책 활성 여부, 생성 수, 제외 수만 남긴다.

## Claim Flow

`POST /api/rankings/rewards/{rewardId}/claim`은 인증 사용자 기준으로 동작하며 하나의 트랜잭션에서 처리한다.

1. `(public_id, user_id)`로 보상 행을 비관적 잠금 조회한다. 다른 사용자의 UUID와 존재하지 않는 UUID는 모두 `404 RANKING_REWARD_NOT_FOUND`로 처리한다.
2. 이미 `CLAIMED`면 추가 적립 없이 저장된 성공 응답을 반환한다.
3. `now >= expiresAt`이면 `410 RANKING_REWARD_EXPIRED`를 반환한다.
4. 해당 사용자의 `RANK_CHANGE` 알림이 `enabled && AGREED`인지 검사한다. 아니면 `403 RANKING_REWARD_CONSENT_REQUIRED`를 반환한다.
5. 저장된 `pointAmount`를 포인트 계정에 적립하고 원장 사유 `WEEKLY_RANKING_REWARD`, 멱등키 `reward.publicId`로 기록한다.
6. 보상을 `CLAIMED`로 전환하고 `claimedAt`을 저장한다.

행 잠금이 같은 보상에 대한 동시 요청을 직렬화하고, 포인트 원장 유니크 제약이 이중 적립을 한 번 더 방어한다. 어느 단계에서든 실패하면 계정, 원장, 보상 상태를 모두 롤백한다.

## API Contract

### Current Ranking

`GET /api/rankings/current`에 다음 필드를 추가한다.

- 최상위 응답의 `rewardTiers`: `[{ rewardRank, pointAmount }]`. 정책이 비활성 또는 사용 불가면 빈 목록이다.
- 각 랭킹 항목의 nullable `rewardRank`, `rewardPointAmount`: 시스템 제외 대상 제거 후 계산한 현재 예상 보상이다.
- 내 랭킹의 nullable `rewardRank`, `rewardPointAmount`, `scoreGapToReward`.

`scoreGapToReward`는 현재 보상권이면 0, 보상권 밖이면 마지막 보상권 후보를 확실히 앞서는 데 필요한 최소 점수다. 동점 정렬이 `user_id DESC`이므로 보상권 밖의 동점 사용자는 1점을 더 필요로 한다. 참가자가 보상 등수보다 적으면 새 참가자의 필요 점수는 1이다. 정책이 비활성, 사용 불가, 또는 현재 사용자가 보상 제외 대상이면 null이다.

### Latest Closed Result

`GET /api/rankings/rewards/latest`는 가장 최근 종료된 주간 시즌을 반환한다.

- 시즌: `seasonCode`, `startedAt`, `endedAt`.
- 수상자: `rewardRank`, `sourceFinalRank`, `userId`, 마스킹 닉네임, 프로필 이미지, `finalScore`, `pointAmount`, `isMe`.
- `myReward`: 수상자가 아니면 null. 수상자면 `rewardId`, `rewardRank`, `pointAmount`, `claimStatus`, `expiresAt`, `claimedAt`.

공개 `claimStatus`는 저장 상태와 현재 시각, 알림 동의 상태에서 계산한다.

- `CLAIMED`: 이미 수령함.
- `EXPIRED`: 미수령이고 `now >= expiresAt`.
- `CONSENT_REQUIRED`: 미수령·미만료이며 `RANK_CHANGE`가 전송 가능한 상태가 아님.
- `CLAIMABLE`: 미수령·미만료이며 `RANK_CHANGE`가 전송 가능한 상태임.

종료된 주간 시즌이 하나도 없으면 `404 RANKING_REWARD_RESULT_NOT_FOUND`를 반환한다. 정책이 비활성이었던 시즌은 빈 수상자 목록과 null `myReward`를 정상 응답한다.

### Claim Response

`POST /api/rankings/rewards/{rewardId}/claim`은 `myReward`와 같은 구조를 반환하며 성공 시 `claimStatus=CLAIMED`다. 같은 인증 사용자의 반복 요청은 멱등 성공이다.

신규 오류 코드는 다음과 같다.

- `RANKING_REWARD_RESULT_NOT_FOUND` — 404
- `RANKING_REWARD_NOT_FOUND` — 404
- `RANKING_REWARD_CONSENT_REQUIRED` — 403
- `RANKING_REWARD_EXPIRED` — 410

## Testing

- 정책: 정상 매핑, 비연속 등수, 빈 매핑, 0·음수 금액, 잘못된 타입, 누락 정책, 비활성 정책.
- 저장소: 후보 정렬, 비활성·0점·시스템 계정 제외, 참가자 부족, 시즌/사용자 및 시즌/보상등수 유니크 제약.
- 시즌 종료: 보정과 최종 순위 저장 후 보상 생성, 금액 스냅샷, 비활성 종료, 정책/제외 목록 실패 시 롤백, 스케줄러 재시도.
- 클레임: 성공, 알림 미동의, 알림 비활성, 정확한 `RANK_CHANGE` 타입 판정, 만료 경계, 타 사용자 UUID, 반복 요청, 두 동시 요청 중 한 번만 적립, 포인트 처리 실패 시 전체 롤백.
- 조회 API: 현재 정책 티어, 시스템 계정 제외 후 예상 보상 순위, 동점 포함 `scoreGapToReward`, 최근 시즌 수상자, 비수상자, 네 가지 공개 클레임 상태.
- PostgreSQL 통합: 수동 DDL 계약, 비관적 잠금과 원장 멱등키를 포함한 동시성 시나리오.

## Rollout

1. 운영 DB 식별자와 현재 스키마를 확인한다.
2. 수동 DDL을 적용하고 테이블, 제약, 인덱스, 비활성 정책 행을 검증한다.
3. 애플리케이션을 배포하고 `ddl-auto=validate` 기동을 확인한다.
4. `enabled=false` 상태에서 현재 랭킹 응답, 시즌 종료, 최근 결과 조회가 기존 동작을 해치지 않는지 확인한다.
5. BEA-103 완료 또는 명시적 위험 승인 후 새 `app_config` 행을 추가해 정책을 활성화한다.
6. 첫 활성 마감에서 보상 행 수, 제외 수, 금액 합계가 정책과 일치하는지 확인한다.

