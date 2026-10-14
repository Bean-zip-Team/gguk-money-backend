package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxAccountService {

    private final KeycapBoxAccountRepository keycapBoxAccountRepository;
    private final com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository userKeycaps;
    private final com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig policy;

    public KeycapBoxAccount createFor(AppUser user) {
        return keycapBoxAccountRepository.save(KeycapBoxAccount.createFor(user));
    }

    public KeycapBoxAccount save(KeycapBoxAccount account) {
        return keycapBoxAccountRepository.save(account);
    }

    public KeycapBoxAccount getForUser(UUID userId) {
        return keycapBoxAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
    }

    /** Caller holds the common user row lock in its transaction. */
    public KeycapBoxAccount getForUserForUpdate(UUID userId) {
        KeycapBoxAccount wallet = keycapBoxAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
        return wallet;
    }

    /** Draw calls this after phase confirmation, before affordability and candidate lookup. */
    public void convertLegacyProgressForDraw(UUID userId, KeycapBoxAccount wallet) {
        var progress = userKeycaps.findLegacyInProgressForUpdate(userId);
        int shards = Math.toIntExact(progress.stream().mapToLong(k -> k.getShardCount()).sum());
        if (shards > 0) wallet.addShards(shards);
        if (!progress.isEmpty()) { userKeycaps.deleteAll(progress); userKeycaps.flush(); }
    }

    public int getAvailableShardBalance(UUID userId) {
        return Math.toIntExact(keycapBoxAccountRepository.findAvailableShardBalance(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND")));
    }

    /** Legacy writes cannot enter the conversion path, including while another pod changes policy. */
    public KeycapBoxAccount getLegacyAccountForUpdate(UUID userId) {
        if (policy.isShardEconomyEnabled())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"KEYCAP_BOX_OPEN_RETIRED");
        return keycapBoxAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
    }

    public KeycapBoxAccount refreshOpenCycleForUpdate(UUID userId, java.time.Instant now, java.time.Duration duration) {
        KeycapBoxAccount account=getLegacyAccountForUpdate(userId);
        account.refreshOpenCycle(now,duration);
        return account;
    }
}
