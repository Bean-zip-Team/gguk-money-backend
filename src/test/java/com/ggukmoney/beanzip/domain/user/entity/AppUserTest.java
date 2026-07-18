package com.ggukmoney.beanzip.domain.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserTest {

    @Test
    void storesNicknameAsIsWhenWithinTypicalLength() {
        AppUser user = AppUser.createActive("Bean", "https://example.com/profile.png");

        assertThat(user.getNickname()).isEqualTo("Bean");
        assertThat(user.getNicknameNormalized()).isEqualTo("bean");
    }

    @Test
    void doesNotTruncateNicknameExceedingFiftyCharactersOnLogin() {
        String longNickname = "A".repeat(60);

        AppUser user = AppUser.createActive(longNickname, null);

        assertThat(user.getNickname()).isEqualTo(longNickname);
        assertThat(user.getNicknameNormalized()).isEqualTo(longNickname.toLowerCase());
    }

    @Test
    void doesNotTruncateNicknameExceedingFiftyCharactersOnProfileUpdate() {
        AppUser user = AppUser.createActive("Bean", null);
        String longNickname = "B".repeat(60);

        user.updateProfile(longNickname, null);

        assertThat(user.getNickname()).isEqualTo(longNickname);
        assertThat(user.getNicknameNormalized()).isEqualTo(longNickname.toLowerCase());
    }

    @Test
    void doesNotTruncateProfileImageUrl() {
        String longUrl = "https://example.com/" + "a".repeat(200) + ".png";

        AppUser user = AppUser.createActive("Bean", longUrl);

        assertThat(user.getProfileImageUrl()).isEqualTo(longUrl);
    }
}
