package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserKeycapRepository extends JpaRepository<UserKeycap, Long> {

    Optional<UserKeycap> findByPublicId(UUID publicId);

    Optional<UserKeycap> findByUserIdAndEquippedTrue(UUID userId);
}
