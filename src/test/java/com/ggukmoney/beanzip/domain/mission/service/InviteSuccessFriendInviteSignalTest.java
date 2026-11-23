package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.friend.entity.Invite;
import com.ggukmoney.beanzip.domain.friend.repository.InviteRepository;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InviteSuccessFriendInviteSignalTest {

    private final InviteRepository invites = mock(InviteRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final FriendPolicyConfig policy = mock(FriendPolicyConfig.class);
    private final InviteSuccessFriendInviteSignal signal =
            new InviteSuccessFriendInviteSignal(invites, users, new FriendAccess(policy));

    private final UUID userId = UUID.randomUUID();
    private final AppUser user = AppUser.createActive("김민지", null);

    @Test
    void countsOnlySignupsThatSucceededThroughMyLink() {
        when(policy.friendsEnabled()).thenReturn(true);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(invites.countByInviterIdAndStatus(userId, Invite.Status.SUCCESS)).thenReturn(4L);

        assertThat(signal.successCountOf(userId)).contains(4L);
    }

    @Test
    void hasNoValueWhileTheFriendFeatureIsOffOrTheUserIsSuspended() {
        when(users.findById(userId)).thenReturn(Optional.of(user));

        when(policy.friendsEnabled()).thenReturn(false);
        assertThat(signal.successCountOf(userId)).as("feature off").isEmpty();

        when(policy.friendsEnabled()).thenReturn(true);
        user.suspend();
        assertThat(signal.successCountOf(userId)).as("suspended").isEmpty();

        // An empty value hides the mission; it is not the same as zero invites.
        verifyNoInteractions(invites);
    }
}
