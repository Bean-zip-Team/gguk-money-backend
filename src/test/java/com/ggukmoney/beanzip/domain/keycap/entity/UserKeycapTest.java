package com.ggukmoney.beanzip.domain.keycap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserKeycapTest {

    private static final Instant ACQUIRED_AT = Instant.parse("2026-10-09T01:00:00Z");

    @Test
    void createsOwnedKeycapAtLevelOneWithAcquisitionTimeAsCompletedAt() {
        AppUser user = AppUser.createActive("Bean", null);
        Keycap keycap = keycap();

        UserKeycap userKeycap = UserKeycap.createOwned(user, keycap, ACQUIRED_AT);

        assertThat(userKeycap.getUser()).isSameAs(user);
        assertThat(userKeycap.getKeycap()).isSameAs(keycap);
        assertThat(userKeycap.getLevel()).isEqualTo(1);
        assertThat(userKeycap.getStatus()).isEqualTo(UserKeycap.Status.COMPLETED);
        assertThat(userKeycap.isCompleted()).isTrue();
        assertThat(userKeycap.getCompletedAt()).isEqualTo(ACQUIRED_AT);
        assertThat(userKeycap.isEquipped()).isFalse();
    }

    @Test
    void rejectsMissingArguments() {
        AppUser user = AppUser.createActive("Bean", null);
        Keycap keycap = keycap();

        assertThatThrownBy(() -> UserKeycap.createOwned(null, keycap, ACQUIRED_AT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UserKeycap.createOwned(user, null, ACQUIRED_AT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UserKeycap.createOwned(user, keycap, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void levelUpAlwaysAddsOneWithoutCapAndKeepsCompletedAt() {
        UserKeycap userKeycap = UserKeycap.createOwned(AppUser.createActive("Bean", null), keycap(), ACQUIRED_AT);

        assertThat(userKeycap.levelUp()).isEqualTo(2);
        for (int i = 0; i < 148; i++) {
            userKeycap.levelUp();
        }

        // 500뽑 시점이면 COMMON 은 Lv 150 근처다. 상한이 없어야 레벨 숫자가 희소성을 거꾸로 보여준다.
        assertThat(userKeycap.getLevel()).isEqualTo(150);
        // 키캡 5개 미션은 completedAt > launchAt 을 본다. 레벨업이 이 값을 건드리면 미션이 오발한다.
        assertThat(userKeycap.getCompletedAt()).isEqualTo(ACQUIRED_AT);
    }

    @Test
    void equipsAndUnequipsOwnedKeycap() {
        UserKeycap userKeycap = UserKeycap.createOwned(AppUser.createActive("Bean", null), keycap(), ACQUIRED_AT);

        userKeycap.equip();
        assertThat(userKeycap.isEquipped()).isTrue();

        userKeycap.unequip();
        assertThat(userKeycap.isEquipped()).isFalse();
    }

    private static Keycap keycap() {
        return Keycap.createFor("BASIC_001", "Basic", Keycap.Grade.COMMON, 10, 1, null, null, 1);
    }
}
