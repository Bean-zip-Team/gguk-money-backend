package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface KeycapBoxOpenRepository extends JpaRepository<KeycapBoxOpen, Long> {

    Optional<KeycapBoxOpen> findByPublicId(UUID publicId);

    @Query("""
            select boxOpen
            from KeycapBoxOpen boxOpen
            join fetch boxOpen.keycap keycap
            where boxOpen.user.id = :userId
              and boxOpen.idempotencyKey = :idempotencyKey
            """)
    Optional<KeycapBoxOpen> findByUserIdAndIdempotencyKeyWithKeycap(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
