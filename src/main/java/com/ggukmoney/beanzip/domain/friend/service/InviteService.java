package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.dto.response.InviteSignupResponse;
import com.ggukmoney.beanzip.domain.friend.entity.Friendship;
import com.ggukmoney.beanzip.domain.friend.entity.Invite;
import com.ggukmoney.beanzip.domain.friend.repository.InviteRepository;
import com.ggukmoney.beanzip.domain.keycap.entity.ShardLedger;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.service.FriendNotificationService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserRewardLock;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
    private final FriendshipService friendships;
    private final KeycapBoxAccountService wallets;
    private final UserRewardLock userRewardLock;
    private final FriendNotificationService friendNotifications;
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

    /**
     * 로그인에 딸려 온 초대 코드를 처리한다. 로그인 트랜잭션이 끝난 뒤에 부른다.
     *
     * <ul>
     *   <li>새로 가입한 사람: 초대 성공. 친구가 되고, 가입한 사람은 조각을 바로 받는다. 초대한 사람의
     *       조각은 초대한 사람이 성공 모달을 볼 때 준다. 초대한 사람이 정지되어 있어도 가입한 사람은 받는다.</li>
     *   <li>이미 회원인 사람(탈퇴 후 재가입 포함): 보상 없이 친구만 된다.</li>
     *   <li>없는 코드, 내 코드, 탈퇴한 초대자, 꺼진 기능: 아무 일도 하지 않는다.</li>
     * </ul>
     *
     * <p>{@code rawVisitKey} 는 문자열 그대로 받는다. 형식이 틀리면 방문 기록과 잇지 않을 뿐이다.
     *
     * @return 초대가 성공했을 때만 그 결과
     */
    @Transactional
    public Optional<InviteSignupResponse> linkOnLogin(UUID userId, boolean newUser, String inviteCode, String rawVisitKey) {
        if (!StringUtils.hasText(inviteCode) || !policy.friendsEnabled()) {
            return Optional.empty();
        }
        AppUser inviter = inviteCodes.findInviter(inviteCode).orElse(null);
        if (inviter == null || inviter.getId().equals(userId)) {
            return Optional.empty();
        }
        if (!newUser) {
            befriend(userId, inviter.getId());
            return Optional.empty();
        }

        Instant now = clock.instant();
        UUID visitKey = parseUuid(rawVisitKey);
        int written = visitKey == null
                ? 0
                : invites.markSuccessForVisit(UUID.randomUUID(), inviter.getId(), userId, visitKey, now);
        if (written == 0) {
            // 방문 기록과 잇지 못했다: visitKey 가 없거나, 같은 기기에서 다른 사람이 이미 그 방문으로 가입했다.
            written = invites.insertSuccess(UUID.randomUUID(), inviter.getId(), userId, now);
        }
        if (written == 0) {
            return Optional.empty();
        }
        Invite invite = invites.findByInviteeId(userId).orElseThrow();

        friendships.connect(userId, inviter.getId(), Friendship.Source.INVITE_SIGNUP);
        int reward = policy.inviteReward();
        if (reward > 0) {
            userRewardLock.acquire(userId);
            wallets.grantShards(
                    userId, reward, ShardLedger.Reason.INVITE_SUCCESS_INVITEE, invite.getPublicId().toString());
        }
        friendNotifications.enqueue(inviter.getId(), NotificationType.FRIEND_INVITE_SUCCESS);
        return Optional.of(new InviteSignupResponse(inviter.getId(), inviter.getNickname(), reward));
    }

    /**
     * 이미 회원인 사람이 초대 링크로 들어왔다. 보상 없이 친구만 맺는다.
     *
     * @return 새로 친구가 됐으면 true. 이때만 초대한 사람에게 '친구 연결' 푸시가 간다
     */
    boolean befriend(UUID userId, UUID inviterId) {
        boolean connected = friendships.connect(userId, inviterId, Friendship.Source.INVITE_LINK);
        if (connected) {
            friendNotifications.enqueue(inviterId, NotificationType.FRIEND_INVITE_SUCCESS);
        }
        return connected;
    }

    private static UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
