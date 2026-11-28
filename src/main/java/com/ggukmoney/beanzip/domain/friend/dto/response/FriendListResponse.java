package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "친구 목록이에요.")
public record FriendListResponse(
        @Schema(description = "하루에 응원할 수 있는 친구 수예요.", example = "10")
        int cheerDailyLimit,
        @Schema(description = "오늘 더 응원할 수 있는 친구 수예요.", example = "7")
        int cheersLeft,
        @Schema(description = "친구들이에요. 오늘 응원하지 않은 친구가 먼저 나오고, 그 안에서는 마지막으로 누른 날이 오래된 친구가 먼저 나와요. 탈퇴한 친구는 빠지고 정지된 친구는 보여요.")
        List<Friend> friends
) {
    @Schema(description = "친구 한 명이에요.")
    public record Friend(
            @Schema(description = "친구의 사용자 ID예요.", example = "33333333-3333-3333-3333-333333333333")
            UUID userId,
            @Schema(description = "이름(실명)이에요. 토스에서 이름을 받지 못한 사용자는 `null`일 수 있어요.", example = "박준호", nullable = true)
            String name,
            @Schema(description = "친구가 오늘 누른 수예요. 친구가 자기 홈에서 보는 숫자(`effectiveTapCountToday`, 자동 클릭 포함)와 같아요.", example = "1240")
            long todayTapCount,
            @Schema(description = "친구가 마지막으로 누른 날(한국 시간)이에요. `todayTapCount` 와 같은 기준이라 1 이상이면 항상 오늘이에요. 한 번도 누르지 않았으면 `null`이에요. '오늘 N번 / 어제까지 / N일째 안 들어왔어요' 문구는 앱이 이 값과 `todayTapCount` 로 정해요.", example = "2026-11-24", nullable = true)
            LocalDate lastTapDate,
            @Schema(description = "오늘 이 친구에게 이미 응원했는지예요.", example = "false")
            boolean cheeredToday
    ) {
    }
}
