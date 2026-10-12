package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    boolean existsByAdRewardId(String adRewardId);

    /** 일괄 개봉 수령 여부. 행이 있으면 이미 받은 것이다 (BEA-280). */
    boolean existsByUserIdAndOpenMethod(UUID userId, KeycapBoxOpen.OpenMethod openMethod);

    @Query("""
            select boxOpen
            from KeycapBoxOpen boxOpen
            join fetch boxOpen.keycap
            where boxOpen.user.id = :userId
              and boxOpen.openMethod = :openMethod
            order by boxOpen.id asc
            """)
    List<KeycapBoxOpen> findAllByUserIdAndOpenMethodWithKeycap(
            @Param("userId") UUID userId,
            @Param("openMethod") KeycapBoxOpen.OpenMethod openMethod
    );

    @Query("""
            select boxOpen
            from KeycapBoxOpen boxOpen
            join fetch boxOpen.keycap keycap
            where boxOpen.user.id = :userId
              and (
                    :cursorOpenedAt is null
                    or boxOpen.openedAt < :cursorOpenedAt
                    or (boxOpen.openedAt = :cursorOpenedAt and boxOpen.id < :cursorId)
                  )
            order by boxOpen.openedAt desc, boxOpen.id desc
            """)
    List<KeycapBoxOpen> findHistoryByUserId(
            @Param("userId") UUID userId,
            @Param("cursorOpenedAt") Instant cursorOpenedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
