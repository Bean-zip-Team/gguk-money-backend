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
    private final NotificationDeliveryPersistenceService service = new NotificationDeliveryPersistenceService(
            deliveryRepository,
            preferenceRepository,
            rankStateRepository,
            rankingSeasonService,
            rankingEntryRepository,
            new NotificationTemplateProperties("WEEKLY", "WEEKLY_SET", "RANK", "RANK_SET", "BOOSTER", "BOOSTER_SET"),
            Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void publicPersistenceMethodsAreTransactional() throws Exception {
        for (String methodName : java.util.List.of(
                "createPending",
                "prepareRankChange",
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
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, "RANK_CHANGE:1:" + userId + ":8:5");
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
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE:1:" + userId + ":8:5"),
                org.mockito.ArgumentMatchers.eq("RANK_SET"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey("RANK_CHANGE:1:" + userId + ":8:5")).thenReturn(Optional.of(pending));

        assertThat(service.prepareRankChange(userId)).contains(pending);

        assertThat(state.getBaselineRank()).isEqualTo(5L);
        verify(rankStateRepository).saveAndFlush(state);
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
        NotificationDelivery pending = pending(userId, NotificationType.RANK_CHANGE, "RANK_CHANGE:1:" + userId + ":11:10");
        when(preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE))
                .thenReturn(Optional.of(agreed(userId, NotificationType.RANK_CHANGE)));
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingEntryRepository.findMyParticipant(season, userId))
                .thenReturn(Optional.of(new RankingEntryRepository.RankingParticipantRow(userId, "me", null, 990L)));
        when(rankingEntryRepository.countParticipantsAhead(season, 990L, userId.toString())).thenReturn(9L);
        when(rankStateRepository.findByUserIdAndSeasonId(userId, season.getId())).thenReturn(Optional.of(state));
        when(deliveryRepository.insertPendingIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq("RANK_CHANGE"),
                org.mockito.ArgumentMatchers.eq("RANK_CHANGE:1:" + userId + ":11:10"), org.mockito.ArgumentMatchers.eq("RANK_SET"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(deliveryRepository.findByDedupeKey("RANK_CHANGE:1:" + userId + ":11:10")).thenReturn(Optional.of(pending));

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
