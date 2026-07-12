package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeycapServiceTest {

    private final UserKeycapRepository userKeycapRepository = mock(UserKeycapRepository.class);
    private final KeycapMapper keycapMapper = Mappers.getMapper(KeycapMapper.class);
    private final KeycapService keycapService = new KeycapService(userKeycapRepository, keycapMapper);

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
        UserKeycap userKeycap = userKeycap(userId, keycapId);
        when(userKeycapRepository.findByUserIdAndEquippedTrue(userId)).thenReturn(Optional.of(userKeycap));

        EquippedKeycapResponse response = keycapService.getEquippedKeycap(userId);

        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.code()).isEqualTo("BASIC_001");
        assertThat(response.name()).isEqualTo("기본 키캡");
        assertThat(response.imageUrl()).isNull();
    }

    private static UserKeycap userKeycap(UUID userId, UUID keycapId) {
        AppUser user = AppUser.createActive("Bean", null);
        ReflectionTestUtils.setField(user, "id", userId);

        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "publicId", keycapId);
        ReflectionTestUtils.setField(keycap, "code", "BASIC_001");
        ReflectionTestUtils.setField(keycap, "name", "기본 키캡");

        UserKeycap userKeycap = newInstance(UserKeycap.class);
        ReflectionTestUtils.setField(userKeycap, "user", user);
        ReflectionTestUtils.setField(userKeycap, "keycap", keycap);
        ReflectionTestUtils.setField(userKeycap, "equipped", true);
        return userKeycap;
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
