package com.ggukmoney.beanzip.domain.notification.config;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class NotificationTemplateProperties {

    private final String weeklyRewardAvailableCampaignCode;
    private final String rankChangeCampaignCode;
    private final String boosterRechargedCampaignCode;
    private final String dailyReminderCampaignCode;
    private final String dailyMissionCampaignCode;
    private final String boosterUnusedCampaignCode;
    private final String keycapBoxOpenAvailableCampaignCode;

    @Autowired
    public NotificationTemplateProperties(
            @Value("${app.smart-message.templates.weekly-reward-available.campaign-code:}") String weeklyRewardAvailableCampaignCode,
            @Value("${app.smart-message.templates.rank-change.campaign-code:}") String rankChangeCampaignCode,
            @Value("${app.smart-message.templates.booster-recharged.campaign-code:}") String boosterRechargedCampaignCode,
            @Value("${app.smart-message.templates.daily-reminder.campaign-code:}") String dailyReminderCampaignCode,
            @Value("${app.smart-message.templates.daily-mission.campaign-code:}") String dailyMissionCampaignCode,
            @Value("${app.smart-message.templates.booster-unused.campaign-code:}") String boosterUnusedCampaignCode,
            @Value("${app.smart-message.templates.keycap-box-open-available.campaign-code:}") String keycapBoxOpenAvailableCampaignCode
    ) {
        this.weeklyRewardAvailableCampaignCode = blankToNull(weeklyRewardAvailableCampaignCode);
        this.rankChangeCampaignCode = blankToNull(rankChangeCampaignCode);
        this.boosterRechargedCampaignCode = blankToNull(boosterRechargedCampaignCode);
        this.dailyReminderCampaignCode = blankToNull(dailyReminderCampaignCode);
        this.dailyMissionCampaignCode = blankToNull(dailyMissionCampaignCode);
        this.boosterUnusedCampaignCode = blankToNull(boosterUnusedCampaignCode);
        this.keycapBoxOpenAvailableCampaignCode = blankToNull(keycapBoxOpenAvailableCampaignCode);
    }

    public String campaignCode(NotificationType type) {
        return switch (type) {
            case WEEKLY_REWARD_AVAILABLE -> weeklyRewardAvailableCampaignCode;
            case RANK_CHANGE -> rankChangeCampaignCode;
            case BOOSTER_RECHARGED -> boosterRechargedCampaignCode;
            case DAILY_REMINDER -> dailyReminderCampaignCode;
            case DAILY_MISSION -> dailyMissionCampaignCode;
            case BOOSTER_UNUSED -> boosterUnusedCampaignCode;
            case KEYCAP_BOX_OPEN_AVAILABLE -> keycapBoxOpenAvailableCampaignCode;
        };
    }

    public boolean isConfigured(NotificationType type) {
        return campaignCode(type) != null;
    }

    /**
     * 설정이 빠진 알림 타입을 기동할 때 한 번 남긴다.
     *
     * <p>캠페인 코드가 없으면 발송 함수가 <b>로그도 없이</b> 반환한다. 그래서 DAILY_REMINDER 가
     * 두 달 동안 매일 정상 실행되면서 한 통도 보내지 않은 것을 아무도 알지 못했다.
     */
    @PostConstruct
    void reportUnconfiguredTypes() {
        List<NotificationType> unconfigured = Arrays.stream(NotificationType.values())
                .filter(type -> !isConfigured(type))
                .toList();
        if (!unconfigured.isEmpty()) {
            log.warn("NOTIFICATION_CAMPAIGN_CODE_MISSING types={} impact=SILENTLY_NOT_SENT", unconfigured);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
