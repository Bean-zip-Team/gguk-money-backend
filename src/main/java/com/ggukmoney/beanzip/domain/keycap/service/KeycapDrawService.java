package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapDrawResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapDraw;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapDrawRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.promotion.service.KeycapFiveCompletionTrigger;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionGrantIssuer;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionTriggerContext;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 조각으로 키캡을 뽑는다 (BEA-329).
 *
 * <p>가격만큼 조각을 빼고 상시 키캡 중 하나를 등급 가중 추첨한다. 중복 방지는 하지 않는다 —
 * 이미 보유한 키캡이 나오면 레벨이 오른다. 광고는 붙지 않는다.
 *
 * <p>멱등: 같은 {@code Idempotency-Key} 재호출은 처음 결과를 그대로 돌려준다. 동시 호출은
 * {@code uq_keycap_draw_user_idempotency} 가 막고, 진 쪽은 이긴 쪽 결과를 재생한다.
 */
@Service
@RequiredArgsConstructor
public class KeycapDrawService {

    private static final Logger log = LoggerFactory.getLogger(KeycapDrawService.class);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final String POINT_REASON_KEYCAP_ALL_COMPLETE = "KEYCAP_ALL_COMPLETE_BONUS";
    private static final long ALL_COMPLETE_BONUS_POINT_AMOUNT = 1;

    private final KeycapBoxAccountService keycapBoxAccountService;
    private final KeycapDrawRepository keycapDrawRepository;
    private final KeycapRepository keycapRepository;
    private final UserKeycapRepository userKeycapRepository;
    private final UserService userService;
    private final KeycapRewardSelector keycapRewardSelector;
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final PromotionGrantIssuer promotionGrantIssuer;
    private final KeycapFiveCompletionTrigger keycapFiveCompletionTrigger;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public KeycapDrawResponse draw(UUID userId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        Optional<KeycapDrawResponse> replay = findReplay(userId, idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }

        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            return transactionTemplate.execute(status -> drawInTransaction(userId, idempotencyKey));
        } catch (DataIntegrityViolationException exception) {
            return findReplay(userId, idempotencyKey).orElseThrow(() -> exception);
        }
    }

    private KeycapDrawResponse drawInTransaction(UUID userId, String idempotencyKey) {
        Optional<KeycapDrawResponse> replay = findReplay(userId, idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }

        Instant drawnAt = clock.instant();
        int price = keycapBoxPolicyConfig.drawPrice();
        KeycapBoxAccount wallet = keycapBoxAccountService.getForUserForUpdate(userId);
        if (!wallet.canAfford(price)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KEYCAP_SHARD_INSUFFICIENT");
        }

        // 차감보다 먼저 확인한다. 후보가 없는데 조각을 쓰면 그대로 날아간다.
        List<Keycap> candidates = keycapRepository
                .findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX);
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "KEYCAP_REWARD_NOT_AVAILABLE");
        }
        Keycap selected = keycapRewardSelector.select(candidates);

        AppUser user = userService.getById(userId);
        Optional<UserKeycap> owned = userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, selected.getId());
        boolean newlyAcquired = owned.isEmpty();
        UserKeycap userKeycap = owned.orElseGet(() -> userKeycapRepository.save(UserKeycap.createOwned(user, selected, drawnAt)));
        if (!newlyAcquired) {
            userKeycap.levelUp();
        }

        wallet.consumeShards(price);

        if (newlyAcquired) {
            long completedCount = userKeycapRepository.countByUserIdAndStatus(userId, UserKeycap.Status.COMPLETED);
            awardAllCompleteBonusIfEligible(userId, user);
            promotionGrantIssuer.issueIfEligible(
                    keycapFiveCompletionTrigger,
                    PromotionTriggerContext.keycapCompleted(user, completedCount, drawnAt));
        }

        KeycapDraw draw = keycapDrawRepository.save(KeycapDraw.createFor(
                user, selected, price, userKeycap.getLevel(), newlyAcquired, idempotencyKey, drawnAt));
        log.info("Keycap drawn: userId={} keycapId={} newlyAcquired={} level={} shardBalance={}",
                userId, selected.getId(), newlyAcquired, userKeycap.getLevel(), wallet.getShardBalance());
        return toResponse(draw, wallet.getShardBalance());
    }

    /**
     * 전체 완성 보너스는 상자 풀({@code BOX})만 보고 판정한다(BEA-285).
     *
     * <p>기준 개수와 완성 개수를 <b>둘 다</b> BOX 로 센다. 기준만 BOX 로 바꾸면 이벤트 키캡이 상자 키캡 한 종을
     * 대신 채워, 상자 키캡을 다 모으지 않았는데도 보너스가 나간다. 키캡 5개 미션은 이벤트 키캡도 세므로
     * 호출부의 완성 개수는 미션 판정에만 그대로 넘긴다.
     */
    private void awardAllCompleteBonusIfEligible(UUID userId, AppUser user) {
        long boxCatalogCount = keycapRepository.countByAcquisitionTypeAndActiveTrue(Keycap.AcquisitionType.BOX);
        long completedBoxCount = userKeycapRepository.countByUserIdAndStatusAndKeycapAcquisitionType(
                userId, UserKeycap.Status.COMPLETED, Keycap.AcquisitionType.BOX);
        if (boxCatalogCount == 0 || completedBoxCount < boxCatalogCount) {
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

    private Optional<KeycapDrawResponse> findReplay(UUID userId, String idempotencyKey) {
        return keycapDrawRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey)
                .map(existing -> {
                    KeycapDrawResponse response = toResponse(existing, keycapBoxAccountService.getForUser(userId).getShardBalance());
                    log.info("Keycap draw replay: userId={} idempotencyKey={} keycapId={}",
                            userId, idempotencyKey, response.keycapId());
                    return response;
                });
    }

    private KeycapDrawResponse toResponse(KeycapDraw draw, int shardBalance) {
        Keycap keycap = draw.getKeycap();
        return new KeycapDrawResponse(
                draw.getPublicId(),
                keycap.getPublicId(),
                keycap.getCode(),
                keycap.getName(),
                keycap.getGrade().name(),
                keycap.getImageUrl(),
                keycap.getSoundUrl(),
                draw.isNewlyAcquired(),
                draw.getLevelAfter(),
                draw.getShardsSpent(),
                shardBalance,
                draw.getDrawnAt()
        );
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }
}
