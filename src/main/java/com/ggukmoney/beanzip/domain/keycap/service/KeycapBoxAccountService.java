package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.ShardLedger;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.ShardLedgerRepository;
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
    private final com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository userKeycaps;
    private final com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig policy;
    private final ShardLedgerRepository shardLedgers;

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

    /** Caller holds the common user row lock in its transaction. */
    public KeycapBoxAccount getForUserForUpdate(UUID userId) {
        return keycapBoxAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
    }

    /** Draw calls this after phase confirmation, before affordability and candidate lookup. */
    public void convertLegacyProgressForDraw(UUID userId, KeycapBoxAccount wallet) {
        var progress = userKeycaps.findLegacyInProgressForUpdate(userId);
        int shards = Math.toIntExact(progress.stream().mapToLong(k -> k.getShardCount()).sum());
        if (shards > 0) {
            wallet.addShards(shards);
            recordShardChange(userId, wallet, shards, ShardLedger.Reason.ECONOMY_MIGRATION, null);
        }
        if (!progress.isEmpty()) { userKeycaps.deleteAll(progress); userKeycaps.flush(); }
    }

    /**
     * 지갑 잔액을 바꾼 같은 트랜잭션에서 원장을 남긴다 (BEA-390).
     *
     * <p>경제 전환 전에는 같은 컬럼이 조각이 아니라 상자 개수라서 남기지 않는다. 전환 여부는 캐시된
     * 값을 먼저 본다. 탭 배치마다 지나는 경로라, 전환된 뒤에는 설정을 다시 읽지 않는다. 캐시가 '전환
     * 전'이라고 할 때만 설정을 직접 읽어 확인한다. 기동 직후 첫 읽기에 실패하면 캐시가 최대 60초 동안
     * 기본값(전환 전)으로 남는데, 그동안의 변동이 원장에서 빠지면 발행량 기준값에 구멍이 생긴다.
     *
     * @param delta 지급은 양수, 사용은 음수. 0이면 아무것도 하지 않는다
     */
    public void recordShardChange(UUID userId, KeycapBoxAccount wallet, int delta, ShardLedger.Reason reason, String refId) {
        if (delta == 0 || !(policy.shardEconomyAtLastRefresh() || policy.isShardEconomyEnabled())) {
            return;
        }
        shardLedgers.save(ShardLedger.record(userId, delta, reason, refId, wallet.getShardBalance(), clock.instant()));
    }

    /**
     * 조각을 지급하고 원장을 남긴다. 호출한 쪽이 이 사용자의 행 잠금을 잡고 있어야 한다.
     *
     * @return 지급 후 지갑
     */
    public KeycapBoxAccount grantShards(UUID userId, int amount, ShardLedger.Reason reason, String refId) {
        KeycapBoxAccount wallet = getForUserForUpdate(userId);
        wallet.addShards(amount);
        keycapBoxAccountRepository.save(wallet);
        recordShardChange(userId, wallet, amount, reason, refId);
        return wallet;
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

    public KeycapBoxAccount refreshOpenCycleForUpdate(UUID userId, Instant now, Duration cycleDuration) {
        KeycapBoxAccount account = getLegacyAccountForUpdate(userId);
        account.refreshOpenCycle(now, cycleDuration);
        return account;
    }
}
