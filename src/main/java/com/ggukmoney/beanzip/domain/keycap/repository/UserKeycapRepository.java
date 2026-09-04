package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface UserKeycapRepository extends JpaRepository<UserKeycap, Long> {

    Optional<UserKeycap> findByPublicId(UUID publicId);

    Optional<UserKeycap> findByUserIdAndEquippedTrue(UUID userId);

    long countByUserIdAndStatus(UUID userId, UserKeycap.Status status);

    /**
     * 커트오프 이후에 완성된 키캡 수. 프로모션 소급 방지에 쓴다.
     * 이 시각 이전에 완성한 키캡은 세지 않으므로 출시 시점 보유분은 자격에 포함되지 않는다.
     */
    long countByUserIdAndStatusAndCompletedAtAfter(UUID userId, UserKeycap.Status status, Instant completedAt);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select userKeycap
            from UserKeycap userKeycap
            where userKeycap.user.id = :userId
              and userKeycap.keycap.id = :keycapId
            """)
    Optional<UserKeycap> findByUserIdAndKeycapIdForUpdate(
            @Param("userId") UUID userId,
            @Param("keycapId") Long keycapId
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
