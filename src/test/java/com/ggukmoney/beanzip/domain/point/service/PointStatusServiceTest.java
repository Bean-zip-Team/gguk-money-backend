package com.ggukmoney.beanzip.domain.point.service;

import com.ggukmoney.beanzip.domain.point.dto.response.PointLedgerPageResponse;
import com.ggukmoney.beanzip.domain.point.dto.response.PointMeResponse;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.entity.PointLedger;
import com.ggukmoney.beanzip.domain.point.repository.PointLedgerRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.CashoutPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointStatusServiceTest {

    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerRepository pointLedgerRepository = mock(PointLedgerRepository.class);
    private final CashoutPolicyConfig cashoutPolicyConfig = mock(CashoutPolicyConfig.class);
    private final PointStatusService pointStatusService =
            new PointStatusService(pointAccountService, pointLedgerRepository, cashoutPolicyConfig);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubCashoutPolicyDefaults() {
        when(cashoutPolicyConfig.minimumPoint()).thenReturn(10);
        when(cashoutPolicyConfig.pointToKrwRate()).thenReturn(0.7);
    }

    @Test
    void marksCashoutEligibleAtExactlyMinimumBalance() {
        AppUser user = mock(AppUser.class);
        PointAccount account = PointAccount.createFor(user);
        account.credit(10);
        when(pointAccountService.getForUser(userId)).thenReturn(account);

        PointMeResponse response = pointStatusService.getMyPoints(userId);

        assertThat(response.balance()).isEqualTo(10L);
        assertThat(response.cashoutEligible()).isTrue();
        assertThat(response.minimumPoint()).isEqualTo(10);
        assertThat(response.estimatedKrw()).isEqualTo(7L);
    }

    @Test
    void marksCashoutIneligibleBelowMinimumBalance() {
        AppUser user = mock(AppUser.class);
        PointAccount account = PointAccount.createFor(user);
        account.credit(9);
        when(pointAccountService.getForUser(userId)).thenReturn(account);

        PointMeResponse response = pointStatusService.getMyPoints(userId);

        assertThat(response.cashoutEligible()).isFalse();
    }

    @Test
    void floorsEstimatedKrwCalculation() {
        AppUser user = mock(AppUser.class);
        PointAccount account = PointAccount.createFor(user);
        account.credit(134);
        when(pointAccountService.getForUser(userId)).thenReturn(account);

        PointMeResponse response = pointStatusService.getMyPoints(userId);

        assertThat(response.estimatedKrw()).isEqualTo(93L);
    }

    @Test
    void returnsHasMoreFalseWhenFetchedCountEqualsPageSize() {
        AppUser user = mock(AppUser.class);
        List<PointLedger> ledgers = ledgersOf(user, 3);
        when(pointLedgerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(ledgers));

        PointLedgerPageResponse response = pointStatusService.getLedger(userId, null, 3, null, null, null, null);

        assertThat(response.items()).hasSize(3);
        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void returnsHasMoreTrueAndTrimsExtraRowWhenExceedingPageSize() {
        AppUser user = mock(AppUser.class);
        List<PointLedger> ledgers = ledgersOf(user, 4);
        when(pointLedgerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(ledgers));

        PointLedgerPageResponse response = pointStatusService.getLedger(userId, null, 3, null, null, null, null);

        assertThat(response.items()).hasSize(3);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNotNull();
    }

    @Test
    void throwsOnMalformedCursor() {
        assertThatThrownBy(() -> pointStatusService.getLedger(userId, "not-a-valid-cursor!!", 20, null, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");
    }

    @Test
    void clampsPageSizeAboveMaximum() {
        when(pointLedgerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(List.of()));

        pointStatusService.getLedger(userId, null, 500, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(pointLedgerRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(101);
    }

    @Test
    void clampsPageSizeBelowMinimum() {
        when(pointLedgerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(List.of()));

        pointStatusService.getLedger(userId, null, 0, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(pointLedgerRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
    }

    private Page<PointLedger> pageOf(List<PointLedger> ledgers) {
        return new PageImpl<>(ledgers);
    }

    private List<PointLedger> ledgersOf(AppUser user, int count) {
        List<PointLedger> ledgers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ledgers.add(PointLedger.createCredit(mock(PointAccount.class), user, 1, "TAP_REWARD", UUID.randomUUID()));
        }
        return ledgers;
    }
}
