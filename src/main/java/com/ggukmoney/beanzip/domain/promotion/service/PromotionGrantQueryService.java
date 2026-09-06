package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.promotion.config.TossPromotionCodeRegistry;
import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantByUserResponse;
import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantItemResponse;
import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantRecentResponse;
import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantSummaryResponse;
import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.domain.promotion.repository.PromotionGrantRepository;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 운영 조회 전용 (BEA-272).
 *
 * <p>1차에는 프론트가 없어 이 경로가 지급 현황을 볼 유일한 창구다. 예산 소진(`4112`)과
 * PENDING 적체를 여기서 인지한다.
 *
 * <p>조회 대상은 레지스트리가 아니라 <b>트리거 목록</b>에서 가져온다. 레지스트리는 토스 코드가
 * 설정된 미션만 담고 있어서, 설정을 빠뜨린 미션이 목록에서 통째로 사라진다. 트리거 기준으로 돌면
 * 그 경우 {@code tossPromotionCode} 가 null 로 드러나 설정 누락 자체가 보인다.
 */
@Service
@RequiredArgsConstructor
public class PromotionGrantQueryService {

    private static final int MAX_RECENT_LIMIT = 100;

    private final PromotionGrantRepository promotionGrantRepository;
    private final UserTapProgressRepository userTapProgressRepository;
    private final TossPromotionCodeRegistry tossPromotionCodeRegistry;
    private final List<PromotionTrigger> promotionTriggers;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PromotionGrantSummaryResponse summary() {
        Instant now = Instant.now(clock);
        List<PromotionGrantSummaryResponse.PromotionSummary> promotions = promotionTriggers.stream()
                .map(trigger -> summarize(trigger, now))
                .toList();
        return new PromotionGrantSummaryResponse(now, promotions);
    }

    @Transactional(readOnly = true)
    public PromotionGrantRecentResponse recent(String promotionCode, int limit) {
        int capped = Math.clamp(limit, 1, MAX_RECENT_LIMIT);
        List<PromotionGrantItemResponse> items = promotionGrantRepository
                .findByPromotionCodeOrderByCreatedAtDesc(promotionCode, PageRequest.of(0, capped))
                .stream()
                .map(PromotionGrantItemResponse::from)
                .toList();
        return new PromotionGrantRecentResponse(items);
    }

    @Transactional(readOnly = true)
    public PromotionGrantByUserResponse byUser(UUID userId) {
        List<PromotionGrantItemResponse> grants = promotionGrantRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PromotionGrantItemResponse::from)
                .toList();

        Optional<UserTapProgress> progress = userTapProgressRepository.findByUserId(userId);
        Long baseline = progress.map(UserTapProgress::getPromotionTapBaseline).orElse(null);
        Long cumulative = progress.map(UserTapProgress::getCumulativeValidTapCount).orElse(null);
        Long net = (baseline == null || cumulative == null) ? null : cumulative - baseline;

        return new PromotionGrantByUserResponse(userId, grants, baseline, cumulative, net);
    }

    private PromotionGrantSummaryResponse.PromotionSummary summarize(PromotionTrigger trigger, Instant now) {
        String promotionCode = trigger.promotionCode();

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (PromotionGrant.Status status : PromotionGrant.Status.values()) {
            statusCounts.put(status.name(), 0L);
        }
        promotionGrantRepository.countByStatus(promotionCode)
                .forEach(row -> statusCounts.put(row.getStatus().name(), row.getCount()));

        Map<String, Long> errorCodeCounts = new LinkedHashMap<>();
        promotionGrantRepository.countByErrorCode(promotionCode)
                .forEach(row -> errorCodeCounts.put(row.getCode(), row.getCount()));

        long oldestUnsettledSeconds = promotionGrantRepository.findOldestUnsettledCreatedAt(promotionCode)
                .map(createdAt -> Duration.between(createdAt, now).toSeconds())
                .orElse(0L);

        return new PromotionGrantSummaryResponse.PromotionSummary(
                promotionCode,
                tossPromotionCodeRegistry.tossCodeOf(promotionCode).orElse(null),
                trigger.issuingEnabled(),
                statusCounts,
                promotionGrantRepository.sumGrantedAmount(promotionCode),
                errorCodeCounts,
                promotionGrantRepository.countByPromotionCodeAndHoldReasonIsNotNull(promotionCode),
                promotionGrantRepository.countByPromotionCodeAndNeedsReviewTrue(promotionCode),
                oldestUnsettledSeconds
        );
    }
}
