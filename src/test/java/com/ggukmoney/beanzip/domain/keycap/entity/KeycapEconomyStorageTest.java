package com.ggukmoney.beanzip.domain.keycap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class KeycapEconomyStorageTest {
    private final AppUser user = AppUser.createActive("Bean", null);
    private final Keycap keycap = Keycap.createFor("cheer", "Cheer", Keycap.Grade.COMMON, 20, 1, "image", "sound", 1);
    private final Instant acquiredAt = Instant.parse("2026-09-01T00:00:00Z");

    @Test void legacyCompletedKeycapStartsAtOneAndDuplicatesPreserveAcquisitionTime() {
        UserKeycap owned = UserKeycap.createCompletedOnboardingReward(user, keycap, acquiredAt);
        assertThat(owned.getLevel()).isEqualTo(1);
        assertThat(owned.levelUp()).isEqualTo(2);
        assertThat(owned.getLevel()).isEqualTo(2);
        assertThat(owned.getShardCount()).isEqualTo(21);
        assertThat(owned.getCompletedAt()).isEqualTo(acquiredAt);
        for (int i = 0; i < 100; i++) owned.levelUp();
        assertThat(owned.getLevel()).isEqualTo(102);
    }

    @Test void progressIsNotOwned() {
        UserKeycap progress = UserKeycap.createInProgress(user, keycap);
        progress.addShard(3, acquiredAt);
        assertThat(progress.getLevel()).isZero();
        assertThat(progress.isLegacyInProgress()).isTrue();
        assertThat(progress.getShardCount()).isEqualTo(3);
    }

    @Test void shardOperationsUseExistingBoxBalanceAndKeepLegacyInsertFields() {
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(user, acquiredAt);
        wallet.addBoxes(12);
        assertThat(wallet.getShardBalance()).isEqualTo(12);
        wallet.consumeShards(5);
        wallet.addShards(3);
        assertThat(wallet.getBoxBalance()).isEqualTo(10);
        assertThat(wallet.getFreeOpenUsedCount()).isZero();
        assertThat(wallet.getAdOpenUsedCount()).isZero();
        assertThat(wallet.getOpenCycleStartedAt()).isEqualTo(acquiredAt);
    }
}
