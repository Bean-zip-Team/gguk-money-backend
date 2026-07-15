package com.ggukmoney.beanzip.domain.point.service;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.entity.PointLedger;
import com.ggukmoney.beanzip.domain.point.repository.PointLedgerRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointLedgerServiceTest {

    private final PointLedgerRepository pointLedgerRepository = mock(PointLedgerRepository.class);
    private final PointLedgerService pointLedgerService = new PointLedgerService(pointLedgerRepository);

    @Test
    void recordCreditSavesCreditEntry() {
        PointAccount account = PointAccount.createFor(mock(AppUser.class));
        AppUser user = mock(AppUser.class);
        UUID idempotencyKey = UUID.randomUUID();

        pointLedgerService.recordCredit(account, user, 1, "TAP_REWARD", idempotencyKey);

        verify(pointLedgerRepository).save(any(PointLedger.class));
    }

    @Test
    void recordDebitSavesDebitEntry() {
        PointAccount account = PointAccount.createFor(mock(AppUser.class));
        AppUser user = mock(AppUser.class);
        UUID idempotencyKey = UUID.randomUUID();

        pointLedgerService.recordDebit(account, user, 1, "CASHOUT", idempotencyKey);

        verify(pointLedgerRepository).save(any(PointLedger.class));
    }

    @Test
    void recordReversalSavesReversalEntry() {
        PointAccount account = PointAccount.createFor(mock(AppUser.class));
        AppUser user = mock(AppUser.class);
        UUID idempotencyKey = UUID.randomUUID();

        pointLedgerService.recordReversal(account, user, 1, "CASHOUT_FAILED", idempotencyKey);

        verify(pointLedgerRepository).save(any(PointLedger.class));
    }

    @Test
    void isAlreadyRecordedDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        when(pointLedgerRepository.existsByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(true);

        assertThat(pointLedgerService.isAlreadyRecorded(userId, idempotencyKey)).isTrue();
    }
}
