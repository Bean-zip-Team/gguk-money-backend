package com.ggukmoney.beanzip.domain.region.repository;

import com.ggukmoney.beanzip.domain.region.entity.UserRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRegionRepository extends JpaRepository<UserRegion, Long> {

    Optional<UserRegion> findByPublicId(UUID publicId);
}
