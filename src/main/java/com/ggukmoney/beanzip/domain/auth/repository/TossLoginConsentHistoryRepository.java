package com.ggukmoney.beanzip.domain.auth.repository;

import com.ggukmoney.beanzip.domain.auth.entity.TossLoginConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TossLoginConsentHistoryRepository extends JpaRepository<TossLoginConsentHistory, Long> {

    List<TossLoginConsentHistory> findByUserIdOrderByOccurredAtAscIdAsc(UUID userId);
}
