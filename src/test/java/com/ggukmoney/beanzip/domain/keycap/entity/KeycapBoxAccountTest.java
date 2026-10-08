package com.ggukmoney.beanzip.domain.keycap.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycapBoxAccountTest {

    @Test
    void createsEmptyShardWallet() {
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null);

        assertThat(wallet.getShardBalance()).isZero();
        assertThat(wallet.canAfford(1)).isFalse();
    }

    @Test
    void accumulatesShardsWithoutCap() {
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null);

        wallet.addShards(3);
        wallet.addShards(1_000_000);

        assertThat(wallet.getShardBalance()).isEqualTo(1_000_003);
        assertThatThrownBy(() -> wallet.addShards(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.addShards(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumesShardsOnlyWhenBalanceCoversPrice() {
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null);
        wallet.addShards(5);

        assertThat(wallet.canAfford(5)).isTrue();
        assertThat(wallet.canAfford(6)).isFalse();

        wallet.consumeShards(5);

        assertThat(wallet.getShardBalance()).isZero();
        assertThatThrownBy(() -> wallet.consumeShards(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient");
        assertThat(wallet.getShardBalance()).isZero();
    }

    @Test
    void rejectsNonPositivePrice() {
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null);
        wallet.addShards(5);

        assertThatThrownBy(() -> wallet.canAfford(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.consumeShards(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(wallet.getShardBalance()).isEqualTo(5);
    }
}
