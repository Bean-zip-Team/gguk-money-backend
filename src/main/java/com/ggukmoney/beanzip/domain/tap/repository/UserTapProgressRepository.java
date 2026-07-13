package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTapProgressRepository extends JpaRepository<UserTapProgress, Long> {

    Optional<UserTapProgress> findByUserId(UUID userId);
}
