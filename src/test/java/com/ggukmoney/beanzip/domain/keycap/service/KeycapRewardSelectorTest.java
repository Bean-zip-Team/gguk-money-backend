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
    void selectsCandidateAtRandomIndex() {
        Keycap first = keycap("FIRST");
        Keycap second = keycap("SECOND");
        KeycapRewardSelector selector = new KeycapRewardSelector(new FixedRandomGenerator(1));

        Keycap selected = selector.select(List.of(first, second));

        assertThat(selected).isSameAs(second);
    }

    @Test
    void rejectsEmptyCandidates() {
        KeycapRewardSelector selector = new KeycapRewardSelector(new FixedRandomGenerator(0));

        assertThatThrownBy(() -> selector.select(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Keycap keycap(String code) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "code", code);
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

    private record FixedRandomGenerator(int value) implements RandomGenerator {
        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}
