package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceAgreementRequest;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class NotificationPreferenceServiceTest {

    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final NotificationTemplateProperties templateProperties = new NotificationTemplateProperties(
            null,
            "clickmoney-asfasf",
            null,
            null,
            null,
            "clickmoney-box"
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
                        NotificationType.BOOSTER_RECHARGED,
                        NotificationType.DAILY_REMINDER,
                        NotificationType.BOOSTER_UNUSED,
                        NotificationType.KEYCAP_BOX_OPEN_AVAILABLE
                );
        assertThat(Arrays.stream(NotificationType.values()).map(Enum::name))
                .doesNotContain("RANK_DROP");
    }

    @Test
    void listReturnsOnlyConfiguredCampaignTypesWithCampaignCodeAsTemplateCode() {
        UUID userId = UUID.randomUUID();
        stubMissingPreferences();
        when(persistenceService.isRankPromptEligible(userId)).thenReturn(true);

        var response = service.list(userId);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().stream()
                .filter(item -> item.type() == NotificationType.RANK_CHANGE)
                .findFirst()
                .orElseThrow()
                .templateCode()).isEqualTo("clickmoney-asfasf");
        assertThat(response.items().stream()
                .filter(item -> item.type() == NotificationType.KEYCAP_BOX_OPEN_AVAILABLE)
                .findFirst()
                .orElseThrow()
                .templateCode()).isEqualTo("clickmoney-box");
    }

    @Test
    void rankAgreementChangesOnlyRankPreferenceAndCapturesRankBaseline() {
        UUID userId = UUID.randomUUID();
        stubMissingPreferences();

        var response = service.agree(
                userId,
                new UpdateNotificationPreferenceAgreementRequest(NotificationType.RANK_CHANGE, "alreadyAgreed")
        );

        assertThat(response.type()).isEqualTo(NotificationType.RANK_CHANGE);
        assertThat(response.enabled()).isTrue();
        assertThat(response.agreementStatus()).isEqualTo(NotificationAgreementStatus.AGREED);
        assertThat(response.templateCode()).isEqualTo("clickmoney-asfasf");
        verify(persistenceService).captureRankBaselineOnAgreement(userId);
        Arrays.stream(NotificationType.values())
                .filter(type -> type != NotificationType.RANK_CHANGE)
                .forEach(type -> verify(preferenceRepository, org.mockito.Mockito.never()).findByUserIdAndType(userId, type));
    }

    @Test
    void boosterAgreementDoesNotCaptureRankBaseline() {
        UUID userId = UUID.randomUUID();
        stubMissingPreferences();

        var response = service.agree(
                userId,
                new UpdateNotificationPreferenceAgreementRequest(NotificationType.BOOSTER_RECHARGED, "agreementRejected")
        );

        assertThat(response.type()).isEqualTo(NotificationType.BOOSTER_RECHARGED);
        assertThat(response.enabled()).isFalse();
        assertThat(response.agreementStatus()).isEqualTo(NotificationAgreementStatus.REJECTED);
        org.mockito.Mockito.verifyNoInteractions(persistenceService);
    }

    @Test
    void enabledCannotBeTurnedOnBeforeAgreement() {
        UUID userId = UUID.randomUUID();
        stubMissingPreferences();

        assertThatThrownBy(() -> service.updateEnabled(userId, NotificationType.RANK_CHANGE, true))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("NOTIFICATION_AGREEMENT_REQUIRED");
    }

    private void stubMissingPreferences() {
        when(preferenceRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
