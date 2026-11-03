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
 * <p>상자는 사라졌다(BEA-329). 경제 전환 전에는 기존 상자 상태를 반환하고, 전환 후에는 상자 필드가 중립값이 된다.
 */
@Schema(description = "탭 배치 제출 응답이에요. 배치를 확정한 직후 화면을 갱신하는 데 필요한 값을 모두 담아요. 경제 전환 전에는 기존 상자 상태를, 전환 후에는 조각 상태를 반환해요(BEA-329).")
public record TapBatchSubmitResponse(
        @Schema(description = "인정된 탭 수예요. 지금은 `submittedCount` 필드 값을 그대로 인정해요. 이미 처리한 배치를 다시 보내면 처음 인정된 탭 수를 돌려줘요.", example = "10")
        int acceptedCount,
        @Schema(description = "오늘 직접 탭으로 쌓인 점수예요. 부스터 배율을 포함하고 클릭 크리티컬과 자동 클릭은 빼요. 보상 상한(3000탭)과 무관하게 계속 늘어나요.", example = "3763")
        int validTapCount,
        @Schema(description = "이번 요청에서 자동 클릭 정산과 직접 탭 배치로 지급된 포인트의 합계예요. 이미 처리한 배치를 다시 보내면 0이에요.", example = "1")
        int pointsAwarded,
        @Schema(description = "[deprecated] 경제 전환 전 이번 요청에서 나온 상자 수예요. 전환 후에는 0이며 `shardsDropped`를 사용해요.", example = "0", deprecated = true)
        int boxesDropped,
        @Schema(description = "제출 후 포인트 잔액이에요.", example = "15")
        long balance,
        @Schema(description = "오늘 포인트 지급 한도에 도달했는지 여부예요. 한도는 앱 설정의 `pointPolicy.dailyLimit` 필드 값과 같아요. 상한 이후 희박 지급이 켜져 있으면 이 값이 true 여도 `pointEarnedToday` 필드 값이 한도 위로 조금 더 늘 수 있어요.", example = "false")
        boolean pointDailyCapReached,
        @Schema(description = "현재 조각 세션에 쌓인 탭 수예요. 이름은 상자 시절 그대로지만 키캡 조각 진행도를 담는 현역 필드예요. 마지막 탭 뒤 유휴 시간이 지나면 세션이 리셋돼 0부터 다시 세요.", example = "45")
        long boxProgressTapCount,
        @Schema(description = "다음 조각이 나오는 세션 누적 탭 수예요. 이름은 상자 시절 그대로지만 키캡 조각 진행도를 담는 현역 필드예요. `boxProgressTapCount` 필드 값이 이 값에 닿으면 조각이 하나 나와요.", example = "100")
        int nextBoxRequiredTapCount,
        @Schema(description = "기준 일자예요. 서비스 기준 시간대(기본 Asia/Seoul)로 정해요.", example = "2026-07-15")
        LocalDate date,
        @Schema(description = "오늘 지급된 포인트예요. 이번 배치에서 지급된 포인트를 포함해요.", example = "12")
        int pointEarnedToday,
        @Schema(description = "다음 포인트까지 남은 탭 수예요. 0이면 오늘은 정해진 간격의 포인트가 더 이상 지급되지 않아요. 포인트 상한이나 일일 탭 상한에 도달한 경우이고, 상한 이후 희박 지급만 예외예요. 상한에 닿기 전에는 항상 1 이상이에요.", example = "10")
        int remainingTapsToNextPoint,
        @Schema(description = "[deprecated] 다음 드롭까지 남은 탭 수예요. 경제 전환 후에는 `remainingTapsToNextShard`와 같아요.", example = "55", deprecated = true)
        int remainingTapsToNextBox,
        @Schema(description = "[deprecated] 경제 전환 전 상자 잔액이에요. 전환 후에는 0이며 `shardBalance`를 사용해요.", example = "0", deprecated = true)
        int boxBalance,
        @Schema(description = "[deprecated] 경제 전환 전 무료 개봉 가능 여부예요. 전환 후에는 false예요.", example = "false", deprecated = true)
        boolean canFreeOpen,
        @Schema(description = "[deprecated] 경제 전환 전 광고 개봉 가능 여부예요. 전환 후에는 false예요.", example = "false", deprecated = true)
        boolean canAdOpen,
        @Schema(description = "[deprecated] 경제 전환 전 개봉 횟수 충전 중인지 여부예요. 전환 후에는 false예요.", example = "false", deprecated = true)
        boolean charging,
        @Schema(description = "[deprecated] 경제 전환 전 다음 개봉 충전 시각이에요. 전환 후에는 null이에요.", nullable = true, deprecated = true)
        Instant nextRechargeAt,
        @Schema(description = "경제 전환 후 이번 요청에서 자동 클릭 정산과 직접 탭 배치로 나온 키캡 조각 수의 합계예요. 전환 전에는 0이에요. 드롭당 기본 1개에 조각 크리티컬을 적용해요. 이미 처리한 배치를 다시 보내면 0이에요.", example = "1")
        int shardsDropped,
        @Schema(description = "경제 전환 후 제출 시점의 키캡 조각 잔액이에요. 이번 배치의 드롭을 포함하며 전환 전에는 0이에요.", example = "12")
        int shardBalance,
        @Schema(description = "다음 조각까지 남은 탭 수예요. `nextBoxRequiredTapCount` 필드 값에서 `boxProgressTapCount` 필드 값을 뺀 값이에요.", example = "55")
        int remainingTapsToNextShard,
        @Schema(description = "이번 직접 탭에 부스터와 클릭 크리티컬(②)을 반영한 클릭 수예요. 부스터 중 크리티컬이 난 탭은 탭 전체 점수에 크리티컬 배율을 곱해요. 자동 클릭(⑤)은 포함하지 않아요. 이미 처리한 배치를 다시 보내면 0이에요.", example = "12")
        int effectiveCount,
        @Schema(description = "이번 요청에서 직접 탭보다 먼저 정산한 자동 클릭(⑤) 수예요. 이미 처리한 배치를 다시 보내면 0이에요.", example = "60")
        int autoClicksGranted,
        @Schema(description = "오늘 직접 탭, 클릭 크리티컬(②), 자동 클릭(⑤)을 합한 클릭 수예요. 포인트 지급용 탭 한도와 무관하게 계속 늘어나요.", example = "3823")
        long effectiveTapCountToday,
        @Schema(description = "앞으로 300탭 안에서 클릭 크리티컬이 터질 탭 예정표예요. 장착한 키캡에 클릭 크리티컬이 없거나 패시브가 꺼져 있으면 null이에요.", nullable = true)
        ClickCriticalScheduleResponse clickCriticalSchedule
) {
}
