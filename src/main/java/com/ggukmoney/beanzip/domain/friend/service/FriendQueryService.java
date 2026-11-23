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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 친구 목록과 홈 요약 (BEA-390). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendQueryService {

    static final int SUMMARY_FACES = 3;

    private final FriendshipRepository friendships;
    private final AppUserRepository users;
    private final CheerRepository cheers;
    private final FriendTapReader tapReader;
    private final FriendPolicyConfig policy;
    private final Clock clock;
    private final ZoneId businessZoneId;

    public FriendListResponse list(UUID userId) {
        LocalDate today = today();
        List<AppUser> friends = visibleFriends(userId);
        Map<UUID, TapState> taps = tapReader.read(friends.stream().map(AppUser::getId).toList(), today);
        Set<UUID> cheeredToday = Set.copyOf(cheers.findCheeredUserIds(userId, today));

        List<FriendListResponse.Friend> items = friends.stream()
                .map(friend -> {
                    TapState state = taps.getOrDefault(friend.getId(), TapState.NONE);
                    return new FriendListResponse.Friend(
                            friend.getId(),
                            friend.getNickname(),
                            state.todayTapCount(),
                            state.lastTapDate(),
                            cheeredToday.contains(friend.getId()));
                })
                // 아직 응원하지 않은 친구 먼저, 그 안에서 오래 안 들어온 친구 먼저. 한 번도 누르지 않은 친구가 맨 앞이다.
                .sorted(Comparator.comparing(FriendListResponse.Friend::cheeredToday)
                        .thenComparing(
                                FriendListResponse.Friend::lastTapDate,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(friend -> friend.userId().toString()))
                .toList();
        int limit = policy.cheerDailyLimit();
        // 오늘 응원한 뒤 탈퇴한 친구도 하루 수에는 들어간다.
        return new FriendListResponse(limit, Math.max(0, limit - cheeredToday.size()), items);
    }

    public FriendSummaryResponse summary(UUID userId) {
        List<AppUser> friends = visibleFriends(userId);
        Map<UUID, Long> tapsToday =
                tapReader.todayTapCounts(friends.stream().map(AppUser::getId).toList(), today());
        List<AppUser> tappedToday = friends.stream()
                .filter(friend -> tapsToday.getOrDefault(friend.getId(), 0L) > 0)
                .sorted(Comparator
                        .comparingLong((AppUser friend) -> tapsToday.get(friend.getId())).reversed()
                        .thenComparing(friend -> friend.getId().toString()))
                .toList();
        return new FriendSummaryResponse(
                friends.size(),
                tappedToday.size(),
                tappedToday.stream()
                        .limit(SUMMARY_FACES)
                        .map(friend -> new FriendProfileResponse(friend.getId(), friend.getNickname()))
                        .toList(),
                cheers.countUnread(userId)
        );
    }

    /** 탈퇴한 친구는 관계를 지우지 않고 여기서만 뺀다. 재가입하면 다시 보인다. 정지된 친구는 보인다. */
    private List<AppUser> visibleFriends(UUID userId) {
        List<UUID> friendIds = friendships.findFriendIds(userId);
        if (friendIds.isEmpty()) {
            return List.of();
        }
        return users.findAllById(friendIds).stream()
                .filter(friend -> friend.getStatus() != AppUser.Status.WITHDRAWN)
                .toList();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), businessZoneId);
    }
}
