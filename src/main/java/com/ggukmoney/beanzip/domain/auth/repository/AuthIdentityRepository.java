package com.ggukmoney.beanzip.domain.auth.repository;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    Optional<AuthIdentity> findByPublicId(UUID publicId);

    Optional<AuthIdentity> findByProviderAndProviderUserId(AuthIdentity.Provider provider, String providerUserId);

    Optional<AuthIdentity> findByUserIdAndProvider(UUID userId, AuthIdentity.Provider provider);

    @Query("""
            SELECT identity.user.id AS userId,
                   identity.providerUserId AS providerUserId
            FROM AuthIdentity identity
            WHERE identity.provider = com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity$Provider.TOSS
              AND identity.user.id IN :userIds
            """)
    List<TossIdentityProjection> findTossIdentitiesByUserIds(@Param("userIds") Collection<UUID> userIds);

    interface TossIdentityProjection {
        UUID getUserId();

        String getProviderUserId();
    }
}
