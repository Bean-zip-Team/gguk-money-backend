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
@Schema(description = "미션 목록 응답")
public record MissionListResponse(
        @Schema(description = "미션 목록. 상시 미션이 먼저 오고, includeDaily=true 일 때만 데일리 미션이 뒤따른다.")
        List<Mission> missions,

        @Schema(description = "오늘의 데일리 미션 요약. includeDaily=true 일 때만 채워진다.")
        DailySummary daily
) {

    @Schema(description = "미션")
    public record Mission(
            @Schema(description = "미션 코드", example = "KEYCAP_FIVE_COMPLETE")
            String code,

            @Schema(description = "미션 이름. 토스 혜택탭에 노출되는 이름과 같다.", example = "키캡 5개 모으기")
            String name,

            @Schema(description = "미션 설명. 없을 수 있다.", example = "데일리 미션 알림을 받으면 드려요")
            String description,

            @Schema(description = "주기", example = "ONE_TIME")
            PeriodType periodType,

            @Schema(description = "보상 재화", example = "TOSS_POINT")
            RewardType rewardType,

            @Schema(description = "보상 금액", example = "500")
            long rewardAmount,

            @Schema(description = "현재 진행도. 커트오프가 적용된 값이다.", example = "2")
            long current,

            @Schema(description = "달성에 필요한 값", example = "5")
            long target,

            @Schema(description = "상태", example = "IN_PROGRESS")
            Status status,

            @Schema(description = "수령 상태. 데일리 미션에만 채워지고 상시 미션은 null 이다 — 상시 미션은 "
                    + "달성하면 서버가 알아서 지급한다.")
            ClaimStatus claimStatus,

            @Schema(description = "수령 API 에 넘길 보상 식별자. 아직 달성하지 않았으면 null 이다.")
            UUID rewardId
    ) {
    }

    @Schema(description = "오늘의 데일리 미션 요약. 진행도 게이지와 리셋 타이머가 쓴다.")
    public record DailySummary(
            @Schema(description = "오늘 달성한 데일리 미션 수. 단발성 미션은 세지 않는다.", example = "2")
            int completedCount,

            @Schema(description = "오늘 나온 데일리 미션 수. 단발성 미션은 세지 않는다. 월요일은 랭킹 미션이 "
                    + "빠져 줄어들고, BEA-299 4단계 전까지는 랭킹 미션이 매일 빠진다.", example = "6")
            int totalCount,

            @Schema(description = "받을 수 있는 보상 합계. 수령 API 가 붙기 전(BEA-299 2단계)에는 "
                    + "오늘 달성한 보상 합계와 같다.", example = "110")
            long claimableRewardTotal,

            @Schema(description = "다음 초기화 시각. 기기 시각이 아니라 이 값을 기준으로 타이머를 돌린다.")
            Instant resetAt,

            @Schema(description = "연속 출석 일수. 최대 60일까지 센다.", example = "4")
            int consecutiveAttendanceDays
    ) {
    }

    @Schema(description = "미션 상태")
    public enum Status {
        @Schema(description = "진행 중")
        IN_PROGRESS,

        @Schema(description = "달성했고 지급 처리 중이거나 수령 대기 중")
        ACHIEVED,

        @Schema(description = "지급 완료")
        REWARDED
    }

    @Schema(description = "미션 주기")
    public enum PeriodType {
        @Schema(description = "한 번만 달성할 수 있다")
        ONE_TIME,

        @Schema(description = "매일 자정에 초기화된다")
        DAILY
    }

    @Schema(description = "보상 재화")
    public enum RewardType {
        @Schema(description = "토스 포인트")
        TOSS_POINT,

        @Schema(description = "앱 내부 포인트")
        INTERNAL_POINT
    }

    @Schema(description = "수령 상태")
    public enum ClaimStatus {
        @Schema(description = "아직 달성하지 못했다")
        LOCKED,

        @Schema(description = "달성했고 받을 수 있다")
        CLAIMABLE,

        @Schema(description = "이미 받았다")
        CLAIMED,

        @Schema(description = "받지 못한 채 자정을 넘겨 소멸했다")
        EXPIRED
    }
}
