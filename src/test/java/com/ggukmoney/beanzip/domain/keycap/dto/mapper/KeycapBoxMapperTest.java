package com.ggukmoney.beanzip.domain.keycap.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KeycapBoxMapperTest {

    private final KeycapBoxMapper keycapBoxMapper = Mappers.getMapper(KeycapBoxMapper.class);

    @Test
    void mapsBoxAccountAndProgressSnapshotToStatusResponse() {
        KeycapBoxAccount account = keycapBoxAccount(UUID.randomUUID(), 2, 1);
        BoxProgressSnapshot progress = new BoxProgressSnapshot(45, 100);

        KeycapBoxStatusResponse response = keycapBoxMapper.mapToStatusResponse(account, progress);

        assertThat(response.boxBalance()).isEqualTo(2);
        assertThat(response.freeOpenTicketCount()).isEqualTo(1);
        assertThat(response.boxProgressTapCount()).isEqualTo(45);
        assertThat(response.nextBoxRequiredTapCount()).isEqualTo(100);
    }

    @Test
    void statusResponseDoesNotExposeInternalIdsOrExcludedFields() {
        assertThat(KeycapBoxStatusResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly(
                        "boxBalance",
                        "freeOpenTicketCount",
                        "boxProgressTapCount",
                        "nextBoxRequiredTapCount"
                );
    }

    private static KeycapBoxAccount keycapBoxAccount(UUID userId, int boxBalance, int freeOpenTicketCount) {
        AppUser user = AppUser.createActive("Bean", null);
        ReflectionTestUtils.setField(user, "id", userId);

        KeycapBoxAccount account = newInstance(KeycapBoxAccount.class);
        ReflectionTestUtils.setField(account, "id", 10L);
        ReflectionTestUtils.setField(account, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "user", user);
        ReflectionTestUtils.setField(account, "boxBalance", boxBalance);
        ReflectionTestUtils.setField(account, "freeOpenTicketCount", freeOpenTicketCount);
        return account;
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
