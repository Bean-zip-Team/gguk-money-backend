package com.ggukmoney.beanzip.domain.user.dto.response;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "내 회원 정보 응답이에요.")
public record MemberMeResponse(
        @Schema(description = "사용자 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "회원 상태예요. `ACTIVE`는 활성, `SUSPENDED`는 정지, `WITHDRAWN`은 탈퇴예요.", example = "ACTIVE")
        String status,
        @Schema(description = "마스킹한 닉네임이에요. 세 글자 이상이면 첫 글자와 마지막 글자만 남기고 나머지를 `*`로 가려요. 두 글자면 두 번째 글자를 가려요. 닉네임이 없으면 null이에요.", example = "B**n")
        String nickname,
        @Schema(description = "프로필 이미지 URL이에요. 등록한 이미지가 없으면 null이에요.", example = "https://example.com/profile.png")
        String profileImageUrl,
        @Schema(description = "현재 장착한 키캡이에요. 장착한 키캡이 없으면 null이에요.")
        EquippedKeycapResponse equippedKeycap,
        @Schema(description = "현재 포인트 잔액이에요.", example = "15")
        long pointBalance
) {
}
