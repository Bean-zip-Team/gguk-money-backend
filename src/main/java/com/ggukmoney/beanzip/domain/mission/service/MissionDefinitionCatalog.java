package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.repository.MissionDefinitionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 미션 정의 캐시 (BEA-299).
 *
 * <p>조회마다 DB 를 때리지 않도록 60초 간격으로 읽어 둔다. 갱신에 실패하면 마지막으로 읽은 값을
 * 그대로 쓴다 — DB 가 잠깐 흔들렸다고 미션 목록이 통째로 비면 유저 화면이 빈 채로 뜬다.
 * {@code TapPolicyConfig} 가 쓰는 방식과 같다.
 *
 * <p>다만 <b>부팅 직후 첫 조회가 실패하면 들고 있을 값이 없어 빈 목록으로 남는다.</b> 그 상태는
 * 응답이 200 이라 겉으로 드러나지 않으므로 경고가 아니라 오류로 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionDefinitionCatalog {

    private final MissionDefinitionRepository missionDefinitionRepository;

    private volatile List<MissionDefinitionView> cache = List.of();

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        try {
            cache = missionDefinitionRepository.findByActiveTrueOrderBySortOrderAscCodeAsc().stream()
                    .map(MissionDefinitionView::from)
                    .toList();
        } catch (RuntimeException exception) {
            if (cache.isEmpty()) {
                log.error("MISSION_DEFINITION_REFRESH_FAILED keptDefinitions=0 impact=DAILY_MISSIONS_HIDDEN", exception);
                return;
            }
            log.warn("MISSION_DEFINITION_REFRESH_FAILED keptDefinitions={}", cache.size(), exception);
        }
    }

    /** 활성 미션 정의. 정렬 순서는 DB 의 {@code sort_order} 를 따른다. */
    public List<MissionDefinitionView> activeDefinitions() {
        return cache;
    }

    public Optional<MissionDefinitionView> findByCode(String code) {
        return cache.stream().filter(definition -> definition.code().equals(code)).findFirst();
    }
}
