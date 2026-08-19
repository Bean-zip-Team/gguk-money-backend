package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapSession;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapSessionRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserTapSessionService {

    private final UserTapSessionRepository userTapSessionRepository;

    public UserTapSession createFor(AppUser user, Instant now, TapPolicyConfig config) {
        Instant expiresAt = now.plusSeconds(config.boxSessionMaxDurationSeconds());
        int initialBoxTarget = drawNextBoxTargetInSession(0, 0, config);
        return userTapSessionRepository.save(UserTapSession.createFor(user, now, expiresAt, initialBoxTarget));
    }

    /**
     * Fetches the user's tap session, resetting it first if the 1-hour hard cap has
     * elapsed since session start. Always persists (creation, reset, or activity touch)
     * so read-only status endpoints stay consistent with what a subsequent tap batch would see.
     */
    public UserTapSession getOrCreateActiveSession(AppUser user, Instant now, TapPolicyConfig config) {
        return userTapSessionRepository.findByUserId(user.getId())
                .map(session -> refreshSession(session, now, config))
                .orElseGet(() -> createFor(user, now, config));
    }

    public BoxProgressSnapshot getBoxProgress(AppUser user, Instant now, TapPolicyConfig config) {
        UserTapSession session = getOrCreateActiveSession(user, now, config);
        return new BoxProgressSnapshot(session.getSessionValidTapCount(), session.getNextBoxTarget());
    }

    public UserTapSession save(UserTapSession session) {
        return userTapSessionRepository.save(session);
    }

    public int drawNextBoxTargetInSession(long cumulativeSessionTaps, int boxesDroppedInSession, TapPolicyConfig config) {
        int step = stepForBoxIndex(boxesDroppedInSession + 1, config);
        return (int) (cumulativeSessionTaps + step);
    }

    private UserTapSession refreshSession(UserTapSession session, Instant now, TapPolicyConfig config) {
        if (session.isExpired(now)) {
            Instant expiresAt = now.plusSeconds(config.boxSessionMaxDurationSeconds());
            session.resetFor(now, expiresAt, drawNextBoxTargetInSession(0, 0, config));
        } else {
            session.recordActivity(now);
        }
        return userTapSessionRepository.save(session);
    }

    private int stepForBoxIndex(int boxIndex, TapPolicyConfig config) {
        return switch (boxIndex) {
            case 1 -> config.boxSessionStep1();
            case 2 -> config.boxSessionStep2();
            case 3 -> config.boxSessionStep3();
            case 4 -> config.boxSessionStep4();
            case 5 -> config.boxSessionStep5();
            default -> config.boxSessionTailStep();
        };
    }
}
