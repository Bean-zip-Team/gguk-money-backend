package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "답 응원 결과예요.")
public record CheerReplyResponse(
        @Schema(description = "응원을 보낸 친구마다의 결과예요. 받은 응원 순서예요.")
        List<Result> results,
        @Schema(description = "이 요청으로 오늘 첫 응원을 보냈는지예요.", example = "false")
        boolean firstOfDay,
        @Schema(description = "이 요청으로 내가 받은 조각 수예요. 오늘 첫 응원을 보냈을 때만 0보다 커요.", example = "0")
        int shardsGranted,
        @Schema(description = "오늘 더 응원할 수 있는 친구 수예요.", example = "5")
        int cheersLeft
) {
    @Schema(description = "친구 한 명에게 답 응원을 보낸 결과예요.")
    public record Result(
            @Schema(description = "답 응원의 대상(나에게 응원을 보낸 친구)이에요.", example = "33333333-3333-3333-3333-333333333333")
            UUID userId,
            @Schema(description = "`SENT`: 보냈어요. `ALREADY_SENT_TODAY`: 오늘 이미 이 친구에게 응원했어요. `DAILY_LIMIT`: 오늘 응원할 수 있는 수를 다 썼어요. `NOT_FRIENDS`: 친구가 아니거나 탈퇴한 사용자예요.", example = "SENT")
            Outcome result
    ) {
    }

    /** 한 사람에게 응원을 시도한 결과. */
    public enum Outcome {
        SENT,
        ALREADY_SENT_TODAY,
        DAILY_LIMIT,
        NOT_FRIENDS
    }
}
