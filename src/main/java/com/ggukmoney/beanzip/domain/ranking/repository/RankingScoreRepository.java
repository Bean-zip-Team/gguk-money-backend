package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RankingScoreRepository extends JpaRepository<RankingScore, Long> {

    Optional<RankingScore> findByPublicId(UUID publicId);
}
