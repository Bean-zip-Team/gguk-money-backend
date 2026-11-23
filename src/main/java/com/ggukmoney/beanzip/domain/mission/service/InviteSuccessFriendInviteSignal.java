package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.friend.entity.Invite;
import com.ggukmoney.beanzip.domain.friend.repository.InviteRepository;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** 성공한 초대를 세어 친구 초대 미션의 진행도로 쓴다 (BEA-390). */
@Component
@RequiredArgsConstructor
public class InviteSuccessFriendInviteSignal implements FriendInviteSignal {

    private final InviteRepository invites;
    private final AppUserRepository users;
    private final FriendAccess friendAccess;

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> successCountOf(UUID userId) {
        return users.findById(userId)
                .filter(friendAccess::canEarnFriendMission)
                .map(user -> invites.countByInviterIdAndStatus(userId, Invite.Status.SUCCESS));
    }
}
