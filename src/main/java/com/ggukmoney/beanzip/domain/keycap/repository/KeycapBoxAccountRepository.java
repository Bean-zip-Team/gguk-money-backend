package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KeycapBoxAccountRepository extends JpaRepository<KeycapBoxAccount, Long> {

    Optional<KeycapBoxAccount> findByPublicId(UUID publicId);

    Optional<KeycapBoxAccount> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from KeycapBoxAccount account
            where account.user.id = :userId
            """)
    Optional<KeycapBoxAccount> findByUserIdForUpdate(@Param("userId") UUID userId);

    @Query("""
            select account.id as accountId, account.user.id as userId
            from KeycapBoxAccount account, NotificationPreference preference
            where preference.userId = account.user.id
              and preference.type = :type
              and preference.enabled = true
              and preference.agreementStatus = com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus.AGREED
              and account.id > :lastAccountId
              and account.boxBalance > 0
              and account.freeOpenUsedCount >= :freeOpenLimit
              and account.adOpenUsedCount >= :adOpenLimit
              and account.openCycleStartedAt <= :cutoff
            order by account.id asc
            """)
    List<KeycapBoxOpenAvailableCandidate> findKeycapBoxOpenAvailableCandidates(
            @Param("type") NotificationType type,
            @Param("lastAccountId") long lastAccountId,
            @Param("freeOpenLimit") int freeOpenLimit,
            @Param("adOpenLimit") int adOpenLimit,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    interface KeycapBoxOpenAvailableCandidate {
        Long getAccountId();

        UUID getUserId();
    }
}
