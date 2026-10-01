package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.entity.WeeklyRankingResetNotificationBatch;
import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryPersistenceService;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class NotificationBatchRepositoryIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private WeeklyRankingResetNotificationBatchRepository resetBatchRepository;

    @Autowired
    private NotificationDeliveryPersistenceService deliveryPersistenceService;

    @Autowired
    private RankingEntryRepository rankingEntryRepository;

    @Autowired
    private RankingSeasonRepository rankingSeasonRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void sendableCandidatesUsePreferenceIdCursorFiltersAndLimit() {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        UUID thirdUser = UUID.randomUUID();
        NotificationPreference first = preferenceRepository.saveAndFlush(agreed(firstUser, NotificationType.DAILY_REMINDER));
        NotificationPreference disabled = preferenceRepository.saveAndFlush(agreed(UUID.randomUUID(), NotificationType.DAILY_REMINDER));
        disabled.updateEnabled(false);
        preferenceRepository.saveAndFlush(disabled);
        preferenceRepository.saveAndFlush(agreed(UUID.randomUUID(), NotificationType.BOOSTER_UNUSED));
        NotificationPreference second = preferenceRepository.saveAndFlush(agreed(secondUser, NotificationType.DAILY_REMINDER));
        NotificationPreference third = preferenceRepository.saveAndFlush(agreed(thirdUser, NotificationType.DAILY_REMINDER));

        List<NotificationPreferenceRepository.SendableCandidate> firstPage = preferenceRepository.findSendableCandidates(
                NotificationType.DAILY_REMINDER, 0L, PageRequest.of(0, 2));
        List<NotificationPreferenceRepository.SendableCandidate> secondPage = preferenceRepository.findSendableCandidates(
                NotificationType.DAILY_REMINDER, second.getId(), PageRequest.of(0, 2));

        assertThat(firstPage).extracting(NotificationPreferenceRepository.SendableCandidate::getPreferenceId)
                .containsExactly(first.getId(), second.getId());
        assertThat(firstPage).extracting(NotificationPreferenceRepository.SendableCandidate::getUserId)
                .containsExactly(firstUser, secondUser);
        assertThat(secondPage).extracting(NotificationPreferenceRepository.SendableCandidate::getPreferenceId)
                .containsExactly(third.getId());
    }

    @Test
    void dbBatchRanksUseAllParticipantsAndUuidDescendingTieBreak() {
        RankingSeason season = rankingSeasonRepository.save(RankingSeason.activeWeekly(
                LocalDate.parse("2026-07-20"),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        ));
        AppUser highest = appUserRepository.save(AppUser.createActive("batch-rank-highest", null));
        AppUser tiedOne = appUserRepository.save(AppUser.createActive("batch-rank-tied-one", null));
        AppUser tiedTwo = appUserRepository.save(AppUser.createActive("batch-rank-tied-two", null));
        rankingEntryRepository.save(RankingEntry.createFor(season, highest, 200L, null, Instant.now()));
        rankingEntryRepository.save(RankingEntry.createFor(season, tiedOne, 100L, null, Instant.now()));
        rankingEntryRepository.saveAndFlush(RankingEntry.createFor(season, tiedTwo, 100L, null, Instant.now()));
        UUID tiedHigher = tiedOne.getId().toString().compareTo(tiedTwo.getId().toString()) > 0
                ? tiedOne.getId() : tiedTwo.getId();
        UUID tiedLower = tiedHigher.equals(tiedOne.getId()) ? tiedTwo.getId() : tiedOne.getId();

        Map<UUID, Long> ranks = rankingEntryRepository.findBatchRanks(
                        season.getId(), List.of(highest.getId(), tiedOne.getId(), tiedTwo.getId()))
                .stream()
                .collect(Collectors.toMap(
                        RankingEntryRepository.RankingBatchRankProjection::getUserId,
                        RankingEntryRepository.RankingBatchRankProjection::getRank
                ));

        assertThat(ranks).containsEntry(highest.getId(), 1L)
                .containsEntry(tiedHigher, 2L)
                .containsEntry(tiedLower, 3L);
    }

    @Test
    void weeklyResetCandidatesRequireFinalRankAndCurrentRankChangeConsent() {
        Instant endsAt = Instant.parse("2026-07-26T15:00:00Z");
        RankingSeason season = rankingSeasonRepository.save(closedWeekly(endsAt));
        AppUser participant = appUserRepository.save(AppUser.createActive("reset-consented-participant", null));
        AppUser noRank = appUserRepository.save(AppUser.createActive("reset-consented-no-rank", null));
        AppUser noConsent = appUserRepository.save(AppUser.createActive("reset-no-consent", null));
        AppUser disabled = appUserRepository.save(AppUser.createActive("reset-disabled", null));
        rankingEntryRepository.saveAndFlush(finalizedEntry(season, participant, 900L, 1L, endsAt));
        rankingEntryRepository.saveAndFlush(finalizedEntry(season, noConsent, 800L, 2L, endsAt));
        rankingEntryRepository.saveAndFlush(finalizedEntry(season, disabled, 700L, 3L, endsAt));

        NotificationPreference included = preferenceRepository.saveAndFlush(agreed(participant.getId(), NotificationType.RANK_CHANGE));
        preferenceRepository.saveAndFlush(agreed(noRank.getId(), NotificationType.RANK_CHANGE));
        preferenceRepository.saveAndFlush(NotificationPreference.defaultOf(noConsent.getId(), NotificationType.RANK_CHANGE));
        NotificationPreference disabledPreference = preferenceRepository.saveAndFlush(agreed(disabled.getId(), NotificationType.RANK_CHANGE));
        disabledPreference.updateEnabled(false);
        preferenceRepository.saveAndFlush(disabledPreference);

        List<NotificationPreferenceRepository.SendableCandidate> candidates = preferenceRepository.findWeeklyResetCandidates(
                season.getId(), 0L, PageRequest.of(0, 10));

        assertThat(candidates).extracting(NotificationPreferenceRepository.SendableCandidate::getPreferenceId)
                .containsExactly(included.getId());
        assertThat(candidates).extracting(NotificationPreferenceRepository.SendableCandidate::getUserId)
                .containsExactly(participant.getId());
    }

    @Test
    void weeklyResetDeliveryIsDeduplicatedAndConsentIsRecheckedAtInsert() {
        Instant endsAt = Instant.parse("2026-07-26T15:00:00Z");
        RankingSeason season = rankingSeasonRepository.save(closedWeekly(endsAt));
        AppUser participant = appUserRepository.save(AppUser.createActive("reset-delivery-participant", null));
        AppUser revoked = appUserRepository.save(AppUser.createActive("reset-delivery-revoked", null));
        rankingEntryRepository.saveAndFlush(finalizedEntry(season, participant, 900L, 1L, endsAt));
        rankingEntryRepository.saveAndFlush(finalizedEntry(season, revoked, 800L, 2L, endsAt));
        preferenceRepository.saveAndFlush(agreed(participant.getId(), NotificationType.RANK_CHANGE));
        NotificationPreference revokedPreference = preferenceRepository.saveAndFlush(
                agreed(revoked.getId(), NotificationType.RANK_CHANGE));
        Instant now = endsAt.plusSeconds(600);
        resetBatchRepository.insertIfAbsent(season.getId(), now);
        assertThat(resetBatchRepository.insertIfAbsent(season.getId(), now.plusSeconds(1))).isZero();
        WeeklyRankingResetNotificationBatch batch = resetBatchRepository.findBySeasonId(season.getId()).orElseThrow();

        String dedupeKey = "RANK_CHANGE:WEEKLY_RESET:%d:%s".formatted(season.getId(), participant.getId());
        int firstInsert = deliveryRepository.insertWeeklyResetPendingIfAbsent(
                UUID.randomUUID(), participant.getId(), batch.getId(), season.getId(), dedupeKey,
                "clickmoney-asfasf", now);
        int duplicateInsert = deliveryRepository.insertWeeklyResetPendingIfAbsent(
                UUID.randomUUID(), participant.getId(), batch.getId(), season.getId(), dedupeKey,
                "clickmoney-asfasf", now.plusSeconds(1));
        revokedPreference.updateEnabled(false);
        preferenceRepository.saveAndFlush(revokedPreference);
        int revokedInsert = deliveryRepository.insertWeeklyResetPendingIfAbsent(
                UUID.randomUUID(), revoked.getId(), batch.getId(), season.getId(),
                "RANK_CHANGE:WEEKLY_RESET:%d:%s".formatted(season.getId(), revoked.getId()),
                "clickmoney-asfasf", now);

        NotificationDelivery stored = deliveryRepository.findByDedupeKey(dedupeKey).orElseThrow();
        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();
        assertThat(revokedInsert).isZero();
        assertThat(stored.getType()).isEqualTo(NotificationType.RANK_CHANGE);
        assertThat(stored.getContextJson()).isEqualTo("{}");
        assertThat(stored.getTemplateSetCode()).isEqualTo("clickmoney-asfasf");
        assertThat(stored.getWeeklyResetBatchId()).isEqualTo(batch.getId());
        assertThat(stored.getAttemptCount()).isZero();
        assertThat(deliveryRepository.findAll()).filteredOn(delivery -> delivery.getWeeklyResetBatchId() != null)
                .hasSize(1);
        assertThat(deliveryRepository.findDueWeeklyResetDeliveries(now, PageRequest.of(0, 10)))
                .containsExactly(stored);

        assertThat(deliveryPersistenceService.claimWeeklyResetAttempt(stored.getId(), now)).contains(stored);

        assertThat(stored.getLastAttemptAt()).isEqualTo(now);
        assertThat(deliveryRepository.countWeeklyResetAttemptsSince(now)).isEqualTo(1L);
        assertThat(deliveryRepository.findDueWeeklyResetDeliveries(now, PageRequest.of(0, 10))).isEmpty();
        assertThat(deliveryRepository.findDueWeeklyResetDeliveries(
                now.plusSeconds(60), PageRequest.of(0, 10))).containsExactly(stored);
    }

    @Test
    void weeklyResetEligibilityReflectsCurrentConsentAndUserStatus() {
        AppUser user = appUserRepository.saveAndFlush(AppUser.createActive("reset-current-eligibility", null));
        NotificationPreference preference = preferenceRepository.saveAndFlush(
                agreed(user.getId(), NotificationType.RANK_CHANGE));

        NotificationPreferenceRepository.WeeklyResetEligibility active = preferenceRepository
                .findWeeklyResetEligibility(user.getId())
                .orElseThrow();
        assertThat(active.getEnabled()).isTrue();
        assertThat(active.getAgreementStatus()).isEqualTo("AGREED");
        assertThat(active.getUserStatus()).isEqualTo("ACTIVE");

        user.withdraw();
        appUserRepository.saveAndFlush(user);
        NotificationPreferenceRepository.WeeklyResetEligibility withdrawn = preferenceRepository
                .findWeeklyResetEligibility(user.getId())
                .orElseThrow();
        assertThat(withdrawn.getUserStatus()).isEqualTo("WITHDRAWN");

        preference.updateEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        NotificationPreferenceRepository.WeeklyResetEligibility disabled = preferenceRepository
                .findWeeklyResetEligibility(user.getId())
                .orElseThrow();
        assertThat(disabled.getEnabled()).isFalse();
    }

    private NotificationPreference agreed(UUID userId, NotificationType type) {
        NotificationPreference preference = NotificationPreference.defaultOf(userId, type);
        preference.applyAgreement("newAgreement");
        return preference;
    }

    private RankingSeason closedWeekly(Instant endsAt) {
        RankingSeason season = RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20), endsAt.minusSeconds(604800), endsAt);
        season.startFinalizing();
        season.close(endsAt.plusSeconds(600));
        return season;
    }

    private RankingEntry finalizedEntry(RankingSeason season, AppUser user, long score, long rank, Instant endsAt) {
        RankingEntry entry = RankingEntry.createFor(season, user, score, null, endsAt);
        entry.finalizeRank(rank, endsAt.plusSeconds(600));
        return entry;
    }
}
