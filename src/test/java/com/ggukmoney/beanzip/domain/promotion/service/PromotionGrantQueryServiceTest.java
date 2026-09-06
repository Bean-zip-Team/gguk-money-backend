package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.promotion.config.TossPromotionCodeRegistry;
import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantByUserResponse;
import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantSummaryResponse;
import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.domain.promotion.repository.PromotionGrantRepository;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionGrantQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T10:15:00Z");

    private final PromotionGrantRepository promotionGrantRepository = mock(PromotionGrantRepository.class);
    private final UserTapProgressRepository userTapProgressRepository = mock(UserTapProgressRepository.class);
    private final TossPromotionCodeRegistry registry = mock(TossPromotionCodeRegistry.class);
    private final PromotionTrigger tapTrigger = mock(PromotionTrigger.class);

    private final PromotionGrantQueryService service = new PromotionGrantQueryService(
            promotionGrantRepository, userTapProgressRepository, registry,
            List.of(tapTrigger), Clock.fixed(NOW, ZoneOffset.UTC));

    private void stubTrigger() {
        when(tapTrigger.promotionCode()).thenReturn("TAP_THOUSAND_COMPLETE");
        lenient().when(tapTrigger.issuingEnabled()).thenReturn(true);
        lenient().when(promotionGrantRepository.countByStatus(anyString())).thenReturn(List.of());
        lenient().when(promotionGrantRepository.countByErrorCode(anyString())).thenReturn(List.of());
        lenient().when(promotionGrantRepository.findOldestUnsettledCreatedAt(anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void reportsEveryStatusEvenWhenThereAreNoRows() {
        stubTrigger();

        PromotionGrantSummaryResponse response = service.summary();

        PromotionGrantSummaryResponse.PromotionSummary summary = response.promotions().getFirst();
        assertThat(summary.statusCounts())
                .containsOnlyKeys("PENDING", "PROCESSING", "SUCCEEDED", "FAILED")
                .containsValue(0L);
        assertThat(response.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void surfacesMissingTossCodeAsNullInsteadOfHidingThePromotion() {
        stubTrigger();
        // 레지스트리 기준으로 순회하면 설정을 빠뜨린 미션이 목록에서 통째로 사라진다.
        when(registry.tossCodeOf("TAP_THOUSAND_COMPLETE")).thenReturn(Optional.empty());

        PromotionGrantSummaryResponse response = service.summary();

        assertThat(response.promotions()).hasSize(1);
        assertThat(response.promotions().getFirst().tossPromotionCode()).isNull();
    }

    @Test
    void reportsOldestUnsettledAgeInSeconds() {
        stubTrigger();
        when(promotionGrantRepository.findOldestUnsettledCreatedAt("TAP_THOUSAND_COMPLETE"))
                .thenReturn(Optional.of(NOW.minusSeconds(143)));

        assertThat(service.summary().promotions().getFirst().oldestUnsettledSeconds()).isEqualTo(143L);
    }

    @Test
    void computesNetTapCountForCsLookup() {
        UUID userId = UUID.randomUUID();
        when(promotionGrantRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        UserTapProgress progress = mock(UserTapProgress.class);
        when(progress.getPromotionTapBaseline()).thenReturn(3200L);
        when(progress.getCumulativeValidTapCount()).thenReturn(4150L);
        when(userTapProgressRepository.findByUserId(userId)).thenReturn(Optional.of(progress));

        PromotionGrantByUserResponse response = service.byUser(userId);

        assertThat(response.netTapCountSinceBaseline()).isEqualTo(950L);
    }

    @Test
    void leavesNetTapCountNullWhenBaselineIsNotAnchoredYet() {
        UUID userId = UUID.randomUUID();
        when(promotionGrantRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        UserTapProgress progress = mock(UserTapProgress.class);
        when(progress.getPromotionTapBaseline()).thenReturn(null);
        when(progress.getCumulativeValidTapCount()).thenReturn(4150L);
        when(userTapProgressRepository.findByUserId(userId)).thenReturn(Optional.of(progress));

        PromotionGrantByUserResponse response = service.byUser(userId);

        // baseline 이 없으면 순증을 0 으로 단정하면 안 된다. 아직 커트오프에 고정되지 않았다는 뜻이다.
        assertThat(response.netTapCountSinceBaseline()).isNull();
        assertThat(response.cumulativeValidTapCount()).isEqualTo(4150L);
    }

    @Test
    void capsRecentLimit() {
        when(promotionGrantRepository.findByPromotionCodeOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(List.<PromotionGrant>of());

        service.recent("TAP_THOUSAND_COMPLETE", 5000);

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        org.mockito.Mockito.verify(promotionGrantRepository)
                .findByPromotionCodeOrderByCreatedAtDesc(anyString(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    private static org.springframework.data.domain.Pageable any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
