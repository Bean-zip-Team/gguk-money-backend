package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapServiceTest {

    private final KeycapRepository keycapRepository = mock(KeycapRepository.class);
    private final UserKeycapRepository userKeycapRepository = mock(UserKeycapRepository.class);
    private final KeycapMapper keycapMapper = Mappers.getMapper(KeycapMapper.class);
    private final KeycapService keycapService = new KeycapService(keycapRepository, userKeycapRepository, keycapMapper);

    @Test
    void getsActiveKeycapCatalogInRepositoryOrder() {
        Keycap first = keycap(UUID.randomUUID(), "BASIC_001", "Basic", Keycap.Grade.COMMON, 10, 1, true, 1);
        Keycap second = keycap(UUID.randomUUID(), "RARE_001", "Rare", Keycap.Grade.RARE, 20, 1, true, 2);
        when(keycapRepository.findByActiveTrueOrderBySortOrderAscCodeAsc()).thenReturn(List.of(first, second));

        KeycapListResponse response = keycapService.getKeycaps();

        assertThat(response.keycaps()).hasSize(2);
        assertThat(response.keycaps().get(0).keycapId()).isEqualTo(first.getPublicId());
        assertThat(response.keycaps().get(0).code()).isEqualTo("BASIC_001");
        assertThat(response.keycaps().get(0).grade()).isEqualTo("COMMON");
        assertThat(response.keycaps().get(0).requiredShardCount()).isEqualTo(10);
        assertThat(response.keycaps().get(0).season()).isEqualTo(1);
        assertThat(response.keycaps().get(0).imageUrl()).isNull();
        assertThat(response.keycaps().get(0).soundUrl()).isNull();
        verify(keycapRepository).findByActiveTrueOrderBySortOrderAscCodeAsc();
    }

    @Test
    void getsEmptyKeycapCatalogWhenThereAreNoActiveKeycaps() {
        when(keycapRepository.findByActiveTrueOrderBySortOrderAscCodeAsc()).thenReturn(List.of());

        assertThat(keycapService.getKeycaps().keycaps()).isEmpty();
    }

    @Test
    void getsOnlyCurrentUsersKeycapsWithJoinedKeycapData() {
        UUID userId = UUID.randomUUID();
        UserKeycap inProgress = userKeycap(userId, UUID.randomUUID(), "BASIC_001", "Basic", 4, UserKeycap.Status.IN_PROGRESS, false);
        UserKeycap completed = userKeycap(userId, UUID.randomUUID(), "RARE_001", "Rare", 20, UserKeycap.Status.COMPLETED, true);
        when(userKeycapRepository.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(userId))
                .thenReturn(List.of(inProgress, completed));

        MyKeycapListResponse response = keycapService.getMyKeycaps(userId);

        assertThat(response.keycaps()).hasSize(2);
        assertThat(response.keycaps().get(0).keycapId()).isEqualTo(inProgress.getKeycap().getPublicId());
        assertThat(response.keycaps().get(0).code()).isEqualTo("BASIC_001");
        assertThat(response.keycaps().get(0).shardCount()).isEqualTo(4);
        assertThat(response.keycaps().get(0).status()).isEqualTo("IN_PROGRESS");
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

    private static UserKeycap userKeycap(
            UUID userId,
            UUID keycapId,
            String code,
            String name,
            int shardCount,
            UserKeycap.Status status,
            boolean equipped
    ) {
        AppUser user = AppUser.createActive("Bean", null);
        ReflectionTestUtils.setField(user, "id", userId);

        UserKeycap userKeycap = newInstance(UserKeycap.class);
        ReflectionTestUtils.setField(userKeycap, "user", user);
        ReflectionTestUtils.setField(userKeycap, "keycap", keycap(keycapId, code, name, Keycap.Grade.COMMON, 10, 1, true, 1));
        ReflectionTestUtils.setField(userKeycap, "shardCount", shardCount);
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
