package com.ggukmoney.beanzip.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "회원 정보 수정 응답")
public record MemberUpdateResponse(
        @Schema(description = "사용자 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "수정된 닉네임", example = "Bean")
        String nickname,
        @Schema(description = "수정된 프로필 이미지 URL", example = "https://example.com/profile.png")
        String profileImageUrl
) {
}
