package com.ggukmoney.beanzip.domain.tap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 탭 배치 제출 응답.
 *
 * <p>클라이언트가 배치 확정 직후 화면을 갱신하는 데 필요한 값을 모두 담는다.
 * 예전에는 이 응답에 없는 값을 채우려고 {@code GET /tap/today}와
 * {@code GET /keycap-boxes/status}를 뒤이어 호출해야 했다. 필드는 추가만 하므로
 * 두 조회를 아직 쓰는 구버전 앱도 그대로 동작한다.
 *
 * <p>상자는 사라졌다(BEA-329). 상자 시절 필드는 구버전 앱이 깨지지 않도록 중립값으로 남긴다.
 */
@Schema(description = "탭 배치 제출 응답")
public record TapBatchSubmitResponse(
        @Schema(description = "인정된 탭 수", example = "10")
        int acceptedCount,
        @Schema(description = "오늘 실제로 친 탭 수. 보상 상한(3000)과 무관하게 계속 증가한다.", example = "3763")
        int validTapCount,
        @Schema(description = "지급된 포인트", example = "1")
        int pointsAwarded,
        @Schema(description = "[deprecated] 상자는 사라졌다. 항상 0. 조각은 shardsDropped", example = "0")
        int boxesDropped,
        @Schema(description = "제출 후 포인트 잔액", example = "15")
        long balance,
        @Schema(description = "오늘 포인트 지급 한도에 도달했는지 여부", example = "false")
        boolean pointDailyCapReached,
        @Schema(description = "현재 조각 진행 탭 수", example = "45")
        long boxProgressTapCount,
        @Schema(description = "다음 조각 획득 필요 탭 수", example = "100")
        int nextBoxRequiredTapCount,
        @Schema(description = "기준 일자", example = "2026-07-15")
        LocalDate date,
        @Schema(description = "오늘 지급된 포인트", example = "12")
        int pointEarnedToday,
        @Schema(description = "다음 포인트까지 남은 탭 수. 0이면 오늘은 더 이상 지급되지 않는다(포인트 상한 또는 일일 탭 상한 도달). 상한 이전에는 항상 1 이상이다.", example = "10")
        int remainingTapsToNextPoint,
        @Schema(description = "다음 조각까지 남은 탭 수. remainingTapsToNextShard 와 같다", example = "55")
        int remainingTapsToNextBox,
        @Schema(description = "[deprecated] 상자는 사라졌다. 항상 0. 조각은 shardBalance", example = "0")
        int boxBalance,
        @Schema(description = "[deprecated] 항상 false", example = "false")
        boolean canFreeOpen,
        @Schema(description = "[deprecated] 항상 false", example = "false")
        boolean canAdOpen,
        @Schema(description = "[deprecated] 항상 false", example = "false")
        boolean charging,
        @Schema(description = "[deprecated] 항상 null", example = "null")
        Instant nextRechargeAt,
        @Schema(description = "이번 배치에서 드롭된 조각 수", example = "1")
        int shardsDropped,
        @Schema(description = "제출 후 조각 잔액", example = "12")
        int shardBalance,
        @Schema(description = "다음 조각까지 남은 탭 수", example = "55")
        int remainingTapsToNextShard
) {
}
