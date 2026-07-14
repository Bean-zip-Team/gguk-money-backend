package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxOpenService {

    private static final int DEFAULT_REWARD_SHARD_COUNT = 1;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final KeycapBoxAccountRepository keycapBoxAccountRepository;
    private final KeycapBoxOpenRepository keycapBoxOpenRepository;
    private final KeycapRepository keycapRepository;
    private final UserKeycapRepository userKeycapRepository;
    private final UserService userService;
    private final KeycapRewardSelector keycapRewardSelector;
    private final KeycapBoxOpenRequestHasher requestHasher;
    private final KeycapBoxMapper keycapBoxMapper;
    private final PlatformTransactionManager transactionManager;

    public KeycapBoxOpenResponse open(UUID userId, String idempotencyKey, KeycapBoxOpenRequest request) {
        validateIdempotencyKey(idempotencyKey);
        String requestHash = requestHasher.hash(request);

        Optional<KeycapBoxOpenResponse> replay = findReplay(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        if (request.openMethod() == KeycapBoxOpen.OpenMethod.ADVERTISEMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ADVERTISEMENT_OPEN_NOT_SUPPORTED");
        }

        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            return transactionTemplate.execute(status -> openInTransaction(userId, idempotencyKey, request, requestHash));
        } catch (DataIntegrityViolationException exception) {
            return findReplay(userId, idempotencyKey, requestHash)
                    .orElseThrow(() -> exception);
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

        KeycapBoxAccount account = keycapBoxAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
        validateFreeOpenResources(account);

        List<Keycap> candidates = keycapRepository.findIncompleteActiveRewardCandidates(userId);
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "KEYCAP_REWARD_NOT_AVAILABLE");
        }

        Keycap selected = keycapRewardSelector.select(candidates);
        account.consumeFreeOpen();

        AppUser user = userService.getById(userId);
        UserKeycap userKeycap = userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, selected.getId())
                .orElseGet(() -> UserKeycap.createInProgress(user, selected));
        boolean completedNow = userKeycap.addShard(DEFAULT_REWARD_SHARD_COUNT, Instant.now());
        if (userKeycap.getId() == null) {
            userKeycapRepository.save(userKeycap);
        }

        KeycapBoxOpen boxOpen = KeycapBoxOpen.createFor(
                user,
                request.openMethod(),
                selected,
                DEFAULT_REWARD_SHARD_COUNT,
                idempotencyKey,
                requestHash,
                normalizeAdRewardId(request.adRewardId()),
                completedNow,
                Instant.now()
        );
        return keycapBoxMapper.mapToOpenResponse(keycapBoxOpenRepository.save(boxOpen));
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
        if (!account.hasFreeOpenTicket()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FREE_OPEN_TICKET_NOT_AVAILABLE");
        }
    }

    private String normalizeAdRewardId(String adRewardId) {
        return StringUtils.hasText(adRewardId) ? adRewardId.trim() : null;
    }
}
