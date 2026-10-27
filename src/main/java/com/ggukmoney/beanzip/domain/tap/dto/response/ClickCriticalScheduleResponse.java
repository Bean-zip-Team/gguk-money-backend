package com.ggukmoney.beanzip.domain.tap.dto.response;

import com.ggukmoney.beanzip.domain.keycap.passive.ClickCriticalSchedule;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "앞으로 300탭 안에서 클릭 크리티컬(②)이 터질 탭 예정표예요. 앱은 이 탭에서 바로 크리티컬을 연출하고, 서버는 같은 탭에 크리티컬을 반영해요. 직접 탭만 세고 자동 클릭(⑤)은 세지 않아요. 키캡을 장착하거나 뽑기 전에는 쌓인 탭을 먼저 보내고, 그 뒤에 `GET /api/tap/today`로 새 예정표를 받아요(BEA-367).")
public record ClickCriticalScheduleResponse(
        @Schema(description = "크리티컬이 터진 탭 한 번이 몇 탭으로 계산되는지예요.", example = "2")
        int multiplier,
        @Schema(description = "예정표를 만든 시점까지 서버가 센 직접 탭의 누적 수예요. 탭 배치 응답에서는 방금 보낸 배치까지 포함해요. 배치를 보내는 중에 받은 예정표도 이 값으로 위치를 맞추고, 이 값이 더 작은 예정표는 버려요.", example = "1300")
        long baseTapNumber,
        @Schema(description = "크리티컬이 터질 탭의 순번이에요. `baseTapNumber`의 다음 탭이 1번이에요. 1부터 300 사이 값이 오름차순으로 오고, 300탭 안에 크리티컬이 없으면 빈 배열이에요.", example = "[7, 23, 61]")
        List<Integer> tapOffsets
) {
    /** Null when the equipped keycap has no click critical or the passive is off. */
    public static ClickCriticalScheduleResponse of(ClickCriticalSchedule schedule, UUID userId, long countedTaps, Critical click) {
        var offsets = schedule.upcoming(userId, countedTaps, click);
        return offsets == null ? null : new ClickCriticalScheduleResponse(click.multiplier(), countedTaps, offsets);
    }
}
