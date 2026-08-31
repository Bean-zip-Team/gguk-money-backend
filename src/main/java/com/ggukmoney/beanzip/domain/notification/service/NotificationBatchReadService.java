package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDeliveryStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationRankState;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationRankStateRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingProperties;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationBatchReadService {

    static final int PAGE_SIZE = 100;

    private final NotificationPreferenceRepository preferenceRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final BoosterGrantRepository boosterGrantRepository;
    private final UserTapDailyRepository userTapDailyRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRankStateRepository rankStateRepository;
    private final RankingSeasonService rankingSeasonService;
    private final RankingRedisRepository rankingRedisRepository;
    private final RankingEntryRepository rankingEntryRepository;
    private final RankingProperties rankingProperties;
    private final Clock clock;

    private Duration rankChangeCooldown = Duration.ofHours(6);

    @Value("${app.smart-message.rank-change.cooldown:6h}")
    void setRankChangeCooldown(Duration rankChangeCooldown) {
        this.rankChangeCooldown = rankChangeCooldown;
    }

    public List<NotificationPreferenceRepository.SendableCandidate> findCandidates(
            NotificationType type,
            long lastPreferenceId
    ) {
        return preferenceRepository.findSendableCandidates(type, lastPreferenceId, PageRequest.of(0, PAGE_SIZE));
    }

    public NotificationPageData loadPage(Collection<UUID> userIds, LocalDate date) {
        if (userIds.isEmpty()) {
            return new NotificationPageData(Map.of(), Set.of(), Map.of());
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(userIds));
        Map<UUID, String> identities = new LinkedHashMap<>();
        authIdentityRepository.findTossIdentitiesByUserIds(ids)
                .forEach(row -> identities.put(row.getUserId(), row.getProviderUserId()));
        Set<UUID> validTapUserIds = new LinkedHashSet<>(userTapDailyRepository.findUserIdsWithValidTaps(ids, date));
        Map<UUID, Long> boosterCounts = new LinkedHashMap<>();
        boosterGrantRepository.countByUserIdsAndGrantDate(ids, date)
                .forEach(row -> boosterCounts.put(row.getUserId(), row.getGrantCount()));
        return new NotificationPageData(identities, validTapUserIds, boosterCounts);
    }

    public Optional<RankPageData> loadRankPage(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Optional.empty();
        }
        Optional<RankingSeason> activeSeason = rankingSeasonService.findActiveWeeklySeason();
        if (activeSeason.isEmpty()) {
            return Optional.empty();
        }
        RankingSeason season = activeSeason.get();
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(userIds));
        Optional<Map<UUID, Long>> ranks = loadRanks(season, ids);
        if (ranks.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, NotificationRankState> states = new LinkedHashMap<>();
        rankStateRepository.findByUserIdInAndSeasonId(ids, season.getId())
                .forEach(state -> states.put(state.getUserId(), state));
        Set<UUID> cooldownUsers = rankChangeCooldown.isZero()
                ? Set.of()
                : new LinkedHashSet<>(deliveryRepository.findUserIdsWithRecentDelivery(
                        ids,
                        NotificationType.RANK_CHANGE,
                        NotificationDeliveryStatus.SENT,
                        clock.instant().minus(rankChangeCooldown)
                ));
        return Optional.of(new RankPageData(season, ranks.get(), states, cooldownUsers));
    }

    private Optional<Map<UUID, Long>> loadRanks(RankingSeason season, List<UUID> userIds) {
        try {
            if (rankingRedisRepository.findReadyMeta(
                    season.getId(),
                    rankingProperties.schemaVersion(),
                    rankingProperties.maxStaleness(),
                    clock.instant()
            ).isPresent()) {
                return Optional.of(rankingRedisRepository.findRanks(season.getId(), userIds));
            }
        } catch (RuntimeException exception) {
            log.warn("Ranking Redis batch read failed; using DB fallback. seasonId={}", season.getId(), exception);
        }
        try {
            Map<UUID, Long> ranks = new LinkedHashMap<>();
            rankingEntryRepository.findBatchRanks(season.getId(), userIds)
                    .forEach(row -> ranks.put(row.getUserId(), row.getRank()));
            return Optional.of(ranks);
        } catch (RuntimeException exception) {
            log.error("Ranking DB batch fallback failed. seasonId={}, userCount={}", season.getId(), userIds.size(), exception);
            return Optional.empty();
        }
    }

    public record NotificationPageData(
            Map<UUID, String> providerUserIds,
            Set<UUID> validTapUserIds,
            Map<UUID, Long> boosterCounts
    ) {
        public long boosterCount(UUID userId) {
            return boosterCounts.getOrDefault(userId, 0L);
        }
    }

    public record RankPageData(
            RankingSeason season,
            Map<UUID, Long> ranks,
            Map<UUID, NotificationRankState> states,
            Set<UUID> cooldownUsers
    ) {
    }
}
