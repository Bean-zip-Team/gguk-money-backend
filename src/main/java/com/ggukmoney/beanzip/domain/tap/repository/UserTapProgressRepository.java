package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTapProgressRepository extends JpaRepository<UserTapProgress, Long> {

    Optional<UserTapProgress> findByUserId(UUID userId);

    @Query("""
            SELECT progress
            FROM UserTapProgress progress
            JOIN FETCH progress.user user
            WHERE user.status = com.ggukmoney.beanzip.domain.user.entity.AppUser$Status.ACTIVE
              AND progress.cumulativeValidTapCount > 0
            ORDER BY progress.updatedAt ASC, progress.id ASC
            """)
    List<UserTapProgress> findActivePositiveProgress(Pageable pageable);
}
