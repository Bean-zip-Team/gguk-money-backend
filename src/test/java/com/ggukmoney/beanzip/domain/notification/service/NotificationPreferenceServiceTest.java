package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.dto.request.NotificationAgreementRequest;
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
import static org.mockito.ArgumentMatchers.any;

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
                        NotificationType.BOOSTER_RECHARGED,
                        NotificationType.DAILY_REMINDER,
                        NotificationType.BOOSTER_UNUSED
                );
        assertThat(Arrays.stream(NotificationType.values()).map(Enum::name))
                .doesNotContain("RANK_DROP");
    }

    @Test
    void listReturnsTemplateCodeNotTemplateSetCode() {
        UUID userId = UUID.randomUUID();
        stubMissingPreferences();
        when(persistenceService.isRankPromptEligible(userId)).thenReturn(true);

        var response = service.list(userId);

        assertThat(response.items()).hasSize(5);
        assertThat(response.items().stream()
                .filter(item -> item.type() == NotificationType.RANK_CHANGE)
                .findFirst()
                .orElseThrow()
                .templateCode()).isEqualTo("TPL_RANK_CODE");
    }

    @Test
    void globalAgreementStoresAllPreferencesAndCapturesRankBaseline() {
        UUID userId = UUID.randomUUID();
        stubMissingPreferences();

        var response = service.agree(
                userId,
                new NotificationAgreementRequest("alreadyAgreed")
        );

        assertThat(response.enabled()).isTrue();
        assertThat(response.agreementStatus()).isEqualTo(NotificationAgreementStatus.AGREED);
        assertThat(response.items()).allSatisfy(item -> {
            assertThat(item.enabled()).isTrue();
            assertThat(item.agreementStatus()).isEqualTo(NotificationAgreementStatus.AGREED);
        });
        verify(persistenceService).captureRankBaselineOnAgreement(userId);
    }

    @Test
    void rejectedGlobalAgreementDisablesAllPreferences() {
        UUID userId = UUID.randomUUID();
        stubMissingPreferences();

        var response = service.agree(
                userId,
                new NotificationAgreementRequest("agreementRejected")
        );

        assertThat(response.enabled()).isFalse();
        assertThat(response.agreementStatus()).isEqualTo(NotificationAgreementStatus.REJECTED);
        assertThat(response.items()).allSatisfy(item -> assertThat(item.enabled()).isFalse());
    }

    private void stubMissingPreferences() {
        when(preferenceRepository.findByUserIdAndType(any(), any())).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
