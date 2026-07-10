package com.ggukmoney.beanzip.domain.tap.service;

/**
 * Pure service-layer result, converted to {@code TapBatchSubmitResponse} by {@code TapMapper}.
 * Deliberately excludes the next point target — that value must never reach the client.
 */
public record TapSubmitOutcome(int acceptedCount, int pointsAwarded, long balance) {
}
