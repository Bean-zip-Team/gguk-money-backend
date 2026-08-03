package com.ggukmoney.beanzip.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameMaskerTest {

    @Test
    void returnsNullForNullOrBlankNames() {
        assertThat(NameMasker.mask(null)).isNull();
        assertThat(NameMasker.mask("")).isNull();
        assertThat(NameMasker.mask("   ")).isNull();
    }

    @Test
    void trimsNameBeforeMasking() {
        assertThat(NameMasker.mask("  김민재  ")).isEqualTo("김*재");
    }

    @Test
    void keepsSingleCharacterName() {
        assertThat(NameMasker.mask("김")).isEqualTo("김");
    }

    @Test
    void masksSecondCharacterOfTwoCharacterName() {
        assertThat(NameMasker.mask("민재")).isEqualTo("민*");
    }

    @Test
    void keepsFirstAndLastCharactersOfLongerName() {
        assertThat(NameMasker.mask("김민재")).isEqualTo("김*재");
        assertThat(NameMasker.mask("제갈민수")).isEqualTo("제**수");
        assertThat(NameMasker.mask("Alexander")).isEqualTo("A*******r");
    }

    @Test
    void handlesSupplementaryCharactersAsSingleCharacters() {
        assertThat(NameMasker.mask("A😀B")).isEqualTo("A*B");
    }
}
