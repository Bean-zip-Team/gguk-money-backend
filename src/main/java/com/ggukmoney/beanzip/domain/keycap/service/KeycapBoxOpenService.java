package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.booster.service.BoosterGrantService;
import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxOpenService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final String POINT_REASON_KEYCAP_ALL_COMPLETE = "KEYCAP_ALL_COMPLETE_BONUS";
    private static final long ALL_COMPLETE_BONUS_POINT_AMOUNT = 1;

    private final KeycapBoxAccountService keycapBoxAccountService;
    private final KeycapBoxOpenRepository keycapBoxOpenRepository;
    private final KeycapRepository keycapRepository;
    private final UserKeycapRepository userKeycapRepository;
    private final UserService userService;
    private final KeycapRewardSelector keycapRewardSelector;
    private final KeycapShardCountGenerator shardCountGenerator;
    private final KeycapBoxOpenRequestHasher requestHasher;
    private final KeycapBoxMapper keycapBoxMapper;
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final BoosterGrantService boosterGrantService;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public KeycapBoxOpenResponse open(UUID userId, String idempotencyKey, KeycapBoxOpenRequest request) {
        validateIdempotencyKey(idempotencyKey);
        String requestHash = requestHasher.hash(request);

        Optional<KeycapBoxOpenResponse> replay = findReplay(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            return transactionTemplate.execute(status -> openInTransaction(userId, idempotencyKey, request, requestHash));
        } catch (DataIntegrityViolationException exception) {
            return findReplay(userId, idempotencyKey, requestHash)
                    .orElseThrow(() -> duplicateAdRewardException(request, exception));
        }
    }

    private KeycapBoxOpenResponse openInTransaction(
            UUID userId,
            String idempotencyKey,
            KeycapBoxOpenRequest request,
            String requestHash
    ) {
        Optional<KeycapBoxOpenResponse> replay = findReplay(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        Instant acceptedAt = clock.instant();
        KeycapBoxAccount account = keycapBoxAccountService.refreshOpenCycleForUpdate(
                userId,
                acceptedAt,
                keycapBoxPolicyConfig.openCycleDuration()
        );
        boolean isAdOpen = request.openMethod() == KeycapBoxOpen.OpenMethod.ADVERTISEMENT;
        if (isAdOpen) {
            validateAdOpenResources(account, request);
        } else {
            validateFreeOpenResources(account);
        }

        List<Keycap> candidates = keycapRepository.findIncompleteActiveRewardCandidates(userId);
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "KEYCAP_REWARD_NOT_AVAILABLE");
        }

        Keycap selected = keycapRewardSelector.select(candidates);
        if (isAdOpen) {
            account.consumeAdOpen(keycapBoxPolicyConfig.adOpenLimit());
        } else {
            account.consumeFreeOpen(keycapBoxPolicyConfig.freeOpenLimit());
        }

        AppUser user = userService.getById(userId);
        UserKeycap userKeycap = userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, selected.getId())
                .orElseGet(() -> UserKeycap.createInProgress(user, selected));
        int shardCountBefore = userKeycap.getShardCount();
        int boostedShardCount = applyBoosterMultiplier(shardCountGenerator.generate(), userId, acceptedAt);
        boolean completedNow = userKeycap.addShard(boostedShardCount, acceptedAt);
        int grantedShardCount = userKeycap.getShardCount() - shardCountBefore;
        if (userKeycap.getId() == null) {
            userKeycapRepository.save(userKeycap);
        }

        if (completedNow) {
            awardAllCompleteBonusIfEligible(userId, user);
        }

        KeycapBoxOpen boxOpen = KeycapBoxOpen.createFor(
                user,
                request.openMethod(),
                selected,
                grantedShardCount,
                idempotencyKey,
                requestHash,
                normalizeAdRewardId(request.adRewardId()),
                completedNow,
                acceptedAt
        );
        return keycapBoxMapper.mapToOpenResponse(keycapBoxOpenRepository.save(boxOpen));
    }

    /**
     * Figma SPEC(548:13) "완성 조각 풀 제외 · 전 종류 완성 시 +1P"를 반영한 일회성 완주 보너스.
     * 카탈로그를 이후 확장하더라도 다시 지급되지 않도록 유저당 고정된 idempotencyKey로 지급 여부를 확인한다.
     */
    private int applyBoosterMultiplier(int rolledShardCount, UUID userId, Instant now) {
        BigDecimal multiplier = boosterGrantService.findActiveMultiplier(userId, now);
        return BigDecimal.valueOf(rolledShardCount)
                .multiply(multiplier)
                .setScale(0, RoundingMode.DOWN)
                .intValueExact();
    }

    private void awardAllCompleteBonusIfEligible(UUID userId, AppUser user) {
        long completedCount = userKeycapRepository.countByUserIdAndStatus(userId, UserKeycap.Status.COMPLETED);
        long activeCatalogCount = keycapRepository.countByActiveTrue();
        if (activeCatalogCount == 0 || completedCount < activeCatalogCount) {
            return;
        }

        UUID bonusIdempotencyKey = allCompleteBonusIdempotencyKey(userId);
        if (pointLedgerService.isAlreadyRecorded(userId, bonusIdempotencyKey)) {
            return;
        }

        PointAccount account = pointAccountService.credit(userId, ALL_COMPLETE_BONUS_POINT_AMOUNT);
        pointLedgerService.recordCredit(
                account,
                user,
                ALL_COMPLETE_BONUS_POINT_AMOUNT,
                POINT_REASON_KEYCAP_ALL_COMPLETE,
                bonusIdempotencyKey
        );
    }

    private UUID allCompleteBonusIdempotencyKey(UUID userId) {
        return UUID.nameUUIDFromBytes((userId + "-" + POINT_REASON_KEYCAP_ALL_COMPLETE).getBytes(StandardCharsets.UTF_8));
    }

    private Optional<KeycapBoxOpenResponse> findReplay(UUID userId, String idempotencyKey, String requestHash) {
        return keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey)
                .map(existing -> {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
                    }
                    return keycapBoxMapper.mapToOpenResponse(existing);
                });
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    private void validateFreeOpenResources(KeycapBoxAccount account) {
        if (!account.hasBox()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KEYCAP_BOX_NOT_AVAILABLE");
        }
        if (!account.canUseFreeOpen(keycapBoxPolicyConfig.freeOpenLimit())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "FREE_OPEN_LIMIT_EXCEEDED");
        }
    }

    // Ad reward validation is trust-based; the shared cycle limit remains the server-side guard.
    private void validateAdOpenResources(KeycapBoxAccount account, KeycapBoxOpenRequest request) {
        if (!account.hasBox()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KEYCAP_BOX_NOT_AVAILABLE");
        }
        if (!account.canUseAdOpen(keycapBoxPolicyConfig.adOpenLimit())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AD_OPEN_LIMIT_EXCEEDED");
        }
        if (!StringUtils.hasText(request.adRewardId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AD_REWARD_ID_REQUIRED");
        }
        if (keycapBoxOpenRepository.existsByAdRewardId(normalizeAdRewardId(request.adRewardId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AD_REWARD_ALREADY_USED");
        }
    }

    private RuntimeException duplicateAdRewardException(
            KeycapBoxOpenRequest request,
            DataIntegrityViolationException exception
    ) {
        String adRewardId = normalizeAdRewardId(request.adRewardId());
        if (StringUtils.hasText(adRewardId) && keycapBoxOpenRepository.existsByAdRewardId(adRewardId)) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "AD_REWARD_ALREADY_USED", exception);
        }
        return exception;
    }

    private String normalizeAdRewardId(String adRewardId) {
        return StringUtils.hasText(adRewardId) ? adRewardId.trim() : null;
    }
}
