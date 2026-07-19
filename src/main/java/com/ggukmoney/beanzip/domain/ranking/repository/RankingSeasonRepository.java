package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RankingSeasonRepository extends JpaRepository<RankingSeason, Long> {

    Optional<RankingSeason> findByCode(String code);

    Optional<RankingSeason> findByCodeAndStatus(String code, RankingSeasonStatus status);
}
