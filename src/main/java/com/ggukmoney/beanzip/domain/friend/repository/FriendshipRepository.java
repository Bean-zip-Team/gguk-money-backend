package com.ggukmoney.beanzip.domain.friend.repository;

import com.ggukmoney.beanzip.domain.friend.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /**
     * 친구 관계를 맺는다. 이미 친구면 넣지 않는다.
     *
     * @return 새로 맺었으면 1, 이미 친구면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO friendship (user_low, user_high, source, created_at)
            VALUES (:userLow, :userHigh, :source, :now)
            ON CONFLICT (user_low, user_high) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userLow") UUID userLow,
            @Param("userHigh") UUID userHigh,
            @Param("source") String source,
            @Param("now") Instant now
    );

    boolean existsByUserLowAndUserHigh(UUID userLow, UUID userHigh);

    /** 이 사용자의 친구들. 탈퇴한 친구도 들어 있다. */
    @Query("""
            SELECT CASE WHEN friendship.userLow = :userId THEN friendship.userHigh ELSE friendship.userLow END
            FROM Friendship friendship
            WHERE friendship.userLow = :userId OR friendship.userHigh = :userId
            """)
    List<UUID> findFriendIds(@Param("userId") UUID userId);
}
