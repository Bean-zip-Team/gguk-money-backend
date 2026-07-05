package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByPublicId(UUID publicId);
}
