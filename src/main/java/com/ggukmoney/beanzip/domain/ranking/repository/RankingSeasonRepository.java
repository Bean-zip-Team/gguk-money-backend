package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RankingSeasonRepository extends JpaRepository<RankingSeason, Long> {

    Optional<RankingSeason> findByPublicId(UUID publicId);
}
