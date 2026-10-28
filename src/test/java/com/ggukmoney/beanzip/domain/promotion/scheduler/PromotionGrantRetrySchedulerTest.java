package com.ggukmoney.beanzip.domain.promotion.scheduler;

import com.ggukmoney.beanzip.domain.abuse.client.DiscordWebhookClient;
import com.ggukmoney.beanzip.domain.promotion.repository.PromotionGrantRepository;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionExecutionService;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionGrantStateService;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionTrigger;
import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BEA-326. needs_review 가 남아 있는 동안 한 시간에 한 번 운영 채널에 알린다.
 */
class PromotionGrantRetrySchedulerTest {

    private static final String CODE = "TAP_THOUSAND_COMPLETE";

    private final PromotionGrantRepository repository = mock(PromotionGrantRepository.class);
    private final PromotionTrigger trigger = mock(PromotionTrigger.class);
    private final PromotionPolicyConfig policyConfig = mock(PromotionPolicyConfig.class);
    private final DiscordWebhookClient discord = mock(DiscordWebhookClient.class);
    private final TickingClock clock = new TickingClock(Instant.parse("2026-10-28T03:00:00Z"));
    private final PromotionGrantRetryScheduler scheduler = new PromotionGrantRetryScheduler(
            repository, mock(PromotionExecutionService.class), mock(PromotionGrantStateService.class),
            List.of(trigger), policyConfig, clock, discord);

    @Test
    void alertsOnceAnHourWhileGrantsNeedReview() {
        when(trigger.promotionCode()).thenReturn(CODE);
        when(policyConfig.executionEnabled()).thenReturn(false);
        when(repository.countByPromotionCodeAndNeedsReviewTrue(CODE)).thenReturn(2L);
        when(repository.countByPromotionCodeAndHoldReasonIsNotNull(CODE)).thenReturn(2L);
        when(repository.findOldestUnsettledCreatedAt(CODE)).thenReturn(Optional.of(clock.instant().minus(Duration.ofDays(44))));

        scheduler.processDueGrants();
        clock.advance(Duration.ofSeconds(10));
        scheduler.processDueGrants();
        clock.advance(Duration.ofMinutes(61));
        scheduler.processDueGrants();

        verify(discord, times(2)).send(contains(CODE + ": 확인 필요 2건, 자동 진행 정지 2건, 가장 오래된 미정산 44일"));
    }

    @Test
    void staysQuietWhenNothingNeedsReview() {
        when(trigger.promotionCode()).thenReturn(CODE);
        when(policyConfig.executionEnabled()).thenReturn(false);
        when(repository.countByPromotionCodeAndNeedsReviewTrue(CODE)).thenReturn(0L);
        when(repository.findOldestUnsettledCreatedAt(CODE)).thenReturn(Optional.empty());

        scheduler.processDueGrants();

        verify(discord, never()).send(any());
    }

    private static final class TickingClock extends Clock {
        private Instant instant;

        private TickingClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
