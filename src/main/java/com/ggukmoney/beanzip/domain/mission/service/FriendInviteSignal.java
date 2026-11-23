package com.ggukmoney.beanzip.domain.mission.service;

import java.util.Optional;
import java.util.UUID;

/**
 * 친구 초대 미션이 보는 값 (BEA-390).
 *
 * <p>미션 쪽이 친구 도메인의 테이블을 직접 읽지 않게 하는 경계다. {@link RankUpSignal} 과 같은 방식이다.
 */
public interface FriendInviteSignal {

    /**
     * 내 초대 링크로 새로 가입한 사람 수. 이미 회원이던 사람과 친구가 된 경우는 세지 않고, 가입한 사람이
     * 나중에 탈퇴해도 줄지 않는다.
     *
     * @return 비어 있으면 이 사용자에게는 친구 미션을 보여 주지 않는다(친구 기능이 꺼져 있거나 정지된 사용자)
     */
    Optional<Long> successCountOf(UUID userId);
}
