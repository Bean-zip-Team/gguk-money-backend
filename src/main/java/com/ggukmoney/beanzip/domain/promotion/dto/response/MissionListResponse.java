package com.ggukmoney.beanzip.domain.promotion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 유저용 미션 목록 (BEA-292).
 *
 * <p>혜택탭에서 미션을 보고 들어온 유저가 앱 안에서 자기 진행도를 확인할 수 있게 한다. 지금은
 * 랜딩이 홈이라 무엇을 하러 왔는지 알 방법이 없어 퍼널이 문 앞에서 끊긴다.
 *
 * <p>운영자용 지급 현황({@code /api/ops/promotions/grants})과는 별개다.
 */
@Schema(description = "미션 목록 응답")
public record MissionListResponse(
        @Schema(description = "미션 목록")
        List<Mission> missions
) {

    @Schema(description = "미션")
    public record Mission(
            @Schema(description = "미션 코드", example = "KEYCAP_FIVE_COMPLETE")
            String code,

            @Schema(description = "미션 이름. 토스 혜택탭에 노출되는 이름과 같다.", example = "키캡 5개 모으기")
            String name,

            @Schema(description = "보상 토스 포인트", example = "500")
            long rewardAmount,

            @Schema(description = "현재 진행도. 커트오프가 적용된 값이다.", example = "2")
            long current,

            @Schema(description = "달성에 필요한 값", example = "5")
            long target,

            @Schema(description = "상태", example = "IN_PROGRESS")
            Status status
    ) {
    }

    @Schema(description = "미션 상태")
    public enum Status {
        @Schema(description = "진행 중")
        IN_PROGRESS,

        @Schema(description = "달성했고 지급 처리 중")
        ACHIEVED,

        @Schema(description = "지급 완료")
        REWARDED
    }
}
