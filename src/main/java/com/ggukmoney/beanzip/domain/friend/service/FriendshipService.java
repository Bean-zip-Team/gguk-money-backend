package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.entity.Friendship;
import com.ggukmoney.beanzip.domain.friend.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 친구 관계를 맺고 확인한다 (BEA-390). */
@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendships;
    private final Clock clock;

    /**
     * 두 사람을 친구로 맺는다. 상대의 행을 잠그지 않고 한 행을 넣기만 한다.
     *
     * @return 새로 맺었으면 true, 이미 친구였으면 false
     */
    @Transactional
    public boolean connect(UUID userId, UUID otherUserId, Friendship.Source source) {
        if (userId.equals(otherUserId)) {
            throw new IllegalArgumentException("a user cannot befriend themselves");
        }
        return friendships.insertIfAbsent(
                low(userId, otherUserId), high(userId, otherUserId), source.name(), clock.instant()) == 1;
    }

    @Transactional(readOnly = true)
    public boolean areFriends(UUID userId, UUID otherUserId) {
        return !userId.equals(otherUserId)
                && friendships.existsByUserLowAndUserHigh(low(userId, otherUserId), high(userId, otherUserId));
    }

    // 소문자 16진수 문자열의 사전 순서는 PostgreSQL 의 uuid 비교(바이트 순서)와 같다.
    static UUID low(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? a : b;
    }

    static UUID high(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? b : a;
    }
}
