package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.OnboardingRewardConfig;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapServiceTest {

    private final KeycapRepository keycapRepository = mock(KeycapRepository.class);
    private final UserKeycapRepository userKeycapRepository = mock(UserKeycapRepository.class);
    private final KeycapMapper keycapMapper = Mappers.getMapper(KeycapMapper.class);
    private final OnboardingRewardConfig onboardingRewardConfig = mock(OnboardingRewardConfig.class);
    private final KeycapService keycapService =
            new KeycapService(keycapRepository, userKeycapRepository, keycapMapper, onboardingRewardConfig);

    @Test
    void getsActiveBoxKeycapCatalogInRepositoryOrder() {
        Keycap first = keycap(UUID.randomUUID(), "BASIC_001", "Basic", Keycap.Grade.COMMON, 10, 1, true, 1);
        Keycap second = keycap(UUID.randomUUID(), "RARE_001", "Rare", Keycap.Grade.RARE, 20, 1, true, 2);
        when(keycapRepository.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX)).thenReturn(List.of(first, second));

        KeycapListResponse response = keycapService.getKeycaps();

        assertThat(response.keycaps()).hasSize(2);
        assertThat(response.keycaps().get(0).keycapId()).isEqualTo(first.getPublicId());
        assertThat(response.keycaps().get(0).code()).isEqualTo("BASIC_001");
        assertThat(response.keycaps().get(0).grade()).isEqualTo("COMMON");
        assertThat(response.keycaps().get(0).requiredShardCount()).isEqualTo(10);
        assertThat(response.keycaps().get(0).season()).isEqualTo(1);
        assertThat(response.keycaps().get(0).imageUrl()).isNull();
        assertThat(response.keycaps().get(0).soundUrl()).isNull();
        verify(keycapRepository).findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX);
    }

    @Test
    void getsEmptyKeycapCatalogWhenThereAreNoActiveBoxKeycaps() {
        when(keycapRepository.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX)).thenReturn(List.of());

        assertThat(keycapService.getKeycaps().keycaps()).isEmpty();
    }

    @Test
    void getsOnlyCurrentUsersKeycapsWithJoinedKeycapData() {
        UUID userId = UUID.randomUUID();
        UserKeycap levelEight = userKeycap(userId, UUID.randomUUID(), "BASIC_001", "Basic", 8, UserKeycap.Status.COMPLETED, false);
        UserKeycap completed = userKeycap(userId, UUID.randomUUID(), "RARE_001", "Rare", 20, UserKeycap.Status.COMPLETED, true);
        when(userKeycapRepository.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(userId))
                .thenReturn(List.of(levelEight, completed));

        MyKeycapListResponse response = keycapService.getMyKeycaps(userId);

        assertThat(response.keycaps()).hasSize(2);
        assertThat(response.keycaps().get(0).keycapId()).isEqualTo(levelEight.getKeycap().getPublicId());
        assertThat(response.keycaps().get(0).code()).isEqualTo("BASIC_001");
        assertThat(response.keycaps().get(0).level()).isEqualTo(8);
        assertThat(response.keycaps().get(0).status()).isEqualTo("COMPLETED");
        assertThat(response.keycaps().get(0).equipped()).isFalse();
        assertThat(response.keycaps().get(1).status()).isEqualTo("COMPLETED");
        assertThat(response.keycaps().get(1).equipped()).isTrue();
        verify(userKeycapRepository).findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(userId);
    }

    @Test
    void getsEmptyMyKeycapsWhenUserOwnsNoKeycaps() {
        UUID userId = UUID.randomUUID();
        when(userKeycapRepository.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(userId)).thenReturn(List.of());

        assertThat(keycapService.getMyKeycaps(userId).keycaps()).isEmpty();
    }

    @Test
    void returnsNullWhenUserHasNoEquippedKeycap() {
        UUID userId = UUID.randomUUID();
        when(userKeycapRepository.findByUserIdAndEquippedTrue(userId)).thenReturn(Optional.empty());

        assertThat(keycapService.getEquippedKeycap(userId)).isNull();
    }

    @Test
    void returnsEquippedKeycapSummaryWithoutInventingImageUrl() {
        UUID userId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        UserKeycap userKeycap = userKeycap(userId, keycapId, "BASIC_001", "Basic", 10, UserKeycap.Status.COMPLETED, true);
        when(userKeycapRepository.findByUserIdAndEquippedTrue(userId)).thenReturn(Optional.of(userKeycap));

        EquippedKeycapResponse response = keycapService.getEquippedKeycap(userId);

        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.code()).isEqualTo("BASIC_001");
        assertThat(response.name()).isEqualTo("Basic");
        assertThat(response.imageUrl()).isNull();
    }

    @Test
    void mapsEachUsersEquippedKeycapAndGivesTheOnboardingKeycapToUsersWithoutOne() {
        UUID equippedUserId = UUID.randomUUID();
        UUID neverClaimedUserId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        UUID defaultKeycapId = UUID.randomUUID();
        UserKeycap equipped = userKeycap(equippedUserId, keycapId, "BASIC_001", "Basic", 10, UserKeycap.Status.COMPLETED, true);
        when(userKeycapRepository.findEquippedWithKeycapByUserIds(List.of(equippedUserId, neverClaimedUserId)))
                .thenReturn(List.of(equipped));
        onboardingKeycap("main");
        when(keycapRepository.findByCode("main"))
                .thenReturn(Optional.of(keycap(defaultKeycapId, "main", "Main", Keycap.Grade.COMMON, 10, 1, true, 1)));

        java.util.Map<UUID, EquippedKeycapResponse> result =
                keycapService.getEquippedKeycaps(List.of(equippedUserId, neverClaimedUserId));

        assertThat(result.get(equippedUserId).keycapId()).isEqualTo(keycapId);
        assertThat(result.get(equippedUserId).code()).isEqualTo("BASIC_001");
        // 온보딩 보상을 받지 않은 유저다. 받았다면 자동으로 장착됐을 키캡을 보여 준다.
        assertThat(result.get(neverClaimedUserId).keycapId()).isEqualTo(defaultKeycapId);
        assertThat(result.get(neverClaimedUserId).code()).isEqualTo("main");
    }

    @Test
    void skipsTheDefaultLookupWhenEveryoneHasAKeycapEquipped() {
        UUID userId = UUID.randomUUID();
        when(userKeycapRepository.findEquippedWithKeycapByUserIds(List.of(userId))).thenReturn(List.of(
                userKeycap(userId, UUID.randomUUID(), "BASIC_001", "Basic", 10, UserKeycap.Status.COMPLETED, true)));

        assertThat(keycapService.getEquippedKeycaps(List.of(userId))).containsOnlyKeys(userId);
        verify(onboardingRewardConfig, never()).resolve();
    }

    @Test
    void leavesTheKeycapOutRatherThanFailingTheRankingWhenTheDefaultIsUnavailable() {
        UUID neverClaimedUserId = UUID.randomUUID();
        when(userKeycapRepository.findEquippedWithKeycapByUserIds(List.of(neverClaimedUserId))).thenReturn(List.of());
        when(onboardingRewardConfig.resolve())
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "ONBOARDING_REWARD_NOT_AVAILABLE"));

        // 온보딩 설정이 깨졌다고 랭킹 화면 전체가 409 로 막히면 안 된다. 그림 하나만 비운다.
        assertThat(keycapService.getEquippedKeycaps(List.of(neverClaimedUserId))).isEmpty();
    }

    @Test
    void leavesTheKeycapOutWhenTheDefaultKeycapIsRetired() {
        UUID neverClaimedUserId = UUID.randomUUID();
        when(userKeycapRepository.findEquippedWithKeycapByUserIds(List.of(neverClaimedUserId))).thenReturn(List.of());
        onboardingKeycap("main");
        when(keycapRepository.findByCode("main"))
                .thenReturn(Optional.of(keycap(UUID.randomUUID(), "main", "Main", Keycap.Grade.COMMON, 10, 1, false, 1)));

        assertThat(keycapService.getEquippedKeycaps(List.of(neverClaimedUserId))).isEmpty();
    }

    @Test
    void asksNothingForAnEmptyRanking() {
        assertThat(keycapService.getEquippedKeycaps(List.of())).isEmpty();
        verify(userKeycapRepository, never()).findEquippedWithKeycapByUserIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void equipsCompletedKeycapAndUnequipsPreviousKeycap() {
        UUID userId = UUID.randomUUID();
        UUID targetKeycapId = UUID.randomUUID();
        UserKeycap current = userKeycap(userId, UUID.randomUUID(), "BASIC_001", "Basic", 10, UserKeycap.Status.COMPLETED, true);
        UserKeycap target = userKeycap(userId, targetKeycapId, "RARE_001", "Rare", 20, UserKeycap.Status.COMPLETED, false);
        when(userKeycapRepository.findByUserIdForUpdate(userId)).thenReturn(List.of(current, target));
        when(userKeycapRepository.findByUserIdAndKeycapPublicIdWithKeycap(userId, targetKeycapId))
                .thenReturn(Optional.of(target));
        when(userKeycapRepository.findEquippedByUserIdForUpdate(userId)).thenReturn(Optional.of(current));

        KeycapEquipResponse response = keycapService.equipKeycap(userId, targetKeycapId);

        assertThat(current.isEquipped()).isFalse();
        assertThat(target.isEquipped()).isTrue();
        assertThat(response.keycapId()).isEqualTo(targetKeycapId);
        assertThat(response.equipped()).isTrue();
    }

    @Test
    void returnsSuccessWhenSameKeycapIsAlreadyEquipped() {
        UUID userId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        UserKeycap target = userKeycap(userId, keycapId, "BASIC_001", "Basic", 10, UserKeycap.Status.COMPLETED, true);
        when(userKeycapRepository.findByUserIdForUpdate(userId)).thenReturn(List.of(target));
        when(userKeycapRepository.findByUserIdAndKeycapPublicIdWithKeycap(userId, keycapId))
                .thenReturn(Optional.of(target));
        when(userKeycapRepository.findEquippedByUserIdForUpdate(userId)).thenReturn(Optional.of(target));

        KeycapEquipResponse response = keycapService.equipKeycap(userId, keycapId);

        assertThat(target.isEquipped()).isTrue();
        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.equipped()).isTrue();
    }

    @Test
    void rejectsMissingOrUnownedKeycapEquipAsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        when(userKeycapRepository.findByUserIdForUpdate(userId)).thenReturn(List.of());
        when(userKeycapRepository.findByUserIdAndKeycapPublicIdWithKeycap(userId, keycapId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> keycapService.equipKeycap(userId, keycapId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("USER_KEYCAP_NOT_FOUND");
    }

    private void onboardingKeycap(String code) {
        when(onboardingRewardConfig.resolve()).thenReturn(new OnboardingRewardConfig.OnboardingRewardPolicy(
                code, "COMMON", 70, java.time.Duration.ofMinutes(15), 45));
    }

    private static UserKeycap userKeycap(
            UUID userId,
            UUID keycapId,
            String code,
            String name,
            int level,
            UserKeycap.Status status,
            boolean equipped
    ) {
        AppUser user = AppUser.createActive("Bean", null);
        ReflectionTestUtils.setField(user, "id", userId);

        UserKeycap userKeycap = newInstance(UserKeycap.class);
        ReflectionTestUtils.setField(userKeycap, "user", user);
        ReflectionTestUtils.setField(userKeycap, "keycap", keycap(keycapId, code, name, Keycap.Grade.COMMON, 10, 1, true, 1));
        ReflectionTestUtils.setField(userKeycap, "level", level);
        ReflectionTestUtils.setField(userKeycap, "status", status);
        ReflectionTestUtils.setField(userKeycap, "equipped", equipped);
        return userKeycap;
    }

    private static Keycap keycap(
            UUID keycapId,
            String code,
            String name,
            Keycap.Grade grade,
            int requiredShardCount,
            int season,
            boolean active,
            int sortOrder
    ) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "publicId", keycapId);
        ReflectionTestUtils.setField(keycap, "code", code);
        ReflectionTestUtils.setField(keycap, "name", name);
        ReflectionTestUtils.setField(keycap, "grade", grade);
        ReflectionTestUtils.setField(keycap, "requiredShardCount", requiredShardCount);
        ReflectionTestUtils.setField(keycap, "season", season);
        ReflectionTestUtils.setField(keycap, "active", active);
        ReflectionTestUtils.setField(keycap, "sortOrder", sortOrder);
        return keycap;
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
