package com.ggukmoney.beanzip.domain.booster.service;

import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterActivateResponse;
import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterStatusResponse;
import com.ggukmoney.beanzip.domain.booster.entity.BoosterGrant;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoosterGrantService {
    private static final Duration LATE_BATCH_GRACE = Duration.ofSeconds(5);

    private final BoosterGrantRepository boosterGrantRepository;
    private final UserService userService;
    private final TapPolicyConfig tapPolicyConfig;

    @Transactional
    public BoosterActivateResponse activate(UUID userId, String adGroupId) {
        if (!StringUtils.hasText(adGroupId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AD_GROUP_ID_REQUIRED");
        }

        Instant now = Instant.now();
        if (boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(userId, BoosterGrant.Status.ACTIVE, now).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "BOOSTER_ALREADY_ACTIVE");
        }

        LocalDate today = LocalDate.now();
        int dailyLimit = tapPolicyConfig.boosterDailyLimit();
        long todayCount = boosterGrantRepository.countByUserIdAndGrantDate(userId, today);
        long windowCount = countWithinLimitWindow(userId, now);
        if (windowCount >= dailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "BOOSTER_DAILY_LIMIT_EXCEEDED");
        }

        AppUser user = userService.getById(userId);
        BoosterGrant grant = BoosterGrant.activate(
                user,
                today,
                (int) todayCount + 1,
                Duration.ofSeconds(tapPolicyConfig.boosterDurationSeconds())
        );
        grant = boosterGrantRepository.save(grant);

        return new BoosterActivateResponse(
                grant.getPublicId(),
                true,
                grant.getMultiplier(),
                grant.getStartsAt(),
                grant.getExpiresAt(),
                (int) (dailyLimit - (windowCount + 1))
        );
    }

    public BoosterStatusResponse getCurrentStatus(UUID userId) {
        Instant now = Instant.now();
        int dailyLimit = tapPolicyConfig.boosterDailyLimit();
        long windowCount = countWithinLimitWindow(userId, now);
        int remainingDailyCount = (int) Math.max(dailyLimit - windowCount, 0);

        return boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(userId, BoosterGrant.Status.ACTIVE, now)
                .map(grant -> new BoosterStatusResponse(
                        true,
                        grant.getMultiplier(),
                        Duration.between(now, grant.getExpiresAt()).getSeconds(),
                        grant.getExpiresAt(),
                        remainingDailyCount
                ))
                .orElseGet(() -> new BoosterStatusResponse(false, BigDecimal.ONE, 0L, null, remainingDailyCount));
    }

    private long countWithinLimitWindow(UUID userId, Instant now) {
        Instant windowStart = now.minusSeconds(tapPolicyConfig.boosterLimitWindowSeconds());
        return boosterGrantRepository.countByUserIdAndStartsAtAfter(userId, windowStart);
    }

    /**
     * 탭 배치가 주장한 부스터 배율을 인정할지 정한다. 끝나기 직전에 누른 탭이 조금 늦게 도착할 수 있어
     * 끝난 뒤 {@link #LATE_BATCH_GRACE} 까지는 켜져 있던 것으로 본다. status 는 만료돼도 ACTIVE 로 남으므로 보지 않는다.
     */
    public boolean wasActive(UUID userId, int multiplier, Instant now) {
        return boosterGrantRepository.existsByUserIdAndMultiplierAndStartsAtLessThanEqualAndExpiresAtAfter(
                userId, BigDecimal.valueOf(multiplier), now, now.minus(LATE_BATCH_GRACE));
    }

    public BigDecimal findActiveMultiplier(UUID userId, Instant now) {
        return boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(userId, BoosterGrant.Status.ACTIVE, now)
                .map(BoosterGrant::getMultiplier)
                .orElse(BigDecimal.ONE);
    }
}
