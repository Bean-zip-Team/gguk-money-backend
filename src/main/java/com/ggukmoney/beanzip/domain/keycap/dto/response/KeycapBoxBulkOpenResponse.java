package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * 일괄 개봉 결과 (BEA-280).
 *
 * <p>단건 결과 N 개를 그대로 내려주면 프론트가 못 쓴다. 30 개를 연 뒤 보여줄 화면은 "몇 개 열어
 * 조각 몇 개를 얻었고 무엇이 완성됐는가" 한 장이므로 합산해서 준다.
 */
@Schema(description = "키캡 상자 일괄 개봉 응답")
public record KeycapBoxBulkOpenResponse(
        @Schema(description = "실제로 열린 상자 수. 보유량이 상한보다 적으면 보유량만큼이다.", example = "30")
        int openedCount,

        @Schema(description = "획득한 조각 합계", example = "45")
        int totalShardCount,

        @Schema(description = "이번에 완성된 키캡. 없으면 빈 배열이다.")
        List<CompletedKeycap> completedKeycaps,

        @Schema(description = "개봉 후 남은 상자 수. 다음 목표를 보여주는 데 쓴다.", example = "80")
        int remainingBoxBalance
) {

    @Schema(description = "완성된 키캡")
    public record CompletedKeycap(
            @Schema(description = "키캡 ID")
            UUID keycapId,

            @Schema(description = "키캡 이름", example = "응원")
            String name,

            @Schema(description = "이미지 URL", example = "https://example.com/keycaps/cheer.webp")
            String imageUrl
    ) {
    }
}
