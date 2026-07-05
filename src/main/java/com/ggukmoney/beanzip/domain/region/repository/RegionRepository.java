package com.ggukmoney.beanzip.domain.region.repository;

import com.ggukmoney.beanzip.domain.region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByPublicId(UUID publicId);

    Optional<Region> findByCode(String code);
}
