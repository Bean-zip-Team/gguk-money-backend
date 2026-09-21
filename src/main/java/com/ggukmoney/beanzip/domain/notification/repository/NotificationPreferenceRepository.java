package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserIdAndType(UUID userId, NotificationType type);

    /** 알림 동의가 실제로 기록됐는지. 일괄 개봉 보상의 자격 조건이다 (BEA-280). */
    boolean existsByUserIdAndAgreementStatus(UUID userId, NotificationAgreementStatus agreementStatus);

    @Query("""
            SELECT preference.id AS preferenceId,
                   preference.userId AS userId
            FROM NotificationPreference preference
            WHERE preference.type = :type
              AND preference.enabled = true
              AND preference.agreementStatus = com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus.AGREED
              AND preference.id > :lastPreferenceId
            ORDER BY preference.id ASC
            """)
    List<SendableCandidate> findSendableCandidates(
            @Param("type") NotificationType type,
            @Param("lastPreferenceId") long lastPreferenceId,
            Pageable pageable
    );

    interface SendableCandidate {
        Long getPreferenceId();

        UUID getUserId();
    }
}
