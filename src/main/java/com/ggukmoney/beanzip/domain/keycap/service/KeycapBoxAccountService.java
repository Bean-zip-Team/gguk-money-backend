package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxAccountService {

    private final KeycapBoxAccountRepository keycapBoxAccountRepository;
    private final Clock clock;

    public KeycapBoxAccount createFor(AppUser user) {
        return keycapBoxAccountRepository.save(KeycapBoxAccount.createFor(user, clock.instant()));
    }

    public KeycapBoxAccount save(KeycapBoxAccount account) {
        return keycapBoxAccountRepository.save(account);
    }

    public KeycapBoxAccount getForUser(UUID userId) {
        return keycapBoxAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
    }

    public KeycapBoxAccount getForUserForUpdate(UUID userId) {
        return keycapBoxAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
    }

    public KeycapBoxAccount refreshOpenCycleForUpdate(UUID userId, Instant now, Duration cycleDuration) {
        KeycapBoxAccount account = getForUserForUpdate(userId);
        account.refreshOpenCycle(now, cycleDuration);
        return account;
    }
}
