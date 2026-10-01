package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryItemResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryResponse;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingReward;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingRewardRepository;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse.RewardStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RankingEntryRepository entryRepository;
    private final RankingHistoryCursorCodec cursorCodec;
    private final WeeklyRankingRewardRepository rewardRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RankingHistoryResponse getHistory(UUID userId, String cursor, Integer size) {
        int pageSize = validatePageSize(size);
        RankingHistoryCursorCodec.Cursor decodedCursor = cursorCodec.decode(cursor);
        List<RankingEntryRepository.RankingHistoryRow> fetched = entryRepository.findWeeklyHistory(
                userId,
                decodedCursor == null ? null : decodedCursor.endsAt(),
                decodedCursor == null ? null : decodedCursor.seasonId(),
                pageSize + 1
        );

        boolean hasNext = fetched.size() > pageSize;
        List<RankingEntryRepository.RankingHistoryRow> page = hasNext ? fetched.subList(0, pageSize) : fetched;
        Map<Long, WeeklyRankingReward> rewardsBySeasonId = page.isEmpty()
                ? Map.of()
                : rewardRepository
                        .findByUserIdAndSeasonIdIn(
                                userId, page.stream().map(RankingEntryRepository.RankingHistoryRow::seasonId).toList())
                        .stream()
                        .collect(Collectors.toMap(reward -> reward.getSeason().getId(), Function.identity()));
        Instant now = clock.instant();
        List<RankingHistoryItemResponse> content = page.stream()
                .map(row -> toResponse(row, rewardsBySeasonId.get(row.seasonId()), now))
                .toList();
        String nextCursor = hasNext ? encodeNextCursor(page.get(page.size() - 1)) : null;

        return new RankingHistoryResponse(
                content, nextCursor, hasNext, rewardRepository.sumClaimedPointAmountByUserId(userId));
    }

    private int validatePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_ERROR");
        }
        return size;
    }

    private RankingHistoryItemResponse toResponse(
            RankingEntryRepository.RankingHistoryRow row,
            WeeklyRankingReward reward,
            Instant now
    ) {
        return new RankingHistoryItemResponse(
                row.seasonCode(),
                row.startedAt(),
                row.endsAt(),
                row.finalRank(),
                row.finalScore(),
                reward == null ? null : reward.getPublicId(),
                rewardStatus(reward, now),
                reward == null ? null : reward.getPointAmount(),
                reward == null ? null : reward.getExpiresAt(),
                reward == null ? null : reward.getClaimedAt()
        );
    }

    private RewardStatus rewardStatus(WeeklyRankingReward reward, Instant now) {
        if (reward == null) {
            return RewardStatus.NONE;
        }
        if (reward.getStatus() == WeeklyRankingReward.Status.CLAIMED) {
            return RewardStatus.CLAIMED;
        }
        if (reward.getStatus() == WeeklyRankingReward.Status.EXPIRED || reward.isExpired(now)) {
            return RewardStatus.EXPIRED;
        }
        return RewardStatus.PENDING;
    }

    private String encodeNextCursor(RankingEntryRepository.RankingHistoryRow row) {
        return cursorCodec.encode(row.endsAt(), row.seasonId());
    }
}
