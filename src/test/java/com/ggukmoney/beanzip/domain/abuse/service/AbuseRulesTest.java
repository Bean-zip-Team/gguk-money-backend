package com.ggukmoney.beanzip.domain.abuse.service;

import com.ggukmoney.beanzip.domain.abuse.service.AbuseRules.Rule;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository.AbuseStatsProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseRulesTest {

    @Test
    void humanHourMatchesNoRule() {
        assertThat(AbuseRules.evaluate(stats(0, 400, 0.4, 6.0, null))).isEmpty();
    }

    @Test
    void threeFastMinutesInAnHourMakeASuspendCandidate() {
        // 빠른 1분 2번은 보류 계정에서도 나왔다. 3번부터 정지 후보다.
        assertThat(AbuseRules.evaluate(stats(2, 2_000, 0.4, 5.0, null)))
                .containsExactly(Rule.FAST);
        assertThat(AbuseRules.evaluate(stats(3, 2_000, 0.4, 5.0, null)))
                .containsExactly(Rule.SUSTAINED_FAST, Rule.FAST);
    }

    @Test
    void regularBurstBecomesMechanicalOnlyAfterTwentyMinutes() {
        assertThat(AbuseRules.evaluate(stats(0, 300, 0.049, 20.0, 20.0)))
                .containsExactly(Rule.REGULAR);
        assertThat(AbuseRules.evaluate(stats(0, 300, 0.049, 21.0, 21.0)))
                .containsExactly(Rule.MECHANICAL, Rule.REGULAR);
    }

    @Test
    void alertOnlyRulesUseHumanControlMaxima() {
        assertThat(AbuseRules.evaluate(stats(0, 702, 0.2, 40.0, null))).isEmpty();
        assertThat(AbuseRules.evaluate(stats(0, 703, 0.2, 41.0, null)))
                .containsExactly(Rule.FAST, Rule.LONG);
        assertThat(List.of(Rule.FAST, Rule.REGULAR, Rule.LONG)).noneMatch(Rule::suspendCandidate);
    }

    @Test
    void internalAccountIsNeverASuspendCandidate() {
        List<Rule> rules = AbuseRules.evaluate(stats(5, 2_300, 0.01, 50.0, 50.0));

        assertThat(new AbuseDetectionService.Finding(null, false, rules).suspendCandidate()).isTrue();
        assertThat(new AbuseDetectionService.Finding(null, true, rules).suspendCandidate()).isFalse();
    }

    private static AbuseStatsProjection stats(long fastMinutes, long maxMinuteTaps, Double minCv,
                                              double longestBurstMinutes, Double longestRegularBurstMinutes) {
        return new AbuseStatsProjection() {
            public UUID getUserId() { return UUID.randomUUID(); }
            public Long getPhysicalTaps() { return maxMinuteTaps; }
            public Long getMaxMinuteTaps() { return maxMinuteTaps; }
            public Long getFastMinutes() { return fastMinutes; }
            public Double getMinCv() { return minCv; }
            public Double getLongestBurstMinutes() { return longestBurstMinutes; }
            public Double getLongestRegularBurstMinutes() { return longestRegularBurstMinutes; }
        };
    }
}
