package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 친구 화면과 응원 판정에 쓰는 탭 수를 읽는다 (BEA-390).
 *
 * <p>두 값 모두 친구가 자기 홈에서 보는 숫자({@code effectiveTapCountToday}, 자동 클릭 포함)와 같은
 * 기준이다. 그래서 오늘 누른 수가 1 이상이면 마지막으로 누른 날은 항상 오늘이다.
 */
@Component
@RequiredArgsConstructor
public class FriendTapReader {

    private final UserTapDailyRepository taps;

    /** 넘긴 사용자마다 한 건씩 돌려준다. 기록이 없는 사용자는 {@link TapState#NONE} 이다. */
    public Map<UUID, TapState> read(Collection<UUID> userIds, LocalDate today) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> todayCounts = new HashMap<>();
        taps.findEffectiveTapCounts(userIds, today)
                .forEach(row -> todayCounts.put(row.getUserId(), row.getTapCount()));
        Map<UUID, LocalDate> lastDates = new HashMap<>();
        taps.findLastEffectiveTapDates(userIds)
                .forEach(row -> lastDates.put(row.getUserId(), row.getLastTapDate()));

        Map<UUID, TapState> states = new HashMap<>();
        for (UUID userId : userIds) {
            states.put(userId, new TapState(todayCounts.getOrDefault(userId, 0L), lastDates.get(userId)));
        }
        return states;
    }

    /**
     * @param todayTapCount 오늘 누른 수
     * @param lastTapDate   마지막으로 누른 날(한국 시간). 한 번도 누르지 않았으면 null
     */
    public record TapState(long todayTapCount, LocalDate lastTapDate) {
        public static final TapState NONE = new TapState(0, null);
    }
}
