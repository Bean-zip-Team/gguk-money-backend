package com.ggukmoney.beanzip.domain.booster.repository;

import com.ggukmoney.beanzip.domain.booster.entity.BoosterGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BoosterGrantRepository extends JpaRepository<BoosterGrant, Long> {

    Optional<BoosterGrant> findByUserIdAndStatusAndExpiresAtAfter(UUID userId, BoosterGrant.Status status, Instant now);

    long countByUserIdAndGrantDate(UUID userId, LocalDate grantDate);
}
