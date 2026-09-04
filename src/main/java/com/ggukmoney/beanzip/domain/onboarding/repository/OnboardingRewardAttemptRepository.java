package com.ggukmoney.beanzip.domain.onboarding.repository;

import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OnboardingRewardAttemptRepository extends JpaRepository<OnboardingRewardAttempt, Long> {

    Optional<OnboardingRewardAttempt> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt
            from OnboardingRewardAttempt attempt
            join fetch attempt.rewardKeycap rewardKeycap
            join fetch attempt.bonusRewardKeycap bonusRewardKeycap
            where attempt.publicId = :publicId
            """)
    Optional<OnboardingRewardAttempt> findByPublicIdWithRewardKeycapForUpdate(@Param("publicId") UUID publicId);

    @Query("""
            select attempt
            from OnboardingRewardAttempt attempt
            join fetch attempt.rewardKeycap rewardKeycap
            join fetch attempt.bonusRewardKeycap bonusRewardKeycap
            where attempt.tapSessionId = :tapSessionId
            """)
    Optional<OnboardingRewardAttempt> findByTapSessionIdWithRewardKeycap(@Param("tapSessionId") UUID tapSessionId);
}
