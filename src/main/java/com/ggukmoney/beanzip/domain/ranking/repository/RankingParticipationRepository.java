package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RankingParticipationRepository extends JpaRepository<RankingParticipation, Long> {

    Optional<RankingParticipation> findByPublicId(UUID publicId);
}
