package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RankingSeasonRepository extends JpaRepository<RankingSeason, Long> {

    Optional<RankingSeason> findByCode(String code);

    Optional<RankingSeason> findByCodeAndStatus(String code, RankingSeasonStatus status);

    Optional<RankingSeason> findByRankingTypeAndStatus(RankingType rankingType, RankingSeasonStatus status);

    List<RankingSeason> findByRankingTypeAndStatusOrderByEndsAtAsc(RankingType rankingType, RankingSeasonStatus status);

    Optional<RankingSeason> findByRankingTypeAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThan(
            RankingType rankingType,
            RankingSeasonStatus status,
            Instant startsAt,
            Instant endsAt
    );

    Optional<RankingSeason> findFirstByRankingTypeAndStatusAndEndsAtLessThanEqualOrderByEndsAtDesc(
            RankingType rankingType,
            RankingSeasonStatus status,
            Instant endsAt
    );
}
