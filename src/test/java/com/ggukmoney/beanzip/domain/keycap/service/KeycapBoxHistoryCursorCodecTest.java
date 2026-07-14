package com.ggukmoney.beanzip.domain.keycap.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycapBoxHistoryCursorCodecTest {

    private final KeycapBoxHistoryCursorCodec cursorCodec = new KeycapBoxHistoryCursorCodec();

    @Test
    void encodesAndDecodesOpenedAtAndInternalIdAsOpaqueCursor() {
        Instant openedAt = Instant.parse("2026-07-15T00:00:00Z");

        String cursor = cursorCodec.encode(openedAt, 10L);
        KeycapBoxHistoryCursorCodec.Cursor decoded = cursorCodec.decode(cursor);

        assertThat(cursor).doesNotContain("2026-07-15T00:00:00Z");
        assertThat(decoded.openedAt()).isEqualTo(openedAt);
        assertThat(decoded.id()).isEqualTo(10L);
    }

    @Test
    void blankCursorDecodesToNull() {
        assertThat(cursorCodec.decode(null)).isNull();
        assertThat(cursorCodec.decode(" ")).isNull();
    }

    @Test
    void invalidCursorUsesCommonValidationError() {
        assertThatThrownBy(() -> cursorCodec.decode("not-base64"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("COMMON_VALIDATION_ERROR");
    }
}
