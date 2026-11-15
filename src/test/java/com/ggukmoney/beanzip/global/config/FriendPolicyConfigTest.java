package com.ggukmoney.beanzip.global.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FriendPolicyConfigTest {

    private final AppConfigBatchLoader batchLoader = mock(AppConfigBatchLoader.class);
    private final KeycapBoxPolicyConfig economy = mock(KeycapBoxPolicyConfig.class);
    private final FriendPolicyConfig config = new FriendPolicyConfig(batchLoader, economy);

    @Test
    void startsFromCodeDefaultsWithTheFeatureOff() {
        when(economy.shardEconomyAtLastRefresh()).thenReturn(true);

        assertThat(config.friendsEnabled()).isFalse();
        assertThat(config.contactsSmsEnabled()).isFalse();
        assertThat(config.inviteReward()).isEqualTo(10);
        assertThat(config.cheerDailyLimit()).isEqualTo(10);
        assertThat(config.cheerReceiveReward()).isEqualTo(3);
        assertThat(config.cheerReceiveDailyCap()).isEqualTo(30);
        assertThat(config.cheerSendFirstReward()).isEqualTo(1);
        assertThat(config.cheerClapThreshold()).isEqualTo(1000);
        assertThat(config.cheerGiftLapsedDays()).isEqualTo(3);
    }

    @Test
    void readsOverridesFromAppConfig() {
        when(economy.shardEconomyAtLastRefresh()).thenReturn(true);
        when(batchLoader.load(eq(FriendPolicyConfig.DEFAULT_VALUES.keySet()), any())).thenReturn(Map.of(
                FriendPolicyConfig.KEY_ENABLED, "true",
                FriendPolicyConfig.KEY_CONTACTS_SMS_ENABLED, " true ",
                FriendPolicyConfig.KEY_INVITE_REWARD, "20",
                FriendPolicyConfig.KEY_CHEER_DAILY_LIMIT, "5"
        ));

        config.refresh();

        assertThat(config.friendsEnabled()).isTrue();
        assertThat(config.contactsSmsEnabled()).isTrue();
        assertThat(config.inviteReward()).isEqualTo(20);
        assertThat(config.cheerDailyLimit()).isEqualTo(5);
        // Keys without a row keep the code default.
        assertThat(config.cheerReceiveReward()).isEqualTo(3);
    }

    @Test
    void keepsTheLastGoodValueWhenARowIsInvalid() {
        when(economy.shardEconomyAtLastRefresh()).thenReturn(true);
        when(batchLoader.load(eq(FriendPolicyConfig.DEFAULT_VALUES.keySet()), any()))
                .thenReturn(Map.of(FriendPolicyConfig.KEY_ENABLED, "true", FriendPolicyConfig.KEY_INVITE_REWARD, "20"))
                .thenReturn(Map.of(
                        // "TRUE" is not the stored JSON boolean, a negative reward would throw in the wallet,
                        // and a zero threshold would turn every cheer into a clap.
                        FriendPolicyConfig.KEY_ENABLED, "TRUE",
                        FriendPolicyConfig.KEY_INVITE_REWARD, "-1",
                        FriendPolicyConfig.KEY_CHEER_CLAP_THRESHOLD, "0",
                        FriendPolicyConfig.KEY_CHEER_GIFT_LAPSED_DAYS, "three"
                ));

        config.refresh();
        config.refresh();

        assertThat(config.friendsEnabled()).isTrue();
        assertThat(config.inviteReward()).isEqualTo(20);
        assertThat(config.cheerClapThreshold()).isEqualTo(1000);
        assertThat(config.cheerGiftLapsedDays()).isEqualTo(3);
    }

    @Test
    void keepsTheLastGoodValuesWhenTheReadFails() {
        when(economy.shardEconomyAtLastRefresh()).thenReturn(true);
        when(batchLoader.load(eq(FriendPolicyConfig.DEFAULT_VALUES.keySet()), any()))
                .thenReturn(Map.of(FriendPolicyConfig.KEY_ENABLED, "true"))
                .thenThrow(new IllegalStateException("database is down"));

        config.refresh();
        config.refresh();

        assertThat(config.friendsEnabled()).isTrue();
    }

    @Test
    void staysOffBeforeTheShardEconomyEvenWhenTheSwitchIsOn() {
        when(batchLoader.load(eq(FriendPolicyConfig.DEFAULT_VALUES.keySet()), any()))
                .thenReturn(Map.of(FriendPolicyConfig.KEY_ENABLED, "true"));
        when(economy.shardEconomyAtLastRefresh()).thenReturn(false);

        config.refresh();

        // The wallet column still counts boxes then, so a friend reward would hand out boxes.
        assertThat(config.friendsEnabled()).isFalse();
    }
}
