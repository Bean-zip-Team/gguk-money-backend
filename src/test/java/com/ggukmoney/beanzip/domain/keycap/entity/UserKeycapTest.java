package com.ggukmoney.beanzip.domain.keycap.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserKeycapTest {

    @Test
    void equipsCompletedKeycap() {
        UserKeycap userKeycap = userKeycap(UserKeycap.Status.COMPLETED, false);

        userKeycap.equip();

        assertThat(userKeycap.isCompleted()).isTrue();
        assertThat(userKeycap.isEquipped()).isTrue();
    }

    @Test
    void rejectsEquipWhenKeycapIsInProgress() {
        UserKeycap userKeycap = userKeycap(UserKeycap.Status.IN_PROGRESS, false);

        assertThatThrownBy(userKeycap::equip)
                .isInstanceOf(IllegalStateException.class);
        assertThat(userKeycap.isEquipped()).isFalse();
    }

    @Test
    void unequipsKeycap() {
        UserKeycap userKeycap = userKeycap(UserKeycap.Status.COMPLETED, true);

        userKeycap.unequip();

        assertThat(userKeycap.isEquipped()).isFalse();
    }

    private static UserKeycap userKeycap(UserKeycap.Status status, boolean equipped) {
        UserKeycap userKeycap = new UserKeycap();
        ReflectionTestUtils.setField(userKeycap, "status", status);
        ReflectionTestUtils.setField(userKeycap, "equipped", equipped);
        return userKeycap;
    }
}
