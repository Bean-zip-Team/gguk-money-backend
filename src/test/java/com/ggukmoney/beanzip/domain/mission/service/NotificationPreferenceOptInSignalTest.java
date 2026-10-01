package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferenceOptInSignalTest {

    private static final NotificationTemplateProperties CONFIGURED = new NotificationTemplateProperties(
            null, null, null, null, "clickmoney-daily-mission", null, null);

    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final NotificationPreferenceOptInSignal signal =
            new NotificationPreferenceOptInSignal(preferenceRepository, CONFIGURED);

    @Test
    void agreedUserHasAchievedTheMission() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.DAILY_MISSION))
                .thenReturn(Optional.of(agreed(userId)));

        assertThat(signal.agreed(userId)).contains(true);
    }

    @Test
    void turningTheNotificationOffAfterAgreeingKeepsTheMissionAchieved() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = agreed(userId);
        preference.updateEnabled(false);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.DAILY_MISSION))
                .thenReturn(Optional.of(preference));

        // 미션의 목적은 동의를 받는 것이다. 동의한 뒤 알림을 껐다고 수행한 사실까지 사라지지는 않는다.
        assertThat(signal.agreed(userId)).contains(true);
    }

    @Test
    void userWhoNeverAnsweredHasNotAchievedTheMission() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.DAILY_MISSION))
                .thenReturn(Optional.empty());

        assertThat(signal.agreed(userId)).contains(false);
    }

    @Test
    void rejectedUserHasNotAchievedTheMission() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = NotificationPreference.defaultOf(userId, NotificationType.DAILY_MISSION);
        preference.applyAgreement("agreementRejected");
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.DAILY_MISSION))
                .thenReturn(Optional.of(preference));

        assertThat(signal.agreed(userId)).contains(false);
    }

    @Test
    void doesNotJudgeWhenTheCampaignCodeIsMissing() {
        NotificationPreferenceOptInSignal unconfiguredSignal = new NotificationPreferenceOptInSignal(
                preferenceRepository, new NotificationTemplateProperties(null, null, null, null, null, null, null));

        // 캠페인 코드가 없으면 알림 설정 목록에 토글이 내려가지 않는다. 동의할 방법이 없는 미션을
        // 목록에 남겨 두면 유저는 영원히 미완료 상태로 보게 된다.
        assertThat(unconfiguredSignal.agreed(UUID.randomUUID())).isEmpty();
        verify(preferenceRepository, never()).findByUserIdAndType(any(), any());
    }

    private NotificationPreference agreed(UUID userId) {
        NotificationPreference preference = NotificationPreference.defaultOf(userId, NotificationType.DAILY_MISSION);
        preference.applyAgreement("newAgreement");
        return preference;
    }
}
