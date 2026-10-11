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

/** 상자 개봉 이력. 개봉은 사라졌고(BEA-329) 조회만 남는다. */
public interface KeycapBoxOpenRepository extends JpaRepository<KeycapBoxOpen, Long> {

    Optional<KeycapBoxOpen> findByPublicId(UUID publicId);

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
