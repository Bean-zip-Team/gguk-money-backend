package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapDraw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface KeycapDrawRepository extends JpaRepository<KeycapDraw, Long> {

    @Query("""
            select draw
            from KeycapDraw draw
            join fetch draw.keycap keycap
            where draw.user.id = :userId
              and draw.idempotencyKey = :idempotencyKey
            """)
    Optional<KeycapDraw> findByUserIdAndIdempotencyKeyWithKeycap(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
