package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreSyncRequestedEvent;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.tap.service.TapBatchService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class RankingScoreSyncTransactionIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TapBatchService tapBatchService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private KeycapBoxAccountRepository keycapBoxAccountRepository;

    @Autowired
    private UserTapProgressRepository userTapProgressRepository;

    @Autowired
    private UserTapDailyRepository userTapDailyRepository;

    @Autowired
    private TapBatchRepository tapBatchRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private ZoneId businessZoneId;

    @MockitoBean
    private RankingProjectionService rankingProjectionService;

    @BeforeEach
    void resetProjectionMock() {
        reset(rankingProjectionService);
    }

    @Test
    void afterCommitListenerRunsOnlyAfterRealCommit() {
        UUID userId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T15:00:00Z");

        transactionTemplate().executeWithoutResult(status ->
                eventPublisher.publishEvent(new RankingScoreSyncRequestedEvent(userId, occurredAt))
        );

        verify(rankingProjectionService).syncLatestWeeklyScore(userId, occurredAt);
    }

    @Test
    void afterCommitListenerDoesNotRunWhenTransactionRollsBack() {
        UUID userId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T15:00:00Z");

        assertThatThrownBy(() -> transactionTemplate().executeWithoutResult(status -> {
            eventPublisher.publishEvent(new RankingScoreSyncRequestedEvent(userId, occurredAt));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        verify(rankingProjectionService, never()).syncLatestWeeklyScore(userId, occurredAt);
    }

    @Test
    void rankingProjectionFailureDoesNotRollbackCommittedTapRewardsAndProgress() {
        AppUser user = registerUserWithProgressTargets(1, 1);
        UUID sessionId = UUID.randomUUID();
        doThrow(new IllegalStateException("projection failed"))
                .when(rankingProjectionService)
                .syncLatestWeeklyScore(eq(user.getId()), any(Instant.class));

        TapBatchSubmitResponse response = tapBatchService.submitBatch(
                user.getId(),
                new TapBatchSubmitRequest(sessionId, 1L, 10)
        );

        assertThat(response.acceptedCount()).isEqualTo(10);
        assertThat(userTapProgressRepository.findByUserId(user.getId()).orElseThrow().getCumulativeValidTapCount())
                .isEqualTo(10L);
        LocalDate tapDate = LocalDate.ofInstant(clock.instant(), businessZoneId);
        assertThat(userTapDailyRepository.findByUserIdAndTapDate(user.getId(), tapDate).orElseThrow().getValidTapCount())
                .isEqualTo(10);
        assertThat(pointAccountRepository.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(1L);
        assertThat(keycapBoxAccountRepository.findByUserId(user.getId()).orElseThrow().getBoxBalance()).isEqualTo(1);
        assertThat(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(user.getId(), sessionId, 1L)).isPresent();
        verify(rankingProjectionService).syncLatestWeeklyScore(eq(user.getId()), any(Instant.class));
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private AppUser registerUserWithProgressTargets(int pointTarget, int boxTarget) {
        AppUser user = appUserRepository.save(AppUser.createActive("ranking-tx-" + UUID.randomUUID(), null));
        pointAccountRepository.save(PointAccount.createFor(user));
        keycapBoxAccountRepository.save(KeycapBoxAccount.createFor(user));
        userTapProgressRepository.save(UserTapProgress.createFor(user, pointTarget, boxTarget));
        return user;
    }
}
