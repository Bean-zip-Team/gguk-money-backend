package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapFiveCompletionTriggerTest {

    private static final Instant LAUNCH_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    private final UserKeycapRepository userKeycapRepository = mock(UserKeycapRepository.class);
    private final PromotionPolicyConfig policyConfig = mock(PromotionPolicyConfig.class);
    private final KeycapFiveCompletionTrigger trigger =
            new KeycapFiveCompletionTrigger(userKeycapRepository, policyConfig);

    private final AppUser user = mock(AppUser.class);
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(userId);
        when(policyConfig.threshold()).thenReturn(5);
        when(policyConfig.launchAt()).thenReturn(Optional.of(LAUNCH_AT));
    }

    private Optional<Integer> evaluateWithCompleted(long completedAfterCutoff) {
        when(userKeycapRepository.countByUserIdAndStatusAndCompletedAtAfter(
                eq(userId), eq(UserKeycap.Status.COMPLETED), eq(LAUNCH_AT)))
                .thenReturn(completedAfterCutoff);
        return trigger.evaluate(PromotionTriggerContext.keycapCompleted(user, completedAfterCutoff, NOW));
    }

    @Test
    @DisplayName("임계치 미만이면 자격이 없다")
    void belowThreshold() {
        assertThat(evaluateWithCompleted(4)).isEmpty();
    }

    @Test
    @DisplayName("임계치에 도달하면 자격이 있다")
    void atThreshold() {
        assertThat(evaluateWithCompleted(5)).contains(5);
    }

    @Test
    @DisplayName("임계치를 넘겨도 자격이 있다 — 한 번 놓쳐도 다음 개봉에서 복구되어야 한다")
    void aboveThresholdStillEligible() {
        assertThat(evaluateWithCompleted(8)).contains(8);
    }

    @Test
    @DisplayName("커트오프가 설정되지 않으면 조회조차 하지 않고 지급하지 않는다")
    void missingCutoffIsFailClosed() {
        when(policyConfig.launchAt()).thenReturn(Optional.empty());

        Optional<Integer> result = trigger.evaluate(PromotionTriggerContext.keycapCompleted(user, 99, NOW));

        assertThat(result).isEmpty();
        verify(userKeycapRepository, never())
                .countByUserIdAndStatusAndCompletedAtAfter(any(), any(), any());
    }

    @Test
    @DisplayName("커트오프 이후 완성분만 센다 — 출시 시점 보유분은 자격에 포함되지 않는다")
    void countsOnlyAfterCutoff() {
        evaluateWithCompleted(5);

        verify(userKeycapRepository).countByUserIdAndStatusAndCompletedAtAfter(
                userId, UserKeycap.Status.COMPLETED, LAUNCH_AT);
    }
}
