package com.ggukmoney.beanzip.domain.user.dto.response;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "내 회원 정보 응답")
public record MemberMeResponse(
        @Schema(description = "사용자 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "회원 상태", example = "ACTIVE")
        String status,
        @Schema(description = "닉네임", example = "Bean")
        String nickname,
        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.png")
        String profileImageUrl,
        @Schema(description = "현재 장착 키캡")
        EquippedKeycapResponse equippedKeycap,
        @Schema(description = "포인트 잔액", example = "15")
        long pointBalance
) {
}
