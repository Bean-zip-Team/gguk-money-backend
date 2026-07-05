package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RankingRewardRepository extends JpaRepository<RankingReward, Long> {

    Optional<RankingReward> findByPublicId(UUID publicId);
}
