package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferenceServiceTest {

    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final NotificationTemplateProperties templateProperties = new NotificationTemplateProperties(
            "TPL_WEEKLY_CODE",
            "TPL_WEEKLY_SET",
            "TPL_RANK_CODE",
            "TPL_RANK_SET",
            "TPL_BOOST_CODE",
            "TPL_BOOST_SET"
    );
    private final NotificationDeliveryPersistenceService persistenceService = mock(NotificationDeliveryPersistenceService.class);
    private final NotificationPreferenceService service =
            new NotificationPreferenceService(preferenceRepository, templateProperties, persistenceService);

    @Test
    void notificationTypeOnlyContainsPlannedTypes() {
        assertThat(NotificationType.values())
                .containsExactly(
                        NotificationType.WEEKLY_REWARD_AVAILABLE,
                        NotificationType.RANK_CHANGE,
                        NotificationType.BOOSTER_RECHARGED
                );
        assertThat(Arrays.stream(NotificationType.values()).map(Enum::name))
                .doesNotContain("DAILY_REMINDER", "RANK_DROP", "BOOSTER_UNUSED");
    }

    @Test
    void listReturnsTemplateCodeNotTemplateSetCode() {
        UUID userId = UUID.randomUUID();
        when(persistenceService.isRankPromptEligible(userId)).thenReturn(true);

        var response = service.list(userId);

        assertThat(response.items()).hasSize(3);
        assertThat(response.items().stream()
                .filter(item -> item.type() == NotificationType.RANK_CHANGE)
                .findFirst()
                .orElseThrow()
                .templateCode()).isEqualTo("TPL_RANK_CODE");
    }

    @Test
    void updateStoresAgreementAndCapturesRankBaseline() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = NotificationPreference.defaultOf(userId, NotificationType.RANK_CHANGE);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(preference));

        var response = service.update(
                userId,
                new UpdateNotificationPreferenceRequest(NotificationType.RANK_CHANGE, true, "alreadyAgreed")
        );

        assertThat(response.enabled()).isTrue();
        assertThat(response.agreementStatus()).isEqualTo(NotificationAgreementStatus.AGREED);
        verify(persistenceService).captureRankBaselineOnAgreement(userId);
    }

    @Test
    void rejectedAgreementDisablesPreference() {
        UUID userId = UUID.randomUUID();
        NotificationPreference preference = NotificationPreference.defaultOf(userId, NotificationType.BOOSTER_RECHARGED);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.BOOSTER_RECHARGED))
                .thenReturn(Optional.of(preference));

        var response = service.update(
                userId,
                new UpdateNotificationPreferenceRequest(NotificationType.BOOSTER_RECHARGED, true, "agreementRejected")
        );

        assertThat(response.enabled()).isFalse();
        assertThat(response.agreementStatus()).isEqualTo(NotificationAgreementStatus.REJECTED);
    }
}
