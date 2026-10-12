package com.ggukmoney.beanzip.domain.tap.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "탭 배치 제출 요청")
public record TapBatchSubmitRequest(
        @Schema(description = "탭 세션 ID", example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "tapSessionId가 필요합니다.")
        UUID tapSessionId,

        @Schema(description = "클라이언트 배치 순번. 0 이상", example = "0")
        @NotNull(message = "sequence가 필요합니다.")
        @Min(value = 0, message = "sequence는 0 이상이어야 합니다.")
        Long sequence,

        /*
         * 상한은 정상 클라이언트가 만들 수 있는 배치 크기의 약 5배로 잡은 방어선이다.
         * 앱은 100탭 또는 30초마다 flush하고 분당 탭 상한이 420이므로 한 배치는 210탭을 넘지 않는다.
         * 상한이 없으면 상자 지급 루프 반복 횟수와 next_box_target의 int 캐스팅이
         * 클라이언트 입력에 그대로 노출된다.
         */
        @Schema(description = "제출한 탭 수. 1 이상 1000 이하", example = "10")
        @NotNull(message = "submittedCount가 필요합니다.")
        @Min(value = 1, message = "submittedCount는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "submittedCount는 1000 이하여야 합니다.")
        Integer submittedCount
) {
}
