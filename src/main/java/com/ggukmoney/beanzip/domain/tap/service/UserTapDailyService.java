package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class UserTapDailyService {

    private final UserTapDailyRepository userTapDailyRepository;
    private final Random random = new SecureRandom();

    public UserTapDaily getOrCreateToday(AppUser user, TapPolicyConfig config) {
        LocalDate today = LocalDate.now();
        return userTapDailyRepository.findByUserIdAndTapDate(user.getId(), today)
                .orElseGet(() -> {
                    int initialTarget = drawNextTarget(0, 0, config);
                    return userTapDailyRepository.save(UserTapDaily.createFor(user, today, initialTarget));
                });
    }

    public UserTapDaily save(UserTapDaily daily) {
        return userTapDailyRepository.save(daily);
    }

    public int drawNextTarget(int currentValidTapCount, int pointEarnedAmountToday, TapPolicyConfig config) {
        boolean decelerating = pointEarnedAmountToday >= config.decelThresholdPoints();
        int base = decelerating ? config.curveDecelBase() : config.curveGeneralBase();
        double variance = decelerating ? config.curveDecelVariance() : config.curveGeneralVariance();
        int increment = drawUniform(base, variance);
        return currentValidTapCount + increment;
    }

    private int drawUniform(int base, double variance) {
        int lowerBound = (int) Math.round(base * (1 - variance));
        int upperBound = (int) Math.round(base * (1 + variance));
        return lowerBound + random.nextInt(upperBound - lowerBound + 1);
    }
}
