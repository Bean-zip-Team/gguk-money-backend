package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.entity.InviteCode;
import com.ggukmoney.beanzip.domain.friend.repository.InviteCodeRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteCodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");

    private final InviteCodeRepository codes = mock(InviteCodeRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final InviteCodeService service =
            new InviteCodeService(codes, users, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void returnsTheExistingCodeWithoutIssuingAnother() {
        UUID userId = UUID.randomUUID();
        when(codes.findById(userId)).thenReturn(Optional.of(code(userId, "K7P2QX9A")));

        assertThat(service.getOrCreate(userId)).isEqualTo("K7P2QX9A");

        verify(codes, never()).insertIfAbsent(any(), anyString(), any());
    }

    @Test
    void issuesAnEightCharacterCodeOfUppercaseLettersAndDigits() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<String> issued = ArgumentCaptor.forClass(String.class);
        when(codes.findById(userId)).thenReturn(Optional.empty());
        when(codes.insertIfAbsent(eq(userId), issued.capture(), eq(NOW))).thenAnswer(invocation -> {
            when(codes.findById(userId)).thenReturn(Optional.of(code(userId, invocation.getArgument(1))));
            return 1;
        });

        String code = service.getOrCreate(userId);

        assertThat(code).isEqualTo(issued.getValue()).matches("[2-9A-HJKMNP-Z]{8}");
    }

    @Test
    void triesANewCodeWhenTheFirstOneIsAlreadyTaken() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<String> issued = ArgumentCaptor.forClass(String.class);
        when(codes.findById(userId)).thenReturn(Optional.empty());
        // The first insert collides with someone else's code and writes nothing.
        when(codes.insertIfAbsent(eq(userId), issued.capture(), eq(NOW))).thenReturn(0).thenAnswer(invocation -> {
            when(codes.findById(userId)).thenReturn(Optional.of(code(userId, invocation.getArgument(1))));
            return 1;
        });

        String code = service.getOrCreate(userId);

        verify(codes, times(2)).insertIfAbsent(eq(userId), anyString(), eq(NOW));
        assertThat(code).isEqualTo(issued.getAllValues().get(1));
    }

    @Test
    void findsTheInviterIgnoringCaseAndSurroundingSpaces() {
        AppUser inviter = AppUser.createActive("김민지", null);
        UUID inviterId = UUID.randomUUID();
        when(codes.findByCode("K7P2QX9A")).thenReturn(Optional.of(code(inviterId, "K7P2QX9A")));
        when(users.findById(inviterId)).thenReturn(Optional.of(inviter));

        assertThat(service.findInviter(" k7p2qx9a ")).containsSame(inviter);
    }

    @Test
    void aSuspendedInviterStillCountsButAWithdrawnOneDoesNot() {
        AppUser suspended = AppUser.createActive("정지", null);
        suspended.suspend();
        AppUser withdrawn = AppUser.createActive("탈퇴", null);
        withdrawn.withdraw();
        UUID suspendedId = UUID.randomUUID();
        UUID withdrawnId = UUID.randomUUID();
        when(codes.findByCode("AAAAAAAA")).thenReturn(Optional.of(code(suspendedId, "AAAAAAAA")));
        when(codes.findByCode("BBBBBBBB")).thenReturn(Optional.of(code(withdrawnId, "BBBBBBBB")));
        when(users.findById(suspendedId)).thenReturn(Optional.of(suspended));
        when(users.findById(withdrawnId)).thenReturn(Optional.of(withdrawn));

        assertThat(service.findInviter("AAAAAAAA")).containsSame(suspended);
        assertThat(service.findInviter("BBBBBBBB")).isEmpty();
    }

    @Test
    void anUnknownOrMalformedCodeHasNoInviter() {
        when(codes.findByCode("ZZZZZZZZ")).thenReturn(Optional.empty());

        assertThat(service.findInviter("ZZZZZZZZ")).isEmpty();
        assertThat(service.findInviter(null)).isEmpty();
        assertThat(service.findInviter("")).isEmpty();
        assertThat(service.findInviter("TOO-LONG-CODE")).isEmpty();
        // Only an eight character code reaches the database.
        verify(codes, times(1)).findByCode(anyString());
    }

    private static InviteCode code(UUID userId, String value) {
        // The entity has no public constructor: rows are only ever created by the repository's insert.
        InviteCode code = BeanUtils.instantiateClass(InviteCode.class);
        ReflectionTestUtils.setField(code, "userId", userId);
        ReflectionTestUtils.setField(code, "code", value);
        ReflectionTestUtils.setField(code, "createdAt", NOW);
        return code;
    }
}
