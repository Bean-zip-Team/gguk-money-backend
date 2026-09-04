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

    public UserTapDaily getOrCreate(AppUser user, LocalDate tapDate) {
        if (tapDate == null) {
            throw new IllegalArgumentException("tapDate is required");
        }
        return userTapDailyRepository.findByUserIdAndTapDate(user.getId(), tapDate)
                .orElseGet(() -> userTapDailyRepository.save(UserTapDaily.createFor(user, tapDate)));
    }

    public UserTapDaily save(UserTapDaily daily) {
        return userTapDailyRepository.save(daily);
    }
}
