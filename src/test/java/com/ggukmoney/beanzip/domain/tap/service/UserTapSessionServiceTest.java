package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapSession;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapSessionRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTapSessionServiceTest {

    private final UserTapSessionRepository userTapSessionRepository = mock(UserTapSessionRepository.class);
    private final UserTapSessionService userTapSessionService = new UserTapSessionService(userTapSessionRepository);
    private final Instant now = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void createForBuildsSessionWithFreshlyDrawnFirstBoxTarget() {
        AppUser user = mock(AppUser.class);
        when(userTapSessionRepository.save(any(UserTapSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapSession session = userTapSessionService.createFor(user, now, sessionConfig());

        assertThat(session.getNextBoxTarget()).isEqualTo(25); // step1=25, fixed
        assertThat(session.getSessionValidTapCount()).isZero();
        assertThat(session.getBoxesDroppedInSession()).isZero();
        assertThat(session.getSessionExpiresAt()).isEqualTo(now.plusSeconds(3600));
    }

    @Test
    void drawNextBoxTargetInSessionUsesStepTableThenFallsBackToTailStep() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.boxSessionStep1()).thenReturn(25);
        when(config.boxSessionStep2()).thenReturn(35);
        when(config.boxSessionStep3()).thenReturn(50);
        when(config.boxSessionStep4()).thenReturn(70);
        when(config.boxSessionStep5()).thenReturn(100);
        when(config.boxSessionTailStep()).thenReturn(180);

        assertThat(userTapSessionService.drawNextBoxTargetInSession(0, 0, config)).isEqualTo(25);
        assertThat(userTapSessionService.drawNextBoxTargetInSession(25, 1, config)).isEqualTo(60);
        assertThat(userTapSessionService.drawNextBoxTargetInSession(60, 2, config)).isEqualTo(110);
        assertThat(userTapSessionService.drawNextBoxTargetInSession(110, 3, config)).isEqualTo(180);
        assertThat(userTapSessionService.drawNextBoxTargetInSession(180, 4, config)).isEqualTo(280);
        assertThat(userTapSessionService.drawNextBoxTargetInSession(280, 5, config)).isEqualTo(460);
        assertThat(userTapSessionService.drawNextBoxTargetInSession(460, 6, config)).isEqualTo(640);
    }

    @Test
    void getOrCreateActiveSessionCreatesNewSessionWhenNoneExists() {
        AppUser user = stubUser();
        when(userTapSessionRepository.save(any(UserTapSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, now, sessionConfig());

        assertThat(session.getSessionStartedAt()).isEqualTo(now);
        assertThat(session.getSessionExpiresAt()).isEqualTo(now.plusSeconds(3600));
    }

    @Test
    void getOrCreateActiveSessionReturnsExistingSessionWithoutWritingWhenNotExpired() {
        AppUser user = stubUser();
        Instant startedAt = now.minusSeconds(600);
        UserTapSession existing = UserTapSession.createFor(user, startedAt, startedAt.plusSeconds(3600), 100);
        existing.addValidTaps(40);
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, now, sessionConfig());

        assertThat(session.getSessionValidTapCount()).isEqualTo(40);
        assertThat(session.getNextBoxTarget()).isEqualTo(100);
        assertThat(session.getLastActivityAt()).isEqualTo(startedAt);
        verify(userTapSessionRepository, never()).save(any());
    }

    @Test
    void getOrCreateActiveSessionResetsWhenHardCapExceeded() {
        AppUser user = stubUser();
        Instant startedAt = now.minusSeconds(7200);
        UserTapSession existing = UserTapSession.createFor(user, startedAt, startedAt.plusSeconds(3600), 999);
        existing.addValidTaps(500);
        existing.recordActivity(now.minusSeconds(10));
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));
        when(userTapSessionRepository.save(any(UserTapSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, now, sessionConfig());

        assertThat(session.getSessionValidTapCount()).isZero();
        assertThat(session.getBoxesDroppedInSession()).isZero();
        assertThat(session.getSessionStartedAt()).isEqualTo(now);
        assertThat(session.getSessionExpiresAt()).isEqualTo(now.plusSeconds(3600));
    }

    @Test
    void getOrCreateActiveSessionKeepsProgressWhenIdleButWithinHardCap() {
        AppUser user = stubUser();
        Instant startedAt = now.minusSeconds(300);
        UserTapSession existing = UserTapSession.createFor(user, startedAt, startedAt.plusSeconds(3600), 999);
        existing.addValidTaps(200);
        existing.recordActivity(now.minusSeconds(1801)); // long idle, but hard cap not reached
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, now, sessionConfig());

        assertThat(session.getSessionValidTapCount()).isEqualTo(200);
        assertThat(session.getSessionStartedAt()).isEqualTo(startedAt);
        assertThat(session.getLastActivityAt()).isEqualTo(now.minusSeconds(1801));
        verify(userTapSessionRepository, never()).save(any());
    }

    @Test
    void getBoxProgressReturnsSnapshotFromSession() {
        AppUser user = stubUser();
        UserTapSession existing = UserTapSession.createFor(user, now, now.plusSeconds(3600), 100);
        existing.addValidTaps(45);
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));
        when(userTapSessionRepository.save(any(UserTapSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BoxProgressSnapshot snapshot = userTapSessionService.getBoxProgress(user, now, sessionConfig());

        assertThat(snapshot.cumulativeValidTapCount()).isEqualTo(45);
        assertThat(snapshot.nextBoxTarget()).isEqualTo(100);
    }

    private AppUser stubUser() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        return user;
    }

    private TapPolicyConfig sessionConfig() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.boxSessionStep1()).thenReturn(25);
        when(config.boxSessionStep2()).thenReturn(35);
        when(config.boxSessionStep3()).thenReturn(50);
        when(config.boxSessionStep4()).thenReturn(70);
        when(config.boxSessionStep5()).thenReturn(100);
        when(config.boxSessionTailStep()).thenReturn(180);
        when(config.boxSessionMaxDurationSeconds()).thenReturn(3600);
        return config;
    }
}
