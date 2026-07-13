package com.ggukmoney.beanzip.domain.keycap.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KeycapMapperTest {

    private final KeycapMapper keycapMapper = Mappers.getMapper(KeycapMapper.class);

    @Test
    void mapsKeycapToCatalogItemWithoutExposingInternalId() {
        UUID keycapId = UUID.randomUUID();
        Keycap keycap = keycap(keycapId, "BASIC_001", "Basic", Keycap.Grade.COMMON, 10, 1, true, 2);
        ReflectionTestUtils.setField(keycap, "imageUrl", "https://example.com/keycap.png");
        ReflectionTestUtils.setField(keycap, "soundUrl", "https://example.com/keycap.mp3");

        KeycapItemResponse response = keycapMapper.mapToKeycapItemResponse(keycap);

        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.code()).isEqualTo("BASIC_001");
        assertThat(response.name()).isEqualTo("Basic");
        assertThat(response.grade()).isEqualTo("COMMON");
        assertThat(response.requiredShardCount()).isEqualTo(10);
        assertThat(response.season()).isEqualTo(1);
        assertThat(response.imageUrl()).isEqualTo("https://example.com/keycap.png");
        assertThat(response.soundUrl()).isEqualTo("https://example.com/keycap.mp3");
    }

    @Test
    void mapsKeycapListWrapper() {
        Keycap keycap = keycap(UUID.randomUUID(), "BASIC_001", "Basic", Keycap.Grade.COMMON, 10, 1, true, 1);

        KeycapListResponse response = keycapMapper.mapToKeycapListResponse(List.of(keycap));

        assertThat(response.keycaps()).hasSize(1);
        assertThat(response.keycaps().get(0).code()).isEqualTo("BASIC_001");
    }

    @Test
    void mapsUserKeycapToMyKeycapItem() {
        UUID keycapId = UUID.randomUUID();
        UserKeycap userKeycap = userKeycap(UUID.randomUUID(), keycapId, "RARE_001", "Rare", 8, UserKeycap.Status.IN_PROGRESS, false);

        MyKeycapItemResponse response = keycapMapper.mapToMyKeycapItemResponse(userKeycap);

        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.code()).isEqualTo("RARE_001");
        assertThat(response.name()).isEqualTo("Rare");
        assertThat(response.shardCount()).isEqualTo(8);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.equipped()).isFalse();
    }

    @Test
    void mapsMyKeycapListWrapper() {
        UserKeycap userKeycap = userKeycap(UUID.randomUUID(), UUID.randomUUID(), "BASIC_001", "Basic", 10, UserKeycap.Status.COMPLETED, true);

        MyKeycapListResponse response = keycapMapper.mapToMyKeycapListResponse(List.of(userKeycap));

        assertThat(response.keycaps()).hasSize(1);
        assertThat(response.keycaps().get(0).status()).isEqualTo("COMPLETED");
    }

    @Test
    void mapsEquippedKeycapImageUrlFromKeycap() {
        UUID keycapId = UUID.randomUUID();
        UserKeycap userKeycap =
                userKeycap(UUID.randomUUID(), keycapId, "BASIC_001", "Basic", 10, UserKeycap.Status.COMPLETED, true);
        ReflectionTestUtils.setField(userKeycap.getKeycap(), "imageUrl", "https://example.com/equipped.png");

        EquippedKeycapResponse response = keycapMapper.mapToEquippedKeycapResponse(userKeycap);

        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.code()).isEqualTo("BASIC_001");
        assertThat(response.name()).isEqualTo("Basic");
        assertThat(response.imageUrl()).isEqualTo("https://example.com/equipped.png");
    }

    @Test
    void mapsNullEquippedKeycapImageUrlWhenKeycapImageUrlIsNull() {
        UUID keycapId = UUID.randomUUID();
        UserKeycap userKeycap =
                userKeycap(UUID.randomUUID(), keycapId, "BASIC_001", "Basic", 10, UserKeycap.Status.COMPLETED, true);

        EquippedKeycapResponse response = keycapMapper.mapToEquippedKeycapResponse(userKeycap);

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
