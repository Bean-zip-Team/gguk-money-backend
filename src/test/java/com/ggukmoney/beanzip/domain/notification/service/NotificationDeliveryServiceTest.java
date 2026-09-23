package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.mission.service.DailyMissionNudgeService;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.event.WeeklyRewardAvailableEvent;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);
    private static final DailyMissionNudgeService.NudgeCriteria CRITERIA =
            new DailyMissionNudgeService.NudgeCriteria("2026-07-25", List.of("ATTENDANCE"));

    private final NotificationDeliveryPersistenceService persistenceService = mock(NotificationDeliveryPersistenceService.class);
    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
    private final BoosterGrantRepository boosterGrantRepository = mock(BoosterGrantRepository.class);
    private final UserTapDailyRepository userTapDailyRepository = mock(UserTapDailyRepository.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final KeycapBoxAccountRepository keycapBoxAccountRepository = mock(KeycapBoxAccountRepository.class);
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig = mock(KeycapBoxPolicyConfig.class);
    private final NotificationTemplateProperties templateProperties = new NotificationTemplateProperties(
            "TPL_WEEKLY",
            "clickmoney-asfasf",
            "TPL_BOOSTER",
            "TPL_DAILY",
            "TPL_MISSION",
            "TPL_UNUSED",
            "clickmoney-box"
    );
    private final TossSmartMessageClient smartMessageClient = mock(TossSmartMessageClient.class);
    private final NotificationBatchReadService batchReadService = mock(NotificationBatchReadService.class);
    private final DailyMissionNudgeService dailyMissionNudgeService = mock(DailyMissionNudgeService.class);
    private final NotificationDeliveryService service = new NotificationDeliveryService(
            persistenceService,
            preferenceRepository,
            authIdentityRepository,
            boosterGrantRepository,
            tapPolicyConfig,
            keycapBoxAccountRepository,
            keycapBoxPolicyConfig,
            templateProperties,
            smartMessageClient,
            batchReadService,
            dailyMissionNudgeService,
            CLOCK
    );

    @Test
    void deliveryServiceHasNoTransactionalBoundaryAroundTossCall() throws Exception {
        assertThat(NotificationDeliveryService.class.getAnnotation(Transactional.class)).isNull();
        for (String methodName : List.of(
                "handleWeeklyRewardAvailable",
                "evaluateRankChange",
                "sendMorningNotifications",
                "sendEveningNotifications",
                "sendDailyMissionNotifications",
                "sendKeycapBoxOpenAvailableNotifications"
        )) {
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
            NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, "weekly");
            stubAgreed(userId, NotificationType.RANK_CHANGE);
            when(persistenceService.createPending(
                    userId,
                    NotificationType.RANK_CHANGE,
                    "RANK_CHANGE:WEEKLY_REWARD_AVAILABLE:" + userId + ":2026-W30",
                    "clickmoney-asfasf",
                    "{\"rewardCycleKey\":\"2026-W30\",\"availableAt\":\"2026-07-25T00:00:00Z\"}"
            )).thenReturn(Optional.of(pending));

            stubSuccessfulToss(userId, pending);

            assertThat(service.handleWeeklyRewardAvailable(event)).contains(pending);
        }

        @Test
        void unconfiguredCampaignDoesNotCreatePendingDeliveryOrCallToss() {
            UUID userId = UUID.randomUUID();
            WeeklyRewardAvailableEvent event = new WeeklyRewardAvailableEvent(userId, "2026-W30", Instant.parse("2026-07-25T00:00:00Z"));
            NotificationDeliveryService unconfiguredService = new NotificationDeliveryService(
                    persistenceService,
                    preferenceRepository,
                    authIdentityRepository,
                    boosterGrantRepository,
                    tapPolicyConfig,
                    keycapBoxAccountRepository,
                    keycapBoxPolicyConfig,
                    new NotificationTemplateProperties(null, null, "TPL_BOOSTER", null, null, null, null),
                    smartMessageClient,
                    batchReadService,
                    dailyMissionNudgeService,
                    CLOCK
            );
            stubAgreed(userId, NotificationType.RANK_CHANGE);

            assertThat(unconfiguredService.handleWeeklyRewardAvailable(event)).isEmpty();

            verify(persistenceService, never()).createPending(any(), any(), anyString(), any(), anyString());
            verify(smartMessageClient, never()).sendMessage(anyString(), anyString(), anyString());
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
            when(smartMessageClient.sendMessage("toss-user-1", "clickmoney-asfasf", "{}"))
                    .thenReturn(new TossSmartMessageClient.SendResult(false, null, "RATE_LIMITED", "temporary", true, null, "{\"error\":true}"));
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
            when(batchReadService.findCandidates(NotificationType.BOOSTER_RECHARGED, 0L))
                    .thenReturn(List.of(sendableCandidate(1L, userId)));
            when(batchReadService.findCandidates(NotificationType.DAILY_REMINDER, 0L)).thenReturn(List.of());
            when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today)))
                    .thenReturn(pageData(Map.of(userId, "toss-user-1"), Set.of(), Map.of()));
            when(persistenceService.createPending(
                    userId,
                    NotificationType.BOOSTER_RECHARGED,
                    "BOOSTER_RECHARGED:" + userId + ":20260725",
                    "TPL_BOOSTER",
                    "{}"
            )).thenReturn(Optional.of(pending));
            stubSuccessfulToss(userId, pending);

            assertThat(service.sendMorningNotifications(today)).containsExactly(pending);
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
            when(batchReadService.findCandidates(NotificationType.BOOSTER_RECHARGED, 0L))
                    .thenReturn(List.of(sendableCandidate(1L, failingUserId), sendableCandidate(2L, nextUserId)));
            when(batchReadService.findCandidates(NotificationType.DAILY_REMINDER, 0L)).thenReturn(List.of());
            when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today)))
                    .thenReturn(pageData(
                            Map.of(failingUserId, "toss-failing", nextUserId, "toss-user-1"),
                            Set.of(),
                            Map.of()
                    ));
            when(persistenceService.createPending(
                    failingUserId, NotificationType.BOOSTER_RECHARGED, "BOOSTER_RECHARGED:" + failingUserId + ":20260725",
                    "TPL_BOOSTER", "{}"
            )).thenThrow(new IllegalStateException("boom"));
            when(persistenceService.createPending(
                    nextUserId, NotificationType.BOOSTER_RECHARGED, "BOOSTER_RECHARGED:" + nextUserId + ":20260725",
                    "TPL_BOOSTER", "{}"
            )).thenReturn(Optional.of(pending));
            stubSuccessfulToss(nextUserId, pending);

            assertThat(service.sendMorningNotifications(today)).containsExactly(pending);
            verify(persistenceService).createPending(
                    nextUserId, NotificationType.BOOSTER_RECHARGED, "BOOSTER_RECHARGED:" + nextUserId + ":20260725",
                    "TPL_BOOSTER", "{}"
            );
        }
    }

    @Test
    void morningRechargedCandidateIsExcludedFromDailyReminder() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.parse("2026-07-25");
        NotificationDelivery pending = pending(userId, NotificationType.BOOSTER_RECHARGED, "morning-boost");
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(boosterGrantRepository.findUserIdsWhoExhaustedDailyBoosters(today.minusDays(1), 3)).thenReturn(List.of(userId));
        when(batchReadService.findCandidates(NotificationType.BOOSTER_RECHARGED, 0L))
                .thenReturn(List.of(sendableCandidate(1L, userId)));
        when(batchReadService.findCandidates(NotificationType.DAILY_REMINDER, 0L))
                .thenReturn(List.of(sendableCandidate(2L, userId)));
        when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today)))
                .thenReturn(pageData(Map.of(userId, "toss-user-1"), Set.of(), Map.of()));
        when(persistenceService.createPending(userId, NotificationType.BOOSTER_RECHARGED,
                "BOOSTER_RECHARGED:" + userId + ":20260725", "TPL_BOOSTER", "{}"))
                .thenReturn(Optional.of(pending));
        stubSuccessfulToss(userId, pending);

        assertThat(service.sendMorningNotifications(today)).containsExactly(pending);
        verify(persistenceService, never()).createPending(userId, NotificationType.DAILY_REMINDER,
                "DAILY_REMINDER:" + userId + ":20260725", "TPL_DAILY", "{}");
    }

    @Test
    void eveningPartialBoosterUseWithoutValidTapSendsBoosterUnused() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.parse("2026-07-25");
        NotificationDelivery pending = pending(userId, NotificationType.BOOSTER_UNUSED, "unused");
        when(batchReadService.findCandidates(NotificationType.RANK_CHANGE, 0L)).thenReturn(List.of());
        when(batchReadService.findCandidates(NotificationType.BOOSTER_UNUSED, 0L))
                .thenReturn(List.of(sendableCandidate(1L, userId)));
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today)))
                .thenReturn(pageData(Map.of(userId, "toss-user-1"), Set.of(), Map.of(userId, 1L)));
        when(persistenceService.createPending(userId, NotificationType.BOOSTER_UNUSED,
                "BOOSTER_UNUSED:" + userId + ":20260725", "TPL_UNUSED", "{}"))
                .thenReturn(Optional.of(pending));
        stubSuccessfulToss(userId, pending);

        assertThat(service.sendEveningNotifications(today)).containsExactly(pending);
    }

    @Test
    void eveningSentBoosterRechargedUserDoesNotReceiveBoosterUnused() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.parse("2026-07-25");
        when(batchReadService.findCandidates(NotificationType.RANK_CHANGE, 0L)).thenReturn(List.of());
        when(batchReadService.findCandidates(NotificationType.BOOSTER_UNUSED, 0L))
                .thenReturn(List.of(sendableCandidate(1L, userId)));
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today)))
                .thenReturn(pageData(Map.of(userId, "toss-user-1"), Set.of(), Map.of(userId, 1L)));
        when(batchReadService.findSentUserIdsSinceStartOfDay(
                List.of(userId), NotificationType.BOOSTER_RECHARGED, today
        )).thenReturn(Set.of(userId));

        assertThat(service.sendEveningNotifications(today)).isEmpty();

        verify(persistenceService, never()).createPending(
                userId,
                NotificationType.BOOSTER_UNUSED,
                "BOOSTER_UNUSED:" + userId + ":20260725",
                "TPL_UNUSED",
                "{}"
        );
    }

    @Test
    void eveningUsersWithoutSentBoosterRechargedDeliveryRemainEligibleForBoosterUnused() {
        UUID failedUserId = UUID.randomUUID();
        UUID retryWaitingUserId = UUID.randomUUID();
        LocalDate today = LocalDate.parse("2026-07-25");
        NotificationDelivery failedUserDelivery = pending(
                failedUserId, NotificationType.BOOSTER_UNUSED, "unused-after-failed-recharged");
        NotificationDelivery retryWaitingUserDelivery = pending(
                retryWaitingUserId, NotificationType.BOOSTER_UNUSED, "unused-after-retry-waiting-recharged");
        when(batchReadService.findCandidates(NotificationType.RANK_CHANGE, 0L)).thenReturn(List.of());
        when(batchReadService.findCandidates(NotificationType.BOOSTER_UNUSED, 0L)).thenReturn(List.of(
                sendableCandidate(1L, failedUserId),
                sendableCandidate(2L, retryWaitingUserId)
        ));
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today))).thenReturn(pageData(
                Map.of(failedUserId, "toss-user-1", retryWaitingUserId, "toss-user-1"),
                Set.of(),
                Map.of(failedUserId, 1L, retryWaitingUserId, 1L)
        ));
        when(batchReadService.findSentUserIdsSinceStartOfDay(
                List.of(failedUserId, retryWaitingUserId), NotificationType.BOOSTER_RECHARGED, today
        )).thenReturn(Set.of());
        when(persistenceService.createPending(
                failedUserId, NotificationType.BOOSTER_UNUSED,
                "BOOSTER_UNUSED:" + failedUserId + ":20260725", "TPL_UNUSED", "{}"
        )).thenReturn(Optional.of(failedUserDelivery));
        when(persistenceService.createPending(
                retryWaitingUserId, NotificationType.BOOSTER_UNUSED,
                "BOOSTER_UNUSED:" + retryWaitingUserId + ":20260725", "TPL_UNUSED", "{}"
        )).thenReturn(Optional.of(retryWaitingUserDelivery));
        stubSuccessfulToss(failedUserId, failedUserDelivery);
        stubSuccessfulToss(retryWaitingUserId, retryWaitingUserDelivery);

        assertThat(service.sendEveningNotifications(today))
                .containsExactly(failedUserDelivery, retryWaitingUserDelivery);
    }

    @Test
    void eveningRankChangeUserDoesNotReceiveBoosterUnused() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.parse("2026-07-25");
        RankingSeason season = mock(RankingSeason.class);
        NotificationDelivery rankDelivery = pending(userId, NotificationType.RANK_CHANGE, "rank-evening");
        when(batchReadService.findCandidates(NotificationType.RANK_CHANGE, 0L))
                .thenReturn(List.of(sendableCandidate(1L, userId)));
        when(batchReadService.findCandidates(NotificationType.BOOSTER_UNUSED, 0L))
                .thenReturn(List.of(sendableCandidate(2L, userId)));
        when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today)))
                .thenReturn(pageData(Map.of(userId, "toss-user-1"), Set.of(), Map.of(userId, 1L)));
        when(batchReadService.loadRankPage(List.of(userId))).thenReturn(Optional.of(
                new NotificationBatchReadService.RankPageData(
                        season, Map.of(userId, 7L), Map.of(), Set.of()
                )
        ));
        when(batchReadService.findSentUserIdsSinceStartOfDay(
                List.of(userId), NotificationType.BOOSTER_RECHARGED, today
        )).thenReturn(Set.of());
        when(persistenceService.prepareScheduledRankChange(userId, season, 7L, null, false))
                .thenReturn(Optional.of(rankDelivery));
        stubSuccessfulToss(userId, rankDelivery);

        assertThat(service.sendEveningNotifications(today)).containsExactly(rankDelivery);

        verify(persistenceService, never()).createPending(
                userId,
                NotificationType.BOOSTER_UNUSED,
                "BOOSTER_UNUSED:" + userId + ":20260725",
                "TPL_UNUSED",
                "{}"
        );
    }

    @Test
    void eveningBoosterUnusedCandidatesUseOneSentDeliveryBatchReadPerPage() {
        LocalDate today = LocalDate.parse("2026-07-25");
        List<NotificationPreferenceRepository.SendableCandidate> firstPage = candidates(1, 100);
        List<NotificationPreferenceRepository.SendableCandidate> secondPage = candidates(101, 200);
        List<NotificationPreferenceRepository.SendableCandidate> thirdPage = candidates(201, 201);
        when(batchReadService.findCandidates(NotificationType.RANK_CHANGE, 0L)).thenReturn(List.of());
        when(batchReadService.findCandidates(NotificationType.BOOSTER_UNUSED, 0L)).thenReturn(firstPage);
        when(batchReadService.findCandidates(NotificationType.BOOSTER_UNUSED, 100L)).thenReturn(secondPage);
        when(batchReadService.findCandidates(NotificationType.BOOSTER_UNUSED, 200L)).thenReturn(thirdPage);
        when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today))).thenAnswer(invocation -> {
            List<UUID> userIds = invocation.getArgument(0);
            return pageData(Map.of(), Set.copyOf(userIds), Map.of());
        });
        when(batchReadService.findSentUserIdsSinceStartOfDay(
                any(),
                org.mockito.ArgumentMatchers.eq(NotificationType.BOOSTER_RECHARGED),
                org.mockito.ArgumentMatchers.eq(today)
        )).thenReturn(Set.of());

        assertThat(service.sendEveningNotifications(today)).isEmpty();

        verify(batchReadService, times(3)).loadPage(any(), org.mockito.ArgumentMatchers.eq(today));
        verify(batchReadService, times(3)).findSentUserIdsSinceStartOfDay(
                any(),
                org.mockito.ArgumentMatchers.eq(NotificationType.BOOSTER_RECHARGED),
                org.mockito.ArgumentMatchers.eq(today)
        );
        verify(preferenceRepository, never()).findByUserIdAndType(any(), any());
        verify(authIdentityRepository, never()).findByUserIdAndProvider(any(), any());
        verify(userTapDailyRepository, never())
                .existsByUserIdAndTapDateAndValidTapCountGreaterThan(any(), any(), any());
        verify(boosterGrantRepository, never()).countByUserIdAndGrantDate(any(), any());
    }

    @Test
    void morningDailyCandidatesUseThreeKeysetPagesWithoutPerUserReads() {
        LocalDate today = LocalDate.parse("2026-07-25");
        List<NotificationPreferenceRepository.SendableCandidate> firstPage = candidates(1, 100);
        List<NotificationPreferenceRepository.SendableCandidate> secondPage = candidates(101, 200);
        List<NotificationPreferenceRepository.SendableCandidate> thirdPage = candidates(201, 201);
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(boosterGrantRepository.findUserIdsWhoExhaustedDailyBoosters(today.minusDays(1), 3))
                .thenReturn(List.of());
        when(batchReadService.findCandidates(NotificationType.BOOSTER_RECHARGED, 0L)).thenReturn(List.of());
        when(batchReadService.findCandidates(NotificationType.DAILY_REMINDER, 0L)).thenReturn(firstPage);
        when(batchReadService.findCandidates(NotificationType.DAILY_REMINDER, 100L)).thenReturn(secondPage);
        when(batchReadService.findCandidates(NotificationType.DAILY_REMINDER, 200L)).thenReturn(thirdPage);
        when(batchReadService.loadPage(any(), org.mockito.ArgumentMatchers.eq(today)))
                .thenReturn(new NotificationBatchReadService.NotificationPageData(Map.of(), Set.of(), Map.of()));

        assertThat(service.sendMorningNotifications(today)).isEmpty();

        verify(batchReadService, times(3)).loadPage(any(), org.mockito.ArgumentMatchers.eq(today));
        verify(batchReadService).findCandidates(NotificationType.DAILY_REMINDER, 100L);
        verify(batchReadService).findCandidates(NotificationType.DAILY_REMINDER, 200L);
        verify(preferenceRepository, never()).findByUserIdAndType(any(), any());
        verify(authIdentityRepository, never()).findByUserIdAndProvider(any(), any());
        verify(userTapDailyRepository, never())
                .existsByUserIdAndTapDateAndValidTapCountGreaterThan(any(), any(), any());
        verify(boosterGrantRepository, never()).countByUserIdAndGrantDate(any(), any());
    }

    @Nested
    class DailyMission {

        private final LocalDate today = LocalDate.parse("2026-07-25");

        @Test
        void sendsOnlyToUsersTheNudgeServicePicked() {
            UUID nudgedUserId = UUID.randomUUID();
            UUID doneUserId = UUID.randomUUID();
            NotificationDelivery pending = pending(nudgedUserId, NotificationType.DAILY_MISSION, "mission-nudge");
            when(batchReadService.findCandidates(NotificationType.DAILY_MISSION, 0L)).thenReturn(List.of(
                    sendableCandidate(1L, nudgedUserId),
                    sendableCandidate(2L, doneUserId)
            ));
            when(dailyMissionNudgeService.criteriaOf(today)).thenReturn(CRITERIA);
            when(dailyMissionNudgeService.usersNeedingNudge(
                    CRITERIA, List.of(nudgedUserId, doneUserId), CLOCK.instant()
            )).thenReturn(Set.of(nudgedUserId));
            when(batchReadService.loadProviderUserIds(List.of(nudgedUserId, doneUserId)))
                    .thenReturn(Map.of(nudgedUserId, "toss-user-1", doneUserId, "toss-user-1"));
            when(persistenceService.createPending(
                    nudgedUserId,
                    NotificationType.DAILY_MISSION,
                    "DAILY_MISSION:" + nudgedUserId + ":20260725",
                    "TPL_MISSION",
                    "{}"
            )).thenReturn(Optional.of(pending));
            stubSuccessfulToss(nudgedUserId, pending);

            assertThat(service.sendDailyMissionNotifications(today)).containsExactly(pending);

            // 오늘 미션을 다 받아 간 유저에게는 보내지 않는다. 할 일이 없는데 오는 알림이기 때문이다.
            verify(persistenceService, never()).createPending(
                    doneUserId,
                    NotificationType.DAILY_MISSION,
                    "DAILY_MISSION:" + doneUserId + ":20260725",
                    "TPL_MISSION",
                    "{}"
            );
        }

        @Test
        void oneFailingUserDoesNotStopTheRestOfThePage() {
            UUID failingUserId = UUID.randomUUID();
            UUID nextUserId = UUID.randomUUID();
            NotificationDelivery pending = pending(nextUserId, NotificationType.DAILY_MISSION, "mission-next");
            when(batchReadService.findCandidates(NotificationType.DAILY_MISSION, 0L)).thenReturn(List.of(
                    sendableCandidate(1L, failingUserId),
                    sendableCandidate(2L, nextUserId)
            ));
            when(dailyMissionNudgeService.criteriaOf(today)).thenReturn(CRITERIA);
            when(dailyMissionNudgeService.usersNeedingNudge(
                    CRITERIA, List.of(failingUserId, nextUserId), CLOCK.instant()
            )).thenReturn(Set.of(failingUserId, nextUserId));
            when(batchReadService.loadProviderUserIds(List.of(failingUserId, nextUserId)))
                    .thenReturn(Map.of(failingUserId, "toss-user-1", nextUserId, "toss-user-1"));
            when(persistenceService.createPending(
                    failingUserId,
                    NotificationType.DAILY_MISSION,
                    "DAILY_MISSION:" + failingUserId + ":20260725",
                    "TPL_MISSION",
                    "{}"
            )).thenThrow(new IllegalStateException("boom"));
            when(persistenceService.createPending(
                    nextUserId,
                    NotificationType.DAILY_MISSION,
                    "DAILY_MISSION:" + nextUserId + ":20260725",
                    "TPL_MISSION",
                    "{}"
            )).thenReturn(Optional.of(pending));
            stubSuccessfulToss(nextUserId, pending);

            assertThat(service.sendDailyMissionNotifications(today)).containsExactly(pending);
        }

        // 커서가 전진하지 않으면 첫 페이지를 영원히 다시 읽는다. 제한이 없으면 실패가 아니라 멈춤으로 나타난다.
        @Test
        @Timeout(10)
        void walksEveryKeysetPageOfCandidates() {
            List<NotificationPreferenceRepository.SendableCandidate> firstPage = candidates(1, 100);
            List<NotificationPreferenceRepository.SendableCandidate> secondPage = candidates(101, 101);
            when(batchReadService.findCandidates(NotificationType.DAILY_MISSION, 0L)).thenReturn(firstPage);
            when(batchReadService.findCandidates(NotificationType.DAILY_MISSION, 100L)).thenReturn(secondPage);
            when(dailyMissionNudgeService.usersNeedingNudge(any(), any(), any())).thenReturn(Set.of());
            when(batchReadService.loadProviderUserIds(any())).thenReturn(Map.of());

            assertThat(service.sendDailyMissionNotifications(today)).isEmpty();

            verify(batchReadService).findCandidates(NotificationType.DAILY_MISSION, 100L);
            verify(persistenceService, never()).createPending(any(), any(), anyString(), any(), anyString());
        }

        @Test
        void unconfiguredCampaignDoesNotQueryCandidates() {
            NotificationDeliveryService unconfiguredService = new NotificationDeliveryService(
                    persistenceService,
                    preferenceRepository,
                    authIdentityRepository,
                    boosterGrantRepository,
                    tapPolicyConfig,
                    keycapBoxAccountRepository,
                    keycapBoxPolicyConfig,
                    new NotificationTemplateProperties(null, "clickmoney-asfasf", null, null, null, null, null),
                    smartMessageClient,
                    batchReadService,
                    dailyMissionNudgeService,
                    CLOCK
            );

            assertThat(unconfiguredService.sendDailyMissionNotifications(today)).isEmpty();

            verify(batchReadService, never()).findCandidates(
                    org.mockito.ArgumentMatchers.eq(NotificationType.DAILY_MISSION),
                    org.mockito.ArgumentMatchers.anyLong()
            );
            verify(dailyMissionNudgeService, never()).criteriaOf(any());
            verify(dailyMissionNudgeService, never()).usersNeedingNudge(any(), any(), any());
        }
    }

    @Nested
    class KeycapBoxOpenAvailable {

        @Test
        void unconfiguredCampaignDoesNotQueryCandidates() {
            NotificationDeliveryService unconfiguredService = new NotificationDeliveryService(
                    persistenceService,
                    preferenceRepository,
                    authIdentityRepository,
                    boosterGrantRepository,
                    tapPolicyConfig,
                    keycapBoxAccountRepository,
                    keycapBoxPolicyConfig,
                    new NotificationTemplateProperties(null, "clickmoney-asfasf", null, null, null, null, null),
                    smartMessageClient,
                    batchReadService,
                    dailyMissionNudgeService,
                    CLOCK
            );

            assertThat(unconfiguredService.sendKeycapBoxOpenAvailableNotifications(
                    Instant.parse("2026-08-03T01:01:00Z")
            )).isEmpty();

            verify(keycapBoxAccountRepository, never()).findKeycapBoxOpenAvailableCandidates(
                    any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt(), any(), any()
            );
        }

        @Test
        void keysetCursorAdvancesAfterFailureAndDispatchesNextCandidate() {
            UUID failingUserId = UUID.randomUUID();
            UUID nextUserId = UUID.randomUUID();
            Instant now = Instant.parse("2026-08-03T01:01:00Z");
            Instant cutoff = Instant.parse("2026-08-03T00:01:00Z");
            KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate failingCandidate = candidate(1L, failingUserId);
            KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate nextCandidate = candidate(2L, nextUserId);
            List<KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate> firstBatch = new java.util.ArrayList<>();
            firstBatch.add(failingCandidate);
            firstBatch.add(nextCandidate);
            IntStream.rangeClosed(3, 200)
                    .mapToObj(accountId -> candidate(accountId, UUID.randomUUID()))
                    .forEach(firstBatch::add);
            NotificationDelivery pending = pending(nextUserId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE, "keycap-next");
            when(keycapBoxPolicyConfig.openCycleDuration()).thenReturn(Duration.ofHours(1));
            when(keycapBoxPolicyConfig.freeOpenLimit()).thenReturn(2);
            when(keycapBoxPolicyConfig.adOpenLimit()).thenReturn(2);
            when(keycapBoxAccountRepository.findKeycapBoxOpenAvailableCandidates(
                    org.mockito.ArgumentMatchers.eq(NotificationType.KEYCAP_BOX_OPEN_AVAILABLE),
                    org.mockito.ArgumentMatchers.eq(0L),
                    org.mockito.ArgumentMatchers.eq(2),
                    org.mockito.ArgumentMatchers.eq(2),
                    org.mockito.ArgumentMatchers.eq(cutoff),
                    org.mockito.ArgumentMatchers.any()
            )).thenReturn(firstBatch);
            when(keycapBoxAccountRepository.findKeycapBoxOpenAvailableCandidates(
                    org.mockito.ArgumentMatchers.eq(NotificationType.KEYCAP_BOX_OPEN_AVAILABLE),
                    org.mockito.ArgumentMatchers.eq(200L),
                    org.mockito.ArgumentMatchers.eq(2),
                    org.mockito.ArgumentMatchers.eq(2),
                    org.mockito.ArgumentMatchers.eq(cutoff),
                    org.mockito.ArgumentMatchers.any()
            )).thenReturn(List.of());
            when(persistenceService.prepareKeycapBoxOpenAvailable(failingUserId, now))
                    .thenThrow(new IllegalStateException("boom"));
            when(persistenceService.prepareKeycapBoxOpenAvailable(nextUserId, now)).thenReturn(Optional.of(pending));
            stubSuccessfulToss(nextUserId, pending);

            assertThat(service.sendKeycapBoxOpenAvailableNotifications(now)).containsExactly(pending);

            verify(keycapBoxAccountRepository).findKeycapBoxOpenAvailableCandidates(
                    org.mockito.ArgumentMatchers.eq(NotificationType.KEYCAP_BOX_OPEN_AVAILABLE),
                    org.mockito.ArgumentMatchers.eq(200L),
                    org.mockito.ArgumentMatchers.eq(2),
                    org.mockito.ArgumentMatchers.eq(2),
                    org.mockito.ArgumentMatchers.eq(cutoff),
                    org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 200)
            );
            verify(persistenceService).prepareKeycapBoxOpenAvailable(nextUserId, now);
        }
    }

    private void stubAgreed(UUID userId, NotificationType type) {
        NotificationPreference preference = NotificationPreference.defaultOf(userId, type);
        preference.applyAgreement("newAgreement");
        when(preferenceRepository.findByUserIdAndType(userId, type)).thenReturn(Optional.of(preference));
    }

    private List<NotificationPreferenceRepository.SendableCandidate> candidates(int firstId, int lastId) {
        return IntStream.rangeClosed(firstId, lastId)
                .mapToObj(id -> {
                    NotificationPreferenceRepository.SendableCandidate candidate =
                            mock(NotificationPreferenceRepository.SendableCandidate.class);
                    when(candidate.getPreferenceId()).thenReturn((long) id);
                    when(candidate.getUserId()).thenReturn(new UUID(0L, id));
                    return candidate;
                })
                .toList();
    }

    private NotificationPreferenceRepository.SendableCandidate sendableCandidate(long preferenceId, UUID userId) {
        return new NotificationPreferenceRepository.SendableCandidate() {
            @Override
            public Long getPreferenceId() {
                return preferenceId;
            }

            @Override
            public UUID getUserId() {
                return userId;
            }
        };
    }

    private NotificationBatchReadService.NotificationPageData pageData(
            Map<UUID, String> providerUserIds,
            Set<UUID> validTapUserIds,
            Map<UUID, Long> boosterCounts
    ) {
        return new NotificationBatchReadService.NotificationPageData(
                providerUserIds, validTapUserIds, boosterCounts
        );
    }

    private void stubSuccessfulToss(UUID userId, NotificationDelivery pending) {
        AuthIdentity identity = AuthIdentity.toss(AppUser.createActive("me", null), "toss-user-1");
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)).thenReturn(Optional.of(identity));
        when(smartMessageClient.sendMessage("toss-user-1", pending.getTemplateSetCode(), pending.getContextJson())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new TossSmartMessageClient.SendResult(true, "content-1", null, null, false, "SUCCESS", "{\"ok\":true}");
        });
        when(persistenceService.markSent(pending.getId(), "content-1", "{\"ok\":true}")).thenReturn(pending);
    }

    private KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate candidate(long accountId, UUID userId) {
        KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate candidate =
                mock(KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate.class);
        when(candidate.getAccountId()).thenReturn(accountId);
        when(candidate.getUserId()).thenReturn(userId);
        return candidate;
    }

    private NotificationDelivery pending(UUID userId, NotificationType type, String dedupeKey) {
        NotificationDelivery delivery = NotificationDelivery.pending(userId, type, dedupeKey, switch (type) {
            case WEEKLY_REWARD_AVAILABLE -> "TPL_WEEKLY";
            case RANK_CHANGE -> "clickmoney-asfasf";
            case BOOSTER_RECHARGED -> "TPL_BOOSTER";
            case DAILY_REMINDER -> "TPL_DAILY";
            case DAILY_MISSION -> "TPL_MISSION";
            case BOOSTER_UNUSED -> "TPL_UNUSED";
            case KEYCAP_BOX_OPEN_AVAILABLE -> "clickmoney-box";
        }, Instant.now());
        ReflectionTestUtils.setField(delivery, "id", Math.abs(dedupeKey.hashCode()) + 1L);
        return delivery;
    }
}
