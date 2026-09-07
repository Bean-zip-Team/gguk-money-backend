package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxBulkOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import com.ggukmoney.beanzip.global.util.TokenHash;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 알림 동의 보상 — 쌓인 상자를 한 번에 연다 (BEA-280).
 *
 * <p>개봉 주기·무료/광고 횟수 제한을 우회한다. 그게 보상의 내용이다. 대신 <b>1인 1회</b>이며,
 * 수령 여부는 {@code BULK_REWARD} 개봉 이력의 존재로 판정한다 — 별도 수령 기록 테이블을 두지
 * 않는다. Flyway 가 없어 새 스키마는 매번 수동 DDL 이 되고, 그게 사고 지점이었다.
 *
 * <p>보상 산정은 단건 개봉과 같은 코드({@link KeycapBoxOpenService#drawAndRecord})를 쓴다.
 * 확률·조각 수·완성 판정을 따로 구현하면 단건과 조용히 어긋난다.
 */
@Service
@RequiredArgsConstructor
public class KeycapBoxBulkOpenService {

    private static final Logger log = LoggerFactory.getLogger(KeycapBoxBulkOpenService.class);

    private final KeycapBoxOpenService keycapBoxOpenService;
    private final KeycapBoxAccountService keycapBoxAccountService;
    private final KeycapBoxOpenRepository keycapBoxOpenRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig;
    private final UserService userService;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public KeycapBoxBulkOpenResponse bulkOpen(UUID userId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        if (!notificationPreferenceRepository.existsByUserIdAndAgreementStatus(
                userId, NotificationAgreementStatus.AGREED)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOTIFICATION_CONSENT_REQUIRED");
        }

        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            return transactionTemplate.execute(status -> bulkOpenInTransaction(userId, idempotencyKey));
        } catch (DataIntegrityViolationException exception) {
            // 동시 호출이 같은 파생 키로 부딪혔다. 먼저 성공한 쪽 결과를 돌려준다.
            return replayOrThrow(userId, exception);
        }
    }

    private KeycapBoxBulkOpenResponse bulkOpenInTransaction(UUID userId, String idempotencyKey) {
        if (keycapBoxOpenRepository.existsByUserIdAndOpenMethod(userId, KeycapBoxOpen.OpenMethod.BULK_REWARD)) {
            // 1회성이자 멱등이다. 이미 받았으면 그때 연 결과를 그대로 재생한다.
            return summarizeAlreadyClaimed(userId);
        }

        Instant acceptedAt = clock.instant();
        KeycapBoxAccount account = keycapBoxAccountService.getForUserForUpdate(userId);
        int openCount = account.consumeForBulkOpen(keycapBoxPolicyConfig.bulkOpenLimit());

        if (openCount == 0) {
            // 줄 상자가 없다. 수령 기록을 남기지 않는다 — 준 게 없는데 1회 권리를 소모시키면
            // 나중에 상자가 쌓여도 영영 못 받는다.
            return new KeycapBoxBulkOpenResponse(0, 0, List.of(), account.getBoxBalance());
        }

        AppUser user = userService.getById(userId);
        List<KeycapBoxOpen> opened = new ArrayList<>(openCount);
        for (int index = 0; index < openCount; index++) {
            // 후보를 매 회 다시 조회한다. 완성된 종이 빠져야 이미 완성한 키캡이 또 뽑히지 않는다.
            opened.add(keycapBoxOpenService.drawAndRecord(
                    user,
                    KeycapBoxOpen.OpenMethod.BULK_REWARD,
                    derivedIdempotencyKey(idempotencyKey, index),
                    requestHash(idempotencyKey),
                    "",
                    acceptedAt,
                    keycapBoxOpenService.requireRewardCandidates(userId)
            ));
        }
        keycapBoxAccountService.save(account);

        log.info("Keycap boxes bulk opened: userId={} openedCount={} remainingBalance={}",
                userId, openCount, account.getBoxBalance());
        return summarize(opened, account.getBoxBalance());
    }

    private KeycapBoxBulkOpenResponse summarizeAlreadyClaimed(UUID userId) {
        List<KeycapBoxOpen> opened = keycapBoxOpenRepository.findAllByUserIdAndOpenMethodWithKeycap(
                userId, KeycapBoxOpen.OpenMethod.BULK_REWARD);
        return summarize(opened, keycapBoxAccountService.getForUser(userId).getBoxBalance());
    }

    private KeycapBoxBulkOpenResponse replayOrThrow(UUID userId, RuntimeException exception) {
        if (!keycapBoxOpenRepository.existsByUserIdAndOpenMethod(userId, KeycapBoxOpen.OpenMethod.BULK_REWARD)) {
            throw exception;
        }
        return summarizeAlreadyClaimed(userId);
    }

    private KeycapBoxBulkOpenResponse summarize(List<KeycapBoxOpen> opened, int remainingBoxBalance) {
        int totalShardCount = opened.stream().mapToInt(KeycapBoxOpen::getShardCount).sum();
        List<KeycapBoxBulkOpenResponse.CompletedKeycap> completed = opened.stream()
                .filter(KeycapBoxOpen::isCompleted)
                .map(boxOpen -> new KeycapBoxBulkOpenResponse.CompletedKeycap(
                        boxOpen.getKeycap().getPublicId(),
                        boxOpen.getKeycap().getName(),
                        boxOpen.getKeycap().getImageUrl()))
                .toList();
        return new KeycapBoxBulkOpenResponse(opened.size(), totalShardCount, completed, remainingBoxBalance);
    }

    /**
     * 행마다 다른 멱등키를 만든다. {@code uq_keycap_box_open_user_idempotency} 가
     * {@code (user_id, idempotency_key)} 유니크라 30 개 행에 같은 키를 쓸 수 없다.
     *
     * <p>결정적으로 파생시키므로 동시 호출이 같은 키로 충돌해 이중 개봉을 막는다.
     */
    private String derivedIdempotencyKey(String idempotencyKey, int index) {
        return TokenHash.sha256Base64Url("bulk=%s;index=%d".formatted(idempotencyKey, index));
    }

    private String requestHash(String idempotencyKey) {
        return TokenHash.sha256Base64Url("bulkOpen=%s".formatted(idempotencyKey));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }
}
