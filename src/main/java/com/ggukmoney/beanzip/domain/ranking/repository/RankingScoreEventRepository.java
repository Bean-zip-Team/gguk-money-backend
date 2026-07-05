package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingScoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RankingScoreEventRepository extends JpaRepository<RankingScoreEvent, Long> {

    Optional<RankingScoreEvent> findByPublicId(UUID publicId);
}
