package com.ggukmoney.beanzip.domain.friend.repository;

import com.ggukmoney.beanzip.domain.friend.entity.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
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

    /**
     * 방문 기록이 있는 초대를 성공으로 바꾼다. 방문 기록이 아직 없으면 성공 행을 새로 넣는다.
     *
     * <p>방문 기록 요청과 로그인 요청은 거의 동시에 온다. 어느 쪽이 먼저 도착해도 한 행으로 끝나도록
     * 한 문장으로 처리한다. 같은 visitKey 의 행이 이미 다른 사람의 성공이면 건드리지 않는다.
     *
     * @return 바꾸거나 넣었으면 1, 아니면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO invite (public_id, inviter_id, invitee_id, visit_key, status, created_at, succeeded_at)
            SELECT :publicId, :inviterId, :inviteeId, :visitKey, 'SUCCESS', :now, :now
            WHERE NOT EXISTS (SELECT 1 FROM invite taken WHERE taken.invitee_id = :inviteeId)
            ON CONFLICT (inviter_id, visit_key) DO UPDATE
                SET status = 'SUCCESS',
                    invitee_id = EXCLUDED.invitee_id,
                    succeeded_at = EXCLUDED.succeeded_at
                WHERE invite.status = 'PENDING'
            """, nativeQuery = true)
    int markSuccessForVisit(
            @Param("publicId") UUID publicId,
            @Param("inviterId") UUID inviterId,
            @Param("inviteeId") UUID inviteeId,
            @Param("visitKey") UUID visitKey,
            @Param("now") Instant now
    );

    /**
     * 방문 기록과 이을 수 없는 가입을 성공 행으로 넣는다. 이미 초대받은 것으로 기록된 사람이면 넣지 않는다.
     *
     * @return 넣었으면 1, 아니면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO invite (public_id, inviter_id, invitee_id, status, created_at, succeeded_at)
            SELECT :publicId, :inviterId, :inviteeId, 'SUCCESS', :now, :now
            WHERE NOT EXISTS (SELECT 1 FROM invite taken WHERE taken.invitee_id = :inviteeId)
            """, nativeQuery = true)
    int insertSuccess(
            @Param("publicId") UUID publicId,
            @Param("inviterId") UUID inviterId,
            @Param("inviteeId") UUID inviteeId,
            @Param("now") Instant now
    );

    Optional<Invite> findByInviteeId(UUID inviteeId);
}
