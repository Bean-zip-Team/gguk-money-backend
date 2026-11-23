package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.dto.response.CheerAckResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerReplyResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerSendResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerUnreadListResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendProfileResponse;
import com.ggukmoney.beanzip.domain.friend.entity.Cheer;
import com.ggukmoney.beanzip.domain.friend.repository.CheerRepository;
import com.ggukmoney.beanzip.domain.friend.service.FriendTapReader.TapState;
import com.ggukmoney.beanzip.domain.keycap.entity.ShardLedger;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.service.FriendNotificationService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.domain.user.service.UserRewardLock;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CheerServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-11-24 00:30 KST. In UTC it is still the 23rd, so a date taken from the wrong zone shows up.
    private static final Instant NOW = Instant.parse("2026-11-23T15:30:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-24");

    private final CheerRepository cheers = mock(CheerRepository.class);
    private final FriendshipService friendships = mock(FriendshipService.class);
    private final FriendTapReader tapReader = mock(FriendTapReader.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final UserRewardLock userRewardLock = mock(UserRewardLock.class);
    private final FriendPolicyConfig policy = mock(FriendPolicyConfig.class);
    private final KeycapBoxAccountService wallets = mock(KeycapBoxAccountService.class);
    private final FriendNotificationService friendNotifications = mock(FriendNotificationService.class);
    private final CheerService service = new CheerService(
            cheers, friendships, tapReader, users, userRewardLock, new FriendAccess(policy), wallets,
            friendNotifications, policy, Clock.fixed(NOW, SEOUL), SEOUL);

    private final AppUser sender = InviteServiceTest.user();
    private final AppUser receiver = InviteServiceTest.user();

    @BeforeEach
    void friendsWithDefaultPolicy() {
        when(userRewardLock.acquire(sender.getId())).thenReturn(sender);
        when(friendships.areFriends(sender.getId(), receiver.getId())).thenReturn(true);
        when(users.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(tapReader.read(List.of(receiver.getId()), TODAY))
                .thenReturn(Map.of(receiver.getId(), new TapState(120, TODAY)));
        when(cheers.save(any(Cheer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(policy.cheerDailyLimit()).thenReturn(10);
        when(policy.cheerSendFirstReward()).thenReturn(1);
        when(policy.cheerClapThreshold()).thenReturn(1000);
        when(policy.cheerGiftLapsedDays()).thenReturn(3);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
            // receiver cheered me today, receiver's taps today, receiver's last tap date -> kind
            "true , 1500, 2026-11-24, RETURN",
            "true ,    0, 2026-11-20, RETURN",
            "false,    0, 2026-11-21, GIFT",
            "false,    0, 2026-11-01, GIFT",
            "false,    0, 2026-11-22, DEFAULT",
            "false, 1000, 2026-11-24, CLAP",
            "false,  999, 2026-11-24, DEFAULT",
            "false,    0, -         , DEFAULT",
    })
    void theKindFollowsTheReceiversSituation(boolean cheeredMe, long tapsToday, LocalDate lastTapDate, Cheer.Kind kind) {
        assertThat(CheerService.kindOf(cheeredMe, new TapState(tapsToday, lastTapDate), TODAY, 3, 1000))
                .isEqualTo(kind);
    }

    @Test
    void theFirstCheerOfTheDayAlsoRewardsTheSender() {
        when(cheers.countByFromUserIdAndCheerDate(sender.getId(), TODAY)).thenReturn(0L);

        CheerSendResponse response = service.send(sender.getId(), receiver.getId());

        ArgumentCaptor<Cheer> saved = ArgumentCaptor.forClass(Cheer.class);
        verify(cheers).save(saved.capture());
        assertThat(saved.getValue().getCheerDate()).as("the Korean date, not the UTC one").isEqualTo(TODAY);
        assertThat(saved.getValue().getKind()).isEqualTo(Cheer.Kind.DEFAULT);
        assertThat(saved.getValue().getTapCountSnapshot()).isEqualTo(120);
        assertThat(saved.getValue().isRead()).isFalse();
        assertThat(response).isEqualTo(new CheerSendResponse(saved.getValue().getPublicId(), true, 1, 9));
        verify(wallets).grantShards(
                sender.getId(), 1, ShardLedger.Reason.CHEER_SENT_FIRST, saved.getValue().getPublicId().toString());
        verify(friendNotifications).enqueue(receiver.getId(), NotificationType.FRIEND_CHEER);
        // The receiver is paid when they confirm, in their own request. Their row is never locked here.
        verify(userRewardLock, never()).acquire(receiver.getId());
    }

    @Test
    void laterCheersTheSameDayDoNotRewardTheSender() {
        when(cheers.countByFromUserIdAndCheerDate(sender.getId(), TODAY)).thenReturn(3L);

        CheerSendResponse response = service.send(sender.getId(), receiver.getId());

        assertThat(response.firstOfDay()).isFalse();
        assertThat(response.shardsGranted()).isZero();
        assertThat(response.cheersLeft()).isEqualTo(6);
        verifyNoInteractions(wallets);
        verify(friendNotifications).enqueue(receiver.getId(), NotificationType.FRIEND_CHEER);
    }

    @Test
    void aZeroFirstCheerRewardLeavesTheWalletAlone() {
        when(policy.cheerSendFirstReward()).thenReturn(0);

        CheerSendResponse response = service.send(sender.getId(), receiver.getId());

        assertThat(response.firstOfDay()).isTrue();
        assertThat(response.shardsGranted()).isZero();
        verifyNoInteractions(wallets);
    }

    @Test
    void aSuspendedSenderIsRefusedBeforeAnythingIsCounted() {
        sender.suspend();

        assertRefused(receiver.getId(), "403 FORBIDDEN \"ACCOUNT_SUSPENDED\"");

        verifyNoInteractions(cheers, friendships);
    }

    @Test
    void onlyAFriendWhoHasNotWithdrawnCanBeCheered() {
        UUID stranger = UUID.randomUUID();
        AppUser withdrawn = InviteServiceTest.user();
        withdrawn.withdraw();
        when(friendships.areFriends(sender.getId(), withdrawn.getId())).thenReturn(true);
        when(users.findById(withdrawn.getId())).thenReturn(Optional.of(withdrawn));

        assertRefused(stranger, "403 FORBIDDEN \"NOT_FRIENDS\"");
        assertRefused(withdrawn.getId(), "403 FORBIDDEN \"NOT_FRIENDS\"");
        assertRefused(sender.getId(), "403 FORBIDDEN \"NOT_FRIENDS\"");

        verify(cheers, never()).save(any());
    }

    @Test
    void aSuspendedFriendCanStillBeCheered() {
        receiver.suspend();

        assertThat(service.send(sender.getId(), receiver.getId()).cheerId()).isNotNull();
    }

    @Test
    void aRepeatedCheerIsReportedAsAlreadySentEvenAfterTheDailyLimitIsUsedUp() {
        when(cheers.countByFromUserIdAndCheerDate(sender.getId(), TODAY)).thenReturn(10L);
        when(cheers.existsByFromUserIdAndToUserIdAndCheerDate(sender.getId(), receiver.getId(), TODAY))
                .thenReturn(true);

        // The app turns the button into "done" on this code, so a retry of the tenth cheer must not say "limit".
        assertRefused(receiver.getId(), "409 CONFLICT \"CHEER_ALREADY_SENT_TODAY\"");
    }

    @Test
    void theEleventhFriendOfTheDayIsRefused() {
        when(cheers.countByFromUserIdAndCheerDate(sender.getId(), TODAY)).thenReturn(10L);

        assertRefused(receiver.getId(), "429 TOO_MANY_REQUESTS \"CHEER_DAILY_LIMIT_EXCEEDED\"");
    }

    @Test
    void confirmingPaysThreePerCheerUntilTheDailyCapAndMarksTheRestReadForNothing() {
        Cheer first = received();
        Cheer second = received();
        Cheer third = received();
        List<UUID> ids = List.of(first.getPublicId(), second.getPublicId(), third.getPublicId());
        when(policy.cheerReceiveReward()).thenReturn(3);
        when(policy.cheerReceiveDailyCap()).thenReturn(30);
        when(cheers.findAckable(sender.getId(), ids)).thenReturn(List.of(first, second, third));
        // 24 already confirmed today (Korean day: 11/23 15:00Z to 11/24 15:00Z), so two more fit.
        when(cheers.sumRewardsReadBetween(
                sender.getId(), Instant.parse("2026-11-23T15:00:00Z"), Instant.parse("2026-11-24T15:00:00Z")))
                .thenReturn(24L);
        when(wallets.getAvailableShardBalance(sender.getId())).thenReturn(138);

        CheerAckResponse response = service.ack(sender.getId(), ids);

        assertThat(response).isEqualTo(new CheerAckResponse(6, true, 138));
        assertThat(List.of(first, second, third)).allMatch(Cheer::isRead);
        assertThat(List.of(first.getRewardShards(), second.getRewardShards(), third.getRewardShards()))
                .containsExactly(3, 3, 0);
        verify(wallets).grantShards(sender.getId(), 3, ShardLedger.Reason.CHEER_RECEIVED, first.getPublicId().toString());
        verify(wallets).grantShards(sender.getId(), 3, ShardLedger.Reason.CHEER_RECEIVED, second.getPublicId().toString());
        verify(wallets, never()).grantShards(sender.getId(), 3, ShardLedger.Reason.CHEER_RECEIVED, third.getPublicId().toString());
    }

    @Test
    void aCheerThatDoesNotFitUnderTheCapPaysNothingRatherThanAPart() {
        Cheer cheer = received();
        when(policy.cheerReceiveReward()).thenReturn(4);
        when(policy.cheerReceiveDailyCap()).thenReturn(30);
        when(cheers.findAckable(sender.getId(), List.of(cheer.getPublicId()))).thenReturn(List.of(cheer));
        when(cheers.sumRewardsReadBetween(any(), any(), any())).thenReturn(28L);

        CheerAckResponse response = service.ack(sender.getId(), List.of(cheer.getPublicId()));

        assertThat(response.shardsGranted()).isZero();
        assertThat(response.capped()).isTrue();
        assertThat(cheer.getRewardShards()).isZero();
    }

    @Test
    void aSuspendedReceiverConfirmsWithoutBeingPaidAndIsNotToldTheCapWasHit() {
        sender.suspend();
        Cheer cheer = received();
        when(policy.cheerReceiveReward()).thenReturn(3);
        when(policy.cheerReceiveDailyCap()).thenReturn(30);
        when(cheers.findAckable(sender.getId(), List.of(cheer.getPublicId()))).thenReturn(List.of(cheer));

        CheerAckResponse response = service.ack(sender.getId(), List.of(cheer.getPublicId()));

        assertThat(response.shardsGranted()).isZero();
        assertThat(response.capped()).isFalse();
        assertThat(cheer.isRead()).isTrue();
        verify(wallets, never()).grantShards(any(), anyInt(), any(), anyString());
    }

    @Test
    void confirmingNothingNewPaysNothing() {
        when(policy.cheerReceiveReward()).thenReturn(3);
        when(policy.cheerReceiveDailyCap()).thenReturn(30);
        when(cheers.findAckable(any(), any())).thenReturn(List.of());
        when(wallets.getAvailableShardBalance(sender.getId())).thenReturn(40);

        assertThat(service.ack(sender.getId(), List.of(UUID.randomUUID())))
                .isEqualTo(new CheerAckResponse(0, false, 40));
        assertThat(service.ack(sender.getId(), List.of())).isEqualTo(new CheerAckResponse(0, false, 40));

        verify(cheers, org.mockito.Mockito.times(1)).findAckable(any(), any());
    }

    @Test
    void unreadCheersCarryWhoSentThemAndTheirKind() {
        UUID cheerId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        Instant sentAt = Instant.parse("2026-11-24T01:12:00Z");
        when(cheers.findUnread(sender.getId(), PageRequest.of(0, 100)))
                .thenReturn(List.of(unread(cheerId, from, "박준호", Cheer.Kind.CLAP, 1240, sentAt)));

        assertThat(service.unread(sender.getId()).cheers()).containsExactly(new CheerUnreadListResponse.Item(
                cheerId, new FriendProfileResponse(from, "박준호"), Cheer.Kind.CLAP, 1240, sentAt));
    }

    @Test
    void replyingSendsToEachSenderOnceInOrderAndStopsAtTheDailyLimit() {
        // Chosen so that id order differs from the order the cheers arrived in.
        AppUser second = friend("00000000-0000-4000-8000-000000000002");
        AppUser first = friend("ffffffff-ffff-4fff-bfff-fffffffffff1");
        AppUser overLimit = friend("77777777-7777-4777-8777-777777777777");
        UUID stranger = UUID.randomUUID();
        List<UUID> cheerIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(policy.cheerDailyLimit()).thenReturn(2);
        when(cheers.countByFromUserIdAndCheerDate(sender.getId(), TODAY)).thenReturn(0L);
        when(cheers.findSenderIds(sender.getId(), cheerIds)).thenReturn(
                List.of(first.getId(), stranger, second.getId(), first.getId(), overLimit.getId()));

        CheerReplyResponse response = service.reply(sender.getId(), cheerIds);

        assertThat(response.results()).containsExactly(
                new CheerReplyResponse.Result(first.getId(), CheerReplyResponse.Outcome.SENT),
                new CheerReplyResponse.Result(stranger, CheerReplyResponse.Outcome.NOT_FRIENDS),
                new CheerReplyResponse.Result(second.getId(), CheerReplyResponse.Outcome.SENT),
                new CheerReplyResponse.Result(overLimit.getId(), CheerReplyResponse.Outcome.DAILY_LIMIT));
        assertThat(response.firstOfDay()).isTrue();
        assertThat(response.shardsGranted()).isEqualTo(1);
        assertThat(response.cheersLeft()).isZero();
        // One first-cheer reward for the whole request, tied to the first cheer that went out.
        ArgumentCaptor<Cheer> saved = ArgumentCaptor.forClass(Cheer.class);
        verify(cheers, org.mockito.Mockito.times(2)).save(saved.capture());
        verify(wallets).grantShards(sender.getId(), 1, ShardLedger.Reason.CHEER_SENT_FIRST,
                saved.getAllValues().getFirst().getPublicId().toString());
        // Pushes are queued in receiver-id order, not arrival order, so two repliers cannot wait on each other.
        InOrder order = inOrder(friendNotifications);
        order.verify(friendNotifications).enqueue(second.getId(), NotificationType.FRIEND_CHEER);
        order.verify(friendNotifications).enqueue(first.getId(), NotificationType.FRIEND_CHEER);
        order.verifyNoMoreInteractions();
    }

    @Test
    void replyingWhenEveryoneWasAlreadyCheeredTodaySendsNothing() {
        List<UUID> cheerIds = List.of(UUID.randomUUID());
        when(cheers.countByFromUserIdAndCheerDate(sender.getId(), TODAY)).thenReturn(1L);
        when(cheers.findSenderIds(sender.getId(), cheerIds)).thenReturn(List.of(receiver.getId()));
        when(cheers.existsByFromUserIdAndToUserIdAndCheerDate(sender.getId(), receiver.getId(), TODAY))
                .thenReturn(true);

        CheerReplyResponse response = service.reply(sender.getId(), cheerIds);

        assertThat(response).isEqualTo(new CheerReplyResponse(
                List.of(new CheerReplyResponse.Result(receiver.getId(), CheerReplyResponse.Outcome.ALREADY_SENT_TODAY)),
                false, 0, 9));
        verify(cheers, never()).save(any());
        verifyNoInteractions(wallets, friendNotifications);
    }

    @Test
    void aSuspendedUserCannotReply() {
        sender.suspend();

        assertThatThrownBy(() -> service.reply(sender.getId(), List.of(UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN \"ACCOUNT_SUSPENDED\"");

        verifyNoInteractions(cheers, friendNotifications);
    }

    /** 보낸 사람 변수(sender)가 받은, 아직 확인하지 않은 응원. */
    private Cheer received() {
        return Cheer.send(UUID.randomUUID(), sender.getId(), TODAY, Cheer.Kind.DEFAULT, 0, NOW);
    }

    private AppUser friend(String id) {
        AppUser friend = InviteServiceTest.user();
        ReflectionTestUtils.setField(friend, "id", UUID.fromString(id));
        when(friendships.areFriends(sender.getId(), friend.getId())).thenReturn(true);
        when(users.findById(friend.getId())).thenReturn(Optional.of(friend));
        return friend;
    }

    private static CheerRepository.UnreadCheer unread(
            UUID cheerId, UUID from, String name, Cheer.Kind kind, long snapshot, Instant createdAt
    ) {
        return new CheerRepository.UnreadCheer() {
            @Override
            public UUID getCheerId() {
                return cheerId;
            }

            @Override
            public UUID getUserId() {
                return from;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Cheer.Kind getKind() {
                return kind;
            }

            @Override
            public long getTapCountSnapshot() {
                return snapshot;
            }

            @Override
            public Instant getCreatedAt() {
                return createdAt;
            }
        };
    }

    private void assertRefused(UUID toUserId, String message) {
        assertThatThrownBy(() -> service.send(sender.getId(), toUserId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage(message);
        verify(cheers, never()).save(any());
        verify(wallets, never()).grantShards(any(), anyInt(), any(), anyString());
        verifyNoInteractions(friendNotifications);
    }
}
