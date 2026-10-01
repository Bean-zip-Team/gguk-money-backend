package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;

/**
 * 캐시에 담는 미션 정의 스냅샷. 엔티티를 그대로 들고 있으면 영속성 컨텍스트 밖에서 다루게 되므로
 * 읽기 전용 값으로 복사해 둔다.
 */
public record MissionDefinitionView(
        String code,
        MissionDefinition.MissionType missionType,
        MissionDefinition.PeriodType periodType,
        String name,
        String description,
        long targetValue,
        long rewardPointAmount,
        int sortOrder
) {

    public static MissionDefinitionView from(MissionDefinition definition) {
        return new MissionDefinitionView(
                definition.getCode(),
                definition.getMissionType(),
                definition.getPeriodType(),
                definition.getName(),
                definition.getDescription(),
                definition.getTargetValue(),
                definition.getRewardPointAmount(),
                definition.getSortOrder()
        );
    }
}
