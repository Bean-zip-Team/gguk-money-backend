package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.random.RandomGenerator;

@Component
public class KeycapRewardSelector {

    private final RandomGenerator randomGenerator;

    public KeycapRewardSelector() {
        this(RandomGenerator.getDefault());
    }

    KeycapRewardSelector(RandomGenerator randomGenerator) {
        this.randomGenerator = randomGenerator;
    }

    public Keycap select(List<Keycap> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Reward candidates must not be empty.");
        }
        return candidates.get(randomGenerator.nextInt(candidates.size()));
    }
}
