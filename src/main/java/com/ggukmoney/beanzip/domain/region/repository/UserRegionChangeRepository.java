package com.ggukmoney.beanzip.domain.region.repository;

import com.ggukmoney.beanzip.domain.region.entity.UserRegionChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRegionChangeRepository extends JpaRepository<UserRegionChange, Long> {

    Optional<UserRegionChange> findByPublicId(UUID publicId);
}
