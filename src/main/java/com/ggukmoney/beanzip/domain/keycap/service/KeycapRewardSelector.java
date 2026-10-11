package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * 등급 가중치는 Figma SPEC(472:143) "등급 가중 60/28/10/2%"를 그대로 반영한다.
 * 같은 등급 안에서는 후보끼리 가중치를 균등하게 나눠 갖는다. 뽑기(BEA-329)는 보유한 키캡도 후보에 남기므로
 * 보통은 네 등급이 모두 있다. 어떤 등급에 활성 BOX 키캡이 하나도 없으면 그 몫은 별도 처리 없이
 * 나머지 등급에 재분배되어 실제 확률이 표시 확률과 달라진다 — 카탈로그를 바꿀 때 확인할 것.
 */
@Component
public class KeycapRewardSelector {

    private static final Map<Keycap.Grade, Double> GRADE_WEIGHTS = Map.of(
            Keycap.Grade.COMMON, 60.0,
            Keycap.Grade.RARE, 28.0,
            Keycap.Grade.EPIC, 10.0,
            Keycap.Grade.LEGENDARY, 2.0
    );
    private static final double DEFAULT_WEIGHT = 1.0;

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

        Map<Keycap.Grade, Long> candidateCountByGrade = candidates.stream()
                .collect(Collectors.groupingBy(Keycap::getGrade, Collectors.counting()));

        double[] weights = new double[candidates.size()];
        double totalWeight = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Keycap.Grade grade = candidates.get(i).getGrade();
            double weight = GRADE_WEIGHTS.getOrDefault(grade, DEFAULT_WEIGHT) / candidateCountByGrade.get(grade);
            weights[i] = weight;
            totalWeight += weight;
        }

        double roll = randomGenerator.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }
}
