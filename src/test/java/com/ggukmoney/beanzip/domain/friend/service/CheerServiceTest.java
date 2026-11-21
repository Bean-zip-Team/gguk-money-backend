package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.dto.response.CheerSendResponse;
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

    private void assertRefused(UUID toUserId, String message) {
        assertThatThrownBy(() -> service.send(sender.getId(), toUserId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage(message);
        verify(cheers, never()).save(any());
        verify(wallets, never()).grantShards(any(), anyInt(), any(), anyString());
        verifyNoInteractions(friendNotifications);
    }
}
