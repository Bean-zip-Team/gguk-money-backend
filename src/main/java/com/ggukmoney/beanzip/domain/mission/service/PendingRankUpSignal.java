package com.ggukmoney.beanzip.domain.mission.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * 전일 순위 스냅샷이 아직 없으므로 항상 판정 불가를 돌려준다.
 *
 * <p>덕분에 랭킹 미션은 스냅샷 배치가 들어오기 전까지 목록에 나오지 않는다. 달성할 수 없는 미션을
 * {@code 0 / 5} 로 계속 보여주는 것보다 낫다. BEA-299 4단계에서 실제 구현으로 교체한다.
 */
@Component
public class PendingRankUpSignal implements RankUpSignal {

    @Override
    public Optional<Long> rankUpOf(UUID userId, LocalDate today) {
        return Optional.empty();
    }
}
