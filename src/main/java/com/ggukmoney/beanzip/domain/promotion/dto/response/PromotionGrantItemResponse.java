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
@Schema(description = "프로모션 지급 건 하나예요.")
public record PromotionGrantItemResponse(
        @Schema(description = "지급 건 ID예요.")
        UUID publicId,

        @Schema(description = "내부 프로모션 코드예요.", example = "TAP_THOUSAND_COMPLETE")
        String promotionCode,

        @Schema(description = "지급 상태예요. `PENDING`은 지급 대기, `PROCESSING`은 토스에 지급을 요청하고 결과를 확인하는 중, "
                + "`SUCCEEDED`는 지급 완료, `FAILED`는 지급 실패로 확정됨이에요.", example = "SUCCEEDED")
        String status,

        @Schema(description = "지급액(토스 포인트)이에요.", example = "5")
        long amount,

        @Schema(description = "자격을 얻은 시점의 판정값이에요. "
                + "키캡 미션은 커트오프 이후 얻은 키캡 수(완성 수), 탭 미션은 커트오프 이후 순증 탭 수예요.", example = "1000")
        Integer triggerSnapshot,

        @Schema(description = "토스 에러 코드예요. 재시도를 기다리는 건에는 `AMBIGUOUS`, `GET_KEY_FAILED`, `NOT_CONFIGURED` 같은 "
                + "우리 쪽 코드가 들어가기도 해요.", example = "4112")
        String tossErrorCode,

        @Schema(description = "실패 사유예요. 실패로 확정된 건에만 들어가요. 우리 쪽 코드(`NO_TOSS_IDENTITY`, `TOSS_RESULT_FAILED`)이거나 토스가 돌려준 오류 사유예요.", example = "NO_TOSS_IDENTITY")
        String failureReason,

        @Schema(description = "자동 진행을 멈춘 사유예요. `KEY_COMMIT_FAILED`나 `MAX_ATTEMPTS_EXCEEDED`가 들어가요. "
                + "멈춘 적이 없거나 지급에 성공했으면 null이에요.", example = "MAX_ATTEMPTS_EXCEEDED")
        String holdReason,

        @Schema(description = "시도 횟수예요. 재시도할 수 있는 실패가 날 때마다 1씩 늘어요.", example = "1")
        Integer attemptCount,

        @Schema(description = "지급 건을 발급한 시각이에요.")
        Instant createdAt,

        @Schema(description = "지급을 완료한 시각이에요. 아직 완료되지 않았으면 null이에요.")
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
