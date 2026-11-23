package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.dto.response.FriendProfileResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteAcceptResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteAckResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteSignupResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteSuccessListResponse;
import com.ggukmoney.beanzip.domain.friend.entity.Friendship;
import com.ggukmoney.beanzip.domain.friend.entity.Invite;
import com.ggukmoney.beanzip.domain.friend.repository.CheerRepository;
import com.ggukmoney.beanzip.domain.friend.repository.InviteRepository;
import com.ggukmoney.beanzip.domain.keycap.entity.ShardLedger;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.service.FriendNotificationService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserRewardLock;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InviteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");

    private final InviteRepository invites = mock(InviteRepository.class);
    private final InviteCodeService inviteCodes = mock(InviteCodeService.class);
    private final FriendshipService friendships = mock(FriendshipService.class);
    private final KeycapBoxAccountService wallets = mock(KeycapBoxAccountService.class);
    private final UserRewardLock userRewardLock = mock(UserRewardLock.class);
    private final FriendNotificationService friendNotifications = mock(FriendNotificationService.class);
    private final FriendPolicyConfig policy = mock(FriendPolicyConfig.class);
    private final CheerRepository cheers = mock(CheerRepository.class);
    private final InviteService service = new InviteService(
            invites, inviteCodes, cheers, friendships, wallets, userRewardLock, friendNotifications,
            new FriendAccess(policy), policy, Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Asia/Seoul"));

    @Test
    void aVisitIsStoredAsPendingForTheOwnerOfTheCode() {
        UUID visitKey = UUID.randomUUID();
        AppUser inviter = user();
        when(policy.friendsEnabled()).thenReturn(true);
        when(inviteCodes.findInviter("K7P2QX9A")).thenReturn(Optional.of(inviter));

        service.recordVisit("K7P2QX9A", visitKey);

        verify(invites).insertPendingIfAbsent(any(UUID.class), eq(inviter.getId()), eq(visitKey), eq(NOW), eq(500));
    }

    @Test
    void aVisitWithAnUnknownCodeStoresNothing() {
        when(policy.friendsEnabled()).thenReturn(true);
        when(inviteCodes.findInviter("ZZZZZZZZ")).thenReturn(Optional.empty());

        service.recordVisit("ZZZZZZZZ", UUID.randomUUID());

        verifyNoInteractions(invites);
    }

    @Test
    void aVisitStoresNothingWhileTheFriendFeatureIsOff() {
        when(policy.friendsEnabled()).thenReturn(false);

        service.recordVisit("K7P2QX9A", UUID.randomUUID());

        verifyNoInteractions(inviteCodes, invites);
    }

    @Test
    void aNewSignupTurnsThePendingVisitIntoASuccessAndRewardsTheInvitee() {
        AppUser inviter = user();
        UUID invitee = UUID.randomUUID();
        UUID visitKey = UUID.randomUUID();
        Invite invite = invite(inviter.getId(), invitee);
        enabledWithReward(10);
        when(inviteCodes.findInviter("K7P2QX9A")).thenReturn(Optional.of(inviter));
        when(invites.markSuccessForVisit(any(UUID.class), eq(inviter.getId()), eq(invitee), eq(visitKey), eq(NOW)))
                .thenReturn(1);
        when(invites.findByInviteeId(invitee)).thenReturn(Optional.of(invite));

        Optional<InviteSignupResponse> result = service.linkOnLogin(invitee, true, "K7P2QX9A", visitKey.toString());

        assertThat(result).contains(new InviteSignupResponse(inviter.getId(), inviter.getNickname(), 10));
        verify(invites, never()).insertSuccess(any(), any(), any(), any());
        verify(friendships).connect(invitee, inviter.getId(), Friendship.Source.INVITE_SIGNUP);
        // The invitee's own row is locked before their wallet changes; the inviter's is never touched.
        InOrder order = inOrder(userRewardLock, wallets);
        order.verify(userRewardLock).acquire(invitee);
        order.verify(wallets).grantShards(
                invitee, 10, ShardLedger.Reason.INVITE_SUCCESS_INVITEE, invite.getPublicId().toString());
        verify(userRewardLock, never()).acquire(inviter.getId());
        verify(friendNotifications).enqueue(inviter.getId(), NotificationType.FRIEND_INVITE_SUCCESS);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "not-a-uuid"})
    void aSignupWithoutAUsableVisitKeyIsStoredAsItsOwnSuccess(String visitKey) {
        AppUser inviter = user();
        UUID invitee = UUID.randomUUID();
        enabledWithReward(10);
        when(inviteCodes.findInviter("K7P2QX9A")).thenReturn(Optional.of(inviter));
        when(invites.insertSuccess(any(UUID.class), eq(inviter.getId()), eq(invitee), eq(NOW))).thenReturn(1);
        when(invites.findByInviteeId(invitee)).thenReturn(Optional.of(invite(inviter.getId(), invitee)));

        assertThat(service.linkOnLogin(invitee, true, "K7P2QX9A", visitKey)).isPresent();

        verify(invites, never()).markSuccessForVisit(any(), any(), any(), any(), any());
    }

    @Test
    void aSignupWhoseVisitAlreadyBelongsToSomeoneElseIsStoredAsItsOwnSuccess() {
        AppUser inviter = user();
        UUID invitee = UUID.randomUUID();
        UUID visitKey = UUID.randomUUID();
        enabledWithReward(10);
        when(inviteCodes.findInviter("K7P2QX9A")).thenReturn(Optional.of(inviter));
        // Same phone, a second account: the visit row is already another person's success.
        when(invites.markSuccessForVisit(any(), any(), any(), any(), any())).thenReturn(0);
        when(invites.insertSuccess(any(UUID.class), eq(inviter.getId()), eq(invitee), eq(NOW))).thenReturn(1);
        when(invites.findByInviteeId(invitee)).thenReturn(Optional.of(invite(inviter.getId(), invitee)));

        assertThat(service.linkOnLogin(invitee, true, "K7P2QX9A", visitKey.toString())).isPresent();
    }

    @Test
    void aUserAlreadyRecordedAsInvitedGetsNothingASecondTime() {
        AppUser inviter = user();
        UUID invitee = UUID.randomUUID();
        enabledWithReward(10);
        when(inviteCodes.findInviter("K7P2QX9A")).thenReturn(Optional.of(inviter));
        when(invites.insertSuccess(any(), any(), any(), any())).thenReturn(0);

        assertThat(service.linkOnLogin(invitee, true, "K7P2QX9A", null)).isEmpty();

        verifyNoInteractions(friendships, wallets, userRewardLock, friendNotifications);
    }

    @Test
    void anExistingMemberOnlyBecomesAFriendAndTheInviterIsToldOnce() {
        AppUser inviter = user();
        UUID member = UUID.randomUUID();
        enabledWithReward(10);
        when(inviteCodes.findInviter("K7P2QX9A")).thenReturn(Optional.of(inviter));
        when(friendships.connect(member, inviter.getId(), Friendship.Source.INVITE_LINK))
                .thenReturn(true).thenReturn(false);

        assertThat(service.linkOnLogin(member, false, "K7P2QX9A", UUID.randomUUID().toString())).isEmpty();
        assertThat(service.linkOnLogin(member, false, "K7P2QX9A", UUID.randomUUID().toString())).isEmpty();

        // No reward and no success row; the pending visit stays pending. Already friends: no second push.
        verify(friendNotifications, times(1)).enqueue(inviter.getId(), NotificationType.FRIEND_INVITE_SUCCESS);
        verifyNoInteractions(invites, wallets, userRewardLock);
    }

    @Test
    void anInviteThatCannotCountIsIgnored() {
        AppUser self = user();
        enabledWithReward(10);
        when(inviteCodes.findInviter("ZZZZZZZZ")).thenReturn(Optional.empty());
        when(inviteCodes.findInviter("MYOWNCOD")).thenReturn(Optional.of(self));

        assertThat(service.linkOnLogin(UUID.randomUUID(), true, "ZZZZZZZZ", null)).as("unknown code").isEmpty();
        assertThat(service.linkOnLogin(self.getId(), false, "MYOWNCOD", null)).as("my own code").isEmpty();
        assertThat(service.linkOnLogin(UUID.randomUUID(), true, null, null)).as("no code").isEmpty();
        assertThat(service.linkOnLogin(UUID.randomUUID(), true, " ", null)).as("blank code").isEmpty();

        verifyNoInteractions(invites, friendships, wallets, userRewardLock, friendNotifications);
    }

    @Test
    void aLoginIgnoresTheInviteWhileTheFriendFeatureIsOff() {
        when(policy.friendsEnabled()).thenReturn(false);

        assertThat(service.linkOnLogin(UUID.randomUUID(), true, "K7P2QX9A", null)).isEmpty();

        verifyNoInteractions(inviteCodes, invites, friendships, wallets, friendNotifications);
    }

    @Test
    void aZeroRewardStillMakesTheInviteSucceedWithoutTouchingTheWallet() {
        AppUser inviter = user();
        UUID invitee = UUID.randomUUID();
        enabledWithReward(0);
        when(inviteCodes.findInviter("K7P2QX9A")).thenReturn(Optional.of(inviter));
        when(invites.insertSuccess(any(), any(), any(), any())).thenReturn(1);
        when(invites.findByInviteeId(invitee)).thenReturn(Optional.of(invite(inviter.getId(), invitee)));

        assertThat(service.linkOnLogin(invitee, true, "K7P2QX9A", null))
                .contains(new InviteSignupResponse(inviter.getId(), inviter.getNickname(), 0));

        // grantShards rejects a zero amount, so it must not be called at all.
        verifyNoInteractions(wallets, userRewardLock);
        verify(friendships).connect(invitee, inviter.getId(), Friendship.Source.INVITE_SIGNUP);
    }

    @Test
    void acceptingALinkMakesFriendsWithoutAnyReward() {
        AppUser inviter = user();
        UUID member = UUID.randomUUID();
        when(inviteCodes.findInviter("K7P2QX9A")).thenReturn(Optional.of(inviter));
        when(friendships.connect(member, inviter.getId(), Friendship.Source.INVITE_LINK))
                .thenReturn(true).thenReturn(false);

        InviteAcceptResponse first = service.accept(member, "K7P2QX9A");
        InviteAcceptResponse again = service.accept(member, "K7P2QX9A");

        assertThat(first).isEqualTo(new InviteAcceptResponse(
                new FriendProfileResponse(inviter.getId(), inviter.getNickname()), false));
        assertThat(again.alreadyFriends()).isTrue();
        verify(friendNotifications, times(1)).enqueue(inviter.getId(), NotificationType.FRIEND_INVITE_SUCCESS);
        verifyNoInteractions(invites, wallets, userRewardLock);
    }

    @Test
    void acceptingAnUnknownOrOwnCodeIsRefused() {
        AppUser self = user();
        when(inviteCodes.findInviter("ZZZZZZZZ")).thenReturn(Optional.empty());
        when(inviteCodes.findInviter("MYOWNCOD")).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.accept(UUID.randomUUID(), "ZZZZZZZZ"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("404 NOT_FOUND \"INVITE_CODE_NOT_FOUND\"");
        assertThatThrownBy(() -> service.accept(self.getId(), "MYOWNCOD"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("400 BAD_REQUEST \"INVITE_SELF\"");

        verifyNoInteractions(friendships, friendNotifications);
    }

    @Test
    void unreadSuccessesShowWhoJoinedAndWhetherIAlreadyCheeredThemToday() {
        UUID me = UUID.randomUUID();
        UUID cheered = UUID.randomUUID();
        UUID notYet = UUID.randomUUID();
        UUID firstInvite = UUID.randomUUID();
        UUID secondInvite = UUID.randomUUID();
        when(policy.inviteReward()).thenReturn(10);
        // 05:00 UTC is 14:00 in Korea, the same calendar day.
        when(cheers.findCheeredUserIds(me, LocalDate.parse("2026-11-24"))).thenReturn(List.of(cheered));
        when(invites.findUnreadSuccesses(me, PageRequest.of(0, 100))).thenReturn(List.of(
                unread(firstInvite, cheered, "이지우"), unread(secondInvite, notYet, null)));

        InviteSuccessListResponse response = service.unreadSuccesses(me);

        assertThat(response.shardsPerInvite()).isEqualTo(10);
        assertThat(response.invites()).containsExactly(
                new InviteSuccessListResponse.Item(firstInvite, new FriendProfileResponse(cheered, "이지우"), true),
                new InviteSuccessListResponse.Item(secondInvite, new FriendProfileResponse(notYet, null), false));
    }

    @Test
    void acknowledgingPaysTheInviterOncePerInvite() {
        AppUser me = user();
        Invite first = invite(me.getId(), UUID.randomUUID());
        Invite second = invite(me.getId(), UUID.randomUUID());
        List<UUID> ids = List.of(first.getPublicId(), second.getPublicId(), UUID.randomUUID());
        when(userRewardLock.acquire(me.getId())).thenReturn(me);
        when(policy.inviteReward()).thenReturn(10);
        when(invites.findAckable(me.getId(), ids)).thenReturn(List.of(first, second));
        when(wallets.getAvailableShardBalance(me.getId())).thenReturn(135);

        InviteAckResponse response = service.ackSuccesses(me.getId(), ids);

        assertThat(response).isEqualTo(new InviteAckResponse(20, 135));
        assertThat(first.getInviterAckedAt()).isEqualTo(NOW);
        assertThat(second.getInviterAckedAt()).isEqualTo(NOW);
        verify(wallets).grantShards(
                me.getId(), 10, ShardLedger.Reason.INVITE_SUCCESS_INVITER, first.getPublicId().toString());
        verify(wallets).grantShards(
                me.getId(), 10, ShardLedger.Reason.INVITE_SUCCESS_INVITER, second.getPublicId().toString());
    }

    @Test
    void acknowledgingNothingNewPaysNothing() {
        AppUser me = user();
        List<UUID> ids = List.of(UUID.randomUUID());
        when(userRewardLock.acquire(me.getId())).thenReturn(me);
        when(policy.inviteReward()).thenReturn(10);
        when(invites.findAckable(me.getId(), ids)).thenReturn(List.of());
        when(wallets.getAvailableShardBalance(me.getId())).thenReturn(125);

        assertThat(service.ackSuccesses(me.getId(), ids)).isEqualTo(new InviteAckResponse(0, 125));
        assertThat(service.ackSuccesses(me.getId(), List.of())).isEqualTo(new InviteAckResponse(0, 125));

        verify(wallets, never()).grantShards(any(), anyInt(), any(), any());
        // An empty id list is not sent to the database as an empty IN ().
        verify(invites, times(1)).findAckable(any(), any());
    }

    @Test
    void aSuspendedInviterSeesTheSuccessButIsNotPaid() {
        AppUser me = user();
        me.suspend();
        Invite invite = invite(me.getId(), UUID.randomUUID());
        when(userRewardLock.acquire(me.getId())).thenReturn(me);
        when(policy.inviteReward()).thenReturn(10);
        when(invites.findAckable(me.getId(), List.of(invite.getPublicId()))).thenReturn(List.of(invite));
        when(wallets.getAvailableShardBalance(me.getId())).thenReturn(40);

        assertThat(service.ackSuccesses(me.getId(), List.of(invite.getPublicId())))
                .isEqualTo(new InviteAckResponse(0, 40));

        // Marked as seen, so the modal does not come back on every launch.
        assertThat(invite.getInviterAckedAt()).isEqualTo(NOW);
        verify(wallets, never()).grantShards(any(), anyInt(), any(), any());
    }

    @Test
    void aWithdrawnAccountWhoseTokenStillWorksCannotCollectAnInviteReward() {
        AppUser me = user();
        me.withdraw();
        Invite invite = invite(me.getId(), UUID.randomUUID());
        when(userRewardLock.acquire(me.getId())).thenReturn(me);
        when(policy.inviteReward()).thenReturn(10);

        assertThatThrownBy(() -> service.ackSuccesses(me.getId(), List.of(invite.getPublicId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN \"ACCOUNT_WITHDRAWN\"");

        // Nothing is marked seen: the invite is still there if the account comes back.
        assertThat(invite.getInviterAckedAt()).isNull();
        verifyNoInteractions(invites, wallets);
    }

    private static InviteRepository.UnreadSuccess unread(UUID inviteId, UUID userId, String name) {
        return new InviteRepository.UnreadSuccess() {
            @Override
            public UUID getInviteId() {
                return inviteId;
            }

            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    private void enabledWithReward(int reward) {
        when(policy.friendsEnabled()).thenReturn(true);
        when(policy.inviteReward()).thenReturn(reward);
    }

    static Invite invite(UUID inviterId, UUID inviteeId) {
        Invite invite = BeanUtils.instantiateClass(Invite.class);
        ReflectionTestUtils.setField(invite, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(invite, "inviterId", inviterId);
        ReflectionTestUtils.setField(invite, "inviteeId", inviteeId);
        ReflectionTestUtils.setField(invite, "status", Invite.Status.SUCCESS);
        return invite;
    }

    static AppUser user() {
        AppUser user = AppUser.createActive("friend-" + UUID.randomUUID(), null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
