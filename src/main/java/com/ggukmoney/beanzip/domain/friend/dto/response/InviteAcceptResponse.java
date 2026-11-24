package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대 수락 결과예요.")
public record InviteAcceptResponse(
        @Schema(description = "친구가 된 사람(초대한 사람)이에요.")
        FriendProfileResponse friend,
        @Schema(description = "이미 친구였는지예요. `true`면 아무것도 바뀌지 않았고 푸시도 가지 않아요.", example = "false")
        boolean alreadyFriends
) {
}
