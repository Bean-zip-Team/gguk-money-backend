package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface UserKeycapRepository extends JpaRepository<UserKeycap, Long> {

    Optional<UserKeycap> findByPublicId(UUID publicId);

    Optional<UserKeycap> findByUserIdAndEquippedTrue(UUID userId);

    @Query("""
            select userKeycap
            from UserKeycap userKeycap
            join fetch userKeycap.keycap keycap
            where userKeycap.user.id in :userIds
              and userKeycap.equipped = true
            """)
    List<UserKeycap> findEquippedWithKeycapByUserIds(@Param("userIds") Collection<UUID> userIds);

    long countByUserIdAndStatus(UUID userId, UserKeycap.Status status);

    /**
     * 획득 경로별 완성 키캡 수. 전체 완성 보너스는 상자 풀({@code BOX})만 센다 — 이벤트 키캡까지 세면
     * 상자 키캡을 한 종 덜 모아도 "전 종류 완성"으로 판정된다(BEA-285).
     */
    long countByUserIdAndStatusAndKeycapAcquisitionType(
            UUID userId,
            UserKeycap.Status status,
            Keycap.AcquisitionType acquisitionType
    );

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

    /**
     * 내 키캡 목록. 구 코드가 남긴 {@code IN_PROGRESS} 행은 미보유이므로 내려주지 않는다(BEA-329 무중단 배포 구간).
     */
    @Query("""
            select userKeycap
            from UserKeycap userKeycap
            join fetch userKeycap.keycap keycap
            where userKeycap.user.id = :userId
              and userKeycap.status = com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap.Status.COMPLETED
            order by keycap.sortOrder asc, keycap.code asc
            """)
    List<UserKeycap> findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(@Param("userId") UUID userId);

    /**
     * 구 코드가 진행 중 행에 남긴 종별 조각 수. 엔티티는 이 컬럼을 더 매핑하지 않으므로(정리 SQL C 가 지운다)
     * 뽑기가 진행 중 행을 보유로 바꿀 때만 직접 읽어 지갑에 더한다. B 가 쓸어 담을 몫을 먼저 옮기는 것이라 합계가 맞는다.
     */
    @Query(value = "select coalesce(shard_count, 0) from user_keycap where id = :id", nativeQuery = true)
    int findLegacyShardCount(@Param("id") Long id);
}
