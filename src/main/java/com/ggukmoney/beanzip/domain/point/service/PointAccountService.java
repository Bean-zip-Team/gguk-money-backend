package com.ggukmoney.beanzip.domain.point.service;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointAccountService {

    private final PointAccountRepository pointAccountRepository;

    public PointAccount createFor(AppUser user) {
        return pointAccountRepository.save(PointAccount.createFor(user));
    }

    public PointAccount getForUser(UUID userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POINT_ACCOUNT_NOT_FOUND"));
    }

    public long getBalance(UUID userId) {
        return getForUser(userId).getBalance();
    }

    public PointAccount credit(UUID userId, long amount) {
        PointAccount account = getForUser(userId);
        account.credit(amount);
        return pointAccountRepository.save(account);
    }

    public PointAccount debit(UUID userId, long amount) {
        PointAccount account = getForUser(userId);
        account.debit(amount);
        return pointAccountRepository.save(account);
    }
}
