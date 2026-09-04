package com.ggukmoney.beanzip.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

@Schema(description = "회원 정보 수정 요청")
public record UpdateMemberRequest(
        @Schema(description = "닉네임. 공백 불가, 최대 50자", example = "Bean")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname,

        @Schema(description = "프로필 이미지 URL. 최대 2048자", example = "https://example.com/profile.png")
        @Size(max = 2048, message = "프로필 이미지 URL은 2048자 이하여야 합니다.")
        String profileImageUrl
) {

    @AssertTrue(message = "수정할 프로필 정보가 필요합니다.")
    public boolean hasUpdateField() {
        return nickname != null || profileImageUrl != null;
    }

    @AssertTrue(message = "닉네임은 공백일 수 없습니다.")
    public boolean isNicknameValid() {
        return nickname == null || StringUtils.hasText(nickname);
    }
}
