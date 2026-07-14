package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KeycapBoxOpenRepositoryTest {

    @Autowired
    private KeycapBoxOpenRepository keycapBoxOpenRepository;

    @Autowired
    private KeycapRepository keycapRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void findsCurrentUsersOpenByIdempotencyKeyWithKeycap() {
        AppUser currentUser = appUserRepository.save(AppUser.createActive("current", null));
        AppUser otherUser = appUserRepository.save(AppUser.createActive("other", null));
        Keycap keycap = keycapRepository.save(keycap("BASIC_001"));
        keycapBoxOpenRepository.save(open(currentUser, keycap, "same-key", "hash-1"));
        keycapBoxOpenRepository.save(open(otherUser, keycap, "same-key", "hash-2"));

        Optional<KeycapBoxOpen> result = keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(
                currentUser.getId(),
                "same-key"
        );

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getId()).isEqualTo(currentUser.getId());
        assertThat(result.get().getKeycap().getCode()).isEqualTo("BASIC_001");
        assertThat(result.get().getRequestHash()).isEqualTo("hash-1");
    }

    @Test
    void findsHistoryForCurrentUserOrderedByOpenedAtAndIdDescWithCursor() {
        AppUser currentUser = appUserRepository.save(AppUser.createActive("current", null));
        AppUser otherUser = appUserRepository.save(AppUser.createActive("other", null));
        Keycap keycap = keycapRepository.save(keycap("HISTORY_001"));
        Instant latest = Instant.parse("2026-07-15T02:00:00Z");
        Instant tied = Instant.parse("2026-07-15T01:00:00Z");
        Instant oldest = Instant.parse("2026-07-15T00:00:00Z");
        KeycapBoxOpen currentOldest = keycapBoxOpenRepository.save(open(currentUser, keycap, "current-1", "hash-1", oldest));
        KeycapBoxOpen currentTieLowId = keycapBoxOpenRepository.save(open(currentUser, keycap, "current-2", "hash-2", tied));
        KeycapBoxOpen currentTieHighId = keycapBoxOpenRepository.save(open(currentUser, keycap, "current-3", "hash-3", tied));
        KeycapBoxOpen currentLatest = keycapBoxOpenRepository.save(open(currentUser, keycap, "current-4", "hash-4", latest));
        keycapBoxOpenRepository.save(open(otherUser, keycap, "other-1", "hash-5", latest));

        List<KeycapBoxOpen> firstPage = keycapBoxOpenRepository.findHistoryByUserId(
                currentUser.getId(),
                null,
                null,
                PageRequest.of(0, 3)
        );
        List<KeycapBoxOpen> secondPage = keycapBoxOpenRepository.findHistoryByUserId(
                currentUser.getId(),
                currentTieHighId.getOpenedAt(),
                currentTieHighId.getId(),
                PageRequest.of(0, 3)
        );

        assertThat(firstPage)
                .extracting(KeycapBoxOpen::getId)
                .containsExactly(currentLatest.getId(), currentTieHighId.getId(), currentTieLowId.getId());
        assertThat(secondPage)
                .extracting(KeycapBoxOpen::getId)
                .containsExactly(currentTieLowId.getId(), currentOldest.getId());
        assertThat(firstPage)
                .allSatisfy(open -> {
                    assertThat(open.getUser().getId()).isEqualTo(currentUser.getId());
                    assertThat(open.getKeycap().getCode()).isEqualTo("HISTORY_001");
                });
    }

    private static KeycapBoxOpen open(AppUser user, Keycap keycap, String idempotencyKey, String requestHash) {
        return open(user, keycap, idempotencyKey, requestHash, Instant.now());
    }

    private static KeycapBoxOpen open(
            AppUser user,
            Keycap keycap,
            String idempotencyKey,
            String requestHash,
            Instant openedAt
    ) {
        return KeycapBoxOpen.createFor(
                user,
                KeycapBoxOpen.OpenMethod.FREE,
                keycap,
                1,
                idempotencyKey,
                requestHash,
                null,
                false,
                openedAt
        );
    }

    private static Keycap keycap(String code) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "code", code);
        ReflectionTestUtils.setField(keycap, "name", code);
        ReflectionTestUtils.setField(keycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(keycap, "requiredShardCount", 10);
        ReflectionTestUtils.setField(keycap, "season", 1);
        ReflectionTestUtils.setField(keycap, "active", true);
        ReflectionTestUtils.setField(keycap, "sortOrder", 1);
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
