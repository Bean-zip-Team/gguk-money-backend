package com.ggukmoney.beanzip.domain.mission.repository;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionDefinitionRepository extends JpaRepository<MissionDefinition, Long> {

    List<MissionDefinition> findByActiveTrueOrderBySortOrderAscCodeAsc();
}
