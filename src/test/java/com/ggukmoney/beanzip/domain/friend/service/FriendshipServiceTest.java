package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.entity.Friendship;
import com.ggukmoney.beanzip.domain.friend.repository.FriendshipRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FriendshipServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");
    // Java's UUID.compareTo is signed, so it puts "ffff…" before "0000…". PostgreSQL compares bytes.
    private static final UUID SMALL = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID LARGE = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");

    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendshipService service = new FriendshipService(friendships, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void storesThePairInPostgresUuidOrderWhicheverSideConnects() {
        assertThat(LARGE.compareTo(SMALL)).as("the Java order this must not follow").isNegative();
        when(friendships.insertIfAbsent(SMALL, LARGE, "INVITE_SIGNUP", NOW)).thenReturn(1).thenReturn(0);

        assertThat(service.connect(LARGE, SMALL, Friendship.Source.INVITE_SIGNUP)).isTrue();
        assertThat(service.connect(SMALL, LARGE, Friendship.Source.INVITE_SIGNUP)).as("already friends").isFalse();
    }

    @Test
    void looksThePairUpInTheSameOrder() {
        when(friendships.existsByUserLowAndUserHigh(SMALL, LARGE)).thenReturn(true);

        assertThat(service.areFriends(LARGE, SMALL)).isTrue();
        assertThat(service.areFriends(SMALL, LARGE)).isTrue();
        verify(friendships, org.mockito.Mockito.times(2)).existsByUserLowAndUserHigh(SMALL, LARGE);
    }

    @Test
    void aUserIsNeverTheirOwnFriend() {
        assertThat(service.areFriends(SMALL, SMALL)).isFalse();
        assertThatThrownBy(() -> service.connect(SMALL, SMALL, Friendship.Source.INVITE_LINK))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(friendships);
    }
}
