package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycapRewardSelectorTest {

    @Test
    void rejectsEmptyCandidates() {
        KeycapRewardSelector selector = new KeycapRewardSelector(new FixedRandomGenerator(0));

        assertThatThrownBy(() -> selector.select(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void splitsWeightEquallyWithinSameGrade() {
        Keycap first = keycap("FIRST", Keycap.Grade.COMMON);
        Keycap second = keycap("SECOND", Keycap.Grade.COMMON);
        KeycapRewardSelector selector = new KeycapRewardSelector(new FixedRandomGenerator(0.6));

        Keycap selected = selector.select(List.of(first, second));

        assertThat(selected).isSameAs(second);
    }

    @Test
    void favorsCommonGradeOverLegendaryPerFigmaWeighting() {
        Keycap common = keycap("COMMON_ITEM", Keycap.Grade.COMMON);
        Keycap legendary = keycap("LEGENDARY_ITEM", Keycap.Grade.LEGENDARY);
        List<Keycap> candidates = List.of(common, legendary);

        Keycap justBelowCommonShare = new KeycapRewardSelector(new FixedRandomGenerator(0.9))
                .select(candidates);
        Keycap withinLegendaryShare = new KeycapRewardSelector(new FixedRandomGenerator(0.99))
                .select(candidates);

        assertThat(justBelowCommonShare).isSameAs(common);
        assertThat(withinLegendaryShare).isSameAs(legendary);
    }

    private static Keycap keycap(String code, Keycap.Grade grade) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "code", code);
        ReflectionTestUtils.setField(keycap, "grade", grade);
        return keycap;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create test entity " + type.getSimpleName(), exception);
        }
    }

    private record FixedRandomGenerator(double value) implements RandomGenerator {
        @Override
        public long nextLong() {
            return (long) value;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }
}
