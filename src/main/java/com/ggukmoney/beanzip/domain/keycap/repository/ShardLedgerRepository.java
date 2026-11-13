package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.ShardLedger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShardLedgerRepository extends JpaRepository<ShardLedger, Long> {

    List<ShardLedger> findByUserIdOrderByIdAsc(UUID userId);
}
