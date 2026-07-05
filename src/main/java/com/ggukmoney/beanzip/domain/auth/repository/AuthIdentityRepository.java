package com.ggukmoney.beanzip.domain.auth.repository;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    Optional<AuthIdentity> findByPublicId(UUID publicId);

    Optional<AuthIdentity> findByProviderAndProviderUserId(AuthIdentity.Provider provider, String providerUserId);
}
