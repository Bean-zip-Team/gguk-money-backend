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
 */
@Schema(description = "탭 배치 제출 응답")
public record TapBatchSubmitResponse(
        @Schema(description = "인정된 탭 수", example = "10")
        int acceptedCount,
        @Schema(description = "오늘 실제로 친 탭 수. 보상 상한(3000)과 무관하게 계속 증가한다.", example = "3763")
        int validTapCount,
        @Schema(description = "지급된 포인트", example = "1")
        int pointsAwarded,
        @Schema(description = "드롭된 키캡 상자 수", example = "0")
        int boxesDropped,
        @Schema(description = "제출 후 포인트 잔액", example = "15")
        long balance,
        @Schema(description = "오늘 포인트 지급 한도에 도달했는지 여부", example = "false")
        boolean pointDailyCapReached,
        @Schema(description = "현재 상자 진행 탭 수", example = "45")
        long boxProgressTapCount,
        @Schema(description = "다음 상자 획득 필요 탭 수", example = "100")
        int nextBoxRequiredTapCount,
        @Schema(description = "기준 일자", example = "2026-07-15")
        LocalDate date,
        @Schema(description = "오늘 지급된 포인트", example = "12")
        int pointEarnedToday,
        @Schema(description = "다음 포인트까지 남은 탭 수", example = "10")
        int remainingTapsToNextPoint,
        @Schema(description = "다음 키캡 상자까지 남은 탭 수", example = "55")
        int remainingTapsToNextBox,
        @Schema(description = "제출 후 보유한 키캡 상자 수", example = "2")
        int boxBalance,
        @Schema(description = "상자를 보유하고 있으며 무료 개봉 한도가 남아 있는지 여부", example = "true")
        boolean canFreeOpen,
        @Schema(description = "상자를 보유하고 있으며 광고 개봉 한도가 남아 있는지 여부", example = "true")
        boolean canAdOpen,
        @Schema(description = "무료와 광고 개봉 횟수를 모두 소진해 공통 주기 충전 중인지 여부", example = "false")
        boolean charging,
        @Schema(description = "charging=true일 때 다음 공통 충전 시각이며 charging=false일 때 null", example = "2026-07-16T01:00:00Z")
        Instant nextRechargeAt
) {
}
