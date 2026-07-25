package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserIdAndType(UUID userId, NotificationType type);

    @Query("""
            SELECT preference.userId
            FROM NotificationPreference preference
            WHERE preference.type = :type
              AND preference.enabled = true
              AND preference.agreementStatus = com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus.AGREED
            """)
    List<UUID> findSendableUserIdsByType(@Param("type") NotificationType type);
}
