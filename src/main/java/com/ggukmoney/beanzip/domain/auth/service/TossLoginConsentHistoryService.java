package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.entity.TossLoginConsentHistory;
import com.ggukmoney.beanzip.domain.auth.repository.TossLoginConsentHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TossLoginConsentHistoryService {

    private static final int TERM_TAG_MAX_LENGTH = 255;

    private final TossLoginConsentHistoryRepository repository;
    private final Clock clock;

    @Transactional
    public void recordAgreements(UUID userId, List<String> agreedTerms, String eventSource) {
        Set<String> requestedTerms = normalizeTerms(agreedTerms);
        if (requestedTerms.isEmpty()) {
            return;
        }

        Set<String> activeTerms = activeTermsOf(userId);
        List<TossLoginConsentHistory> newAgreements = requestedTerms.stream()
                .filter(termTag -> !activeTerms.contains(termTag))
                .map(termTag -> TossLoginConsentHistory.agreed(userId, termTag, requireEventSource(eventSource), clock.instant()))
                .toList();
        if (!newAgreements.isEmpty()) {
            repository.saveAll(newAgreements);
        }
    }

    @Transactional
    public void withdrawActiveAgreements(UUID userId, String eventSource) {
        Set<String> activeTerms = activeTermsOf(userId);
        if (activeTerms.isEmpty()) {
            return;
        }

        Instant occurredAt = clock.instant();
        repository.saveAll(activeTerms.stream()
                .map(termTag -> TossLoginConsentHistory.withdrawn(userId, termTag, requireEventSource(eventSource), occurredAt))
                .toList());
    }

    private Set<String> activeTermsOf(UUID userId) {
        Set<String> activeTerms = new LinkedHashSet<>();
        for (TossLoginConsentHistory history : repository.findByUserIdOrderByOccurredAtAscIdAsc(userId)) {
            if (history.getStatus() == TossLoginConsentHistory.Status.AGREED) {
                activeTerms.add(history.getTermTag());
            } else {
                activeTerms.remove(history.getTermTag());
            }
        }
        return activeTerms;
    }

    private Set<String> normalizeTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedTerms = new LinkedHashSet<>();
        for (String term : terms) {
            if (!StringUtils.hasText(term)) {
                continue;
            }
            String termTag = term.trim();
            if (termTag.length() <= TERM_TAG_MAX_LENGTH) {
                normalizedTerms.add(termTag);
            }
        }
        return normalizedTerms;
    }

    private String requireEventSource(String eventSource) {
        if (!StringUtils.hasText(eventSource) || eventSource.trim().length() > 30) {
            throw new IllegalArgumentException("eventSource is required and must not exceed 30 characters");
        }
        return eventSource.trim();
    }
}
