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
        Instant expiresAt = now.plusSeconds(config.boxSessionIdleTimeoutSeconds());
        int initialBoxTarget = drawNextBoxTargetInSession(0, 0, config);
        return userTapSessionRepository.save(UserTapSession.createFor(user, now, expiresAt, initialBoxTarget));
    }

    /**
     * 유저의 탭 세션을 가져오되, 마지막 탭으로부터 유휴 시간(tap.box.session.idleTimeoutSeconds)이
     * 지났으면 먼저 리셋하고 그 결과를 영속화한다.
     *
     * <p><b>쓰기 경로 전용이다.</b> 세션을 만들거나 리셋하면서 version 을 올리므로 조회에서
     * 부르면 안 된다. 조회는 저장하지 않는 {@link #getBoxProgress} 를 쓴다. 이 구분이 무너지면
     * 상태 조회와 탭 배치가 같은 세션을 동시에 갱신해 낙관적 락 충돌이 난다.
     *
     * <p>유휴 마감 자체는 여기서 밀지 않는다. 마감을 미는 것은 탭이 인정된 경우뿐이며
     * {@link UserTapSession#recordActivity} 가 담당한다.
     */
    public UserTapSession getOrCreateActiveSession(AppUser user, Instant now, TapPolicyConfig config) {
        return userTapSessionRepository.findByUserId(user.getId())
                .map(session -> refreshSession(session, now, config))
                .orElseGet(() -> createFor(user, now, config));
    }

    /**
     * 조회 전용 상자 진행도. 세션이 아직 없거나 유휴로 만료됐으면 리셋 이후의 값을 계산해서
     * 돌려주되 <b>저장하지 않는다.</b>
     *
     * <p>조회가 리셋을 영속화하면 두 가지가 깨진다. 첫째로 유휴 만료의 의미가 무너진다.
     * 리셋을 저장하는 순간 유휴 창이 마지막 탭이 아니라 마지막 조회부터 다시 시작하므로,
     * 상태 폴링만 반복해도 세션이 살아남는다. 둘째로 동시성이 깨진다. 유휴 만료 시점은
     * "쉬었다 돌아와 다시 치는 순간"과 겹치는데 클라이언트는 그때 상태 조회와 탭 배치를
     * 거의 동시에 보낸다. 양쪽이 같은 version 을 읽고 각자 리셋을 저장하면 낙관적 락이 깨진다.
     *
     * <p>그래서 리셋을 실제로 남기는 곳은 탭 배치 경로 하나뿐이다. 조회가 보여주는 값과
     * 다음 탭이 확정할 값은 어차피 같다. 만료된 세션은 다음 탭에서 똑같이 리셋되기 때문이다.
     */
    public BoxProgressSnapshot getBoxProgress(AppUser user, Instant now, TapPolicyConfig config) {
        return userTapSessionRepository.findByUserId(user.getId())
                .filter(session -> !session.isExpired(now))
                .map(session -> new BoxProgressSnapshot(session.getSessionValidTapCount(), session.getNextBoxTarget()))
                .orElseGet(() -> new BoxProgressSnapshot(0L, drawNextBoxTargetInSession(0, 0, config)));
    }

    public UserTapSession save(UserTapSession session) {
        return userTapSessionRepository.save(session);
    }

    public int drawNextBoxTargetInSession(long cumulativeSessionTaps, int boxesDroppedInSession, TapPolicyConfig config) {
        int step = stepForBoxIndex(boxesDroppedInSession + 1, config);
        return (int) (cumulativeSessionTaps + step);
    }

    private UserTapSession refreshSession(UserTapSession session, Instant now, TapPolicyConfig config) {
        if (!session.isExpired(now)) {
            return session;
        }
        Instant expiresAt = now.plusSeconds(config.boxSessionIdleTimeoutSeconds());
        session.resetFor(now, expiresAt, drawNextBoxTargetInSession(0, 0, config));
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
