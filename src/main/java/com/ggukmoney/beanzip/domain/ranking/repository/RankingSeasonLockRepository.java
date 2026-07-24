package com.ggukmoney.beanzip.domain.ranking.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RankingSeasonLockRepository {

    private final EntityManager entityManager;

    public void acquireWeeklyRankingTransactionLock(long lockKey) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
