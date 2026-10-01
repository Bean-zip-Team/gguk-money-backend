package com.ggukmoney.beanzip.domain.mission.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * 어제 순위 대비 상승폭 (BEA-299).
 *
 * <p>비어 있으면 <b>오늘은 랭킹 미션을 판정할 수 없다</b>는 뜻이고, 해당 미션은 목록에서 빠진다.
 * 월요일이 여기 해당한다 — 주간 시즌이 초기화되어 전원 0점이 되므로 어제 순위와 비교하면 모든
 * 유저가 대폭 상승으로 잡힌다. 전날 스냅샷이 없는 신규 유저도 마찬가지다.
 */
public interface RankUpSignal {

    Optional<Long> rankUpOf(UUID userId, LocalDate today);
}
