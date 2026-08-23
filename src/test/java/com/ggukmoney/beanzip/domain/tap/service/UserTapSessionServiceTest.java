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
        assertThat(session.getSessionExpiresAt()).isEqualTo(now.plusSeconds(1800));
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
        assertThat(session.getSessionExpiresAt()).isEqualTo(now.plusSeconds(1800));
    }

    @Test
    void getOrCreateActiveSessionReturnsExistingSessionWithoutWritingWhenNotExpired() {
        AppUser user = stubUser();
        Instant startedAt = now.minusSeconds(600);
        UserTapSession existing = UserTapSession.createFor(user, startedAt, startedAt.plusSeconds(1800), 100);
        existing.addValidTaps(40);
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, now, sessionConfig());

        assertThat(session.getSessionValidTapCount()).isEqualTo(40);
        assertThat(session.getNextBoxTarget()).isEqualTo(100);
        assertThat(session.getLastActivityAt()).isEqualTo(startedAt);
        verify(userTapSessionRepository, never()).save(any());
    }

    @Test
    void getOrCreateActiveSessionResetsWhenIdleTimeoutExceeded() {
        AppUser user = stubUser();
        Instant startedAt = now.minusSeconds(7200);
        UserTapSession existing = UserTapSession.createFor(user, startedAt, startedAt.plusSeconds(1800), 999);
        existing.addValidTaps(500);
        existing.recordActivity(now.minusSeconds(1801), 1800); // 마지막 탭이 유휴 시간보다 오래됐다
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));
        when(userTapSessionRepository.save(any(UserTapSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, now, sessionConfig());

        assertThat(session.getSessionValidTapCount()).isZero();
        assertThat(session.getBoxesDroppedInSession()).isZero();
        assertThat(session.getNextBoxTarget()).isEqualTo(25); // 싼 스텝부터 다시 시작
        assertThat(session.getSessionStartedAt()).isEqualTo(now);
        assertThat(session.getSessionExpiresAt()).isEqualTo(now.plusSeconds(1800));
    }

    @Test
    void getOrCreateActiveSessionKeepsProgressWhileTappingRegardlessOfSessionAge() {
        AppUser user = stubUser();
        Instant startedAt = now.minusSeconds(7200); // 2시간 전에 시작했지만
        UserTapSession existing = UserTapSession.createFor(user, startedAt, startedAt.plusSeconds(1800), 999);
        existing.addValidTaps(500);
        existing.recordActivity(now.minusSeconds(60), 1800); // 1분 전까지 계속 쳤다

        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, now, sessionConfig());

        assertThat(session.getSessionValidTapCount()).isEqualTo(500);
        assertThat(session.getNextBoxTarget()).isEqualTo(999); // tailStep 유지, 싼 사다리 재시작 없음
        assertThat(session.getSessionStartedAt()).isEqualTo(startedAt);
        verify(userTapSessionRepository, never()).save(any());
    }

    @Test
    void getOrCreateActiveSessionDoesNotExtendIdleDeadlineOnReadOnlyAccess() {
        AppUser user = stubUser();
        Instant startedAt = now.minusSeconds(600);
        UserTapSession existing = UserTapSession.createFor(user, startedAt, startedAt.plusSeconds(1800), 100);
        existing.addValidTaps(40);
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        userTapSessionService.getOrCreateActiveSession(user, now, sessionConfig());

        // 조회만으로 마감이 밀리면 상태 폴링으로 유휴 만료를 영원히 피할 수 있다.
        assertThat(existing.getSessionExpiresAt()).isEqualTo(startedAt.plusSeconds(1800));
        assertThat(existing.getLastActivityAt()).isEqualTo(startedAt);
    }

    @Test
    void recordActivitySlidesIdleDeadlineFromLastTap() {
        AppUser user = stubUser();
        UserTapSession session = UserTapSession.createFor(user, now, now.plusSeconds(1800), 25);

        Instant tappedAt = now.plusSeconds(1500);
        session.recordActivity(tappedAt, 1800);

        assertThat(session.getLastActivityAt()).isEqualTo(tappedAt);
        assertThat(session.getSessionExpiresAt()).isEqualTo(tappedAt.plusSeconds(1800));
        assertThat(session.isExpired(tappedAt.plusSeconds(1799))).isFalse();
        assertThat(session.isExpired(tappedAt.plusSeconds(1800))).isTrue();
    }

    @Test
    void getBoxProgressReturnsSnapshotFromSession() {
        AppUser user = stubUser();
        UserTapSession existing = UserTapSession.createFor(user, now, now.plusSeconds(1800), 100);
        existing.addValidTaps(45);
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        BoxProgressSnapshot snapshot = userTapSessionService.getBoxProgress(user, now, sessionConfig());

        assertThat(snapshot.cumulativeValidTapCount()).isEqualTo(45);
        assertThat(snapshot.nextBoxTarget()).isEqualTo(100);
        verify(userTapSessionRepository, never()).save(any());
    }

    @Test
    void getBoxProgressShowsPostResetValuesWithoutPersistingWhenIdleExpired() {
        AppUser user = stubUser();
        Instant startedAt = now.minusSeconds(7200);
        UserTapSession existing = UserTapSession.createFor(user, startedAt, startedAt.plusSeconds(1800), 999);
        existing.addValidTaps(500);
        existing.recordActivity(now.minusSeconds(1801), 1800);
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        BoxProgressSnapshot snapshot = userTapSessionService.getBoxProgress(user, now, sessionConfig());

        // 화면에는 리셋 이후 값을 보여준다. 다음 탭이 확정할 값과 같다.
        assertThat(snapshot.cumulativeValidTapCount()).isZero();
        assertThat(snapshot.nextBoxTarget()).isEqualTo(25);

        // 그러나 저장하지 않는다. 저장하면 탭 배치와 같은 행을 동시에 갱신해 낙관적 락이 깨진다.
        verify(userTapSessionRepository, never()).save(any());
        assertThat(existing.getSessionValidTapCount()).isEqualTo(500);
        assertThat(existing.getNextBoxTarget()).isEqualTo(999);
    }

    @Test
    void getBoxProgressReturnsFreshLadderWithoutCreatingSessionWhenNoneExists() {
        AppUser user = stubUser();
        when(userTapSessionRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        BoxProgressSnapshot snapshot = userTapSessionService.getBoxProgress(user, now, sessionConfig());

        assertThat(snapshot.cumulativeValidTapCount()).isZero();
        assertThat(snapshot.nextBoxTarget()).isEqualTo(25);
        verify(userTapSessionRepository, never()).save(any()); // 조회가 세션을 만들지 않는다
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
        when(config.boxSessionIdleTimeoutSeconds()).thenReturn(1800);
        return config;
    }
}
