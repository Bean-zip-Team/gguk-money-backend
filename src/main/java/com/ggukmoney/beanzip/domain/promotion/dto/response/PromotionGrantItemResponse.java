package com.ggukmoney.beanzip.domain.promotion.dto.response;

import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * 지급 건 하나.
 *
 * <p>{@code userId} 를 담지 않는다. 목록으로 훑으면 전체 유저의 지급 이력이 쏟아진다.
 * 특정 건을 지목할 때는 {@code publicId} 로 충분하다. 알림 로그에서 user_id 를 걷어낸
 * 선례(BEA-227)와 같은 기준이다.
 */
@Schema(description = "지급 건")
public record PromotionGrantItemResponse(
        @Schema(description = "지급 건 식별자")
        UUID publicId,

        @Schema(description = "내부 프로모션 코드", example = "TAP_THOUSAND_COMPLETE")
        String promotionCode,

        @Schema(description = "상태", example = "SUCCEEDED")
        String status,

        @Schema(description = "지급액", example = "5")
        long amount,

        @Schema(description = "자격 획득 시점의 판정값 — 키캡은 완성 수, 탭은 순증 탭 수", example = "1000")
        Integer triggerSnapshot,

        @Schema(description = "토스 에러코드", example = "4112")
        String tossErrorCode,

        @Schema(description = "우리 쪽 실패 사유", example = "NO_TOSS_IDENTITY")
        String failureReason,

        @Schema(description = "자동 진행 중단 사유", example = "MAX_ATTEMPTS_EXCEEDED")
        String holdReason,

        @Schema(description = "시도 횟수", example = "1")
        Integer attemptCount,

        @Schema(description = "발급 시각")
        Instant createdAt,

        @Schema(description = "지급 완료 시각")
        Instant grantedAt
) {

    public static PromotionGrantItemResponse from(PromotionGrant grant) {
        return new PromotionGrantItemResponse(
                grant.getPublicId(),
                grant.getPromotionCode(),
                grant.getStatus().name(),
                grant.getAmount(),
                grant.getTriggerSnapshot(),
                grant.getTossErrorCode(),
                grant.getFailureReason(),
                grant.getHoldReason(),
                grant.getAttemptCount(),
                grant.getCreatedAt(),
                grant.getGrantedAt()
        );
    }
}
