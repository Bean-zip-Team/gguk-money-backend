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

    @Query(value = """
            SELECT preference.enabled AS enabled,
                   preference.agreement_status::text AS agreementStatus,
                   user_record.status::text AS userStatus
            FROM notification_preference preference
            JOIN app_user user_record ON user_record.id = preference.user_id
            WHERE preference.user_id = :userId
              AND preference.notification_type = 'RANK_CHANGE'
            """, nativeQuery = true)
    Optional<WeeklyResetEligibility> findWeeklyResetEligibility(@Param("userId") UUID userId);

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

    @Query(value = """
            SELECT preference.id AS preferenceId,
                   preference.user_id AS userId
            FROM notification_preference preference
            JOIN ranking_entry entry ON entry.user_id = preference.user_id
            JOIN ranking_season season ON season.id = entry.season_id
            JOIN app_user user_record ON user_record.id = preference.user_id
            WHERE preference.notification_type = 'RANK_CHANGE'
              AND preference.enabled = true
              AND preference.agreement_status = 'AGREED'
              AND preference.id > :lastPreferenceId
              AND entry.season_id = :seasonId
              AND entry.final_rank IS NOT NULL
              AND entry.finalized_at IS NOT NULL
              AND season.ranking_type = 'WEEKLY'
              AND season.status = 'CLOSED'
              AND user_record.status = 'ACTIVE'
            ORDER BY preference.id ASC
            LIMIT :#{#pageable.pageSize}
            """, nativeQuery = true)
    List<SendableCandidate> findWeeklyResetCandidates(
            @Param("seasonId") Long seasonId,
            @Param("lastPreferenceId") long lastPreferenceId,
            Pageable pageable
    );

    interface SendableCandidate {
        Long getPreferenceId();

        UUID getUserId();
    }

    interface WeeklyResetEligibility {
        Boolean getEnabled();

        String getAgreementStatus();

        String getUserStatus();
    }
}
