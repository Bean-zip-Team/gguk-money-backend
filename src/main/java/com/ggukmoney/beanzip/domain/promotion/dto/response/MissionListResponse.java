package com.ggukmoney.beanzip.domain.promotion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 유저용 미션 목록 (BEA-292, BEA-299).
 *
 * <p>혜택탭에서 미션을 보고 들어온 유저가 앱 안에서 자기 진행도를 확인할 수 있게 한다. 지금은
 * 랜딩이 홈이라 무엇을 하러 왔는지 알 방법이 없어 퍼널이 문 앞에서 끊긴다.
 *
 * <p>상시 미션과 데일리 미션을 <b>한 목록으로</b> 내려준다. 화면이 갈라지면 유저는 둘 다 보지
 * 않는다. 두 미션은 보상 재화도 지급 방식도 다르므로 {@code periodType}·{@code rewardType}·
 * {@code claimStatus} 로 구분한다.
 *
 * <p>운영자용 지급 현황({@code /api/ops/promotions/grants})과는 별개다.
 */
@Schema(description = "미션 목록 응답이에요.")
public record MissionListResponse(
        @Schema(description = "미션 목록이에요. 상시 미션이 먼저 오고 데일리 미션이 뒤따라요.")
        List<Mission> missions,

        @Schema(description = "오늘의 데일리 미션 요약이에요.")
        DailySummary daily
) {

    @Schema(description = "미션 하나예요.")
    public record Mission(
            @Schema(description = "미션 코드예요. 상시 미션은 내부 프로모션 코드를 그대로 써요.", example = "KEYCAP_FIVE_COMPLETE")
            String code,

            @Schema(description = "미션 이름이에요. 상시 미션은 토스 혜택탭에 노출되는 이름과 같아요.", example = "키캡 5종 모으기")
            String name,

            @Schema(description = "미션 설명이에요. 설명이 없으면 null이에요. 상시 미션은 항상 null이에요.", example = "데일리 미션 알림을 받으면 드려요")
            String description,

            @Schema(description = "미션 종류예요. 데일리 미션에만 채워지고 상시 미션은 null이에요. "
                    + "상시 미션은 프로모션 기반이라 이 분류를 갖지 않아요. "
                    + "화면을 종류마다 다르게 그려야 하므로 `code` 필드로 추측하지 말고 이 값을 써요. "
                    + "`ATTENDANCE`는 출석, `TAP_COUNT`는 오늘 누른 횟수, `RANK_UP`은 전일 대비 랭킹 상승, "
                    + "`NOTIFICATION_OPT_IN`은 데일리 미션 알림 동의예요.", example = "TAP_COUNT")
            MissionType missionType,

            @Schema(description = "미션 주기예요. `ONE_TIME`은 한 번만 달성할 수 있는 미션, "
                    + "`DAILY`는 매일 자정에 초기화되는 미션이에요. 상시 미션은 항상 `ONE_TIME`이에요.", example = "ONE_TIME")
            PeriodType periodType,

            @Schema(description = "보상 재화예요. `TOSS_POINT`는 토스 포인트, `INTERNAL_POINT`는 앱 내부 포인트예요. "
                    + "상시 미션은 `TOSS_POINT`, 데일리 미션은 `INTERNAL_POINT`예요.", example = "TOSS_POINT")
            RewardType rewardType,

            @Schema(description = "보상 금액(포인트)이에요. 어떤 포인트인지는 `rewardType` 필드를 따라요.", example = "500")
            long rewardAmount,

            @Schema(description = "현재 진행도예요. 상시 미션은 커트오프가 적용된 값이에요. "
                    + "키캡 5종 모으기 미션은 커트오프 이후 새로 얻은 키캡 수를 세요. "
                    + "이 미션의 지급 여부는 뽑기로 새 키캡을 처음 얻을 때 판정해요.", example = "2")
            long current,

            @Schema(description = "달성에 필요한 값이에요.", example = "5")
            long target,

            @Schema(description = "미션 상태예요. `IN_PROGRESS`는 진행 중, "
                    + "`ACHIEVED`는 달성했고 지급 처리 중이거나 수령 대기 중, `REWARDED`는 지급 완료예요. "
                    + "지급에 실패한 상시 미션도 `ACHIEVED`예요.", example = "IN_PROGRESS")
            Status status,

            @Schema(description = "수령 상태예요. 데일리 미션에만 채워지고 상시 미션은 null이에요. "
                    + "상시 미션은 달성하면 서버가 알아서 지급해요. "
                    + "`LOCKED`는 아직 달성하지 못함, `CLAIMABLE`은 받을 수 있음, `CLAIMED`는 이미 받음, "
                    + "`EXPIRED`는 받지 못한 채 자정을 넘겨 소멸함이에요.")
            ClaimStatus claimStatus,

            @Schema(description = "수령 API에 넘길 보상 ID예요. 아직 달성하지 않았으면 null이에요. "
                    + "상시 미션은 받기 버튼이 없어 항상 null이에요.")
            UUID rewardId
    ) {
    }

    @Schema(description = "오늘의 데일리 미션 요약이에요. 진행도 게이지와 리셋 타이머가 써요.")
    public record DailySummary(
            @Schema(description = "오늘 달성한 데일리 미션 수예요. 단발성 미션은 세지 않아요.", example = "2")
            int completedCount,

            @Schema(description = "오늘 나온 데일리 미션 수예요. 단발성 미션은 세지 않아요. "
                    + "월요일은 랭킹 미션이 빠져 줄어들어요. "
                    + "BEA-299 4단계 전에는 랭킹 미션이 매일 빠졌어요.", example = "6")
            int totalCount,

            @Schema(description = "받을 수 있는 보상 합계(포인트)예요. 단발성 미션 보상도 합산하고, 이미 소멸한 보상은 빼요. "
                    + "수령 API가 붙기 전(BEA-299 2단계)에는 오늘 달성한 보상 합계와 같았어요.", example = "110")
            long claimableRewardTotal,

            @Schema(description = "다음 초기화 시각이에요. 서비스 기준 시간대의 다음 날 자정이에요. "
                    + "기기 시각이 아니라 이 값을 기준으로 타이머를 돌려요.")
            Instant resetAt,

            @Schema(description = "연속 출석 일수예요. 최대 60일까지 세요. "
                    + "오늘 출석 기록이 아직 없으면 어제부터 거꾸로 세요.", example = "4")
            int consecutiveAttendanceDays
    ) {
    }

    @Schema(description = "미션 상태예요.")
    public enum Status {
        @Schema(description = "진행 중이에요.")
        IN_PROGRESS,

        @Schema(description = "달성했고 지급 처리 중이거나 수령 대기 중이에요.")
        ACHIEVED,

        @Schema(description = "지급을 완료했어요.")
        REWARDED
    }

    @Schema(description = "미션 종류예요.")
    public enum MissionType {
        @Schema(description = "출석이에요. 앱에 들어오면 달성돼요.")
        ATTENDANCE,

        @Schema(description = "클릭 횟수예요. 진행바로 그려요.")
        TAP_COUNT,

        @Schema(description = "전일 대비 랭킹 상승이에요.")
        RANK_UP,

        @Schema(description = "데일리 미션 알림 동의예요. 누르면 토스 동의 시트를 띄워요.")
        NOTIFICATION_OPT_IN
    }

    @Schema(description = "미션 주기예요.")
    public enum PeriodType {
        @Schema(description = "한 번만 달성할 수 있어요.")
        ONE_TIME,

        @Schema(description = "매일 자정에 초기화돼요.")
        DAILY
    }

    @Schema(description = "보상 재화예요.")
    public enum RewardType {
        @Schema(description = "토스 포인트예요.")
        TOSS_POINT,

        @Schema(description = "앱 내부 포인트예요.")
        INTERNAL_POINT
    }

    @Schema(description = "수령 상태예요.")
    public enum ClaimStatus {
        @Schema(description = "아직 달성하지 못했어요.")
        LOCKED,

        @Schema(description = "달성했고 받을 수 있어요.")
        CLAIMABLE,

        @Schema(description = "이미 받았어요.")
        CLAIMED,

        @Schema(description = "받지 못한 채 자정을 넘겨 소멸했어요.")
        EXPIRED
    }
}
