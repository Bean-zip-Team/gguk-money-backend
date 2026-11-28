package com.ggukmoney.beanzip.domain.friend.dto.response;

import com.ggukmoney.beanzip.domain.friend.entity.Cheer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "아직 확인하지 않은 응원 목록이에요.")
public record CheerUnreadListResponse(
        @Schema(description = "확인하지 않은 응원이에요. 오래된 것부터 최대 100건이에요. 탈퇴한 사용자가 보낸 응원은 나오지 않아요.")
        List<Item> cheers
) {
    @Schema(description = "받은 응원 한 건이에요.")
    public record Item(
            @Schema(description = "응원 ID예요. 확인(ack)과 답 응원에 보내요.", example = "9b2e6c1a-4d7f-4b0e-8a35-2c9d1e7f6b40")
            UUID cheerId,
            @Schema(description = "응원을 보낸 친구예요.")
            FriendProfileResponse from,
            @Schema(description = "응원의 종류예요. `RETURN`(돌려줌) · `GIFT`(선물) · `CLAP`(박수) · `DEFAULT`(기본). 모달 문구는 앱이 정해요.", example = "CLAP")
            Cheer.Kind kind,
            @Schema(description = "응원받은 시점에 내가 그날 누른 수예요. 박수 모달의 '오늘 N번'에 써요.", example = "1240")
            long tapCountSnapshot,
            @Schema(description = "응원을 받은 시각이에요.", example = "2026-11-24T01:12:00Z")
            Instant createdAt
    ) {
    }
}
