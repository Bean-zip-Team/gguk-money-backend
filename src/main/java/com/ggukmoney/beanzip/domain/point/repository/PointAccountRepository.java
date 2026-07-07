package com.ggukmoney.beanzip.domain.point.repository;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

    Optional<PointAccount> findByUserId(UUID userId);
}
