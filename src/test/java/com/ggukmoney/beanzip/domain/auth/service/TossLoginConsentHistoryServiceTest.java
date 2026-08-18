package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.entity.TossLoginConsentHistory;
import com.ggukmoney.beanzip.domain.auth.repository.TossLoginConsentHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TossLoginConsentHistoryServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-19T00:00:00Z");
    private TossLoginConsentHistoryRepository repository;
    private TossLoginConsentHistoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(TossLoginConsentHistoryRepository.class);
        service = new TossLoginConsentHistoryService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void recordsOnlyTermsThatAreNotAlreadyActive() {
        when(repository.findByUserIdOrderByOccurredAtAscIdAsc(userId)).thenReturn(List.of(
                TossLoginConsentHistory.agreed(userId, "service_terms_v1", "LOGIN", now.minusSeconds(60))
        ));

        service.recordAgreements(userId, List.of("service_terms_v1", "privacy_v2", "privacy_v2"), "LOGIN");

        ArgumentCaptor<List<TossLoginConsentHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(TossLoginConsentHistory::getTermTag, TossLoginConsentHistory::getStatus, TossLoginConsentHistory::getEventSource, TossLoginConsentHistory::getOccurredAt)
                .containsExactly(tuple("privacy_v2", TossLoginConsentHistory.Status.AGREED, "LOGIN", now));
    }

    @Test
    void recordsWithdrawalForEveryActiveTermOnly() {
        when(repository.findByUserIdOrderByOccurredAtAscIdAsc(userId)).thenReturn(List.of(
                TossLoginConsentHistory.agreed(userId, "service_terms_v1", "LOGIN", now.minusSeconds(90)),
                TossLoginConsentHistory.agreed(userId, "privacy_v1", "LOGIN", now.minusSeconds(60)),
                TossLoginConsentHistory.withdrawn(userId, "privacy_v1", "WITHDRAWAL_TERMS", now.minusSeconds(30))
        ));

        service.withdrawActiveAgreements(userId, "WITHDRAWAL_TOSS");

        ArgumentCaptor<List<TossLoginConsentHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(TossLoginConsentHistory::getTermTag, TossLoginConsentHistory::getStatus, TossLoginConsentHistory::getEventSource, TossLoginConsentHistory::getOccurredAt)
                .containsExactly(tuple("service_terms_v1", TossLoginConsentHistory.Status.WITHDRAWN, "WITHDRAWAL_TOSS", now));
    }

    @Test
    void doesNotWriteWithdrawalWhenNoActiveTermExists() {
        when(repository.findByUserIdOrderByOccurredAtAscIdAsc(userId)).thenReturn(List.of(
                TossLoginConsentHistory.withdrawn(userId, "service_terms_v1", "UNLINK", now.minusSeconds(30))
        ));

        service.withdrawActiveAgreements(userId, "UNLINK");

        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
