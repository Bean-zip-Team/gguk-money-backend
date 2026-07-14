package com.ggukmoney.beanzip.domain.keycap.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
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

    @Test
    void mapsBoxOpenToOpenResponse() {
        UUID boxOpenId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-07-14T00:00:00Z");
        KeycapBoxOpen open = boxOpen(boxOpenId, keycapId, openedAt, true);

        var response = keycapBoxMapper.mapToOpenResponse(open);

        assertThat(response.boxOpenId()).isEqualTo(boxOpenId);
        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.shardCount()).isEqualTo(1);
        assertThat(response.completed()).isTrue();
        assertThat(response.openedAt()).isEqualTo(openedAt);
    }

    @Test
    void openResponseDoesNotExposeInternalIdsOrBoostApplied() {
        assertThat(com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("boxOpenId", "keycapId", "shardCount", "completed", "openedAt");
    }

    @Test
    void mapsBoxOpenToHistoryItemResponse() {
        UUID boxOpenId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-07-15T00:00:00Z");
        KeycapBoxOpen open = boxOpen(boxOpenId, keycapId, openedAt, false);
        ReflectionTestUtils.setField(open, "openMethod", KeycapBoxOpen.OpenMethod.FREE);

        var response = keycapBoxMapper.mapToHistoryItemResponse(open);

        assertThat(response.boxOpenId()).isEqualTo(boxOpenId);
        assertThat(response.openMethod()).isEqualTo("FREE");
        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.shardCount()).isEqualTo(1);
        assertThat(response.completed()).isFalse();
        assertThat(response.openedAt()).isEqualTo(openedAt);
    }

    @Test
    void historyItemResponseDoesNotExposeInternalOrIdempotencyFields() {
        assertThat(com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("boxOpenId", "openMethod", "keycapId", "shardCount", "completed", "openedAt");
    }

    @Test
    void historyPageResponseUsesContentNextCursorAndHasNext() {
        assertThat(com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("content", "nextCursor", "hasNext");
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

    private static KeycapBoxOpen boxOpen(UUID boxOpenId, UUID keycapId, Instant openedAt, boolean completed) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "publicId", keycapId);

        KeycapBoxOpen open = newInstance(KeycapBoxOpen.class);
        ReflectionTestUtils.setField(open, "publicId", boxOpenId);
        ReflectionTestUtils.setField(open, "keycap", keycap);
        ReflectionTestUtils.setField(open, "shardCount", 1);
        ReflectionTestUtils.setField(open, "completed", completed);
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
