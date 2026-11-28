package com.ggukmoney.beanzip.domain.friend.repository;

import com.ggukmoney.beanzip.domain.friend.entity.Cheer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CheerRepository extends JpaRepository<Cheer, Long> {

    long countByFromUserIdAndCheerDate(UUID fromUserId, LocalDate cheerDate);

    boolean existsByFromUserIdAndToUserIdAndCheerDate(UUID fromUserId, UUID toUserId, LocalDate cheerDate);

    /** 이 날짜에 내가 응원한 사람들. */
    @Query("select cheer.toUserId from Cheer cheer where cheer.fromUserId = :fromUserId and cheer.cheerDate = :cheerDate")
    List<UUID> findCheeredUserIds(@Param("fromUserId") UUID fromUserId, @Param("cheerDate") LocalDate cheerDate);

    /** 아직 확인하지 않은 응원 수. 탈퇴한 사용자가 보낸 응원은 세지 않는다. */
    @Query("""
            SELECT COUNT(cheer)
            FROM Cheer cheer
            JOIN AppUser sender ON sender.id = cheer.fromUserId
            WHERE cheer.toUserId = :toUserId
              AND cheer.readAt IS NULL
              AND sender.status <> com.ggukmoney.beanzip.domain.user.entity.AppUser.Status.WITHDRAWN
            """)
    long countUnread(@Param("toUserId") UUID toUserId);

    /** 아직 확인하지 않은 응원. 오래된 것부터다. 탈퇴한 사용자가 보낸 응원은 빠진다. */
    @Query("""
            SELECT cheer.publicId AS cheerId,
                   sender.id AS userId,
                   sender.nickname AS name,
                   cheer.kind AS kind,
                   cheer.tapCountSnapshot AS tapCountSnapshot,
                   cheer.createdAt AS createdAt
            FROM Cheer cheer
            JOIN AppUser sender ON sender.id = cheer.fromUserId
            WHERE cheer.toUserId = :toUserId
              AND cheer.readAt IS NULL
              AND sender.status <> com.ggukmoney.beanzip.domain.user.entity.AppUser.Status.WITHDRAWN
            ORDER BY cheer.createdAt, cheer.id
            """)
    List<UnreadCheer> findUnread(@Param("toUserId") UUID toUserId, Pageable pageable);

    /** 넘긴 ID 중에서 지금 확인할 수 있는 것만. 남이 받은 것, 이미 확인한 것, 탈퇴한 사용자가 보낸 것은 빠진다. */
    @Query("""
            SELECT cheer
            FROM Cheer cheer
            JOIN AppUser sender ON sender.id = cheer.fromUserId
            WHERE cheer.toUserId = :toUserId
              AND cheer.publicId IN :publicIds
              AND cheer.readAt IS NULL
              AND sender.status <> com.ggukmoney.beanzip.domain.user.entity.AppUser.Status.WITHDRAWN
            ORDER BY cheer.createdAt, cheer.id
            """)
    List<Cheer> findAckable(@Param("toUserId") UUID toUserId, @Param("publicIds") Collection<UUID> publicIds);

    /** 이 기간에 확인하면서 받은 조각의 합. 하루 상한은 확인한 날 기준이라 read_at 으로 센다. */
    @Query("""
            SELECT COALESCE(SUM(cheer.rewardShards), 0)
            FROM Cheer cheer
            WHERE cheer.toUserId = :toUserId
              AND cheer.readAt >= :from
              AND cheer.readAt < :to
            """)
    long sumRewardsReadBetween(
            @Param("toUserId") UUID toUserId, @Param("from") Instant from, @Param("to") Instant to);

    /** 넘긴 ID 중 내가 받은 응원을 보낸 사람들. 받은 순서이고, 같은 사람이 여러 번 나올 수 있다. */
    @Query("""
            SELECT cheer.fromUserId
            FROM Cheer cheer
            WHERE cheer.toUserId = :toUserId
              AND cheer.publicId IN :publicIds
            ORDER BY cheer.createdAt, cheer.id
            """)
    List<UUID> findSenderIds(@Param("toUserId") UUID toUserId, @Param("publicIds") Collection<UUID> publicIds);

    interface UnreadCheer {
        UUID getCheerId();

        UUID getUserId();

        String getName();

        Cheer.Kind getKind();

        long getTapCountSnapshot();

        Instant getCreatedAt();
    }
}
