package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.event.WeeklyRewardAvailableEvent;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryServiceTest {

    private final NotificationDeliveryPersistenceService persistenceService = mock(NotificationDeliveryPersistenceService.class);
    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
    private final BoosterGrantRepository boosterGrantRepository = mock(BoosterGrantRepository.class);
    private final UserTapDailyRepository userTapDailyRepository = mock(UserTapDailyRepository.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final NotificationTemplateProperties templateProperties = new NotificationTemplateProperties(
            "TPL_AGREEMENT",
            "TPL_WEEKLY_CODE", "TPL_WEEKLY_SET",
            "TPL_RANK_CODE", "TPL_RANK_SET",
            "TPL_BOOST_CODE", "TPL_BOOST_SET",
            "TPL_DAILY_CODE", "TPL_DAILY_SET",
            "TPL_UNUSED_CODE", "TPL_UNUSED_SET"
    );
    private final TossSmartMessageClient smartMessageClient = mock(TossSmartMessageClient.class);
    private final NotificationDeliveryService service = new NotificationDeliveryService(
            persistenceService,
            preferenceRepository,
            authIdentityRepository,
            boosterGrantRepository,
            userTapDailyRepository,
            tapPolicyConfig,
            templateProperties,
            smartMessageClient
    );

    @Test
    void deliveryServiceHasNoTransactionalBoundaryAroundTossCall() throws Exception {
        assertThat(NotificationDeliveryService.class.getAnnotation(Transactional.class)).isNull();
        for (String methodName : List.of("handleWeeklyRewardAvailable", "evaluateRankChange", "sendBoosterRechargedForDate")) {
            Method method = java.util.Arrays.stream(NotificationDeliveryService.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            assertThat(method.getAnnotation(Transactional.class)).isNull();
        }
    }

    @Nested
    class WeeklyReward {

        @Test
        void agreedUserCreatesPendingThenCallsTossOutsideTransaction() {
            UUID userId = UUID.randomUUID();
            WeeklyRewardAvailableEvent event = new WeeklyRewardAvailableEvent(userId, "2026-W30", Instant.parse("2026-07-25T00:00:00Z"));
            NotificationDelivery pending = pending(userId, NotificationType.WEEKLY_REWARD_AVAILABLE, "weekly");
            stubAgreed(userId, NotificationType.WEEKLY_REWARD_AVAILABLE);
            when(persistenceService.createPending(
                    userId,
                    NotificationType.WEEKLY_REWARD_AVAILABLE,
                    "WEEKLY_REWARD_AVAILABLE:" + userId + ":2026-W30",
                    "TPL_WEEKLY_SET",
                    "{\"rewardCycleKey\":\"2026-W30\",\"availableAt\":\"2026-07-25T00:00:00Z\"}"
            )).thenReturn(Optional.of(pending));

            stubSuccessfulToss(userId, pending);

            assertThat(service.handleWeeklyRewardAvailable(event)).contains(pending);
        }
    }

    @Nested
    class RankChange {

        @Test
        void onlyPreparedPendingDeliveryCallsTossOutsideTransaction() {
            UUID userId = UUID.randomUUID();
            NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, "rank");
            when(persistenceService.prepareRankChange(userId)).thenReturn(Optional.of(pending));
            stubSuccessfulToss(userId, pending);

            assertThat(service.evaluateRankChange(userId)).contains(pending);

            verify(persistenceService).prepareRankChange(userId);
        }

        @Test
        void noPreparedDeliveryDoesNotCallToss() {
            UUID userId = UUID.randomUUID();
            when(persistenceService.prepareRankChange(userId)).thenReturn(Optional.empty());

            assertThat(service.evaluateRankChange(userId)).isEmpty();

            verify(smartMessageClient, never()).sendMessage(anyString(), anyString());
        }

        @Test
        void retryableTossFailureStoresProviderResponseForPreparedDelivery() {
            UUID userId = UUID.randomUUID();
            NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, "rank-retry");
            when(persistenceService.prepareRankChange(userId)).thenReturn(Optional.of(pending));
            AuthIdentity identity = AuthIdentity.toss(AppUser.createActive("me", null), "toss-user-1");
            when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)).thenReturn(Optional.of(identity));
            when(smartMessageClient.sendMessage("toss-user-1", "TPL_RANK_SET", "{}"))
                    .thenReturn(new TossSmartMessageClient.SendResult(false, null, "RATE_LIMITED", "temporary", true, "{\"error\":true}"));
            when(persistenceService.markRetryWaiting(pending.getId(), "RATE_LIMITED", "temporary", "{\"error\":true}"))
                    .thenReturn(pending);

            assertThat(service.evaluateRankChange(userId)).contains(pending);

            verify(persistenceService).markRetryWaiting(pending.getId(), "RATE_LIMITED", "temporary", "{\"error\":true}");
        }
    }

    @Nested
    class BoosterRecharged {

        @Test
        void sendsOnlyYesterdayExhaustedAndTodayUnusedUsers() {
            UUID userId = UUID.randomUUID();
            LocalDate today = LocalDate.parse("2026-07-25");
            NotificationDelivery pending = pending(userId, NotificationType.BOOSTER_RECHARGED, "booster");
            when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
            when(boosterGrantRepository.findUserIdsWhoExhaustedDailyBoosters(today.minusDays(1), 3)).thenReturn(List.of(userId));
            when(boosterGrantRepository.countByUserIdAndGrantDate(userId, today)).thenReturn(0L);
            stubAgreed(userId, NotificationType.BOOSTER_RECHARGED);
            when(persistenceService.createPending(
                    userId,
                    NotificationType.BOOSTER_RECHARGED,
                    "BOOSTER_RECHARGED:" + userId + ":20260725",
                    "TPL_BOOST_SET",
                    "{}"
            )).thenReturn(Optional.of(pending));
            stubSuccessfulToss(userId, pending);

            assertThat(service.sendBoosterRechargedForDate(today)).containsExactly(pending);
        }

        @Test
        void failedUserDoesNotStopNextUser() {
            UUID failingUserId = UUID.randomUUID();
            UUID nextUserId = UUID.randomUUID();
            LocalDate today = LocalDate.parse("2026-07-25");
            NotificationDelivery pending = pending(nextUserId, NotificationType.BOOSTER_RECHARGED, "booster-next");
            when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
            when(boosterGrantRepository.findUserIdsWhoExhaustedDailyBoosters(today.minusDays(1), 3))
                    .thenReturn(List.of(failingUserId, nextUserId));
            when(boosterGrantRepository.countByUserIdAndGrantDate(failingUserId, today)).thenThrow(new IllegalStateException("boom"));
            when(boosterGrantRepository.countByUserIdAndGrantDate(nextUserId, today)).thenReturn(0L);
            stubAgreed(nextUserId, NotificationType.BOOSTER_RECHARGED);
            when(persistenceService.createPending(any(), any(), anyString(), anyString(), anyString())).thenReturn(Optional.of(pending));
            stubSuccessfulToss(nextUserId, pending);

            assertThat(service.sendBoosterRechargedForDate(today)).containsExactly(pending);
            verify(boosterGrantRepository).countByUserIdAndGrantDate(nextUserId, today);
        }
    }

    @Test
    void morningRechargedCandidateIsExcludedFromDailyReminder() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.parse("2026-07-25");
        NotificationDelivery pending = pending(userId, NotificationType.BOOSTER_RECHARGED, "morning-boost");
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(boosterGrantRepository.findUserIdsWhoExhaustedDailyBoosters(today.minusDays(1), 3)).thenReturn(List.of(userId));
        when(boosterGrantRepository.countByUserIdAndGrantDate(userId, today)).thenReturn(0L);
        when(preferenceRepository.findSendableUserIdsByType(NotificationType.DAILY_REMINDER)).thenReturn(List.of(userId));
        stubAgreed(userId, NotificationType.BOOSTER_RECHARGED);
        when(persistenceService.createPending(userId, NotificationType.BOOSTER_RECHARGED,
                "BOOSTER_RECHARGED:" + userId + ":20260725", "TPL_BOOST_SET", "{}"))
                .thenReturn(Optional.of(pending));
        stubSuccessfulToss(userId, pending);

        assertThat(service.sendMorningNotifications(today)).containsExactly(pending);
        verify(persistenceService, never()).createPending(userId, NotificationType.DAILY_REMINDER,
                "DAILY_REMINDER:" + userId + ":20260725", "TPL_DAILY_SET", "{}");
    }

    @Test
    void eveningPartialBoosterUseWithoutValidTapSendsBoosterUnused() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.parse("2026-07-25");
        NotificationDelivery pending = pending(userId, NotificationType.BOOSTER_UNUSED, "unused");
        when(preferenceRepository.findSendableUserIdsByType(NotificationType.RANK_CHANGE)).thenReturn(List.of());
        when(preferenceRepository.findSendableUserIdsByType(NotificationType.BOOSTER_UNUSED)).thenReturn(List.of(userId));
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(userTapDailyRepository.existsByUserIdAndTapDateAndValidTapCountGreaterThan(userId, today, 0)).thenReturn(false);
        when(boosterGrantRepository.countByUserIdAndGrantDate(userId, today)).thenReturn(1L);
        stubAgreed(userId, NotificationType.BOOSTER_UNUSED);
        when(persistenceService.createPending(userId, NotificationType.BOOSTER_UNUSED,
                "BOOSTER_UNUSED:" + userId + ":20260725", "TPL_UNUSED_SET", "{}"))
                .thenReturn(Optional.of(pending));
        stubSuccessfulToss(userId, pending);

        assertThat(service.sendEveningNotifications(today)).containsExactly(pending);
    }

    private void stubAgreed(UUID userId, NotificationType type) {
        NotificationPreference preference = NotificationPreference.defaultOf(userId, type);
        preference.applyAgreement("newAgreement");
        when(preferenceRepository.findByUserIdAndType(userId, type)).thenReturn(Optional.of(preference));
    }

    private void stubSuccessfulToss(UUID userId, NotificationDelivery pending) {
        AuthIdentity identity = AuthIdentity.toss(AppUser.createActive("me", null), "toss-user-1");
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)).thenReturn(Optional.of(identity));
        when(smartMessageClient.sendMessage("toss-user-1", pending.getTemplateSetCode(), pending.getContextJson())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new TossSmartMessageClient.SendResult(true, "content-1", null, null, false, "{\"ok\":true}");
        });
        when(persistenceService.markSent(pending.getId(), "content-1", "{\"ok\":true}")).thenReturn(pending);
    }

    private NotificationDelivery pending(UUID userId, NotificationType type, String dedupeKey) {
        NotificationDelivery delivery = NotificationDelivery.pending(userId, type, dedupeKey, switch (type) {
            case WEEKLY_REWARD_AVAILABLE -> "TPL_WEEKLY_SET";
            case RANK_CHANGE -> "TPL_RANK_SET";
            case BOOSTER_RECHARGED -> "TPL_BOOST_SET";
            case DAILY_REMINDER -> "TPL_DAILY_SET";
            case BOOSTER_UNUSED -> "TPL_UNUSED_SET";
        }, Instant.now());
        ReflectionTestUtils.setField(delivery, "id", Math.abs(dedupeKey.hashCode()) + 1L);
        return delivery;
    }
}
