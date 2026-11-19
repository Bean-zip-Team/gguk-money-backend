package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.repository.InviteRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InviteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");

    private final InviteRepository invites = mock(InviteRepository.class);
    private final InviteCodeService inviteCodes = mock(InviteCodeService.class);
    private final FriendPolicyConfig policy = mock(FriendPolicyConfig.class);
    private final InviteService service =
            new InviteService(invites, inviteCodes, policy, Clock.fixed(NOW, ZoneOffset.UTC));

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

    static AppUser user() {
        AppUser user = AppUser.createActive("friend-" + UUID.randomUUID(), null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
