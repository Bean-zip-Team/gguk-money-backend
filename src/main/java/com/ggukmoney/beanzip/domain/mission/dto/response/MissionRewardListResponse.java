package com.ggukmoney.beanzip.domain.mission.dto.response;

import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 미션 보상 이력 (BEA-299).
 *
 * <p>받지 못한 채 자정을 넘긴 보상도 여기 남는다. 소멸은 정책이지만 얼마를 놓쳤는지는 보여줄 수
 * 있어야 하고, 소멸 규모를 재야 정책이 과한지 판단할 수 있다.
 */
@Schema(description = "미션 보상 목록 응답이에요.")
public record MissionRewardListResponse(
        @Schema(description = "보상 목록이에요. 달성 시각 기준 최신순이에요.")
        List<Reward> rewards,

        @Schema(description = "보상 금액 합계(포인트)예요. `rewards` 필드에 담긴 보상의 포인트를 합한 값이에요.", example = "320")
        long totalPointAmount
) {

    @Schema(description = "미션 보상 하나예요.")
    public record Reward(
            @Schema(description = "보상 ID예요. 수령 API 경로에 넣어요.")
            UUID rewardId,

            @Schema(description = "미션 코드예요.", example = "TAP_1000")
            String missionCode,

            @Schema(description = "반복 단위예요. 데일리 보상은 날짜, 단발성 보상은 `ONCE`예요.", example = "2026-09-21")
            String periodKey,

            @Schema(description = "보상 포인트예요.", example = "20")
            long rewardPointAmount,

            @Schema(description = "보상 상태예요. `CLAIMABLE`은 받을 수 있음, `CLAIMED`는 이미 받음, "
                    + "`EXPIRED`는 받지 못한 채 소멸함이에요.", example = "CLAIMABLE")
            RewardStatus status,

            @Schema(description = "달성 시각이에요.")
            Instant achievedAt,

            @Schema(description = "소멸 시각이에요. 단발성 보상은 만료가 없어 null이에요.")
            Instant expiresAt,

            @Schema(description = "수령 시각이에요. 받지 않았으면 null이에요.")
            Instant claimedAt
    ) {
    }

    /**
     * 보상 상태. 저장 모델의 상태를 그대로 노출하지 않는다 — 영속 모델에 값을 하나 추가하는 일이
     * 곧 공개 계약 변경이 되면 곤란하다.
     */
    @Schema(description = "보상 상태예요.")
    public enum RewardStatus {
        @Schema(description = "받을 수 있어요.")
        CLAIMABLE,

        @Schema(description = "이미 받았어요.")
        CLAIMED,

        @Schema(description = "받지 못한 채 소멸했어요.")
        EXPIRED;

        public static RewardStatus from(MissionReward.Status status) {
            return switch (status) {
                case CLAIMABLE -> CLAIMABLE;
                case CLAIMED -> CLAIMED;
                case EXPIRED -> EXPIRED;
            };
        }

        public MissionReward.Status toEntityStatus() {
            return switch (this) {
                case CLAIMABLE -> MissionReward.Status.CLAIMABLE;
                case CLAIMED -> MissionReward.Status.CLAIMED;
                case EXPIRED -> MissionReward.Status.EXPIRED;
            };
        }
    }
}
