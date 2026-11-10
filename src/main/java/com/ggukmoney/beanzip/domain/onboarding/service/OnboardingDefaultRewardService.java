package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapRewardSelector;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.OnboardingRewardConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 한 장짜리 온보딩(상자 열기 없이 바로 로그인)으로 가입한 신규 유저에게 온보딩 보상을 준다.
 * 보상 구성은 상자 온보딩과 같은 app_config `onboarding.reward.*` 정책을 따른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingDefaultRewardService {

    private static final String POINT_REASON_ONBOARDING_REWARD = "ONBOARDING_REWARD";
    private static final String IDEMPOTENCY_KEY_PREFIX = "onboarding-default-reward:";

    private final OnboardingRewardConfig rewardConfig;
    private final KeycapRepository keycapRepository;
    private final KeycapRewardSelector keycapRewardSelector;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final UserKeycapRepository userKeycapRepository;
    private final Clock clock;

    /**
     * 보상을 지급하면 true. 정책·키캡을 찾지 못하면 로그인을 막지 않도록 지급 없이 false를 돌려준다.
     * 쓰기 전에 지급 가능 여부를 모두 확인해 로그인 트랜잭션이 rollback-only로 바뀌지 않게 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean grantForNewUser(AppUser user) {
        Optional<DefaultReward> reward = resolveReward();
        if (reward.isEmpty()) {
            return false;
        }
        Instant now = Instant.now(clock);
        DefaultReward resolved = reward.get();

        PointAccount account = pointAccountService.credit(user.getId(), resolved.pointAmount());
        pointLedgerService.recordCredit(
                account,
                user,
                resolved.pointAmount(),
                POINT_REASON_ONBOARDING_REWARD,
                idempotencyKey(user)
        );
        grantKeycapIfMissing(user, resolved.rewardKeycap(), now, true);
        grantKeycapIfMissing(user, resolved.bonusKeycap(), now, false);
        user.claimOnboardingReward(now);
        return true;
    }

    private Optional<DefaultReward> resolveReward() {
        OnboardingRewardConfig.OnboardingRewardPolicy policy;
        try {
            policy = rewardConfig.resolve();
        } catch (ResponseStatusException exception) {
            log.warn("온보딩 기본 보상 정책을 읽지 못해 보상 없이 가입시킨다: {}", exception.getReason());
            return Optional.empty();
        }
        Optional<Keycap> rewardKeycap = keycapRepository.findByCode(policy.rewardKeycapCode())
                .filter(Keycap::isActive);
        Optional<Keycap.Grade> bonusGrade = parseGrade(policy.bonusKeycapGrade());
        if (rewardKeycap.isEmpty() || bonusGrade.isEmpty()) {
            log.warn("온보딩 기본 보상 키캡 설정이 올바르지 않아 보상 없이 가입시킨다: keycap={}, grade={}",
                    policy.rewardKeycapCode(), policy.bonusKeycapGrade());
            return Optional.empty();
        }
        // 이벤트 키캡은 온보딩 보너스로도 나가지 않는다(BEA-285).
        List<Keycap> bonusCandidates = keycapRepository
                .findByGradeAndAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(
                        bonusGrade.get(), Keycap.AcquisitionType.BOX)
                .stream()
                .filter(candidate -> !candidate.getCode().equals(rewardKeycap.get().getCode()))
                .toList();
        if (bonusCandidates.isEmpty()) {
            log.warn("온보딩 기본 보상 보너스 키캡 후보가 없어 보상 없이 가입시킨다: grade={}", bonusGrade.get());
            return Optional.empty();
        }
        return Optional.of(new DefaultReward(
                rewardKeycap.get(),
                keycapRewardSelector.select(bonusCandidates),
                policy.rewardPointAmount()
        ));
    }

    private Optional<Keycap.Grade> parseGrade(String grade) {
        try {
            return Optional.of(Keycap.Grade.valueOf(grade));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void grantKeycapIfMissing(AppUser user, Keycap keycap, Instant now, boolean equip) {
        UserKeycap userKeycap = userKeycapRepository.findByUserIdAndKeycapIdForUpdate(user.getId(), keycap.getId())
                .orElseGet(() -> userKeycapRepository.save(UserKeycap.createOwned(user, keycap, now)));
        if (equip) {
            userKeycap.equip();
        }
    }

    /** 유저당 한 번만 지급되도록 유저 ID로 고정된 원장 멱등 키를 만든다. */
    private static UUID idempotencyKey(AppUser user) {
        return UUID.nameUUIDFromBytes((IDEMPOTENCY_KEY_PREFIX + user.getId()).getBytes(StandardCharsets.UTF_8));
    }

    private record DefaultReward(Keycap rewardKeycap, Keycap bonusKeycap, int pointAmount) {
    }
}
