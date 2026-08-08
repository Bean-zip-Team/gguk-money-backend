package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
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
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryPersistenceServiceTest {

    private final NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final NotificationRankStateRepository rankStateRepository = mock(NotificationRankStateRepository.class);
    private final RankingSeasonService rankingSeasonService = mock(RankingSeasonService.class);
    private final RankingEntryRepository rankingEntryRepository = mock(RankingEntryRepository.class);
    private final KeycapBoxAccountRepository keycapBoxAccountRepository = mock(KeycapBoxAccountRepository.class);
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig = mock(KeycapBoxPolicyConfig.class);
    private final NotificationDeliveryPersistenceService service = new NotificationDeliveryPersistenceService(
            deliveryRepository,
            preferenceRepository,
            rankStateRepository,
            rankingSeasonService,
            rankingEntryRepository,
            keycapBoxAccountRepository,
            keycapBoxPolicyConfig,
            new NotificationTemplateProperties("WEEKLY", "RANK_SET", "BOOSTER", null, null, "clickmoney-box"),
            Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void publicPersistenceMethodsAreTransactional() throws Exception {
        for (String methodName : java.util.List.of(
                "createPending",
                "prepareRankChange",
                "prepareKeycapBoxOpenAvailable",
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
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 8L, Instant.parse("2026-07-25T10:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        NotificationPreference preference = agreed(userId, NotificationType.RANK_CHANGE);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE)).thenReturn(Optional.of(preference));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(4L);
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

        assertThat(state.getBaselineRank()).isEqualTo(5L);
        verify(rankStateRepository).saveAndFlush(state);
    }

    @Test
    void rankChangeUsesBaselineRecordedAtToCreateANewEventForTheSameRankTransition() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 8L, Instant.parse("2026-07-25T09:00:00Z"));
        String dedupeKey = "RANK_CHANGE:1:" + userId + ":" + state.getBaselineRecordedAt().toEpochMilli();
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(4L);
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
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 8L, Instant.parse("2026-07-25T09:00:00Z"));
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(4L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.existsByUserIdAndTypeAndStatusAndRequestedAtAfter(
                userId,
                NotificationType.RANK_CHANGE,
                NotificationDeliveryStatus.SENT,
                Instant.parse("2026-07-25T04:00:00Z")
        )).thenReturn(true);

        assertThat(service.prepareRankChange(userId)).isEmpty();

        assertThat(state.getBaselineRank()).isEqualTo(5L);
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
        ReflectionTestUtils.setField(service, "rankChangeCooldown", java.time.Duration.ZERO);
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 8L, Instant.parse("2026-07-25T09:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(4L);
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
        ReflectionTestUtils.setField(service, "minimumRankChange", 1);
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 6L, Instant.parse("2026-07-25T10:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(4L);
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
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 8L, Instant.parse("2026-07-25T10:00:00Z"));
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 995L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 995L, userId.toString())).thenReturn(4L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("RANK_SET"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()
        )).thenReturn(0);

        assertThat(service.prepareRankChange(userId)).isEmpty();

        assertThat(state.getBaselineRank()).isEqualTo(5L);
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
    void topTenEntryCreatesPendingForSmallRankChange() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        NotificationRankState state = NotificationRankState.record(userId, season.getId(), 11L, Instant.parse("2026-07-25T10:00:00Z"));
        String dedupeKey = rankDedupeKey(season, userId, state);
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, dedupeKey);
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 990L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 990L, userId.toString())).thenReturn(9L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey), org.mockito.ArgumentMatchers.eq("RANK_SET"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareRankChange(userId)).contains(pending);
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

    @Test
    void keycapCampaignMissingStopsBeforePreferenceAndAccountLock() {
        NotificationDeliveryPersistenceService unconfiguredService = new NotificationDeliveryPersistenceService(
                deliveryRepository,
                preferenceRepository,
                rankStateRepository,
                rankingSeasonService,
                rankingEntryRepository,
                keycapBoxAccountRepository,
                keycapBoxPolicyConfig,
                new NotificationTemplateProperties(null, "RANK_SET", null, null, null, null),
                Clock.fixed(Instant.parse("2026-08-03T01:00:00Z"), ZoneOffset.UTC)
        );
        UUID userId = UUID.randomUUID();

        assertThat(unconfiguredService.prepareKeycapBoxOpenAvailable(userId, Instant.parse("2026-08-03T01:00:00Z")))
                .isEmpty();

        verify(preferenceRepository, org.mockito.Mockito.never()).findByUserIdAndType(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE);
        verify(keycapBoxAccountRepository, org.mockito.Mockito.never()).findByUserIdForUpdate(userId);
    }

    @Test
    void keycapAgreementMissingStopsBeforeAccountLock() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE))
                .thenReturn(Optional.empty());

        assertThat(service.prepareKeycapBoxOpenAvailable(userId, Instant.parse("2026-08-03T01:00:00Z")))
                .isEmpty();

        verify(keycapBoxAccountRepository, org.mockito.Mockito.never()).findByUserIdForUpdate(userId);
    }

    @Test
    void keycapPendingCreationRefreshesCycleInTheSamePreparation() {
        UUID userId = UUID.randomUUID();
        Instant cycleStartedAt = Instant.parse("2026-08-03T00:00:00Z");
        Instant now = Instant.parse("2026-08-03T01:01:00Z");
        KeycapBoxAccount account = exhaustedAccount(cycleStartedAt);
        String dedupeKey = "KEYCAP_BOX_OPEN_AVAILABLE:" + userId + ":2026-08-03T01:00:00Z";
        NotificationDelivery pending = NotificationDelivery.pending(
                userId,
                NotificationType.KEYCAP_BOX_OPEN_AVAILABLE,
                dedupeKey,
                "clickmoney-box",
                now
        );
        stubKeycapPolicy();
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE)));
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("KEYCAP_BOX_OPEN_AVAILABLE"),
                org.mockito.ArgumentMatchers.eq(dedupeKey),
                org.mockito.ArgumentMatchers.eq("clickmoney-box"),
                org.mockito.ArgumentMatchers.eq("{}"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey(dedupeKey)).thenReturn(Optional.of(pending));

        assertThat(service.prepareKeycapBoxOpenAvailable(userId, now)).contains(pending);

        assertThat(account.getOpenCycleStartedAt()).isEqualTo(Instant.parse("2026-08-03T01:00:00Z"));
        assertThat(account.getFreeOpenUsedCount()).isZero();
        assertThat(account.getAdOpenUsedCount()).isZero();
        verify(keycapBoxAccountRepository).saveAndFlush(account);
    }

    @Test
    void duplicateKeycapDeliveryDoesNotRefreshCycle() {
        UUID userId = UUID.randomUUID();
        Instant cycleStartedAt = Instant.parse("2026-08-03T00:00:00Z");
        Instant now = Instant.parse("2026-08-03T01:01:00Z");
        KeycapBoxAccount account = exhaustedAccount(cycleStartedAt);
        stubKeycapPolicy();
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE)));
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("KEYCAP_BOX_OPEN_AVAILABLE"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("clickmoney-box"),
                org.mockito.ArgumentMatchers.eq("{}"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(0);

        assertThat(service.prepareKeycapBoxOpenAvailable(userId, now)).isEmpty();

        assertThat(account.getOpenCycleStartedAt()).isEqualTo(cycleStartedAt);
        assertThat(account.getFreeOpenUsedCount()).isEqualTo(2);
        assertThat(account.getAdOpenUsedCount()).isEqualTo(2);
        verify(keycapBoxAccountRepository, org.mockito.Mockito.never()).saveAndFlush(account);
    }

    @Test
    void keycapAccountWithRemainingOpenDoesNotCreatePendingOrRefreshCycle() {
        UUID userId = UUID.randomUUID();
        Instant cycleStartedAt = Instant.parse("2026-08-03T00:00:00Z");
        KeycapBoxAccount account = exhaustedAccount(cycleStartedAt);
        ReflectionTestUtils.setField(account, "freeOpenUsedCount", 1);
        stubKeycapPolicy();
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE)));
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        assertThat(service.prepareKeycapBoxOpenAvailable(userId, Instant.parse("2026-08-03T01:01:00Z")))
                .isEmpty();

        verify(deliveryRepository, org.mockito.Mockito.never()).insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(account.getOpenCycleStartedAt()).isEqualTo(cycleStartedAt);
        assertThat(account.getFreeOpenUsedCount()).isEqualTo(1);
    }

    @Test
    void zeroKeycapOpenCapacityStopsBeforeAccountLock() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.KEYCAP_BOX_OPEN_AVAILABLE)));
        when(keycapBoxPolicyConfig.openCycleDuration()).thenReturn(java.time.Duration.ofHours(1));
        when(keycapBoxPolicyConfig.freeOpenLimit()).thenReturn(0);
        when(keycapBoxPolicyConfig.adOpenLimit()).thenReturn(0);

        assertThat(service.prepareKeycapBoxOpenAvailable(userId, Instant.parse("2026-08-03T01:01:00Z")))
                .isEmpty();

        verify(keycapBoxAccountRepository, org.mockito.Mockito.never()).findByUserIdForUpdate(userId);
    }

    private NotificationPreference agreed(UUID userId, NotificationType type) {
        NotificationPreference preference = NotificationPreference.defaultOf(userId, type);
        preference.applyAgreement("newAgreement");
        return preference;
    }

    private void stubKeycapPolicy() {
        when(keycapBoxPolicyConfig.openCycleDuration()).thenReturn(java.time.Duration.ofHours(1));
        when(keycapBoxPolicyConfig.freeOpenLimit()).thenReturn(2);
        when(keycapBoxPolicyConfig.adOpenLimit()).thenReturn(2);
    }

    private KeycapBoxAccount exhaustedAccount(Instant cycleStartedAt) {
        KeycapBoxAccount account = KeycapBoxAccount.createFor(null, cycleStartedAt);
        ReflectionTestUtils.setField(account, "boxBalance", 1);
        ReflectionTestUtils.setField(account, "freeOpenUsedCount", 2);
        ReflectionTestUtils.setField(account, "adOpenUsedCount", 2);
        return account;
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
