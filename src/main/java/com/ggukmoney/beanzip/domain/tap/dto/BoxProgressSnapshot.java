package com.ggukmoney.beanzip.domain.tap.dto;

public record BoxProgressSnapshot(
        long cumulativeValidTapCount,
        int nextBoxTarget
) {
}
