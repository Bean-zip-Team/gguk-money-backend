package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxAccountService {

    private final KeycapBoxAccountRepository keycapBoxAccountRepository;
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig;
    private final Clock clock;

    public KeycapBoxAccount createFor(AppUser user) {
        return keycapBoxAccountRepository.save(KeycapBoxAccount.createFor(user, clock.instant()));
    }

    public KeycapBoxAccount addBoxes(UUID userId, int count) {
        KeycapBoxAccount account = keycapBoxAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
        account.addBoxes(count);
        return keycapBoxAccountRepository.save(account);
    }

    /**
     * 마지막 충전 이후 경과한 시간만큼 무료 개봉권을 충전하고 저장한다. 비관적 락으로 조회해
     * 동시 요청(상태 조회·개봉 시도)이 겹쳐도 충전 계산이 꼬이지 않는다.
     */
    public KeycapBoxAccount refillFreeTickets(UUID userId) {
        KeycapBoxAccount account = keycapBoxAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
        account.grantElapsedFreeTickets(clock.instant(), keycapBoxPolicyConfig.refillPerHour(), keycapBoxPolicyConfig.cap());
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
