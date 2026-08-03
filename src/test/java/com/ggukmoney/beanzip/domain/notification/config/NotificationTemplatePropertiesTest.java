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
                null,
                "clickmoney-box"
        );

        assertThat(properties.campaignCode(NotificationType.RANK_CHANGE)).isEqualTo("clickmoney-asfasf");
        assertThat(properties.campaignCode(NotificationType.KEYCAP_BOX_OPEN_AVAILABLE)).isEqualTo("clickmoney-box");
        assertThat(properties.campaignCode(NotificationType.BOOSTER_RECHARGED)).isNull();
        assertThat(properties.isConfigured(NotificationType.RANK_CHANGE)).isTrue();
    }

    @Test
    void unsetCampaignIsNotConfigured() {
        NotificationTemplateProperties properties = new NotificationTemplateProperties(null, null, null, null, null, null);

        assertThat(properties.campaignCode(NotificationType.WEEKLY_REWARD_AVAILABLE)).isNull();
        assertThat(properties.isConfigured(NotificationType.WEEKLY_REWARD_AVAILABLE)).isFalse();
    }
}
