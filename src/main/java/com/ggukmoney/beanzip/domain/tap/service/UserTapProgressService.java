package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserTapProgressService {

    private final UserTapProgressRepository userTapProgressRepository;
    private final Random random = new SecureRandom();

    public UserTapProgress createFor(AppUser user, TapPolicyConfig config) {
        int initialPointTarget = drawNextTarget(0, 0, config);
        return userTapProgressRepository.save(UserTapProgress.createFor(user, initialPointTarget));
    }

    public UserTapProgress getForUser(UUID userId) {
        return userTapProgressRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TAP_PROGRESS_NOT_FOUND"));
    }

    public UserTapProgress save(UserTapProgress progress) {
        return userTapProgressRepository.save(progress);
    }

    /**
     * 다음 포인트까지 남은 탭 수. 오늘 더 이상 포인트가 지급되지 않는 상태에서는 0을 돌려준다.
     *
     * <p>포인트 상한(tap.point.dailyCap)과 일일 탭 상한(tap.validity.maxPerDay)은 모두 진행도를
     * 멈춰 세운다. 그대로 목표와의 차이를 노출하면 포인트 상한에서는 0이 고정되고 탭 상한에서는
     * 양수가 고정돼, 둘 다 "곧 지급된다"로 읽힌다. 상한에 걸린 상태를 0 하나로 모아
     * "오늘은 더 없음"을 뜻하게 한다. 상한 이전에는 목표 재추첨 폭이 있어 항상 1 이상이다.
     */
    public int remainingTapsToNextPoint(UserTapProgress progress, UserTapDaily daily, TapPolicyConfig config) {
        boolean noMorePointsToday = daily.getPointEarnedAmount() >= config.pointDailyCap()
                || daily.getValidTapCount() >= config.maxPerDay();
        if (noMorePointsToday) {
            return 0;
        }
        return (int) Math.max(progress.getNextPointTarget() - progress.getCumulativeValidTapCount(), 0);
    }

    public int drawNextTarget(long currentCumulativeTaps, int pointEarnedAmountToday, TapPolicyConfig config) {
        boolean decelerating = pointEarnedAmountToday >= config.decelThresholdPoints();
        int base = decelerating ? config.curveDecelBase() : config.curveGeneralBase();
        double variance = decelerating ? config.curveDecelVariance() : config.curveGeneralVariance();
        int increment = drawUniform(base, variance);
        return (int) (currentCumulativeTaps + increment);
    }

    private int drawUniform(int base, double variance) {
        int lowerBound = (int) Math.round(base * (1 - variance));
        int upperBound = (int) Math.round(base * (1 + variance));
        return lowerBound + random.nextInt(upperBound - lowerBound + 1);
    }
}
