package com.ggukmoney.beanzip.domain.ranking.boost;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface RankingBoostRunRepository extends JpaRepository<RankingBoostRun, Long> {
    Optional<RankingBoostRun> findByRunDate(LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from RankingBoostRun run where run.runDate = :date")
    Optional<RankingBoostRun> findDateForUpdate(@Param("date") LocalDate date);

    @Query("select distinct run.selectedUserId from RankingBoostRun run where run.seasonId = :seasonId and run.status = com.ggukmoney.beanzip.domain.ranking.boost.RankingBoostRun$Status.APPLIED")
    Set<UUID> findSeasonRecipients(@Param("seasonId") Long seasonId);
}
