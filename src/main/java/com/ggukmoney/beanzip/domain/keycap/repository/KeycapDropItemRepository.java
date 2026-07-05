package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapDropItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KeycapDropItemRepository extends JpaRepository<KeycapDropItem, Long> {

    Optional<KeycapDropItem> findByPublicId(UUID publicId);
}
