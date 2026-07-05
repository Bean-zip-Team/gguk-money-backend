package com.ggukmoney.beanzip.domain.legal.repository;

import com.ggukmoney.beanzip.domain.legal.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    Optional<UserConsent> findByPublicId(UUID publicId);
}
