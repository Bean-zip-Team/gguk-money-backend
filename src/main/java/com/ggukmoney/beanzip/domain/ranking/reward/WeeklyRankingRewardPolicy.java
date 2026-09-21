package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.global.config.AppConfigBatchLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyRankingRewardPolicy {

    public static final String KEY = "ranking.weeklyReward.policy";

    private final AppConfigBatchLoader loader;
    private final ObjectMapper objectMapper;

    public Optional<Snapshot> load(Instant now) {
        try {
            String raw = loader.load(Set.of(KEY), now).get(KEY);
            if (raw == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(raw);
            JsonNode rewardNode = root.path("rewards");
            if (!root.isObject() || !root.path("enabled").isBoolean() || !rewardNode.isObject()) {
                throw new IllegalArgumentException("invalid weekly ranking reward policy shape");
            }

            TreeMap<Integer, Long> rewards = new TreeMap<>();
            rewardNode.properties().forEach(entry -> {
                int rank = Integer.parseInt(entry.getKey());
                JsonNode amountNode = entry.getValue();
                if (!amountNode.isIntegralNumber() || !amountNode.canConvertToLong()) {
                    throw new IllegalArgumentException("weekly ranking reward amount must fit a long integer");
                }
                long amount = amountNode.longValue();
                if (rank <= 0 || amount <= 0) {
                    throw new IllegalArgumentException("weekly ranking reward ranks and amounts must be positive");
                }
                rewards.put(rank, amount);
            });

            if (rewards.isEmpty() || rewards.firstKey() != 1 || rewards.lastKey() != rewards.size()) {
                throw new IllegalArgumentException("weekly ranking reward ranks must be contiguous from 1");
            }
            return Optional.of(new Snapshot(root.path("enabled").booleanValue(), rewards));
        } catch (Exception exception) {
            log.error("Weekly ranking reward policy unavailable", exception);
            return Optional.empty();
        }
    }

    public record Snapshot(boolean enabled, NavigableMap<Integer, Long> rewards) {
        public Snapshot {
            rewards = Collections.unmodifiableNavigableMap(new TreeMap<>(rewards));
        }

        public int maxRewardRank() {
            return rewards.lastKey();
        }

        public long pointAmount(int rank) {
            Long amount = rewards.get(rank);
            if (amount == null) {
                throw new IllegalArgumentException("no weekly reward configured for rank " + rank);
            }
            return amount;
        }
    }
}
