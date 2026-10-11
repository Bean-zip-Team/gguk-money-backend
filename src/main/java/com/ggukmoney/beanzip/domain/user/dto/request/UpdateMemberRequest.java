package com.ggukmoney.beanzip.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

@Schema(description = "회원 정보 수정 요청이에요. `nickname` 필드와 `profileImageUrl` 필드 중 하나 이상을 보내야 해요.")
public record UpdateMemberRequest(
        @Schema(description = "바꿀 닉네임이에요. 최대 50자까지 쓸 수 있어요. 공백만 있는 값은 받지 않아요. 보내지 않으면 기존 닉네임을 유지해요.", example = "Bean")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname,

        @Schema(description = "바꿀 프로필 이미지 URL이에요. 최대 2048자까지 쓸 수 있어요. 보내지 않으면 기존 값을 유지해요. 빈 문자열을 보내면 프로필 이미지를 지워요.", example = "https://example.com/profile.png")
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
