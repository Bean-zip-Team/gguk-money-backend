package com.ggukmoney.beanzip.domain.onboarding.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OnboardingRewardAttemptRepositoryTest {

    @Autowired
    private OnboardingRewardAttemptRepository attemptRepository;

    @Autowired
    private KeycapRepository keycapRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void savesOpenedAttemptAndFindsByTapSessionIdWithRewardKeycap() {
        Keycap keycap = keycapRepository.save(keycap("ONBOARDING_BASIC"));
        UUID tapSessionId = UUID.randomUUID();
        OnboardingRewardAttempt saved = attemptRepository.save(OnboardingRewardAttempt.open(
                tapSessionId,
                "request-hash",
                45,
                keycap,
                100,
                Instant.parse("2026-07-15T01:00:05Z"),
                Instant.parse("2026-07-16T01:00:05Z")
        ));

        Optional<OnboardingRewardAttempt> result = attemptRepository.findByTapSessionIdWithRewardKeycap(tapSessionId);

        assertThat(result).isPresent();
        assertThat(result.get().getPublicId()).isNotNull();
        assertThat(result.get().getTapSessionId()).isEqualTo(tapSessionId);
        assertThat(result.get().getRequestHash()).isEqualTo("request-hash");
        assertThat(result.get().getAcceptedTapCount()).isEqualTo(45);
        assertThat(result.get().getRewardKeycap().getCode()).isEqualTo("ONBOARDING_BASIC");
        assertThat(result.get().getRewardPointAmount()).isEqualTo(100);
        assertThat(result.get().getStatus()).isEqualTo(OnboardingRewardAttempt.Status.OPENED);
        assertThat(result.get().getClaimedUser()).isNull();
        assertThat(result.get().getClaimedAt()).isNull();
        assertThat(attemptRepository.findByPublicId(saved.getPublicId())).isPresent();
    }

    @Test
    void openedAttemptCanLaterReferenceClaimedUserWithoutThisIssueClaimingIt() {
        Keycap keycap = keycapRepository.save(keycap("ONBOARDING_CLAIM"));
        AppUser user = appUserRepository.save(AppUser.createActive("Bean", null));
        OnboardingRewardAttempt attempt = OnboardingRewardAttempt.open(
                UUID.randomUUID(),
                "request-hash",
                45,
                keycap,
                100,
                Instant.parse("2026-07-15T01:00:05Z"),
                Instant.parse("2026-07-16T01:00:05Z")
        );
        ReflectionTestUtils.setField(attempt, "claimedUser", user);
        ReflectionTestUtils.setField(attempt, "claimedAt", Instant.parse("2026-07-15T02:00:00Z"));
        ReflectionTestUtils.setField(attempt, "status", OnboardingRewardAttempt.Status.CLAIMED);

        OnboardingRewardAttempt saved = attemptRepository.save(attempt);

        assertThat(saved.getClaimedUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getClaimedAt()).isEqualTo(Instant.parse("2026-07-15T02:00:00Z"));
    }

    private static Keycap keycap(String code) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "code", code);
        ReflectionTestUtils.setField(keycap, "name", code);
        ReflectionTestUtils.setField(keycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(keycap, "requiredShardCount", 1);
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
