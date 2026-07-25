package com.ggukmoney.beanzip.domain.notification.config;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateProperties {

    private final Template weeklyRewardAvailable;
    private final Template rankChange;
    private final Template boosterRecharged;

    public NotificationTemplateProperties(
            @Value("${app.smart-message.templates.weekly-reward-available.template-code:}") String weeklyRewardAvailableTemplateCode,
            @Value("${app.smart-message.templates.weekly-reward-available.template-set-code:}") String weeklyRewardAvailableTemplateSetCode,
            @Value("${app.smart-message.templates.rank-change.template-code:}") String rankChangeTemplateCode,
            @Value("${app.smart-message.templates.rank-change.template-set-code:}") String rankChangeTemplateSetCode,
            @Value("${app.smart-message.templates.booster-recharged.template-code:}") String boosterRechargedTemplateCode,
            @Value("${app.smart-message.templates.booster-recharged.template-set-code:}") String boosterRechargedTemplateSetCode
    ) {
        this.weeklyRewardAvailable = new Template(blankToNull(weeklyRewardAvailableTemplateCode), blankToNull(weeklyRewardAvailableTemplateSetCode));
        this.rankChange = new Template(blankToNull(rankChangeTemplateCode), blankToNull(rankChangeTemplateSetCode));
        this.boosterRecharged = new Template(blankToNull(boosterRechargedTemplateCode), blankToNull(boosterRechargedTemplateSetCode));
    }

    public String templateCode(NotificationType type) {
        return template(type).templateCode();
    }

    public String templateSetCode(NotificationType type) {
        return template(type).templateSetCode();
    }

    private Template template(NotificationType type) {
        return switch (type) {
            case WEEKLY_REWARD_AVAILABLE -> weeklyRewardAvailable;
            case RANK_CHANGE -> rankChange;
            case BOOSTER_RECHARGED -> boosterRecharged;
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Template(String templateCode, String templateSetCode) {
    }
}
