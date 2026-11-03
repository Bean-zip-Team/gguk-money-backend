package com.ggukmoney.beanzip.domain.tap.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "탭 배치 제출 요청이에요.")
public record TapBatchSubmitRequest(
        @Schema(description = "클라이언트가 만든 탭 세션 ID예요. `sequence` 필드와 함께 같은 배치의 중복 제출을 가리는 데 써요.", example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "tapSessionId가 필요합니다.")
        UUID tapSessionId,

        @Schema(description = "클라이언트 배치 순번이에요. 0 이상이어야 해요. 같은 탭 세션에서 이미 제출한 순번을 다시 보내면 탭을 다시 적립하지 않아요.", example = "0")
        @NotNull(message = "sequence가 필요합니다.")
        @Min(value = 0, message = "sequence는 0 이상이어야 합니다.")
        Long sequence,

        /*
         * 상한은 정상 클라이언트가 만들 수 있는 배치 크기의 약 5배로 잡은 방어선이다.
         * 앱은 100탭 또는 30초마다 flush하고 분당 탭 상한이 420이므로 한 배치는 210탭을 넘지 않는다.
         * 상한이 없으면 상자 지급 루프 반복 횟수와 next_box_target의 int 캐스팅이
         * 클라이언트 입력에 그대로 노출된다.
         */
        @Schema(description = "제출한 탭 수예요. 1 이상 1000 이하여야 해요. `boosterMultiplier` 필드를 보내면 사용자가 실제로 누른 횟수예요. 보내지 않으면(구버전 앱) 앱이 부스터 배율을 미리 곱한 점수로 보고 배율을 다시 곱하지 않아요.", example = "10")
        @NotNull(message = "submittedCount가 필요합니다.")
        @Min(value = 1, message = "submittedCount는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "submittedCount는 1000 이하여야 합니다.")
        Integer submittedCount,

        @Schema(description = "이 배치에서 탭 한 번이 몇 점인지예요. 평소 1, 부스터 중에는 부스터 응답의 `multiplier` 필드 값(지금은 2)이에요. 서버는 최근 5초 안에 켜져 있던 같은 배율의 부스터가 있을 때만 이 배율을 곱하고, 없으면 1로 계산해요. 부스터 중 클릭 크리티컬이 난 탭은 탭 전체 점수에 크리티컬 배율을 곱해요. 새 앱은 평소에도 1을 보내요.", example = "2", nullable = true)
        @Min(value = 1, message = "boosterMultiplier는 1 이상이어야 합니다.")
        Integer boosterMultiplier
) {
    /** 구버전 앱 요청: boosterMultiplier 없이 배율을 미리 곱한 점수를 보낸다. */
    public TapBatchSubmitRequest(UUID tapSessionId, Long sequence, Integer submittedCount) {
        this(tapSessionId, sequence, submittedCount, null);
    }
}
