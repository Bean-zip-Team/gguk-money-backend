package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface KeycapBoxAccountRepository extends JpaRepository<KeycapBoxAccount, Long> {

    Optional<KeycapBoxAccount> findByPublicId(UUID publicId);

    Optional<KeycapBoxAccount> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from KeycapBoxAccount account
            where account.user.id = :userId
            """)
    Optional<KeycapBoxAccount> findByUserIdForUpdate(@Param("userId") UUID userId);
    /** One database snapshot includes pending legacy shards without changing state. */
    @Query("""
            select account.boxBalance + coalesce((select sum(k.shardCount) from UserKeycap k
                where k.user.id = :userId
                  and k.status = com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap.Status.IN_PROGRESS), 0)
            from KeycapBoxAccount account where account.user.id = :userId
            """)
    Optional<Long> findAvailableShardBalance(@Param("userId") UUID userId);
}
