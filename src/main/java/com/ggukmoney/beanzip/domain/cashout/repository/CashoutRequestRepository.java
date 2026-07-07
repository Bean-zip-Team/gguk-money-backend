package com.ggukmoney.beanzip.domain.cashout.repository;

import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashoutRequestRepository extends JpaRepository<CashoutRequest, Long> {
}
