package com.ggukmoney.beanzip.domain.friend.repository;

import com.ggukmoney.beanzip.domain.friend.entity.Cheer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
}
