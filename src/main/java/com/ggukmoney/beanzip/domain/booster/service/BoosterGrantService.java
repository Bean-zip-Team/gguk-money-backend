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

    private final BoosterGrantRepository boosterGrantRepository;
    private final UserService userService;
    private final TapPolicyConfig tapPolicyConfig;

    @Transactional
    public BoosterActivateResponse activate(UUID userId, UUID adViewId) {
        if (adViewId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AD_VIEW_ID_REQUIRED");
        }

        Instant now = Instant.now();
        if (boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(userId, BoosterGrant.Status.ACTIVE, now).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "BOOSTER_ALREADY_ACTIVE");
        }

        LocalDate today = LocalDate.now();
        int dailyLimit = tapPolicyConfig.boosterDailyLimit();
        long todayCount = boosterGrantRepository.countByUserIdAndGrantDate(userId, today);
        if (todayCount >= dailyLimit) {
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
                (int) (dailyLimit - (todayCount + 1))
        );
    }

    public BoosterStatusResponse getCurrentStatus(UUID userId) {
        Instant now = Instant.now();
        int dailyLimit = tapPolicyConfig.boosterDailyLimit();
        long todayCount = boosterGrantRepository.countByUserIdAndGrantDate(userId, LocalDate.now());
        int remainingDailyCount = (int) Math.max(dailyLimit - todayCount, 0);

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

    public BigDecimal findActiveMultiplier(UUID userId, Instant now) {
        return boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(userId, BoosterGrant.Status.ACTIVE, now)
                .map(BoosterGrant::getMultiplier)
                .orElse(BigDecimal.ONE);
    }
}
