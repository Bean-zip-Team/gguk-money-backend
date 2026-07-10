package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UserTapDailyRepository extends JpaRepository<UserTapDaily, Long> {

    Optional<UserTapDaily> findByUserIdAndTapDate(UUID userId, LocalDate tapDate);
}
