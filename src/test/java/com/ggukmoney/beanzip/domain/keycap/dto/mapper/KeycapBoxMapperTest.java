package com.ggukmoney.beanzip.domain.keycap.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KeycapBoxMapperTest {

    private final KeycapBoxMapper keycapBoxMapper = Mappers.getMapper(KeycapBoxMapper.class);

    @Test
    void mapsBoxOpenToHistoryItemWithoutExposingInternalFields() {
        UUID boxOpenId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-07-15T00:00:00Z");
        KeycapBoxOpen open = open(boxOpenId, keycapId, openedAt);

        KeycapBoxHistoryItemResponse response = keycapBoxMapper.mapToHistoryItemResponse(open);

        assertThat(response.boxOpenId()).isEqualTo(boxOpenId);
        assertThat(response.openMethod()).isEqualTo("FREE");
        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.shardCount()).isEqualTo(1);
        assertThat(response.completed()).isFalse();
        assertThat(response.openedAt()).isEqualTo(openedAt);
        assertThat(KeycapBoxHistoryItemResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("boxOpenId", "openMethod", "keycapId", "shardCount", "completed", "openedAt");
    }

    private static KeycapBoxOpen open(UUID boxOpenId, UUID keycapId, Instant openedAt) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "publicId", keycapId);
        ReflectionTestUtils.setField(keycap, "code", "BASIC_001");
        ReflectionTestUtils.setField(keycap, "name", "Basic");
        ReflectionTestUtils.setField(keycap, "grade", Keycap.Grade.COMMON);

        KeycapBoxOpen open = newInstance(KeycapBoxOpen.class);
        ReflectionTestUtils.setField(open, "publicId", boxOpenId);
        ReflectionTestUtils.setField(open, "openMethod", KeycapBoxOpen.OpenMethod.FREE);
        ReflectionTestUtils.setField(open, "keycap", keycap);
        ReflectionTestUtils.setField(open, "shardCount", 1);
        ReflectionTestUtils.setField(open, "completed", false);
        ReflectionTestUtils.setField(open, "openedAt", openedAt);
        return open;
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
