package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KeycapBoxLedgerRepository extends JpaRepository<KeycapBoxLedger, Long> {

    Optional<KeycapBoxLedger> findByPublicId(UUID publicId);
}
