package com.ggukmoney.beanzip.domain.user.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

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

    @Test
    void reactivatesWithdrawnUserWithLatestTossProfile() {
        AppUser user = AppUser.createActive("Bean", "https://example.com/old.png");
        user.withdraw();

        user.reactivate(" Toss Bean ", " https://example.com/new.png ");

        assertThat(user.isWithdrawn()).isFalse();
        assertThat(user.getStatus()).isEqualTo(AppUser.Status.ACTIVE);
        assertThat(user.getWithdrawnAt()).isNull();
        assertThat(user.getNickname()).isEqualTo("Toss Bean");
        assertThat(user.getNicknameNormalized()).isEqualTo("toss bean");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new.png");
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void keepsSuspensionWhenSuspendedUserWithdrawsAndReactivates() {
        AppUser user = AppUser.createActive("Bean", null);
        user.suspend();
        Instant suspendedAt = user.getSuspendedAt();
        user.withdraw();

        user.reactivate("Bean", null);

        assertThat(user.isWithdrawn()).isFalse();
        assertThat(user.getStatus()).isEqualTo(AppUser.Status.SUSPENDED);
        assertThat(user.isSuspended()).isTrue();
        assertThat(user.getSuspendedAt()).isEqualTo(suspendedAt);
        assertThat(user.getWithdrawnAt()).isNull();
    }

    @Test
    void suspendKeepsFirstSuspendedAtAndMarksWithdrawnUserWithoutChangingStatus() {
        AppUser user = AppUser.createActive("Bean", null);
        user.suspend();
        Instant first = user.getSuspendedAt();
        user.suspend();
        assertThat(user.getSuspendedAt()).isEqualTo(first);

        AppUser withdrawn = AppUser.createActive("Gone", null);
        withdrawn.withdraw();
        withdrawn.suspend();

        assertThat(withdrawn.getStatus()).isEqualTo(AppUser.Status.WITHDRAWN);
        assertThat(withdrawn.isSuspended()).isTrue();
        assertThat(withdrawn.getSuspendedAt()).isNotNull();
    }
}
