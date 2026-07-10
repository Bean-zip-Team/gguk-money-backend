package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserTapDailyService {

    private final UserTapDailyRepository userTapDailyRepository;

    public UserTapDaily getOrCreateToday(AppUser user, TapCurveCalculator curveCalculator, TapPolicyConfig config) {
        LocalDate today = LocalDate.now();
        return userTapDailyRepository.findByUserIdAndTapDate(user.getId(), today)
                .orElseGet(() -> {
                    int initialTarget = curveCalculator.drawNextTarget(0, 0, config);
                    return userTapDailyRepository.save(UserTapDaily.createFor(user, today, initialTarget));
                });
    }

    public UserTapDaily save(UserTapDaily daily) {
        return userTapDailyRepository.save(daily);
    }
}
