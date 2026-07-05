package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpenResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KeycapBoxOpenResultRepository extends JpaRepository<KeycapBoxOpenResult, Long> {

    Optional<KeycapBoxOpenResult> findByPublicId(UUID publicId);
}
