package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserKeycapRepository extends JpaRepository<UserKeycap, Long> {

    Optional<UserKeycap> findByPublicId(UUID publicId);

    Optional<UserKeycap> findByUserIdAndEquippedTrue(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select userKeycap
            from UserKeycap userKeycap
            where userKeycap.user.id = :userId
            """)
    List<UserKeycap> findByUserIdForUpdate(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select userKeycap
            from UserKeycap userKeycap
            where userKeycap.user.id = :userId
              and userKeycap.equipped = true
            """)
    Optional<UserKeycap> findEquippedByUserIdForUpdate(@Param("userId") UUID userId);

    @Query("""
            select userKeycap
            from UserKeycap userKeycap
            join fetch userKeycap.keycap keycap
            where userKeycap.user.id = :userId
              and keycap.publicId = :keycapPublicId
            """)
    Optional<UserKeycap> findByUserIdAndKeycapPublicIdWithKeycap(
            @Param("userId") UUID userId,
            @Param("keycapPublicId") UUID keycapPublicId
    );

    @Query("""
            select userKeycap
            from UserKeycap userKeycap
            join fetch userKeycap.keycap keycap
            where userKeycap.user.id = :userId
            order by keycap.sortOrder asc, keycap.code asc
            """)
    List<UserKeycap> findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(@Param("userId") UUID userId);
}
