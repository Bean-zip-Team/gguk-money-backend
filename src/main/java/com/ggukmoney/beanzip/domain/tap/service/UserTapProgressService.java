package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
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
        int initialPointTarget = drawNextTarget(0, 0, config);
        int initialBoxTarget = drawNextBoxTarget(0, config);
        return userTapProgressRepository.save(UserTapProgress.createFor(user, initialPointTarget, initialBoxTarget));
    }

    public UserTapProgress getForUser(UUID userId) {
        return userTapProgressRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TAP_PROGRESS_NOT_FOUND"));
    }

    public UserTapProgress save(UserTapProgress progress) {
        return userTapProgressRepository.save(progress);
    }

    public int drawNextTarget(long currentCumulativeTaps, int pointEarnedAmountToday, TapPolicyConfig config) {
        boolean decelerating = pointEarnedAmountToday >= config.decelThresholdPoints();
        int base = decelerating ? config.curveDecelBase() : config.curveGeneralBase();
        double variance = decelerating ? config.curveDecelVariance() : config.curveGeneralVariance();
        int increment = drawUniform(base, variance);
        return (int) (currentCumulativeTaps + increment);
    }

    public int drawNextBoxTarget(long currentCumulativeTaps, TapPolicyConfig config) {
        int increment = drawUniform(config.boxDropBase(), config.boxDropVariance());
        return (int) (currentCumulativeTaps + increment);
    }

    private int drawUniform(int base, double variance) {
        int lowerBound = (int) Math.round(base * (1 - variance));
        int upperBound = (int) Math.round(base * (1 + variance));
        return lowerBound + random.nextInt(upperBound - lowerBound + 1);
    }
}
