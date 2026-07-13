package com.ggukmoney.beanzip.domain.tap.service;

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

    public UserTapDaily getOrCreateToday(AppUser user) {
        LocalDate today = LocalDate.now();
        return userTapDailyRepository.findByUserIdAndTapDate(user.getId(), today)
                .orElseGet(() -> userTapDailyRepository.save(UserTapDaily.createFor(user, today)));
    }

    public UserTapDaily save(UserTapDaily daily) {
        return userTapDailyRepository.save(daily);
    }
}
