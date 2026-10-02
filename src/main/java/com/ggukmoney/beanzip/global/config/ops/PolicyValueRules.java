package com.ggukmoney.beanzip.global.config.ops;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.ranking.boost.SystemRankingBoostPolicy;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingRewardPolicy;
import com.ggukmoney.beanzip.global.config.CashoutPolicyConfig;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import com.ggukmoney.beanzip.global.config.OnboardingRewardConfig;
import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import com.ggukmoney.beanzip.global.config.RankChangeNotificationPolicyConfig;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 운영 화면에서 바꿀 수 있는 키와 그 값의 규칙.
 *
 * <p>규칙은 그 키를 읽는 로더와 같거나 더 엄격하다. 로더가 거부하는 값이 들어가면 기능이 멈추거나
 * (온보딩 0탭 → 지급 409, 주간 보상 {} → 보상 꺼짐) 재시작 뒤 코드 기본값으로 조용히 바뀐다(출금 환율).
 * 여기 없는 키는 화면에서 읽기만 된다. 새 정책 키를 만들면 여기에도 규칙을 추가한다.
 */
@Component
public class PolicyValueRules {

    public enum Kind { INTEGER, DECIMAL, BOOLEAN, TEXT, JSON }

    /** check 는 저장될 JSON 텍스트를 받는다. TEXT 만 따옴표를 벗긴 문자열을 받는다. */
    public record Rule(Kind kind, String hint, Predicate<String> check) {

        boolean accepts(String value) {
            try {
                return check.test(value);
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    /**
     * 돈과 바로 이어지는 값의 상한. 로더는 받아들이지만 오타 한 번이면 크게 나간다(0.02 → 2 는 출금 100배).
     * 더 큰 값이 정말 필요하면 SQL 로 넣는다.
     */
    static final long MAX_MONEY_AMOUNT = 10_000;
    static final long MAX_WEEKLY_REWARD = 1_000_000;

    private final Map<String, Rule> rules = new HashMap<>();

    public PolicyValueRules(KeycapRepository keycapRepository, ObjectMapper mapper) {
        // 탭
        integer(TapPolicyConfig.KEY_MIN_INTERVAL_MS, 0);
        integer(TapPolicyConfig.KEY_MAX_PER_MINUTE, 1);
        integer(TapPolicyConfig.KEY_MAX_PER_DAY, 1);
        integer(TapPolicyConfig.KEY_CURVE_GENERAL_BASE, 1);
        rules.put(TapPolicyConfig.KEY_CURVE_GENERAL_VARIANCE, new Rule(Kind.DECIMAL, "0 이상 1 미만의 숫자",
                raw -> { double value = finite(raw); return value >= 0 && value < 1; }));
        integer(TapPolicyConfig.KEY_POINT_DAILY_CAP, 0);
        bool(TapPolicyConfig.KEY_BOT_ENABLED);
        integer(TapPolicyConfig.KEY_BOT_SAMPLE_SIZE, 2);
        decimal(TapPolicyConfig.KEY_BOT_STDDEV_THRESHOLD_MS, false);
        bool(TapPolicyConfig.KEY_RATE_LIMIT_ENABLED);
        integer(TapPolicyConfig.KEY_RATE_LIMIT_CAPACITY, 1);
        decimal(TapPolicyConfig.KEY_RATE_LIMIT_REFILL_PER_SECOND, true);
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_1, 1);
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_2, 1);
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_3, 1);
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_4, 1);
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_5, 1);
        integer(TapPolicyConfig.KEY_BOX_SESSION_TAIL_STEP, 1);
        integer(TapPolicyConfig.KEY_BOX_SESSION_IDLE_TIMEOUT_SECONDS, 1);
        integer(TapPolicyConfig.KEY_BOOSTER_DURATION_SECONDS, 1);
        integer(TapPolicyConfig.KEY_BOOSTER_DAILY_LIMIT, 0);
        integer(TapPolicyConfig.KEY_BOOSTER_LIMIT_WINDOW_SECONDS, 1);

        // 출금 · 상자
        integer(CashoutPolicyConfig.KEY_MINIMUM_POINT, 1);
        rules.put(CashoutPolicyConfig.KEY_POINT_TO_KRW_RATE, new Rule(Kind.DECIMAL, "0보다 크고 1 이하인 숫자 (1P 당 원)",
                raw -> { double value = finite(raw); return value > 0 && value <= 1; }));
        integer(KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS, 1);
        integer(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT, 0);
        integer(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT, 0);
        integer(KeycapBoxPolicyConfig.KEY_BULK_OPEN_LIMIT, 0);

        // 온보딩: 로더가 매 요청 직접 읽고 대체값이 없다. 잘못되면 온보딩 지급이 바로 막힌다.
        rules.put(OnboardingRewardConfig.KEY_REWARD_KEYCAP_CODE, new Rule(Kind.TEXT, "활성 키캡 코드",
                code -> keycapRepository.findByCode(code).filter(Keycap::isActive).isPresent()));
        rules.put(OnboardingRewardConfig.KEY_BONUS_KEYCAP_GRADE, new Rule(Kind.TEXT,
                "등급: " + Arrays.toString(Keycap.Grade.values()),
                grade -> Arrays.stream(Keycap.Grade.values()).anyMatch(value -> value.name().equals(grade))));
        rules.put(OnboardingRewardConfig.KEY_REWARD_POINT_AMOUNT, new Rule(Kind.INTEGER, "0 이상 " + MAX_MONEY_AMOUNT + " 이하의 정수",
                raw -> { int value = Integer.parseInt(raw); return value >= 0 && value <= MAX_MONEY_AMOUNT; }));
        integer(OnboardingRewardConfig.KEY_ATTEMPT_TTL_SECONDS, 1);
        integer(OnboardingRewardConfig.KEY_REQUIRED_TAP_COUNT, 1);

        // 알림
        integer(RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, 1);
        integer(RankChangeNotificationPolicyConfig.KEY_COOLDOWN_MINUTES, 0);

        // 프로모션 (돈이 나간다)
        bool(PromotionPolicyConfig.KEY_ENABLED);
        bool(PromotionPolicyConfig.KEY_EXECUTION_ENABLED);
        bool(PromotionPolicyConfig.KEY_TAP_ENABLED);
        integer(PromotionPolicyConfig.KEY_THRESHOLD, 1);
        integer(PromotionPolicyConfig.KEY_TAP_THRESHOLD, 1);
        money(PromotionPolicyConfig.KEY_AMOUNT);
        money(PromotionPolicyConfig.KEY_TAP_AMOUNT);
        instant(PromotionPolicyConfig.KEY_LAUNCH_AT);
        instant(PromotionPolicyConfig.KEY_TAP_LAUNCH_AT);
        rules.put(PromotionPolicyConfig.KEY_EXCLUDED_USER_IDS, new Rule(Kind.JSON, "유저 UUID 문자열 배열",
                raw -> {
                    Arrays.stream(mapper.readValue(raw, String[].class)).forEach(id -> UUID.fromString(id.trim()));
                    return true;
                }));

        // 랭킹: 로더의 파서를 그대로 쓴다.
        rules.put(WeeklyRankingRewardPolicy.KEY, new Rule(Kind.JSON,
                "{\"enabled\": bool, \"rewards\": {\"1\": 금액, ...}} — 순위는 1부터 연속, 금액은 " + MAX_WEEKLY_REWARD + " 이하",
                raw -> WeeklyRankingRewardPolicy.parse(mapper, raw).rewards().values().stream()
                        .allMatch(amount -> amount <= MAX_WEEKLY_REWARD)));
        rules.put(SystemRankingBoostPolicy.KEY, new Rule(Kind.JSON, "enabled, internalUserIds, minimumLeaderScore, minIncrement, maxIncrement",
                raw -> SystemRankingBoostPolicy.parse(mapper, raw) != null));
    }

    public Optional<Rule> find(String key) {
        return Optional.ofNullable(rules.get(key));
    }

    private void integer(String key, int min) {
        // 로더는 Integer.parseInt 로 읽는다. int 범위를 넘으면 거부한다.
        rules.put(key, new Rule(Kind.INTEGER, min + " 이상의 정수", raw -> Integer.parseInt(raw) >= min));
    }

    private void money(String key) {
        rules.put(key, new Rule(Kind.INTEGER, "1 이상 " + MAX_MONEY_AMOUNT + " 이하의 정수",
                raw -> { long value = Long.parseLong(raw); return value >= 1 && value <= MAX_MONEY_AMOUNT; }));
    }

    private void decimal(String key, boolean positive) {
        rules.put(key, new Rule(Kind.DECIMAL, positive ? "0보다 큰 숫자" : "0 이상의 숫자",
                raw -> positive ? finite(raw) > 0 : finite(raw) >= 0));
    }

    private void bool(String key) {
        rules.put(key, new Rule(Kind.BOOLEAN, "true 또는 false", raw -> raw.equals("true") || raw.equals("false")));
    }

    private void instant(String key) {
        rules.put(key, new Rule(Kind.TEXT, "ISO-8601 시각 (예: 2026-09-06T00:00:00Z)", raw -> Instant.parse(raw) != null));
    }

    private static double finite(String raw) {
        double value = new BigDecimal(raw).doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("not finite");
        }
        return value;
    }
}
