package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class WeeklyRankingRewardSnapshotServiceTest {

    private final WeeklyRankingRewardPolicy policy = mock(WeeklyRankingRewardPolicy.class);
    private final RankingEntryRepository entries = mock(RankingEntryRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
    private final WeeklyRankingRewardRepository rewards = mock(WeeklyRankingRewardRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final WeeklyRankingRewardSnapshotService service = new WeeklyRankingRewardSnapshotService(
            policy, entries, users, preferences, rewards, events
    );

    @Test
    void keepsRewardSlotsWhenAnEligibleRankLacksSnapshotConsent(CapturedOutput output) {
        Instant finalizedAt = Instant.parse("2026-09-21T15:00:00Z");
        RankingSeason season = mock(RankingSeason.class);
        when(season.getId()).thenReturn(10L);
        when(rewards.existsBySeasonId(10L)).thenReturn(false);
        when(policy.load(finalizedAt)).thenReturn(Optional.of(new WeeklyRankingRewardPolicy.Snapshot(
                true, new java.util.TreeMap<>(Map.of(1, 10_000L, 2, 5_000L, 3, 2_500L))
        )));
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        when(entries.findRewardCandidates(10L, Set.of(), 3)).thenReturn(List.of(
                new RankingEntryRepository.RankingRewardCandidateRow(firstId, 1L, 900L),
                new RankingEntryRepository.RankingRewardCandidateRow(secondId, 2L, 800L),
                new RankingEntryRepository.RankingRewardCandidateRow(thirdId, 3L, 700L)
        ));
        AppUser first = user(firstId);
        AppUser second = user(secondId);
        AppUser third = user(thirdId);
        when(users.findAllById(List.of(firstId, secondId, thirdId))).thenReturn(List.of(first, second, third));
        when(preferences.findByUserIdAndType(firstId, NotificationType.RANK_CHANGE)).thenReturn(Optional.of(sendablePreference()));
        when(preferences.findByUserIdAndType(secondId, NotificationType.RANK_CHANGE)).thenReturn(Optional.empty());
        when(preferences.findByUserIdAndType(thirdId, NotificationType.RANK_CHANGE)).thenReturn(Optional.of(sendablePreference()));

        service.snapshot(season, finalizedAt);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WeeklyRankingReward>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(rewards).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(WeeklyRankingReward::getRewardRank).containsExactly(1, 3);
        assertThat(captor.getValue()).extracting(WeeklyRankingReward::getExpiresAt)
                .containsOnly(finalizedAt.plusSeconds(3 * 24 * 60 * 60));
        org.mockito.Mockito.verify(entries).findRewardCandidates(10L, java.util.Set.of(), 3);
        assertThat(output).contains(
                "WEEKLY_RANKING_REWARD_UNPAID",
                "reason=CONSENT_NOT_GRANTED",
                "seasonId=10",
                "count=1",
                "pointAmount=5000"
        );
    }

    private AppUser user(UUID id) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private NotificationPreference sendablePreference() {
        NotificationPreference preference = NotificationPreference.defaultOf(
                UUID.randomUUID(), NotificationType.RANK_CHANGE);
        preference.applyAgreement("newAgreement");
        return preference;
    }
}
