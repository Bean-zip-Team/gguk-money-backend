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
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 운영 화면에서 바꿀 수 있는 키와 그 값의 규칙·분류·의미. 화면은 여기 적힌 순서대로 보여준다.
 *
 * <p>규칙은 그 키를 읽는 로더와 같거나 더 엄격하다. 로더가 거부하는 값이 들어가면 기능이 멈추거나
 * (온보딩 0탭 → 지급 409, 주간 보상 {} → 보상 꺼짐) 재시작 뒤 코드 기본값으로 조용히 바뀐다(출금 환율).
 * 여기 없는 키는 화면에서 읽기만 된다. 새 정책 키를 만들면 여기에도 규칙을 추가한다.
 */
@Component
public class PolicyValueRules {

    public enum Kind { INTEGER, DECIMAL, BOOLEAN, TEXT, JSON }

    /**
     * check 는 저장될 JSON 텍스트를 받는다. TEXT 만 따옴표를 벗긴 문자열을 받는다.
     * summary 는 JSON 값을 사람이 읽기 쉽게 풀어 쓴다(없으면 값 그대로).
     */
    public record Rule(Kind kind, String hint, Predicate<String> check,
                       String group, String description, Function<String, String> summary) {

        boolean accepts(String value) {
            try {
                return check.test(value);
            } catch (RuntimeException exception) {
                return false;
            }
        }

        public String summarize(String rawJson) {
            if (summary == null) {
                return null;
            }
            try {
                return summary.apply(rawJson);
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    /**
     * 돈과 바로 이어지는 값의 상한. 로더는 받아들이지만 오타 한 번이면 크게 나간다(0.02 → 2 는 출금 100배).
     * 더 큰 값이 정말 필요하면 SQL 로 넣는다.
     */
    static final long MAX_MONEY_AMOUNT = 10_000;
    static final long MAX_WEEKLY_REWARD = 1_000_000;

    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private String group;

    public PolicyValueRules(KeycapRepository keycapRepository, ObjectMapper mapper) {
        group = "탭 적립";
        integer(TapPolicyConfig.KEY_CURVE_GENERAL_BASE, 1, "몇 탭마다 1P 를 주는지 (기준 탭 수)");
        add(TapPolicyConfig.KEY_CURVE_GENERAL_VARIANCE, Kind.DECIMAL, "0 이상 1 미만의 숫자",
                raw -> { double value = finite(raw); return value >= 0 && value < 1; },
                "기준 탭 수의 랜덤 폭 (0.25 = ±25%)", null);
        integer(TapPolicyConfig.KEY_POINT_DAILY_CAP, 0, "하루 최대 적립 포인트");
        integer(TapPolicyConfig.KEY_MAX_PER_DAY, 1, "하루 최대 인정 탭 수");
        integer(TapPolicyConfig.KEY_MAX_PER_MINUTE, 1, "분당 최대 인정 탭 수");
        integer(TapPolicyConfig.KEY_MIN_INTERVAL_MS, 0, "탭 사이 최소 간격 (ms)");
        bool(TapPolicyConfig.KEY_RATE_LIMIT_ENABLED, "요청 속도 제한 사용");
        integer(TapPolicyConfig.KEY_RATE_LIMIT_CAPACITY, 1, "속도 제한: 한 번에 허용하는 요청 수");
        decimal(TapPolicyConfig.KEY_RATE_LIMIT_REFILL_PER_SECOND, true, "속도 제한: 초당 다시 채워지는 요청 수");
        bool(TapPolicyConfig.KEY_BOT_ENABLED, "봇 탐지 사용");
        integer(TapPolicyConfig.KEY_BOT_SAMPLE_SIZE, 2, "봇 탐지: 판단에 쓰는 탭 간격 표본 수");
        decimal(TapPolicyConfig.KEY_BOT_STDDEV_THRESHOLD_MS, false, "봇 탐지: 탭 간격 표준편차가 이보다 작으면 봇 (ms)");

        group = "부스터";
        integer(TapPolicyConfig.KEY_BOOSTER_DAILY_LIMIT, 0, "하루 사용 횟수");
        integer(TapPolicyConfig.KEY_BOOSTER_DURATION_SECONDS, 1, "지속 시간 (초)");
        integer(TapPolicyConfig.KEY_BOOSTER_LIMIT_WINDOW_SECONDS, 1, "사용 횟수를 세는 기간 (초)");

        group = "조각";
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_1, 1, "세션 1번째 조각까지 필요한 탭 수");
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_2, 1, "세션 2번째 조각까지 필요한 탭 수");
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_3, 1, "세션 3번째 조각까지 필요한 탭 수");
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_4, 1, "세션 4번째 조각까지 필요한 탭 수");
        integer(TapPolicyConfig.KEY_BOX_SESSION_STEP_5, 1, "세션 5번째 조각까지 필요한 탭 수");
        integer(TapPolicyConfig.KEY_BOX_SESSION_TAIL_STEP, 1, "6번째 조각부터 조각 간격 (탭)");
        integer(TapPolicyConfig.KEY_BOX_SESSION_IDLE_TIMEOUT_SECONDS, 1, "이 시간 동안 쉬면 세션 초기화 (초)");
        integer(KeycapBoxPolicyConfig.KEY_DRAW_PRICE, 1, "뽑기 1회 가격 (조각)");

        group = "키캡 패시브";
        var passiveDefaults = com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.DEFAULT_VALUES;
        passiveDefaults.keySet().stream().sorted().forEach(key -> {
            if (key.endsWith(".enabled")) bool(key,"패시브 지급 사용 (기본 꺼짐)");
            else if (key.endsWith(".enabledAt")) add(key,Kind.TEXT,"스위치 활성화 시 자동 기록",raw -> false,
                    "활성화 이전 적립을 차단하는 시각 (직접 수정 불가)",null);
            else if (key.endsWith(".probability")) add(key,Kind.DECIMAL,"0 이상 1 이하; 전체 기대배수 검증",
                    raw -> finite(raw)>=0 && finite(raw)<=1,"상한 레벨의 크리티컬 확률: "+key,null);
            else if (key.endsWith(".startRatio")) add(key,Kind.DECIMAL,"0보다 크고 1 이하; 전체 정책 검증",
                    raw -> finite(raw)>0 && finite(raw)<=1,
                    "Lv1 효과 / 상한 효과의 비율 (0.4 = 40%): "+key,null);
            else integer(key,key.endsWith(".autoClickBase")||key.endsWith(".autoClickPerLevel")||key.endsWith(".autoClickCap")?0:1,
                    key.endsWith(".capDays")?"자동 클릭 미수령 상한 (일)":key.endsWith(".autoClickBase")?"Lv1 일당 자동 클릭: "+key:
                    key.endsWith(".autoClickPerLevel")?"레벨당 일당 자동 클릭 증가: "+key:
                    key.endsWith(".autoClickCap")?"일당 자동 클릭 상한: "+key:key);
        });
        com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.CONFIG_KEYS.stream()
                .filter(key -> key.endsWith("Strength")).sorted().forEach(key ->
                    add(key,Kind.DECIMAL,"읽기 전용: 같은 등급의 startRatio를 사용하세요",raw -> false,
                            "이전 보간 기준값 (실제 캡 아님, 기존 이력 보존): "+key,null));

        // 온보딩: 로더가 매 요청 직접 읽고 대체값이 없다. 잘못되면 온보딩 지급이 바로 막힌다.
        group = "온보딩";
        add(OnboardingRewardConfig.KEY_REWARD_POINT_AMOUNT, Kind.INTEGER, "0 이상 " + MAX_MONEY_AMOUNT + " 이하의 정수",
                raw -> { int value = Integer.parseInt(raw); return value >= 0 && value <= MAX_MONEY_AMOUNT; },
                "완주 보상 포인트", null);
        integer(OnboardingRewardConfig.KEY_REQUIRED_TAP_COUNT, 1, "온보딩 필수 탭 수 (프론트와 같아야 함)");
        add(OnboardingRewardConfig.KEY_REWARD_KEYCAP_CODE, Kind.TEXT, "활성 키캡 코드",
                code -> keycapRepository.findByCode(code).filter(Keycap::isActive).isPresent(),
                "완주 보상 키캡 (바로 장착됨)", null);
        add(OnboardingRewardConfig.KEY_BONUS_KEYCAP_GRADE, Kind.TEXT, "등급: " + Arrays.toString(Keycap.Grade.values()),
                grade -> Arrays.stream(Keycap.Grade.values()).anyMatch(value -> value.name().equals(grade)),
                "보너스 키캡 등급", null);
        integer(OnboardingRewardConfig.KEY_ATTEMPT_TTL_SECONDS, 1, "상자를 연 뒤 로그인해서 보상을 받을 수 있는 시간 (초)");

        group = "출금";
        integer(CashoutPolicyConfig.KEY_MINIMUM_POINT, 1, "최소 출금 포인트");
        add(CashoutPolicyConfig.KEY_POINT_TO_KRW_RATE, Kind.DECIMAL, "0보다 크고 1 이하인 숫자",
                raw -> { double value = finite(raw); return value > 0 && value <= 1; },
                "1P 당 원 (0.02 = 1P 에 0.02원)", null);

        group = "프로모션";
        bool(PromotionPolicyConfig.KEY_ENABLED, "키캡 5종 모으기: 달성자에게 지급 대상 만들기");
        bool(PromotionPolicyConfig.KEY_TAP_ENABLED, "1,000번 누르기: 달성자에게 지급 대상 만들기");
        bool(PromotionPolicyConfig.KEY_EXECUTION_ENABLED, "토스 포인트 실제 지급 (두 프로모션 공용 킬스위치)");
        instant(PromotionPolicyConfig.KEY_LAUNCH_AT, "키캡 5종 모으기: 이 시각 이후 달성분만 지급");
        instant(PromotionPolicyConfig.KEY_TAP_LAUNCH_AT, "1,000번 누르기: 이 시각 이후 달성분만 지급");
        integer(PromotionPolicyConfig.KEY_THRESHOLD, 1, "키캡 5종 모으기: 필요한 키캡 수");
        integer(PromotionPolicyConfig.KEY_TAP_THRESHOLD, 1, "1,000번 누르기: 필요한 탭 수");
        money(PromotionPolicyConfig.KEY_AMOUNT, "키캡 5종 모으기: 지급 토스 포인트");
        money(PromotionPolicyConfig.KEY_TAP_AMOUNT, "1,000번 누르기: 지급 토스 포인트");
        add(PromotionPolicyConfig.KEY_EXCLUDED_USER_IDS, Kind.JSON, "유저 UUID 문자열 배열",
                raw -> {
                    Arrays.stream(mapper.readValue(raw, String[].class)).forEach(id -> UUID.fromString(id.trim()));
                    return true;
                },
                "지급 제외 유저 (테스터 등)",
                raw -> mapper.readValue(raw, String[].class).length + "명");

        // 랭킹: 로더의 파서를 그대로 쓴다.
        group = "랭킹·알림";
        add(WeeklyRankingRewardPolicy.KEY, Kind.JSON,
                "{\"enabled\": bool, \"rewards\": {\"1\": 금액, ...}} — 순위는 1부터 연속, 금액은 " + MAX_WEEKLY_REWARD + " 이하",
                raw -> WeeklyRankingRewardPolicy.parse(mapper, raw).rewards().values().stream()
                        .allMatch(amount -> amount <= MAX_WEEKLY_REWARD),
                "주간 랭킹 보상 (순위별 포인트)",
                raw -> {
                    WeeklyRankingRewardPolicy.Snapshot policy = WeeklyRankingRewardPolicy.parse(mapper, raw);
                    return onOff(policy.enabled()) + " · " + policy.rewards().entrySet().stream()
                            .map(entry -> entry.getKey() + "위 " + number(entry.getValue()))
                            .collect(Collectors.joining(" / "));
                });
        add(SystemRankingBoostPolicy.KEY, Kind.JSON, "enabled, internalUserIds, minimumLeaderScore, minIncrement, maxIncrement",
                raw -> SystemRankingBoostPolicy.parse(mapper, raw) != null,
                "사내 계정 탭 수 부스트 (실유저가 1위일 때만)",
                raw -> {
                    SystemRankingBoostPolicy.Snapshot policy = SystemRankingBoostPolicy.parse(mapper, raw);
                    return onOff(policy.enabled()) + " · 증가폭 " + number(policy.minIncrement()) + "~" + number(policy.maxIncrement())
                            + " · 1위 최소 " + number(policy.minimumLeaderScore())
                            + " · 사내 계정 " + policy.internalUserIds().size() + "개";
                });
        integer(RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, 1, "순위가 이만큼 이상 떨어지면 알림");
        integer(RankChangeNotificationPolicyConfig.KEY_COOLDOWN_MINUTES, 0, "같은 유저에게 다시 보내기까지 (분, 0 이면 바로)");
    }

    public Optional<Rule> find(String key) {
        return Optional.ofNullable(rules.get(key));
    }

    /** 화면에 보여줄 순서. */
    public Map<String, Rule> all() {
        return rules;
    }

    private void add(String key, Kind kind, String hint, Predicate<String> check, String description,
                     Function<String, String> summary) {
        rules.put(key, new Rule(kind, hint, check, group, description, summary));
    }

    private void integer(String key, int min, String description) {
        // 로더는 Integer.parseInt 로 읽는다. int 범위를 넘으면 거부한다.
        add(key, Kind.INTEGER, min + " 이상의 정수", raw -> Integer.parseInt(raw) >= min, description, null);
    }

    private void money(String key, String description) {
        add(key, Kind.INTEGER, "1 이상 " + MAX_MONEY_AMOUNT + " 이하의 정수",
                raw -> { long value = Long.parseLong(raw); return value >= 1 && value <= MAX_MONEY_AMOUNT; },
                description, null);
    }

    private void decimal(String key, boolean positive, String description) {
        add(key, Kind.DECIMAL, positive ? "0보다 큰 숫자" : "0 이상의 숫자",
                raw -> positive ? finite(raw) > 0 : finite(raw) >= 0, description, null);
    }

    private void bool(String key, String description) {
        add(key, Kind.BOOLEAN, "true 또는 false", raw -> raw.equals("true") || raw.equals("false"), description, null);
    }

    private void instant(String key, String description) {
        add(key, Kind.TEXT, "ISO-8601 시각 (예: 2026-09-06T00:00:00Z)", raw -> Instant.parse(raw) != null, description, null);
    }

    private static double finite(String raw) {
        double value = new BigDecimal(raw).doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("not finite");
        }
        return value;
    }

    private static String onOff(boolean enabled) {
        return enabled ? "켜짐" : "꺼짐";
    }

    private static String number(long value) {
        return NumberFormat.getIntegerInstance(java.util.Locale.KOREA).format(value);
    }
}
