package com.ggukmoney.beanzip.domain.ranking.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;

@Component
public class RankingHistoryCursorCodec {

    private static final String DELIMITER = "|";
    private static final int MAX_CURSOR_LENGTH = 512;

    String encode(Instant endsAt, Long seasonId) {
        if (endsAt == null || seasonId == null || seasonId <= 0) {
            throw new IllegalArgumentException("ranking history cursor requires endsAt and positive seasonId");
        }
        String rawCursor = endsAt + DELIMITER + seasonId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    Cursor decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        if (cursor.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor(null);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid ranking history cursor");
            }
            Long seasonId = Long.parseLong(parts[1]);
            if (seasonId <= 0) {
                throw new IllegalArgumentException("Invalid ranking history season id cursor");
            }
            return new Cursor(Instant.parse(parts[0]), seasonId);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor(exception);
        }
    }

    private ResponseStatusException invalidCursor(Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_ERROR", cause);
    }

    record Cursor(Instant endsAt, Long seasonId) {
    }
}
