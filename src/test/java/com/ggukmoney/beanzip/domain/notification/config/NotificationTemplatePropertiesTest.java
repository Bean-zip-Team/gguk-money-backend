package com.ggukmoney.beanzip.domain.notification.config;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplatePropertiesTest {

    @Test
    void configuredCampaignCodeIsSharedByConsentAndDeliveryConsumers() {
        NotificationTemplateProperties properties = new NotificationTemplateProperties(
                null,
                "clickmoney-asfasf",
                null,
                null,
                "clickmoney-daily-mission",
                null,
                "clickmoney-box",
                null,
                null
        );

        assertThat(properties.campaignCode(NotificationType.RANK_CHANGE)).isEqualTo("clickmoney-asfasf");
        assertThat(properties.campaignCode(NotificationType.KEYCAP_BOX_OPEN_AVAILABLE)).isEqualTo("clickmoney-box");
        assertThat(properties.campaignCode(NotificationType.DAILY_MISSION)).isEqualTo("clickmoney-daily-mission");
        assertThat(properties.campaignCode(NotificationType.BOOSTER_RECHARGED)).isNull();
        assertThat(properties.isConfigured(NotificationType.RANK_CHANGE)).isTrue();
    }

    @Test
    void friendConsentUsesTheCheerCampaignCodeAndEachPushKeepsItsOwn() {
        NotificationTemplateProperties properties = new NotificationTemplateProperties(
                null, null, null, null, null, null, null, " clickmoney-friend-cheer ", "clickmoney-friend-connected");

        // The app opens the Toss consent sheet with the FRIEND item's code; one consent covers both campaigns.
        assertThat(properties.campaignCode(NotificationType.FRIEND)).isEqualTo("clickmoney-friend-cheer");
        assertThat(properties.campaignCode(NotificationType.FRIEND_CHEER)).isEqualTo("clickmoney-friend-cheer");
        assertThat(properties.campaignCode(NotificationType.FRIEND_INVITE_SUCCESS))
                .isEqualTo("clickmoney-friend-connected");
    }

    @Test
    void unsetCampaignIsNotConfigured() {
        NotificationTemplateProperties properties = new NotificationTemplateProperties(null, null, null, null, null, null, null, null, null);

        assertThat(properties.campaignCode(NotificationType.WEEKLY_REWARD_AVAILABLE)).isNull();
        assertThat(properties.isConfigured(NotificationType.WEEKLY_REWARD_AVAILABLE)).isFalse();
    }
}
