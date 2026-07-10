package com.ggukmoney.beanzip.domain.tap.service;

import java.util.UUID;

/**
 * Pure service-layer input, produced from {@code TapBatchSubmitRequest} by {@code TapMapper}.
 * Deliberately framework/HTTP-agnostic.
 */
public record TapSubmitCommand(UUID userId, UUID tapSessionId, Long sequence, Integer submittedCount) {
}
