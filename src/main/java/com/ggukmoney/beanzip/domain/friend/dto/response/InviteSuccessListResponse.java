package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "초대한 사람이 아직 확인하지 않은 초대 성공 목록이에요.")
public record InviteSuccessListResponse(
        @Schema(description = "확인하지 않은 초대 성공이에요. 오래된 것부터 최대 100건이에요.")
        List<Item> invites,
        @Schema(description = "초대 성공 한 건을 확인할 때 받는 조각 수예요.", example = "10")
        int shardsPerInvite
) {
    @Schema(description = "초대 성공 한 건이에요.")
    public record Item(
            @Schema(description = "초대 ID예요. 확인(ack)할 때 보내요.", example = "6a1f3c5e-7b92-4d08-a1c4-5e9f2b7d8c30")
            UUID inviteId,
            @Schema(description = "내 초대로 가입한 친구예요.")
            FriendProfileResponse friend,
            @Schema(description = "오늘 이 친구에게 이미 응원했는지예요. `true`면 앱은 '응원하기' 버튼을 숨겨요.", example = "false")
            boolean cheeredToday
    ) {
    }
}
