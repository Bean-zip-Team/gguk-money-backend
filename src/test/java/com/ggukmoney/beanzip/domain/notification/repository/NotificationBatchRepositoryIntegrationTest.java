package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
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

    private NotificationPreference agreed(UUID userId, NotificationType type) {
        NotificationPreference preference = NotificationPreference.defaultOf(userId, type);
        preference.applyAgreement("newAgreement");
        return preference;
    }
}
