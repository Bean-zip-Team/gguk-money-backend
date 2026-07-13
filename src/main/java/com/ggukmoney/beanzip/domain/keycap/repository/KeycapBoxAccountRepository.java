package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KeycapBoxAccountRepository extends JpaRepository<KeycapBoxAccount, Long> {

    Optional<KeycapBoxAccount> findByPublicId(UUID publicId);

    Optional<KeycapBoxAccount> findByUserId(UUID userId);
}
