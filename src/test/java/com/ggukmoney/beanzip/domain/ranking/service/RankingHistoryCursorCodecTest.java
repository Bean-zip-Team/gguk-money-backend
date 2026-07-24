package com.ggukmoney.beanzip.domain.ranking.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingHistoryCursorCodecTest {

    private final RankingHistoryCursorCodec cursorCodec = new RankingHistoryCursorCodec();

    @Test
    void blankCursorDecodesToNull() {
        assertThat(cursorCodec.decode(null)).isNull();
        assertThat(cursorCodec.decode(" ")).isNull();
    }

    @Test
    void encodesAndDecodesEndsAtAndSeasonIdAsOpaqueCursor() {
        Instant endsAt = Instant.parse("2026-07-26T15:00:00Z");

        String cursor = cursorCodec.encode(endsAt, 123L);
        RankingHistoryCursorCodec.Cursor decoded = cursorCodec.decode(cursor);

        assertThat(cursor).doesNotContain("2026-07-26T15:00:00Z");
        assertThat(decoded.endsAt()).isEqualTo(endsAt);
        assertThat(decoded.seasonId()).isEqualTo(123L);
    }

    @Test
    void invalidCursorUsesCommonValidationError() {
        assertInvalid("not-base64");
        assertInvalid(java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-07-26T15:00:00Z".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertInvalid(java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-instant|1".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertInvalid(java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-07-26T15:00:00Z|0".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertInvalid("a".repeat(513));
    }

    private void assertInvalid(String cursor) {
        assertThatThrownBy(() -> cursorCodec.decode(cursor))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");
    }
}
