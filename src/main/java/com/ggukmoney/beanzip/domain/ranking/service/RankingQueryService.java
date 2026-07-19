package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.dto.response.CurrentRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.MyRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingItemResponse;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisMeta;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final RankingProperties properties;
    private final Clock clock;

    public CurrentRankingResponse getCurrentRanking(UUID userId, Integer requestedLimit) {
        int limit = validateLimit(requestedLimit);
        RankingSeason season = seasonService.getActiveAllTimeSeason();

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
            return new CurrentRankingResponse(List.of(), new MyRankingResponse(null, 0L, 0L), 0L);
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
        List<RankingItemResponse> items = members.stream()
                .map(member -> toItem(member.rank(), rowsByUserId.get(member.userId()), member.score(), userId))
                .toList();

        Long myRank = redisRepository.findRank(season.getId(), userId);
        if (myRank != null && entryRepository.findMyParticipant(season, userId).isEmpty()) {
            log.warn("Ranking Redis myRank member is not an active DB participant; falling back to PostgreSQL seasonId={} userId={}", season.getId(), userId);
            return fromPostgreSql(season, userId, limit);
        }
        long myScore = myRank == null ? 0L : redisRepository.findScore(season.getId(), userId);
        long firstScore = members.isEmpty() ? 0L : members.get(0).score();
        return new CurrentRankingResponse(
                items,
                new MyRankingResponse(myRank, myScore, Math.max(firstScore - myScore, 0L)),
                meta.participantCount()
        );
    }

    private CurrentRankingResponse fromPostgreSql(RankingSeason season, UUID userId, int limit) {
        List<RankingEntryRepository.RankingParticipantRow> rows = entryRepository.findTopParticipants(season, limit);
        List<RankingItemResponse> items = new java.util.ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            items.add(toItem(index + 1L, rows.get(index), userId));
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
        return new CurrentRankingResponse(
                items,
                new MyRankingResponse(myRank, myScore, Math.max(firstScore - myScore, 0L)),
                entryRepository.countParticipants(season)
        );
    }

    private RankingItemResponse toItem(long rank, RankingEntryRepository.RankingParticipantRow row, UUID me) {
        return toItem(rank, row, row.score(), me);
    }

    private RankingItemResponse toItem(long rank, RankingEntryRepository.RankingParticipantRow row, long score, UUID me) {
        return new RankingItemResponse(
                rank,
                row.userId(),
                row.nickname(),
                row.profileImageUrl(),
                score,
                row.userId().equals(me)
        );
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? properties.defaultLimit() : requestedLimit;
        if (limit < 1 || limit > properties.maxLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_ERROR");
        }
        return limit;
    }
}
