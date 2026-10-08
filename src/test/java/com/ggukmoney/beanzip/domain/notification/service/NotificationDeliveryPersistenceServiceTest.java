package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDeliveryStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationRankState;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationRankStateRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.global.config.AppConfigBatchLoader;
import com.ggukmoney.beanzip.global.config.RankChangeNotificationPolicyConfig;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryPersistenceServiceTest {

    private final NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final NotificationRankStateRepository rankStateRepository = mock(NotificationRankStateRepository.class);
    private final RankingSeasonService rankingSeasonService = mock(RankingSeasonService.class);
    private final RankingEntryRepository rankingEntryRepository = mock(RankingEntryRepository.class);
    private final AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
    private final RankChangeNotificationPolicyConfig rankChangePolicyConfig =
            new RankChangeNotificationPolicyConfig(new AppConfigBatchLoader(appConfigRepository));
    private final NotificationDeliveryPersistenceService service = new NotificationDeliveryPersistenceService(
            deliveryRepository,
            preferenceRepository,
            rankStateRepository,
            rankingSeasonService,
            rankingEntryRepository,
            rankChangePolicyConfig,
            new NotificationTemplateProperties("WEEKLY", "RANK_SET", "BOOSTER", null, null, null, "clickmoney-box"),
            Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void systemBoostPreparesOneStepDropWithExistingRankTypeAndCampaign() {
        UUID userId = UUID.randomUUID();
        String key = "RANK_CHANGE:SYSTEM_RANKING_BOOST:1:2026-07-25:" + userId;
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, key);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(deliveryRepository.insertPendingIfAbsent(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.eq("{\"currentRank\":2,\"rankChange\":1,\"direction\":\"DOWN\"}"),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(key)).thenReturn(Optional.of(pending));
        Optional<?> result = ReflectionTestUtils.invokeMethod(service, "prepareSystemRankingBoost",
                userId, 1L, LocalDate.of(2026, 7, 25), 1L, 2L);
        assertThat(result.orElseThrow()).isEqualTo(pending);
    }

    @Test
    void ordinaryOneStepDropCreatesPendingWithDefaultPolicy() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, 1L, 1L, Instant.parse("2026-07-25T09:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE"), org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("RANK_SET"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareScheduledRankChange(userId, season, 2L, state, false)).contains(pending);
        assertThat(state.getBaselineRank()).isEqualTo(2L);
    }

    @Test
    void topTenExitAloneDoesNotOverrideConfiguredThreeRankThreshold() {
        when(appConfigRepository.findLatestEffectiveByConfigKeys(
                org.mockito.ArgumentMatchers.eq(RankChangeNotificationPolicyConfig.KEYS),
                org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(java.util.List.of(AppConfig.createFor(
                RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, "3", Instant.EPOCH)));
        rankChangePolicyConfig.refresh();
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, 1L, 10L, Instant.parse("2026-07-25T09:00:00Z"));

        assertThat(service.prepareScheduledRankChange(userId, season, 11L, state, false)).isEmpty();
        assertThat(state.getBaselineRank()).isEqualTo(11L);
        verify(deliveryRepository, never()).insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void unchangedRankDoesNotCreatePendingDelivery() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(
                userId, season.getId(), 5L, Instant.parse("2026-07-25T09:00:00Z"));

        assertThat(service.prepareScheduledRankChange(userId, season, 5L, state, false)).isEmpty();

        assertThat(state.getBaselineRank()).isEqualTo(5L);
        verify(deliveryRepository, never()).insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void weeklyResetAttemptsUseOneThenFiveMinuteBackoffAndStopAfterThirdTransientFailure() {
        UUID userId = UUID.randomUUID();
        NotificationDelivery delivery = pending(userId, NotificationType.RANK_CHANGE, "reset-retry");
        delivery.attachWeeklyResetBatch(20L);
        ReflectionTestUtils.setField(delivery, "id", 30L);
        when(deliveryRepository.findById(30L)).thenReturn(Optional.of(delivery));
        Instant first = Instant.parse("2026-07-25T10:00:00Z");

        assertThat(service.claimWeeklyResetAttempt(30L, first)).contains(delivery);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastAttemptAt()).isEqualTo(first);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(Instant.parse("2026-07-25T10:01:00Z"));
        service.markWeeklyResetRetryWaiting(30L, first, "RATE_LIMITED", "temporary", "{\"error\":true}");
        assertThat(delivery.getNextAttemptAt()).isEqualTo(Instant.parse("2026-07-25T10:01:00Z"));

        Instant second = Instant.parse("2026-07-25T10:01:00Z");
        assertThat(service.claimWeeklyResetAttempt(30L, second)).contains(delivery);
        assertThat(delivery.getLastAttemptAt()).isEqualTo(second);
        service.markWeeklyResetRetryWaiting(30L, second, "SERVER_ERROR", "temporary", null);
        assertThat(delivery.getAttemptCount()).isEqualTo(2);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(Instant.parse("2026-07-25T10:06:00Z"));

        Instant third = Instant.parse("2026-07-25T10:06:00Z");
        assertThat(service.claimWeeklyResetAttempt(30L, third)).contains(delivery);
        service.markWeeklyResetRetryWaiting(30L, third, "SERVER_ERROR", "temporary", null);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void weeklyResetEnqueueUsesTheExistingRankChangeCampaignAndSeasonUserKey() {
        UUID userId = UUID.randomUUID();
        when(deliveryRepository.insertWeeklyResetPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE:WEEKLY_RESET:10:" + userId),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.eq(Instant.parse("2026-07-25T10:00:00Z"))
        )).thenReturn(1);

        assertThat(service.enqueueWeeklyReset(userId, 20L, 10L)).isEqualTo(1);

        verify(preferenceRepository, never()).findByUserIdAndType(userId, NotificationType.RANK_CHANGE);
    }

    @Test
    void systemBoostRejectsOtherTransitionsAndMissingConsent() {
        UUID userId = UUID.randomUUID();
        Optional<?> other = ReflectionTestUtils.invokeMethod(service, "prepareSystemRankingBoost",
                userId, 1L, LocalDate.of(2026, 7, 25), 2L, 3L);
        assertThat(other).isEmpty();
        Optional<?> noConsent = ReflectionTestUtils.invokeMethod(service, "prepareSystemRankingBoost",
                userId, 1L, LocalDate.of(2026, 7, 25), 1L, 2L);
        assertThat(noConsent).isEmpty();
    }

    @Test
    void systemBoostRespectsExistingSentCooldown() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(deliveryRepository.existsByUserIdAndTypeAndStatusAndRequestedAtAfter(userId, NotificationType.RANK_CHANGE,
                NotificationDeliveryStatus.SENT, Instant.parse("2026-07-25T04:00:00Z"))).thenReturn(true);
        Optional<?> result = ReflectionTestUtils.invokeMethod(service, "prepareSystemRankingBoost",
                userId, 1L, LocalDate.of(2026, 7, 25), 1L, 2L);
        assertThat(result).isEmpty();
    }

    @Test
    void publicPersistenceMethodsAreTransactional() throws Exception {
        for (String methodName : java.util.List.of(
                "createPending",
                "prepareRankChange",
                "prepareScheduledRankChange",
                "enqueueWeeklyReset",
                "claimWeeklyResetAttempt",
                "markWeeklyResetRetryWaiting",
                "captureRankBaselineOnAgreement",
                "markSent",
                "markRetryWaiting",
                "markFailed"
        )) {
            Method method = java.util.Arrays.stream(NotificationDeliveryPersistenceService.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            assertThat(method.getAnnotation(Transactional.class)).isNotNull();
        }
    }

    @Test
    void scheduledRankChangeUsesPreloadedPolicyInputsWithoutPerUserReads() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(
                userId, season.getId(), 5L, Instant.parse("2026-07-25T09:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareScheduledRankChange(userId, season, 8L, state, false)).contains(pending);

        assertThat(state.getBaselineRank()).isEqualTo(8L);
        verify(preferenceRepository, never()).findByUserIdAndType(userId, NotificationType.RANK_CHANGE);
        verify(rankingSeasonService, never()).findActiveWeeklySeason();
        verify(rankingEntryRepository, never()).findMyParticipant(season, userId);
        verify(rankingEntryRepository, never()).countParticipantsAhead(season, 8L, userId.toString());
    }

    @Test
    void sentAndFailedStoreProviderResponseJson() {
        UUID userId = UUID.randomUUID();
        NotificationDelivery delivery = NotificationDelivery.pending(
                userId,
                NotificationType.WEEKLY_REWARD_AVAILABLE,
                "dedupe",
                "TPL_SET",
                Instant.now()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(delivery, "id", 1L);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        service.markSent(1L, "content-1", "{\"sent\":true}");
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(delivery.getProviderResponseJson()).isEqualTo("{\"sent\":true}");

        service.markFailed(1L, "INVALID_TEMPLATE", "invalid", "{\"error\":true}");
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(delivery.getProviderResponseJson()).isEqualTo("{\"error\":true}");
    }

    @Test
    void significantRankChangeCreatesPendingAndUpdatesBaselineInPreparation() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 5L, Instant.parse("2026-07-25T10:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        NotificationPreference preference = agreed(userId, NotificationType.RANK_CHANGE);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE)).thenReturn(Optional.of(preference));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(7L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareRankChange(userId)).contains(pending);

        assertThat(state.getBaselineRank()).isEqualTo(8L);
        verify(rankStateRepository).saveAndFlush(state);
    }

    @Test
    void rankChangeUsesBaselineRecordedAtToCreateANewEventForTheSameRankTransition() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 5L, Instant.parse("2026-07-25T09:00:00Z"));
        String dedupeKey = "RANK_CHANGE:1:" + userId + ":" + state.getBaselineRecordedAt().toEpochMilli();
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(7L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareRankChange(userId)).contains(pending);
    }

    @Test
    void recentSentRankNotificationSkipsDeliveryButUpdatesBaseline() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 5L, Instant.parse("2026-07-25T09:00:00Z"));
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(7L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.existsByUserIdAndTypeAndStatusAndRequestedAtAfter(
                userId,
                NotificationType.RANK_CHANGE,
                NotificationDeliveryStatus.SENT,
                Instant.parse("2026-07-25T07:00:00Z")
        )).thenReturn(true);

        assertThat(service.prepareRankChange(userId)).isEmpty();

        assertThat(state.getBaselineRank()).isEqualTo(8L);
        verify(deliveryRepository, org.mockito.Mockito.never()).insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void zeroRankNotificationCooldownAllowsImmediateRepeatedRankChanges() {
        when(appConfigRepository.findLatestEffectiveByConfigKeys(
                org.mockito.ArgumentMatchers.eq(RankChangeNotificationPolicyConfig.KEYS),
                org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(java.util.List.of(AppConfig.createFor(
                RankChangeNotificationPolicyConfig.KEY_COOLDOWN_MINUTES, "0", Instant.EPOCH)));
        rankChangePolicyConfig.refresh();
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 5L, Instant.parse("2026-07-25T09:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(7L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareRankChange(userId)).contains(pending);

        verify(deliveryRepository, org.mockito.Mockito.never()).existsByUserIdAndTypeAndStatusAndRequestedAtAfter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void oneRankChangeCreatesPendingForTestThreshold() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 5L, Instant.parse("2026-07-25T10:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(5L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareRankChange(userId)).contains(pending);
    }

    @Test
    void duplicateRankDeliveryStillUpdatesBaselineWithoutReturningPending() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 5L, Instant.parse("2026-07-25T10:00:00Z"));
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(7L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("RANK_SET"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(0);

        assertThat(service.prepareRankChange(userId)).isEmpty();

        assertThat(state.getBaselineRank()).isEqualTo(8L);
        verify(rankStateRepository).saveAndFlush(state);
    }

    @Test
    void firstRankBaselineIsStoredWithoutCreatingPendingDelivery() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(2L);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(4L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.empty());

        assertThat(service.prepareRankChange(userId)).isEmpty();

        verify(deliveryRepository, org.mockito.Mockito.never()).insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()
        );
        verify(rankStateRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(state -> state.getBaselineRank() == 5L));
    }

    @Test
    void smallRankChangeUpdatesBaselineWithoutCreatingPendingDelivery() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 20L, Instant.parse("2026-07-25T10:00:00Z"));
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 982L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 982L, userId.toString())).thenReturn(17L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));

        assertThat(service.prepareRankChange(userId)).isEmpty();

        assertThat(state.getBaselineRank()).isEqualTo(18L);
        verify(deliveryRepository, org.mockito.Mockito.never()).insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void topTenEntryUpdatesBaselineWithoutCreatingPendingDelivery() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 11L, Instant.parse("2026-07-25T10:00:00Z"));
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 990L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 990L, userId.toString())).thenReturn(9L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        assertThat(service.prepareRankChange(userId)).isEmpty();

        assertThat(state.getBaselineRank()).isEqualTo(10L);
        verify(deliveryRepository, org.mockito.Mockito.never()).insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void nonDedupeDataIntegrityViolationIsPropagated() {
        UUID userId = UUID.randomUUID();
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq("dedupe"), org.mockito.ArgumentMatchers.eq("RANK_SET"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()
        )).thenThrow(new org.springframework.dao.DataIntegrityViolationException("other constraint"));

        assertThatThrownBy(() -> service.createPending(userId, NotificationType.RANK_CHANGE, "dedupe", "RANK_SET"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void significantRankDropCreatesPendingWithDownContext() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(
                userId, season.getId(), 5L, Instant.parse("2026-07-25T10:00:00Z")
        );
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 980L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 980L, userId.toString())).thenReturn(7L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.eq("{\"currentRank\":8,\"rankChange\":3,\"direction\":\"DOWN\"}"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareRankChange(userId)).contains(pending);
        assertThat(state.getBaselineRank()).isEqualTo(8L);
    }

    @Test
    void topTenExitCreatesPendingForSmallRankChange() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(
                userId, season.getId(), 10L, Instant.parse("2026-07-25T10:00:00Z")
        );
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 970L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 970L, userId.toString())).thenReturn(10L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareRankChange(userId)).contains(pending);
        assertThat(state.getBaselineRank()).isEqualTo(11L);
    }

    private NotificationPreference agreed(UUID userId, NotificationType type) {
        NotificationPreference preference = NotificationPreference.defaultOf(userId, type);
        preference.applyAgreement("newAgreement");
        return preference;
    }

    private NotificationDelivery pending(UUID userId, NotificationType type, String dedupeKey) {
        NotificationDelivery delivery = NotificationDelivery.pending(userId, type, dedupeKey, "RANK_SET", Instant.parse("2026-07-25T10:00:00Z"));
        ReflectionTestUtils.setField(delivery, "id", 1L);
        return delivery;
    }

    private String rankDedupeKey(RankingSeason season, UUID userId, NotificationRankState state) {
        return "RANK_CHANGE:%d:%s:%d".formatted(
                season.getId(), userId, state.getBaselineRecordedAt().toEpochMilli());
    }

    private RankingSeason weeklySeason(Long id) {
        RankingSeason season = RankingSeason.activeWeekly(
                LocalDate.parse("2026-07-20"),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        ReflectionTestUtils.setField(season, "id", id);
        return season;
    }
}
