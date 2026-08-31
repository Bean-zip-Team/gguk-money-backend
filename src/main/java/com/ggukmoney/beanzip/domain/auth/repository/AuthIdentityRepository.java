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
            select identity.user.id as userId, identity.providerUserId as providerUserId
            from AuthIdentity identity
            where identity.user.id in :userIds
              and identity.provider = :provider
            """)
    List<UserProviderIdentity> findProviderIdentitiesByUserIds(
            @Param("userIds") Collection<UUID> userIds,
            @Param("provider") AuthIdentity.Provider provider
    );

    interface UserProviderIdentity {
        UUID getUserId();

        String getProviderUserId();
    }
}
