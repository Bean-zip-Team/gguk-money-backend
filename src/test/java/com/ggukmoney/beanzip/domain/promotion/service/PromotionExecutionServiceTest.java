package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.promotion.config.TossPromotionCodeRegistry;
import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.client.toss.TossPromotionClient;
import com.ggukmoney.beanzip.global.client.toss.TossPromotionClient.PromotionExecutionOutcome;
import com.ggukmoney.beanzip.global.client.toss.TossPromotionClient.PromotionResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BEA-326. 거절 응답(모르는 코드)을 받아도 key 가 있으면 토스에 결과를 먼저 묻는다.
 */
class PromotionExecutionServiceTest {

    private static final long GRANT_ID = 14L;
    private static final String TOSS_USER_KEY = "toss-user";
    private static final String TOSS_CODE = "TOSS_TAP";
    private static final String KEY = "key-14";

    private final TossPromotionClient tossPromotionClient = mock(TossPromotionClient.class);
    private final TossPromotionCodeRegistry registry = mock(TossPromotionCodeRegistry.class);
    private final PromotionGrantStateService stateService = mock(PromotionGrantStateService.class);
    private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
    private final Instant now = Instant.parse("2026-10-28T03:00:00Z");
    private final PromotionExecutionService service = new PromotionExecutionService(
            tossPromotionClient, registry, stateService, new PromotionErrorPolicy(), authIdentityRepository,
            Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        PromotionGrant grant = PromotionGrant.createPending(user, "TAP_THOUSAND_COMPLETE", 5L, null, now);
        ReflectionTestUtils.setField(grant, "id", GRANT_ID);
        grant.assignTossKey(KEY, TOSS_CODE, now);
        when(stateService.find(GRANT_ID)).thenReturn(Optional.of(grant));
        when(registry.tossCodeOf("TAP_THOUSAND_COMPLETE")).thenReturn(Optional.of(TOSS_CODE));

        AuthIdentity identity = mock(AuthIdentity.class);
        when(identity.getProviderUserId()).thenReturn(TOSS_USER_KEY);
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS))
                .thenReturn(Optional.of(identity));
    }

    @Test
    void rejectedExecuteConvergesWhenTossReportsTheKeyAsGranted() {
        rejectWith("4000");
        when(tossPromotionClient.getExecutionResult(TOSS_USER_KEY, TOSS_CODE, KEY)).thenReturn(PromotionResultStatus.SUCCESS);

        service.execute(GRANT_ID);

        verify(stateService).markSucceeded(GRANT_ID, now);
        verify(stateService, never()).deferAttempt(anyLong(), any(), anyString(), any());
        verify(stateService, never()).flagForReview(anyLong(), any());
    }

    @Test
    void rejectedExecuteHandsOverToPollingWhenTossIsStillProcessing() {
        rejectWith("4000");
        when(tossPromotionClient.getExecutionResult(TOSS_USER_KEY, TOSS_CODE, KEY)).thenReturn(PromotionResultStatus.PENDING);

        service.execute(GRANT_ID);

        verify(stateService).markProcessing(GRANT_ID, now);
        verify(stateService, never()).flagForReview(anyLong(), any());
    }

    @Test
    void rejectedExecuteStaysInReviewWhenTheLookupItselfFails() {
        rejectWith("4000");
        // 4111(지급내역 없음)도 네트워크 오류도 클라이언트는 전부 BAD_GATEWAY 로 던진다. 둘 다 "모르는 상태"다.
        when(tossPromotionClient.getExecutionResult(TOSS_USER_KEY, TOSS_CODE, KEY))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR"));

        service.execute(GRANT_ID);

        verify(stateService).deferAttempt(eq(GRANT_ID), any(), eq("4000"), eq(now));
        verify(stateService).flagForReview(GRANT_ID, now);
        verify(stateService, never()).markSucceeded(anyLong(), any());
        verify(stateService, never()).markFailed(anyLong(), any(), any(), any());
    }

    @Test
    void knownCodesDoNotTriggerTheLookup() {
        rejectWith(PromotionErrorPolicy.ERROR_ALREADY_GRANTED);

        service.execute(GRANT_ID);

        verify(stateService).markSucceeded(GRANT_ID, now);
        verify(tossPromotionClient, never()).getExecutionResult(anyString(), anyString(), anyString());
    }

    private void rejectWith(String errorCode) {
        when(tossPromotionClient.executePromotion(TOSS_USER_KEY, TOSS_CODE, KEY, 5L))
                .thenReturn(PromotionExecutionOutcome.failed(errorCode, "rejected"));
    }
}
