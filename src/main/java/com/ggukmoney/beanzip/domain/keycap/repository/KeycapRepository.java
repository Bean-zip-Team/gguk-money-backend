package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KeycapRepository extends JpaRepository<Keycap, Long> {

    Optional<Keycap> findByPublicId(UUID publicId);

    Optional<Keycap> findByCode(String code);

    boolean existsByCode(String code);

    List<Keycap> findByActiveTrueOrderBySortOrderAscCodeAsc();

    long countByActiveTrue();

    @Query("""
            select keycap
            from Keycap keycap
            left join UserKeycap userKeycap
              on userKeycap.keycap = keycap
             and userKeycap.user.id = :userId
            where keycap.active = true
              and (userKeycap.id is null or userKeycap.status = com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap.Status.IN_PROGRESS)
            order by keycap.sortOrder asc, keycap.code asc
            """)
    List<Keycap> findIncompleteActiveRewardCandidates(@Param("userId") UUID userId);
}
