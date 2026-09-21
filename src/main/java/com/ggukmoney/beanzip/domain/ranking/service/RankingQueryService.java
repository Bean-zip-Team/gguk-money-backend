package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.dto.response.CurrentRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.MyRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingItemResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingSeasonResponse;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisMeta;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingRewardPreviewService;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingRewardPreviewService.Preview;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingRewardPreviewService.ProvisionalReward;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.global.util.NameMasker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

    private static final Logger log = LoggerFactory.getLogger(RankingQueryService.class);

    private final RankingSeasonService seasonService;
    private final RankingEntryRepository entryRepository;
    private final RankingRedisRepository redisRepository;
    private final WeeklyRankingRewardPreviewService rewardPreviewService;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final RankingProperties properties;
    private final Clock clock;
    private final ZoneId businessZoneId;

    public CurrentRankingResponse getCurrentRanking(UUID userId, Integer requestedLimit) {
        int limit = validateLimit(requestedLimit);
        RankingSeason season = seasonService.getActiveWeeklySeason();

        try {
            Optional<RankingRedisMeta> meta = redisRepository.findReadyMeta(
                    season.getId(),
                    properties.schemaVersion(),
                    properties.maxStaleness(),
                    clock.instant()
            );
            if (meta.isPresent()) {
                return fromRedisOrFallback(season, userId, limit, meta.get());
            }
        } catch (RuntimeException exception) {
            log.warn("Ranking Redis read failed. Falling back to PostgreSQL seasonId={}", season.getId(), exception);
        }
        return fromPostgreSql(season, userId, limit);
    }

    private CurrentRankingResponse fromRedisOrFallback(
            RankingSeason season,
            UUID userId,
            int limit,
            RankingRedisMeta meta
    ) {
        if (meta.participantCount() == 0 && redisRepository.isGlobalZSetMissing(season.getId())) {
            return fromPostgreSql(season, userId, limit);
        }
        if (meta.participantCount() > 0 && redisRepository.isGlobalZSetMissing(season.getId())) {
            return fromPostgreSql(season, userId, limit);
        }
        if (redisRepository.findParticipantCount(season.getId()) != meta.participantCount()) {
            return fromPostgreSql(season, userId, limit);
        }

        List<RankingRedisRepository.RankingRedisMember> members = redisRepository.findTopMembers(season.getId(), limit);
        List<UUID> userIds = members.stream().map(RankingRedisRepository.RankingRedisMember::userId).toList();
        Map<UUID, RankingEntryRepository.RankingParticipantRow> rowsByUserId =
                entryRepository.findParticipantsByUserIds(season, userIds).stream()
                        .collect(Collectors.toMap(RankingEntryRepository.RankingParticipantRow::userId, Function.identity()));
        if (rowsByUserId.size() != userIds.size()) {
            log.warn("Ranking Redis top members include inactive or missing users; falling back to PostgreSQL seasonId={}", season.getId());
            return fromPostgreSql(season, userId, limit);
        }
        if (members.stream().anyMatch(member -> rowsByUserId.get(member.userId()).score() != member.score())) {
            log.warn("Ranking Redis scores differ from PostgreSQL; falling back to PostgreSQL seasonId={}", season.getId());
            return fromPostgreSql(season, userId, limit);
        }
        List<RankedParticipant> rankedParticipants = members.stream()
                .map(member -> new RankedParticipant(member.rank(), rowsByUserId.get(member.userId()), member.score()))
                .toList();

        Long myRank = redisRepository.findRank(season.getId(), userId);
        long myScore = myRank == null ? 0L : redisRepository.findScore(season.getId(), userId);
        Preview rewardPreview = rewardPreviewService.preview(season, userId, myScore, clock.instant());
        boolean rewardPreviewEnabled = !rewardPreview.tiers().isEmpty();
        Optional<RankingEntryRepository.RankingParticipantRow> myParticipant = Optional.empty();
        if (myRank != null || rewardPreviewEnabled) {
            myParticipant = entryRepository.findMyParticipant(season, userId);
        }
        if ((myRank != null && myParticipant.isEmpty())
                || (rewardPreviewEnabled && myRank == null && myParticipant.isPresent())) {
            log.warn("Ranking Redis myRank member is not an active DB participant; falling back to PostgreSQL seasonId={} userId={}", season.getId(), userId);
            return fromPostgreSql(season, userId, limit);
        }
        if (rewardPreviewEnabled && myRank != null) {
            RankingEntryRepository.RankingParticipantRow myRow = myParticipant.orElseThrow();
            long databaseRank = entryRepository.countParticipantsAhead(season, myRow.score(), userId.toString()) + 1L;
            if (myRow.score() != myScore || databaseRank != myRank) {
                log.warn("Ranking Redis my score or rank differs from PostgreSQL; falling back to PostgreSQL seasonId={} userId={}",
                        season.getId(), userId);
                return fromPostgreSql(season, userId, limit);
            }
        }
        long firstScore = members.isEmpty() ? 0L : members.get(0).score();
        if (rewardPreviewEnabled && !isRewardPreviewConsistentWithRedis(season, members, rewardPreview)) {
            log.warn("Ranking Redis reward candidates differ from PostgreSQL; falling back to PostgreSQL seasonId={}",
                    season.getId());
            return fromPostgreSql(season, userId, limit);
        }
        return assembleResponse(
                season, rankedParticipants, userId, myRank, myScore, firstScore, meta.participantCount(), rewardPreview);
    }

    private CurrentRankingResponse fromPostgreSql(RankingSeason season, UUID userId, int limit) {
        List<RankingEntryRepository.RankingParticipantRow> rows = entryRepository.findTopParticipants(season, limit);
        List<RankedParticipant> rankedParticipants = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            RankingEntryRepository.RankingParticipantRow row = rows.get(index);
            rankedParticipants.add(new RankedParticipant(index + 1L, row, row.score()));
        }

        Optional<RankingEntryRepository.RankingParticipantRow> myParticipant =
                entryRepository.findMyParticipant(season, userId);
        Long myRank = myParticipant
                .map(row -> entryRepository.countParticipantsAhead(season, row.score(), row.userId().toString()) + 1)
                .orElse(null);
        long myScore = myParticipant.map(RankingEntryRepository.RankingParticipantRow::score).orElse(0L);
        long firstScore = rows.stream()
                .map(RankingEntryRepository.RankingParticipantRow::score)
                .max(Comparator.naturalOrder())
                .orElse(0L);
        Preview rewardPreview = rewardPreviewService.preview(season, userId, myScore, clock.instant());
        return assembleResponse(
                season,
                rankedParticipants,
                userId,
                myRank,
                myScore,
                firstScore,
                entryRepository.countParticipants(season),
                rewardPreview
        );
    }

    private CurrentRankingResponse assembleResponse(
            RankingSeason season,
            List<RankedParticipant> rankedParticipants,
            UUID me,
            Long myRank,
            long myScore,
            long firstScore,
            long totalParticipantCount,
            Preview rewardPreview
    ) {
        Optional<RankingSeason> previousSeason =
                Optional.ofNullable(seasonService.findPreviousClosedWeeklySeason(season)).orElse(Optional.empty());
        Map<UUID, Long> previousRanks = previousRanks(previousSeason, rankedParticipants, me);
        List<RankingItemResponse> items = rankedParticipants.stream()
                .map(participant -> toItem(
                        participant,
                        previousRanks.get(participant.row().userId()),
                        me,
                        rewardPreview.rewardsByUser().get(participant.row().userId())))
                .toList();
        Long previousMyRank = previousRanks.get(me);
        ProvisionalReward myReward = rewardPreview.rewardsByUser().get(me);
        return new CurrentRankingResponse(
                seasonResponse(season),
                items,
                new MyRankingResponse(
                        myRank,
                        previousMyRank,
                        rankChange(previousMyRank, myRank),
                        myScore,
                        Math.max(firstScore - myScore, 0L),
                        myReward == null ? null : myReward.rewardRank(),
                        myReward == null ? null : myReward.pointAmount(),
                        rewardPreview.scoreGapToReward(),
                        notificationPreferenceRepository.findByUserIdAndType(me, NotificationType.RANK_CHANGE)
                                .map(NotificationPreference::isSendable)
                                .orElse(false)
                ),
                totalParticipantCount,
                rewardPreview.tiers()
        );
    }

    private boolean isRewardPreviewConsistentWithRedis(
            RankingSeason season,
            List<RankingRedisRepository.RankingRedisMember> displayedMembers,
            Preview rewardPreview
    ) {
        for (RankingEntryRepository.RankingCurrentRewardCandidateRow candidate : rewardPreview.candidateRows()) {
            Long cachedRank = redisRepository.findRank(season.getId(), candidate.userId());
            if (!Objects.equals(cachedRank, candidate.currentRank())
                    || redisRepository.findScore(season.getId(), candidate.userId()) != candidate.score()) {
                return false;
            }
        }

        if (displayedMembers.isEmpty()) {
            return true;
        }
        List<UUID> displayedUserIds = displayedMembers.stream()
                .map(RankingRedisRepository.RankingRedisMember::userId)
                .toList();
        Map<UUID, Long> databaseRanks = entryRepository.findBatchRanks(season.getId(), displayedUserIds).stream()
                .collect(Collectors.toMap(
                        RankingEntryRepository.RankingBatchRankProjection::getUserId,
                        RankingEntryRepository.RankingBatchRankProjection::getRank
                ));
        return displayedMembers.stream().allMatch(member ->
                Objects.equals(databaseRanks.get(member.userId()), member.rank()));
    }

    private Map<UUID, Long> previousRanks(
            Optional<RankingSeason> previousSeason,
            List<RankedParticipant> rankedParticipants,
            UUID me
    ) {
        if (previousSeason.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<UUID> userIds = rankedParticipants.stream()
                .map(participant -> participant.row().userId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        userIds.add(me);
        return entryRepository.findFinalRanksByUserIds(previousSeason.get(), new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(
                        RankingEntryRepository.RankingFinalRankRow::userId,
                        RankingEntryRepository.RankingFinalRankRow::finalRank
                ));
    }

    private RankingSeasonResponse seasonResponse(RankingSeason season) {
        return new RankingSeasonResponse(
                season.getStartsAt(),
                season.getEndsAt(),
                season.getEndsAt(),
                properties.weeklyResetDayOfWeek().name(),
                properties.weeklyResetTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                businessZoneId.getId()
        );
    }

    private RankingItemResponse toItem(
            RankedParticipant participant,
            Long previousRank,
            UUID me,
            ProvisionalReward reward
    ) {
        long rank = participant.rank();
        RankingEntryRepository.RankingParticipantRow row = participant.row();
        return new RankingItemResponse(
                rank,
                previousRank,
                rankChange(previousRank, rank),
                row.userId(),
                NameMasker.mask(row.nickname()),
                row.profileImageUrl(),
                participant.score(),
                row.userId().equals(me),
                reward == null ? null : reward.rewardRank(),
                reward == null ? null : reward.pointAmount()
        );
    }

    private Long rankChange(Long previousRank, Long currentRank) {
        if (previousRank == null || currentRank == null) {
            return null;
        }
        return previousRank - currentRank;
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? properties.defaultLimit() : requestedLimit;
        if (limit < 1 || limit > properties.maxLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_ERROR");
        }
        return limit;
    }

    private record RankedParticipant(
            long rank,
            RankingEntryRepository.RankingParticipantRow row,
            long score
    ) {
    }
}
