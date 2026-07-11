package com.ggukmoney.beanzip.domain.point.service;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.entity.PointLedger;
import com.ggukmoney.beanzip.domain.point.repository.PointLedgerRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointLedgerService {

    private final PointLedgerRepository pointLedgerRepository;

    public boolean isAlreadyRecorded(UUID userId, UUID idempotencyKey) {
        return pointLedgerRepository.existsByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    public void recordCredit(PointAccount account, AppUser user, long amount, String reason, UUID idempotencyKey) {
        pointLedgerRepository.save(PointLedger.createCredit(account, user, amount, reason, idempotencyKey));
    }
}
