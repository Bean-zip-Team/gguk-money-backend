package com.ggukmoney.beanzip.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "회원 정보 수정 응답이에요.")
public record MemberUpdateResponse(
        @Schema(description = "사용자 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "수정한 뒤의 닉네임이에요. 내 회원 정보 조회와 같은 방식으로 마스킹해요. 닉네임이 없으면 null이에요.", example = "B**n")
        String nickname,
        @Schema(description = "수정한 뒤의 프로필 이미지 URL이에요. 등록한 이미지가 없으면 null이에요.", example = "https://example.com/profile.png")
        String profileImageUrl
) {
}
