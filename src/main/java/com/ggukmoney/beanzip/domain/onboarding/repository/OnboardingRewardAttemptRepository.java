package com.ggukmoney.beanzip.domain.onboarding.repository;

import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OnboardingRewardAttemptRepository extends JpaRepository<OnboardingRewardAttempt, Long> {

    Optional<OnboardingRewardAttempt> findByPublicId(UUID publicId);

    @Query("""
            select attempt
            from OnboardingRewardAttempt attempt
            join fetch attempt.rewardKeycap rewardKeycap
            where attempt.tapSessionId = :tapSessionId
            """)
    Optional<OnboardingRewardAttempt> findByTapSessionIdWithRewardKeycap(@Param("tapSessionId") UUID tapSessionId);
}
