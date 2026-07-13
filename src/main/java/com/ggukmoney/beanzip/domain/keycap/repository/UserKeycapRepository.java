package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserKeycapRepository extends JpaRepository<UserKeycap, Long> {

    Optional<UserKeycap> findByPublicId(UUID publicId);

    Optional<UserKeycap> findByUserIdAndEquippedTrue(UUID userId);

    @Query("""
            select userKeycap
            from UserKeycap userKeycap
            join fetch userKeycap.keycap keycap
            where userKeycap.user.id = :userId
            order by keycap.sortOrder asc, keycap.code asc
            """)
    List<UserKeycap> findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(@Param("userId") UUID userId);
}
