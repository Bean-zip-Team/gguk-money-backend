package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KeycapBoxOpenRepository extends JpaRepository<KeycapBoxOpen, Long> {

    Optional<KeycapBoxOpen> findByPublicId(UUID publicId);
}
