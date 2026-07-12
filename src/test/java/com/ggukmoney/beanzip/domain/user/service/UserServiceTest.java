package com.ggukmoney.beanzip.domain.user.service;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapService;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.user.dto.mapper.MemberMapper;
import com.ggukmoney.beanzip.domain.user.dto.request.UpdateMemberRequest;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberMeResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberUpdateResponse;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.common.ValidationFailureException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final KeycapService keycapService = mock(KeycapService.class);
    private final MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);
    private final UserService userService = new UserService(
            appUserRepository,
            pointAccountService,
            keycapService,
            memberMapper
    );

    @Test
    void getsCurrentMemberWithPointBalanceAndEquippedKeycap() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "Bean", "https://img");
        EquippedKeycapResponse equippedKeycap = new EquippedKeycapResponse(UUID.randomUUID(), "BASIC_001", "기본 키캡", null);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pointAccountService.getBalance(userId)).thenReturn(42L);
        when(keycapService.getEquippedKeycap(userId)).thenReturn(equippedKeycap);

        MemberMeResponse response = userService.getCurrentMember(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.equippedKeycap()).isEqualTo(equippedKeycap);
        assertThat(response.pointBalance()).isEqualTo(42L);
    }

    @Test
    void getsCurrentMemberWithZeroBalanceAndNoEquippedKeycap() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user(userId, "Bean", null)));
        when(pointAccountService.getBalance(userId)).thenReturn(0L);
        when(keycapService.getEquippedKeycap(userId)).thenReturn(null);

        MemberMeResponse response = userService.getCurrentMember(userId);

        assertThat(response.equippedKeycap()).isNull();
        assertThat(response.pointBalance()).isZero();
    }

    @Test
    void updatesOnlyNicknameAndNormalizesIt() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "Old", "https://old");
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appUserRepository.existsByNicknameNormalizedAndStatusAndIdNot("new", AppUser.Status.ACTIVE, userId))
                .thenReturn(false);
        when(appUserRepository.save(user)).thenReturn(user);

        MemberUpdateResponse response = userService.updateCurrentMember(
                userId,
                new UpdateMemberRequest(" New ", null)
        );

        assertThat(response.nickname()).isEqualTo("New");
        assertThat(response.profileImageUrl()).isEqualTo("https://old");
        assertThat(user.getNicknameNormalized()).isEqualTo("new");
    }

    @Test
    void updatesOnlyProfileImageUrl() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "Bean", "https://old");
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        MemberUpdateResponse response = userService.updateCurrentMember(
                userId,
                new UpdateMemberRequest(null, " https://new ")
        );

        assertThat(response.nickname()).isEqualTo("Bean");
        assertThat(response.profileImageUrl()).isEqualTo("https://new");
        verify(appUserRepository, never()).existsByNicknameNormalizedAndStatusAndIdNot(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsBlankNickname() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user(userId, "Bean", null)));

        assertThatThrownBy(() -> userService.updateCurrentMember(userId, new UpdateMemberRequest("   ", null)))
                .isInstanceOf(ValidationFailureException.class);
    }

    @Test
    void rejectsEmptyPatchRequest() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user(userId, "Bean", null)));

        assertThatThrownBy(() -> userService.updateCurrentMember(userId, new UpdateMemberRequest(null, null)))
                .isInstanceOf(ValidationFailureException.class);
    }

    @Test
    void rejectsDuplicateActiveNickname() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user(userId, "Bean", null)));
        when(appUserRepository.existsByNicknameNormalizedAndStatusAndIdNot("taken", AppUser.Status.ACTIVE, userId))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.updateCurrentMember(userId, new UpdateMemberRequest("Taken", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("NICKNAME_ALREADY_EXISTS");
    }

    @Test
    void rejectsWithdrawnUserLookupAndUpdate() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "Bean", null);
        user.withdraw();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getCurrentMember(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("ACCOUNT_WITHDRAWN");

        assertThatThrownBy(() -> userService.updateCurrentMember(userId, new UpdateMemberRequest("New", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("ACCOUNT_WITHDRAWN");
    }

    private static AppUser user(UUID userId, String nickname, String profileImageUrl) {
        AppUser user = AppUser.createActive(nickname, profileImageUrl);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
