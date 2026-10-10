package com.ggukmoney.beanzip.domain.keycap.passive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class KeycapPassivePolicyTest {
    private final KeycapPassivePolicy policy = KeycapPassivePolicy.defaults();

    @ParameterizedTest
    @CsvSource({
        "main,50,0.10,2,0,1,0,1", "cheer,50,0,1,0,1,0,1",
        "dolphin,50,0,1,0.06,2,0,1", "lucky,50,0,1,0.015,5,0,1",
        "redlego,50,0,1,0,1,0.10,2", "yellowlego,50,0,1,0,1,0.025,5",
        "biscuit,45,0.18,2,0,1,0,1", "jellyfoot,45,0,1,0.10,2,0,1",
        "pinkjelly,45,0,1,0,1,0,1", "earth,20,0.15,2,0.09,2,0,1",
        "moon,20,0,1,0,1,0.15,2", "space,20,0.22,2,0,1,0.22,2",
        "pudding,5,0.20,2,0.13,2,0.20,2", "radio,5,0.28,2,0,1,0.28,2"
    })
    void exposesAssignedAxesAtTheirEffectCap(String code, int cap, double shardChance, int shardMultiplier,
            double clickChance, int clickMultiplier, double pointChance, int pointMultiplier) {
        var effects = policy.effects(code, cap);
        assertThat(effects.shard().probability()).isCloseTo(shardChance, within(1e-12));
        assertThat(effects.shard().multiplier()).isEqualTo(shardMultiplier);
        assertThat(effects.click().probability()).isCloseTo(clickChance, within(1e-12));
        assertThat(effects.click().multiplier()).isEqualTo(clickMultiplier);
        assertThat(effects.point().probability()).isCloseTo(pointChance, within(1e-12));
        assertThat(effects.point().multiplier()).isEqualTo(pointMultiplier);
        assertThat(effects.capLevel()).isEqualTo(cap);
        assertThat(effects.capReached()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "main,1,0.04", "main,5,0.04489795918367347", "main,50,0.10", "main,63,0.10",
        "biscuit,1,0.08", "biscuit,23,0.13", "biscuit,45,0.18", "biscuit,99,0.18",
        "earth,1,0.072", "earth,20,0.15", "earth,200,0.15",
        "pudding,1,0.10285714285714286", "pudding,3,0.15142857142857144",
        "pudding,5,0.20", "pudding,2147483647,0.20"
    })
    void interpolatesProbabilityWithoutCappingTheOwnedLevel(String code, int level, double want) {
        assertThat(policy.effects(code, level).shard().probability()).isCloseTo(want, within(1e-12));
    }

    @Test
    void overlappingAxesMultiplyRatherThanAdd() {
        var earth = policy.effects("earth", 20);
        var moon = policy.effects("moon", 20);
        var pudding = policy.effects("pudding", 5);
        assertThat(earth.shard().expectedMultiplier() * earth.click().expectedMultiplier())
                .isCloseTo(1.2535, within(1e-12));
        assertThat(moon.click().expectedMultiplier() * moon.point().expectedMultiplier())
                .isCloseTo(1.15, within(1e-12));
        assertThat(pudding.click().expectedMultiplier() * pudding.shard().expectedMultiplier())
                .isCloseTo(1.356, within(1e-12));
    }

    @Test
    void frequentAndRareCommonVariantsKeepEqualExpectedRewards() {
        assertThat(policy.effects("main", 50).shard().expectedMultiplier()).isCloseTo(1.10, within(1e-12));
        assertThat(policy.effects("cheer", 50).shard().expectedMultiplier()).isEqualTo(1.0);
        assertThat(policy.effects("dolphin", 50).click().expectedMultiplier()).isCloseTo(1.06, within(1e-12));
        assertThat(policy.effects("lucky", 50).click().expectedMultiplier()).isCloseTo(1.06, within(1e-12));
    }

    @Test
    void noEquipmentAndUnassignedEventDoNotUseTheDisplayFallback() {
        for (String code : new String[]{null, "unassigned-event"}) {
            var effects = policy.effects(code, 1);
            assertThat(effects.shard().probability()).isZero();
            assertThat(effects.click().probability()).isZero();
            assertThat(effects.point().probability()).isZero();
            assertThat(effects.capReached()).isFalse();
        }
    }

    @Test
    void reportsCapOnlyOnceGrowthStops() {
        assertThat(policy.effects("radio", 4).capReached()).isFalse();
        assertThat(policy.effects("radio", 5).capReached()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"cheer,1,60,false", "cheer,6,120,false", "cheer,11,180,true",
        "pinkjelly,1,100,false", "pinkjelly,11,300,true", "moon,1,160,false",
        "moon,11,480,true", "radio,1,300,false", "radio,5,540,false",
        "radio,11,900,true", "radio,2147483647,900,true", "main,50,0,false"})
    void exposesAutomaticClicksWithAnIndependentCap(String code, int level, int clicks, boolean capped) throws Exception {
        var effects = policy.effects(code, level);
        assertThat(effects.getClass().getMethod("autoClicksPerDay").invoke(effects)).isEqualTo(clicks);
        assertThat(effects.getClass().getMethod("autoClickCapReached").invoke(effects)).isEqualTo(capped);
    }

    @Test
    void rejectsInvalidOwnedLevels() {
        assertThatThrownBy(() -> policy.effects("radio", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
