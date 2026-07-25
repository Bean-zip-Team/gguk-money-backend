package com.ggukmoney.beanzip.domain.notification.config;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateProperties {

    private final String agreementTemplateCode;
    private final Template weeklyRewardAvailable;
    private final Template rankChange;
    private final Template boosterRecharged;
    private final Template dailyReminder;
    private final Template boosterUnused;

    @Autowired
    public NotificationTemplateProperties(
            @Value("${app.smart-message.agreement.template-code:}") String agreementTemplateCode,
            @Value("${app.smart-message.templates.weekly-reward-available.template-code:}") String weeklyRewardAvailableTemplateCode,
            @Value("${app.smart-message.templates.weekly-reward-available.template-set-code:}") String weeklyRewardAvailableTemplateSetCode,
            @Value("${app.smart-message.templates.rank-change.template-code:}") String rankChangeTemplateCode,
            @Value("${app.smart-message.templates.rank-change.template-set-code:}") String rankChangeTemplateSetCode,
            @Value("${app.smart-message.templates.booster-recharged.template-code:}") String boosterRechargedTemplateCode,
            @Value("${app.smart-message.templates.booster-recharged.template-set-code:}") String boosterRechargedTemplateSetCode,
            @Value("${app.smart-message.templates.daily-reminder.template-code:}") String dailyReminderTemplateCode,
            @Value("${app.smart-message.templates.daily-reminder.template-set-code:}") String dailyReminderTemplateSetCode,
            @Value("${app.smart-message.templates.booster-unused.template-code:}") String boosterUnusedTemplateCode,
            @Value("${app.smart-message.templates.booster-unused.template-set-code:}") String boosterUnusedTemplateSetCode
    ) {
        this.agreementTemplateCode = blankToNull(agreementTemplateCode);
        this.weeklyRewardAvailable = new Template(blankToNull(weeklyRewardAvailableTemplateCode), blankToNull(weeklyRewardAvailableTemplateSetCode));
        this.rankChange = new Template(blankToNull(rankChangeTemplateCode), blankToNull(rankChangeTemplateSetCode));
        this.boosterRecharged = new Template(blankToNull(boosterRechargedTemplateCode), blankToNull(boosterRechargedTemplateSetCode));
        this.dailyReminder = new Template(blankToNull(dailyReminderTemplateCode), blankToNull(dailyReminderTemplateSetCode));
        this.boosterUnused = new Template(blankToNull(boosterUnusedTemplateCode), blankToNull(boosterUnusedTemplateSetCode));
    }

    public NotificationTemplateProperties(
            String weeklyRewardAvailableTemplateCode,
            String weeklyRewardAvailableTemplateSetCode,
            String rankChangeTemplateCode,
            String rankChangeTemplateSetCode,
            String boosterRechargedTemplateCode,
            String boosterRechargedTemplateSetCode
    ) {
        this(
                null,
                weeklyRewardAvailableTemplateCode,
                weeklyRewardAvailableTemplateSetCode,
                rankChangeTemplateCode,
                rankChangeTemplateSetCode,
                boosterRechargedTemplateCode,
                boosterRechargedTemplateSetCode,
                null,
                null,
                null,
                null
        );
    }

    public String agreementTemplateCode() {
        return agreementTemplateCode;
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
            case DAILY_REMINDER -> dailyReminder;
            case BOOSTER_UNUSED -> boosterUnused;
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Template(String templateCode, String templateSetCode) {
    }
}
