package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserTapProgressService {

    private final UserTapProgressRepository userTapProgressRepository;
    private final Random random = new SecureRandom();

    public UserTapProgress createFor(AppUser user, TapPolicyConfig config) {
        int initialPointTarget = drawNextTarget(0, config);
        return userTapProgressRepository.save(UserTapProgress.createFor(user, initialPointTarget));
    }

    public UserTapProgress getForUser(UUID userId) {
        return userTapProgressRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TAP_PROGRESS_NOT_FOUND"));
    }

    public UserTapProgress save(UserTapProgress progress) {
        return userTapProgressRepository.save(progress);
    }

    /**
     * 다음 포인트 지급 지점을 뽑는다. 기준은 전 기간 누적 유효 탭이며, 간격은
     * base(20) 를 중심으로 ±variance(25%) 안에서 균등 추출한다. 즉 15~25탭마다 1P 다.
     *
     * <p>하루 적립량에 따라 간격을 벌리는 감속 커브가 한때 있었으나 정책에서 빠졌다.
     * 지급 간격은 하루 내내 균일하다.
     */
    public int drawNextTarget(long currentCumulativeTaps, TapPolicyConfig config) {
        int increment = drawUniform(config.curveGeneralBase(), config.curveGeneralVariance());
        return (int) (currentCumulativeTaps + increment);
    }

    private int drawUniform(int base, double variance) {
        int lowerBound = (int) Math.round(base * (1 - variance));
        int upperBound = (int) Math.round(base * (1 + variance));
        return lowerBound + random.nextInt(upperBound - lowerBound + 1);
    }
}
