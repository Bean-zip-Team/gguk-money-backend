package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.repository.InviteRepository;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 초대의 흐름: 링크 방문(PENDING) → 가입(SUCCESS) → 초대한 사람의 확인 (BEA-390). */
@Service
@RequiredArgsConstructor
public class InviteService {

    /**
     * 한 사람 앞으로 쌓일 수 있는 PENDING 초대 수. 방문 기록은 로그인 없이 부를 수 있어서, 임의의
     * visitKey 로 반복 호출해도 테이블이 끝없이 늘지 않게 한다. 성공한 초대는 세지 않는다.
     */
    static final int PENDING_CAP_PER_INVITER = 500;

    private final InviteRepository invites;
    private final InviteCodeService inviteCodes;
    private final FriendPolicyConfig policy;
    private final Clock clock;

    /**
     * 초대 링크로 앱이 열린 것을 남긴다.
     *
     * <p>없는 코드, 탈퇴한 초대자, 꺼진 기능, 이미 남긴 방문은 모두 조용히 넘어간다. 호출한 쪽에 차이를
     * 알려 주면 로그인 없이 초대 코드가 유효한지 알아낼 수 있다.
     */
    @Transactional
    public void recordVisit(String inviteCode, UUID visitKey) {
        if (!policy.friendsEnabled()) {
            return;
        }
        inviteCodes.findInviter(inviteCode).ifPresent(inviter -> invites.insertPendingIfAbsent(
                UUID.randomUUID(), inviter.getId(), visitKey, clock.instant(), PENDING_CAP_PER_INVITER));
    }
}
