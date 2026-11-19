package com.ggukmoney.beanzip.domain.friend.repository;

import com.ggukmoney.beanzip.domain.friend.entity.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    /**
     * 초대 링크 방문을 PENDING 으로 남긴다. 같은 (초대한 사람, visitKey) 가 이미 있으면 넣지 않는다.
     *
     * <p>로그인 없이 부르는 경로라서 한 사람 앞으로 쌓일 수 있는 PENDING 수에 상한을 둔다. 동시에 들어오면
     * 상한을 몇 건 넘을 수 있다. 정확한 한도가 아니라 무한히 쌓이지 않게 하는 장치다.
     *
     * @return 넣었으면 1, 아니면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO invite (public_id, inviter_id, visit_key, status, created_at)
            SELECT :publicId, :inviterId, :visitKey, 'PENDING', :now
            WHERE (
                SELECT count(*) FROM invite pending
                WHERE pending.inviter_id = :inviterId AND pending.status = 'PENDING'
            ) < :pendingCap
            ON CONFLICT (inviter_id, visit_key) DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("publicId") UUID publicId,
            @Param("inviterId") UUID inviterId,
            @Param("visitKey") UUID visitKey,
            @Param("now") Instant now,
            @Param("pendingCap") int pendingCap
    );
}
