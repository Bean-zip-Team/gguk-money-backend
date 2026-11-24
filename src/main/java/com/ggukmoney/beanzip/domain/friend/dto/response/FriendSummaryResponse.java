package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "홈 화면에 보여 줄 친구 요약이에요.")
public record FriendSummaryResponse(
        @Schema(description = "친구 수예요. 탈퇴한 친구는 세지 않아요.", example = "4")
        int friendCount,
        @Schema(description = "오늘 한 번 이상 누른 친구 수예요.", example = "2")
        int todayTappedCount,
        @Schema(description = "오늘 누른 친구 중 많이 누른 순으로 최대 3명이에요. 홈의 얼굴 스트립에 써요.")
        List<FriendProfileResponse> todayTappedFriends,
        @Schema(description = "아직 확인하지 않은 응원 수예요. 홈의 배지에 써요.", example = "3")
        long unreadCheerCount
) {
}
