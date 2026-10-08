package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapDraw;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class KeycapDrawRepositoryTest {

    @Autowired
    private KeycapDrawRepository keycapDrawRepository;

    @Autowired
    private KeycapRepository keycapRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsCurrentUsersDrawByIdempotencyKeyWithKeycapLoaded() {
        AppUser currentUser = appUserRepository.save(AppUser.createActive("current", null));
        AppUser otherUser = appUserRepository.save(AppUser.createActive("other", null));
        Keycap keycap = keycapRepository.save(Keycap.createFor("DRAW_001", "Draw", Keycap.Grade.COMMON, 10, 1, null, null, 1));
        Instant drawnAt = Instant.parse("2026-10-09T01:00:00Z");
        keycapDrawRepository.save(KeycapDraw.createFor(currentUser, keycap, 5, 1, true, "same-key", drawnAt));
        keycapDrawRepository.save(KeycapDraw.createFor(otherUser, keycap, 5, 2, false, "same-key", drawnAt));
        entityManager.flush();
        entityManager.clear();

        Optional<KeycapDraw> result = keycapDrawRepository.findByUserIdAndIdempotencyKeyWithKeycap(currentUser.getId(), "same-key");

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getId()).isEqualTo(currentUser.getId());
        assertThat(result.get().getLevelAfter()).isEqualTo(1);
        assertThat(result.get().isNewlyAcquired()).isTrue();
        assertThat(result.get().getShardsSpent()).isEqualTo(5);
        assertThat(result.get().getDrawnAt()).isEqualTo(drawnAt);
        assertThat(Hibernate.isInitialized(result.get().getKeycap())).isTrue();
        assertThat(result.get().getKeycap().getCode()).isEqualTo("DRAW_001");
        assertThat(keycapDrawRepository.findByUserIdAndIdempotencyKeyWithKeycap(currentUser.getId(), "other-key")).isEmpty();
    }

    @Test
    void rejectsSecondDrawWithSameIdempotencyKeyForSameUser() {
        AppUser user = appUserRepository.save(AppUser.createActive("current", null));
        Keycap keycap = keycapRepository.save(Keycap.createFor("DRAW_002", "Draw", Keycap.Grade.COMMON, 10, 1, null, null, 1));
        keycapDrawRepository.saveAndFlush(KeycapDraw.createFor(user, keycap, 5, 1, true, "dup-key", Instant.now()));

        // 동시 호출이 같은 키로 부딪히면 이 제약이 이중 뽑기를 막고, 진 쪽은 이긴 쪽 결과를 재생한다.
        assertThatThrownBy(() -> keycapDrawRepository.saveAndFlush(
                KeycapDraw.createFor(user, keycap, 5, 2, false, "dup-key", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
