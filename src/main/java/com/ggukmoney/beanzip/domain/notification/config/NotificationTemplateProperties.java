package com.ggukmoney.beanzip.domain.notification.config;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateProperties {

    private final String weeklyRewardAvailableCampaignCode;
    private final String rankChangeCampaignCode;
    private final String boosterRechargedCampaignCode;
    private final String dailyReminderCampaignCode;
    private final String boosterUnusedCampaignCode;
    private final String keycapBoxOpenAvailableCampaignCode;

    @Autowired
    public NotificationTemplateProperties(
            @Value("${app.smart-message.templates.weekly-reward-available.campaign-code:}") String weeklyRewardAvailableCampaignCode,
            @Value("${app.smart-message.templates.rank-change.campaign-code:}") String rankChangeCampaignCode,
            @Value("${app.smart-message.templates.booster-recharged.campaign-code:}") String boosterRechargedCampaignCode,
            @Value("${app.smart-message.templates.daily-reminder.campaign-code:}") String dailyReminderCampaignCode,
            @Value("${app.smart-message.templates.booster-unused.campaign-code:}") String boosterUnusedCampaignCode,
            @Value("${app.smart-message.templates.keycap-box-open-available.campaign-code:}") String keycapBoxOpenAvailableCampaignCode
    ) {
        this.weeklyRewardAvailableCampaignCode = blankToNull(weeklyRewardAvailableCampaignCode);
        this.rankChangeCampaignCode = blankToNull(rankChangeCampaignCode);
        this.boosterRechargedCampaignCode = blankToNull(boosterRechargedCampaignCode);
        this.dailyReminderCampaignCode = blankToNull(dailyReminderCampaignCode);
        this.boosterUnusedCampaignCode = blankToNull(boosterUnusedCampaignCode);
        this.keycapBoxOpenAvailableCampaignCode = blankToNull(keycapBoxOpenAvailableCampaignCode);
    }

    public String campaignCode(NotificationType type) {
        return switch (type) {
            case WEEKLY_REWARD_AVAILABLE -> weeklyRewardAvailableCampaignCode;
            case RANK_CHANGE -> rankChangeCampaignCode;
            case BOOSTER_RECHARGED -> boosterRechargedCampaignCode;
            case DAILY_REMINDER -> dailyReminderCampaignCode;
            case BOOSTER_UNUSED -> boosterUnusedCampaignCode;
            case KEYCAP_BOX_OPEN_AVAILABLE -> keycapBoxOpenAvailableCampaignCode;
        };
    }

    public boolean isConfigured(NotificationType type) {
        return campaignCode(type) != null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
