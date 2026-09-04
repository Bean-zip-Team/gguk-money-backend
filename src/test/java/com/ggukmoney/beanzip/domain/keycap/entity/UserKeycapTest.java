package com.ggukmoney.beanzip.domain.keycap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

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

    @Test
    void createsInProgressUserKeycapForReward() {
        AppUser user = AppUser.createActive("Bean", null);
        Keycap keycap = keycap(10);

        UserKeycap userKeycap = UserKeycap.createInProgress(user, keycap);

        assertThat(userKeycap.getUser()).isSameAs(user);
        assertThat(userKeycap.getKeycap()).isSameAs(keycap);
        assertThat(userKeycap.getShardCount()).isZero();
        assertThat(userKeycap.getStatus()).isEqualTo(UserKeycap.Status.IN_PROGRESS);
        assertThat(userKeycap.isEquipped()).isFalse();
    }

    @Test
    void addsShardWithoutCompletingBeforeRequiredShardCount() {
        UserKeycap userKeycap = userKeycap(UserKeycap.Status.IN_PROGRESS, false);
        Keycap keycap = keycap(3);
        ReflectionTestUtils.setField(userKeycap, "keycap", keycap);
        ReflectionTestUtils.setField(userKeycap, "shardCount", 1);
        Instant now = Instant.parse("2026-07-14T00:00:00Z");

        boolean completedNow = userKeycap.addShard(1, now);

        assertThat(completedNow).isFalse();
        assertThat(userKeycap.getShardCount()).isEqualTo(2);
        assertThat(userKeycap.getStatus()).isEqualTo(UserKeycap.Status.IN_PROGRESS);
        assertThat(userKeycap.getCompletedAt()).isNull();
    }

    @Test
    void capsShardCountAndCompletesWhenRequiredShardCountIsReached() {
        UserKeycap userKeycap = userKeycap(UserKeycap.Status.IN_PROGRESS, false);
        Keycap keycap = keycap(3);
        ReflectionTestUtils.setField(userKeycap, "keycap", keycap);
        ReflectionTestUtils.setField(userKeycap, "shardCount", 2);
        Instant now = Instant.parse("2026-07-14T00:00:00Z");

        boolean completedNow = userKeycap.addShard(2, now);

        assertThat(completedNow).isTrue();
        assertThat(userKeycap.getShardCount()).isEqualTo(3);
        assertThat(userKeycap.getStatus()).isEqualTo(UserKeycap.Status.COMPLETED);
        assertThat(userKeycap.getCompletedAt()).isEqualTo(now);
    }

    @Test
    void rejectsShardAdditionWhenAlreadyCompleted() {
        UserKeycap userKeycap = userKeycap(UserKeycap.Status.COMPLETED, false);
        Keycap keycap = keycap(3);
        ReflectionTestUtils.setField(userKeycap, "keycap", keycap);
        ReflectionTestUtils.setField(userKeycap, "shardCount", 3);

        assertThatThrownBy(() -> userKeycap.addShard(1, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static UserKeycap userKeycap(UserKeycap.Status status, boolean equipped) {
        UserKeycap userKeycap = new UserKeycap();
        ReflectionTestUtils.setField(userKeycap, "status", status);
        ReflectionTestUtils.setField(userKeycap, "equipped", equipped);
        return userKeycap;
    }

    private static Keycap keycap(int requiredShardCount) {
        Keycap keycap = new Keycap();
        ReflectionTestUtils.setField(keycap, "requiredShardCount", requiredShardCount);
        return keycap;
    }
}
