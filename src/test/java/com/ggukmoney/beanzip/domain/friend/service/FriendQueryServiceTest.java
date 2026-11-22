package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.dto.response.FriendListResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendProfileResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendSummaryResponse;
import com.ggukmoney.beanzip.domain.friend.repository.CheerRepository;
import com.ggukmoney.beanzip.domain.friend.repository.FriendshipRepository;
import com.ggukmoney.beanzip.domain.friend.service.FriendTapReader.TapState;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FriendQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-11-24 00:30 in Korea, still the 23rd in UTC.
    private static final Instant NOW = Instant.parse("2026-11-23T15:30:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-24");

    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CheerRepository cheers = mock(CheerRepository.class);
    private final FriendTapReader tapReader = mock(FriendTapReader.class);
    private final FriendPolicyConfig policy = mock(FriendPolicyConfig.class);
    private final FriendQueryService service =
            new FriendQueryService(friendships, users, cheers, tapReader, policy, Clock.fixed(NOW, SEOUL), SEOUL);

    private final UUID me = UUID.randomUUID();
    private final List<AppUser> friends = new ArrayList<>();
    private final Map<UUID, TapState> taps = new HashMap<>();

    @Test
    void friendsNotYetCheeredComeFirstThenTheOnesAwayLongest() {
        AppUser cheered = friend("응원함", 0, TODAY.minusDays(9));
        AppUser activeToday = friend("오늘누름", 1240, TODAY);
        AppUser never = friend("한번도안누름", 0, null);
        AppUser awayThreeDays = friend("사흘전", 0, TODAY.minusDays(3));
        stubFriends();
        when(policy.cheerDailyLimit()).thenReturn(10);
        when(cheers.findCheeredUserIds(me, TODAY)).thenReturn(List.of(cheered.getId()));

        FriendListResponse response = service.list(me);

        assertThat(response.cheerDailyLimit()).isEqualTo(10);
        assertThat(response.cheersLeft()).isEqualTo(9);
        assertThat(response.friends()).containsExactly(
                new FriendListResponse.Friend(never.getId(), "한번도안누름", 0, null, false),
                new FriendListResponse.Friend(awayThreeDays.getId(), "사흘전", 0, TODAY.minusDays(3), false),
                new FriendListResponse.Friend(activeToday.getId(), "오늘누름", 1240, TODAY, false),
                new FriendListResponse.Friend(cheered.getId(), "응원함", 0, TODAY.minusDays(9), true));
    }

    @Test
    void aWithdrawnFriendIsHiddenButACheerSentToThemTodayStillCounts() {
        AppUser withdrawn = friend("탈퇴", 500, TODAY);
        withdrawn.withdraw();
        AppUser suspended = friend("정지", 10, TODAY);
        suspended.suspend();
        stubFriends();
        when(policy.cheerDailyLimit()).thenReturn(10);
        when(cheers.findCheeredUserIds(me, TODAY)).thenReturn(List.of(withdrawn.getId()));

        FriendListResponse response = service.list(me);

        assertThat(response.friends()).extracting(FriendListResponse.Friend::name).containsExactly("정지");
        assertThat(response.cheersLeft()).isEqualTo(9);
    }

    @Test
    void cheersLeftNeverGoesBelowZeroWhenTheLimitIsLoweredDuringTheDay() {
        AppUser first = friend("하나", 0, null);
        AppUser second = friend("둘", 0, null);
        stubFriends();
        when(policy.cheerDailyLimit()).thenReturn(1);
        when(cheers.findCheeredUserIds(me, TODAY)).thenReturn(List.of(first.getId(), second.getId()));

        assertThat(service.list(me).cheersLeft()).isZero();
    }

    @Test
    void theSummaryShowsTheThreeFriendsWhoTappedMostToday() {
        friend("조금", 3, TODAY);
        AppUser most = friend("제일많이", 5000, TODAY);
        friend("안누름", 0, TODAY.minusDays(1));
        AppUser some = friend("꽤", 900, TODAY);
        AppUser more = friend("많이", 1200, TODAY);
        AppUser withdrawn = friend("탈퇴", 9999, TODAY);
        withdrawn.withdraw();
        stubFriends();
        when(cheers.countUnread(me)).thenReturn(3L);

        FriendSummaryResponse response = service.summary(me);

        assertThat(response.friendCount()).isEqualTo(5);
        assertThat(response.todayTappedCount()).isEqualTo(4);
        assertThat(response.todayTappedFriends()).containsExactly(
                new FriendProfileResponse(most.getId(), "제일많이"),
                new FriendProfileResponse(more.getId(), "많이"),
                new FriendProfileResponse(some.getId(), "꽤"));
        assertThat(response.unreadCheerCount()).isEqualTo(3);
    }

    @Test
    void someoneWithNoFriendsGetsEmptyAnswers() {
        when(friendships.findFriendIds(me)).thenReturn(List.of());
        when(policy.cheerDailyLimit()).thenReturn(10);

        assertThat(service.list(me)).isEqualTo(new FriendListResponse(10, 10, List.of()));
        assertThat(service.summary(me)).isEqualTo(new FriendSummaryResponse(0, 0, List.of(), 0));
    }

    private AppUser friend(String name, long tapsToday, LocalDate lastTapDate) {
        AppUser friend = InviteServiceTest.user();
        friend.recordLogin(name, null);
        friends.add(friend);
        taps.put(friend.getId(), new TapState(tapsToday, lastTapDate));
        return friend;
    }

    private void stubFriends() {
        List<UUID> ids = friends.stream().map(AppUser::getId).toList();
        when(friendships.findFriendIds(me)).thenReturn(ids);
        when(users.findAllById(ids)).thenReturn(friends);
        when(tapReader.read(anyCollection(), eq(TODAY))).thenReturn(taps);
    }
}
