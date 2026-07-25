package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationRankState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRankStateRepository extends JpaRepository<NotificationRankState, Long> {

    Optional<NotificationRankState> findByUserIdAndSeasonId(UUID userId, Long seasonId);
}
