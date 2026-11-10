package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapRewardSelector;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.OnboardingRewardConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingDefaultRewardServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    private final OnboardingRewardConfig rewardConfig = mock(OnboardingRewardConfig.class);
    private final KeycapRepository keycapRepository = mock(KeycapRepository.class);
    private final KeycapRewardSelector keycapRewardSelector = mock(KeycapRewardSelector.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final UserKeycapRepository userKeycapRepository = mock(UserKeycapRepository.class);
    private final OnboardingDefaultRewardService service = new OnboardingDefaultRewardService(
            rewardConfig,
            keycapRepository,
            keycapRewardSelector,
            pointAccountService,
            pointLedgerService,
            userKeycapRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void grantsPolicyPointsEquipsMainKeycapAndAddsBonusKeycap() {
        UUID userId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", null), userId);
        Keycap mainKeycap = keycap(1L, "main", true);
        Keycap bonusKeycap = keycap(2L, "cheer", true);
        PointAccount account = PointAccount.createFor(user);
        givenPolicy("COMMON");
        when(keycapRepository.findByCode("main")).thenReturn(Optional.of(mainKeycap));
        when(keycapRepository.findByGradeAndAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(
                Keycap.Grade.COMMON, Keycap.AcquisitionType.BOX))
                .thenReturn(List.of(mainKeycap, bonusKeycap));
        when(keycapRewardSelector.select(List.of(bonusKeycap))).thenReturn(bonusKeycap);
        when(pointAccountService.credit(userId, 70)).thenReturn(account);
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(eq(userId), any())).thenReturn(Optional.empty());
        when(userKeycapRepository.save(any(UserKeycap.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean granted = service.grantForNewUser(user);

        assertThat(granted).isTrue();
        assertThat(user.isOnboardingRewardClaimed()).isTrue();
        assertThat(user.getOnboardingCompletedAt()).isEqualTo(NOW);
        verify(pointLedgerService).recordCredit(eq(account), eq(user), eq(70L), eq("ONBOARDING_REWARD"), any(UUID.class));
        ArgumentCaptor<UserKeycap> saved = ArgumentCaptor.forClass(UserKeycap.class);
        verify(userKeycapRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getKeycap()).isEqualTo(mainKeycap);
        assertThat(saved.getAllValues().get(0).isEquipped()).isTrue();
        assertThat(saved.getAllValues().get(1).getKeycap()).isEqualTo(bonusKeycap);
        assertThat(saved.getAllValues().get(1).isEquipped()).isFalse();
    }

    @Test
    void usesSameLedgerIdempotencyKeyForSameUser() {
        UUID userId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", null), userId);
        Keycap mainKeycap = keycap(1L, "main", true);
        Keycap bonusKeycap = keycap(2L, "cheer", true);
        givenPolicy("COMMON");
        when(keycapRepository.findByCode("main")).thenReturn(Optional.of(mainKeycap));
        when(keycapRepository.findByGradeAndAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(
                Keycap.Grade.COMMON, Keycap.AcquisitionType.BOX))
                .thenReturn(List.of(bonusKeycap));
        when(keycapRewardSelector.select(any())).thenReturn(bonusKeycap);
        when(pointAccountService.credit(userId, 70)).thenReturn(PointAccount.createFor(user));
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(eq(userId), any())).thenReturn(Optional.empty());
        when(userKeycapRepository.save(any(UserKeycap.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.grantForNewUser(user);
        service.grantForNewUser(user);

        ArgumentCaptor<UUID> keys = ArgumentCaptor.forClass(UUID.class);
        verify(pointLedgerService, times(2)).recordCredit(any(), any(), anyLong(), any(), keys.capture());
        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
    }

    @Test
    void skipsRewardWithoutWritesWhenPolicyIsUnavailable() {
        AppUser user = withId(AppUser.createActive("Bean", null), UUID.randomUUID());
        when(rewardConfig.resolve()).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_REWARD_NOT_AVAILABLE"));

        assertThat(service.grantForNewUser(user)).isFalse();
        assertNoRewardWritten(user);
    }

    @Test
    void skipsRewardWhenMainKeycapIsInactive() {
        AppUser user = withId(AppUser.createActive("Bean", null), UUID.randomUUID());
        givenPolicy("COMMON");
        when(keycapRepository.findByCode("main")).thenReturn(Optional.of(keycap(1L, "main", false)));

        assertThat(service.grantForNewUser(user)).isFalse();
        assertNoRewardWritten(user);
    }

    @Test
    void skipsRewardWhenBonusGradeIsUnknownOrHasNoCandidates() {
        AppUser user = withId(AppUser.createActive("Bean", null), UUID.randomUUID());
        Keycap mainKeycap = keycap(1L, "main", true);
        when(keycapRepository.findByCode("main")).thenReturn(Optional.of(mainKeycap));

        givenPolicy("MYTHIC");
        assertThat(service.grantForNewUser(user)).isFalse();

        givenPolicy("COMMON");
        when(keycapRepository.findByGradeAndAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(
                Keycap.Grade.COMMON, Keycap.AcquisitionType.BOX))
                .thenReturn(List.of(mainKeycap));
        assertThat(service.grantForNewUser(user)).isFalse();
        assertNoRewardWritten(user);
    }

    private void givenPolicy(String bonusGrade) {
        when(rewardConfig.resolve()).thenReturn(new OnboardingRewardConfig.OnboardingRewardPolicy(
                "main", bonusGrade, 70, Duration.ofSeconds(900), 45));
    }

    private void assertNoRewardWritten(AppUser user) {
        assertThat(user.isOnboardingRewardClaimed()).isFalse();
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(pointLedgerService, never()).recordCredit(any(), any(), anyLong(), any(), any());
        verify(userKeycapRepository, never()).save(any());
    }

    private static Keycap keycap(Long id, String code, boolean active) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "id", id);
        ReflectionTestUtils.setField(keycap, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(keycap, "code", code);
        ReflectionTestUtils.setField(keycap, "name", code);
        ReflectionTestUtils.setField(keycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(keycap, "requiredShardCount", 3);
        ReflectionTestUtils.setField(keycap, "active", active);
        return keycap;
    }

    private static AppUser withId(AppUser user, UUID userId) {
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create test entity " + type.getSimpleName(), exception);
        }
    }
}
