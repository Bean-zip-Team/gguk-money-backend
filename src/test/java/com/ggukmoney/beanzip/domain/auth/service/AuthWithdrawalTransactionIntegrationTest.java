package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.client.TossAuthClient;
import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.entity.TossLoginConsentHistory;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.auth.repository.TossLoginConsentHistoryRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingEligibilityChangeService;
import com.ggukmoney.beanzip.domain.user.dto.request.UserWithdrawalRequest;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.service.RedisService;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class AuthWithdrawalTransactionIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuthIdentityRepository authIdentityRepository;

    @Autowired
    private TossLoginConsentHistoryService tossLoginConsentHistoryService;

    @Autowired
    private TossLoginConsentHistoryRepository tossLoginConsentHistoryRepository;

    @MockitoBean
    private TossAuthClient tossAuthClient;

    @MockitoBean
    private RedisService redisService;

    @MockitoBean
    private RankingEligibilityChangeService rankingEligibilityChangeService;

    @BeforeEach
    void resetTossClient() {
        reset(tossAuthClient, redisService, rankingEligibilityChangeService);
        lenient().when(redisService.executeScript(
                any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn(0L);
    }

    @Test
    void callsAllTossApisOutsideDatabaseTransaction() {
        AppUser user = appUserRepository.save(AppUser.createActive("withdraw-tx-" + UUID.randomUUID(), null));
        authIdentityRepository.save(AuthIdentity.toss(user, "toss-user-key"));
        tossLoginConsentHistoryService.recordAgreements(user.getId(), List.of("service_terms_v1"), "LOGIN");
        AtomicBoolean writeTransactionObserved = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            writeTransactionObserved.set(true);
            return null;
        }).when(rankingEligibilityChangeService).publishAllTimeEligibilityChanged(any(AppUser.class));
        when(redisService.executeScript(
                any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return 0L;
        });
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new TossAuthClient.TossToken("toss-access");
        });
        when(tossAuthClient.loginMe("toss-access")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new TossAuthClient.TossLoginMe("toss-user-key", null, null, List.of());
        });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(tossAuthClient).removeByUserKey("toss-access", "toss-user-key");

        authService.withdrawCurrentUser(
                user.getId(),
                "access-jti",
                Instant.now().plusSeconds(900),
                new UserWithdrawalRequest("code", "DEFAULT")
        );

        assertThat(appUserRepository.findById(user.getId()).orElseThrow().isWithdrawn()).isTrue();
        assertThat(tossLoginConsentHistoryRepository.findByUserIdOrderByOccurredAtAscIdAsc(user.getId()))
                .extracting(TossLoginConsentHistory::getStatus)
                .containsExactly(TossLoginConsentHistory.Status.AGREED, TossLoginConsentHistory.Status.WITHDRAWN);
        assertThat(writeTransactionObserved).isTrue();
    }
}
