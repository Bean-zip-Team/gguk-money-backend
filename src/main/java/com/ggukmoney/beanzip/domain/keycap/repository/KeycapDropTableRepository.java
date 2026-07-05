package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapDropTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KeycapDropTableRepository extends JpaRepository<KeycapDropTable, Long> {

    Optional<KeycapDropTable> findByPublicId(UUID publicId);
}
