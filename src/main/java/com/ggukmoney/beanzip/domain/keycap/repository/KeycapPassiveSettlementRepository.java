package com.ggukmoney.beanzip.domain.keycap.repository;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapPassiveSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface KeycapPassiveSettlementRepository extends JpaRepository<KeycapPassiveSettlement,Long> {
    Optional<KeycapPassiveSettlement> findByUserIdAndIdempotencyKey(UUID userId,String key);
}
