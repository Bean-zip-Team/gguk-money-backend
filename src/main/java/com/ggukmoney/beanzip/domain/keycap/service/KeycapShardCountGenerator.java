package com.ggukmoney.beanzip.domain.keycap.service;

import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

/**
 * 개봉당 조각 수는 지급 정책 확정안(조각 총량 유지, 개봉당 1~3개 → 1~2개 하향)을 반영한다.
 */
@Component
public class KeycapShardCountGenerator {

    private static final int MIN_SHARD_COUNT = 1;
    private static final int MAX_SHARD_COUNT = 2;

    private final RandomGenerator randomGenerator;

    public KeycapShardCountGenerator() {
        this(RandomGenerator.getDefault());
    }

    KeycapShardCountGenerator(RandomGenerator randomGenerator) {
        this.randomGenerator = randomGenerator;
    }

    public int generate() {
        return MIN_SHARD_COUNT + randomGenerator.nextInt(MAX_SHARD_COUNT - MIN_SHARD_COUNT + 1);
    }
}
