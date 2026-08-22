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
        int initialPointTarget = drawNextTarget(0, config);
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

    /**
     * 다음 포인트 지급 지점을 뽑는다. 기준은 전 기간 누적 유효 탭이며, 간격은
     * base(20) 를 중심으로 ±variance(25%) 안에서 균등 추출한다. 즉 15~25탭마다 1P 다.
     *
     * <p>하루 적립량에 따라 간격을 벌리는 감속 커브가 한때 있었으나 정책에서 빠졌다.
     * 지급 간격은 하루 내내 균일하다.
     */
    public int drawNextTarget(long currentCumulativeTaps, TapPolicyConfig config) {
        int increment = drawUniform(config.curveGeneralBase(), config.curveGeneralVariance());
        return (int) (currentCumulativeTaps + increment);
    }

    private int drawUniform(int base, double variance) {
        int lowerBound = (int) Math.round(base * (1 - variance));
        int upperBound = (int) Math.round(base * (1 + variance));
        return lowerBound + random.nextInt(upperBound - lowerBound + 1);
    }
}
