package com.ggukmoney.beanzip.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 친구 초대·응원 정책 (BEA-390).
 *
 * <p>{@link TapPolicyConfig} 와 같은 방식으로 app_config 를 60초마다 다시 읽고, 읽은 값이 틀리면
 * 마지막으로 읽은 정상 값을 유지한다. 그래서 스위치를 꺼도 최대 60초 뒤에 반영된다.
 */
@Component
@RequiredArgsConstructor
public class FriendPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(FriendPolicyConfig.class);

    public static final String KEY_ENABLED = "friends.enabled";
    public static final String KEY_CONTACTS_SMS_ENABLED = "friends.contactsSms.enabled";
    public static final String KEY_INVITE_REWARD = "friends.invite.reward";
    public static final String KEY_CHEER_DAILY_LIMIT = "friends.cheer.dailyLimit";
    public static final String KEY_CHEER_RECEIVE_REWARD = "friends.cheer.receiveReward";
    public static final String KEY_CHEER_RECEIVE_DAILY_CAP = "friends.cheer.receiveDailyCap";
    public static final String KEY_CHEER_SEND_FIRST_REWARD = "friends.cheer.sendFirstReward";
    public static final String KEY_CHEER_CLAP_THRESHOLD = "friends.cheer.clapThreshold";
    public static final String KEY_CHEER_GIFT_LAPSED_DAYS = "friends.cheer.giftLapsedDays";

    public static final Map<String, String> DEFAULT_VALUES = Map.of(
            // 출시 때 운영 화면에서 켠다. 문제가 생기면 앱 업데이트 없이 끄는 비상 스위치이기도 하다.
            KEY_ENABLED, "false",
            // 연락처 문자만 끄는 스위치. 검수에서 반려되면 이 값만 끄고 링크 공유로 출시한다.
            KEY_CONTACTS_SMS_ENABLED, "false",
            KEY_INVITE_REWARD, "10",
            KEY_CHEER_DAILY_LIMIT, "10",
            KEY_CHEER_RECEIVE_REWARD, "3",
            KEY_CHEER_RECEIVE_DAILY_CAP, "30",
            KEY_CHEER_SEND_FIRST_REWARD, "1",
            KEY_CHEER_CLAP_THRESHOLD, "1000",
            KEY_CHEER_GIFT_LAPSED_DAYS, "3"
    );

    private static final Set<String> BOOLEAN_KEYS = Set.of(KEY_ENABLED, KEY_CONTACTS_SMS_ENABLED);
    /** 0 이면 판정이 항상 참이 되어 모든 응원이 박수·선물로 분류된다. */
    private static final Set<String> POSITIVE_KEYS = Set.of(KEY_CHEER_CLAP_THRESHOLD, KEY_CHEER_GIFT_LAPSED_DAYS);

    private final AppConfigBatchLoader batchLoader;
    private final KeycapBoxPolicyConfig economy;

    private volatile Map<String, String> cache = Map.copyOf(DEFAULT_VALUES);

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        try {
            Map<String, String> loaded = batchLoader.load(DEFAULT_VALUES.keySet(), Instant.now());
            Map<String, String> current = cache;
            Map<String, String> next = new HashMap<>(DEFAULT_VALUES.size());
            for (String key : DEFAULT_VALUES.keySet()) {
                String fallback = current.getOrDefault(key, DEFAULT_VALUES.get(key));
                String candidate = loaded.get(key);
                boolean valid = candidate != null && isValid(key, candidate);
                if (candidate != null && !valid) {
                    log.warn("Invalid friend policy config value; key={} fallback=last-known-good", key);
                }
                next.put(key, valid ? candidate.trim() : fallback);
            }
            cache = Map.copyOf(next);
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh friend policy config from AppConfig; fallback=last-known-good", exception);
        }
    }

    /**
     * 친구 기능이 실제로 켜져 있는지.
     *
     * <p>스위치가 켜져 있어도 경제 전환 전이면 꺼진 것으로 본다. 전환 전에는 지갑의 같은 칸이 조각이
     * 아니라 상자 개수라서, 초대·응원 보상을 넣으면 상자가 늘어난다.
     */
    public boolean friendsEnabled() {
        return getBoolean(KEY_ENABLED) && economy.shardEconomyAtLastRefresh();
    }

    public boolean contactsSmsEnabled() {
        return getBoolean(KEY_CONTACTS_SMS_ENABLED);
    }

    /** 초대 성공 때 양쪽이 각각 받는 조각 수. */
    public int inviteReward() {
        return getInt(KEY_INVITE_REWARD);
    }

    /** 하루에 응원할 수 있는 친구 수. */
    public int cheerDailyLimit() {
        return getInt(KEY_CHEER_DAILY_LIMIT);
    }

    /** 받은 응원 1건당 조각 수. */
    public int cheerReceiveReward() {
        return getInt(KEY_CHEER_RECEIVE_REWARD);
    }

    /** 하루에 응원으로 받을 수 있는 조각 상한. 확인한 날 기준으로 센다. */
    public int cheerReceiveDailyCap() {
        return getInt(KEY_CHEER_RECEIVE_DAILY_CAP);
    }

    /** 하루 첫 응원을 보낸 사람이 받는 조각 수. */
    public int cheerSendFirstReward() {
        return getInt(KEY_CHEER_SEND_FIRST_REWARD);
    }

    /** 받는 사람의 오늘 탭 수가 이 값 이상이면 박수(CLAP)다. */
    public int cheerClapThreshold() {
        return getInt(KEY_CHEER_CLAP_THRESHOLD);
    }

    /** 받는 사람의 마지막 탭이 이 일수 이상 전이면 선물(GIFT)이다. */
    public int cheerGiftLapsedDays() {
        return getInt(KEY_CHEER_GIFT_LAPSED_DAYS);
    }

    private int getInt(String key) {
        return Integer.parseInt(cache.getOrDefault(key, DEFAULT_VALUES.get(key)));
    }

    private boolean getBoolean(String key) {
        return Boolean.parseBoolean(cache.getOrDefault(key, DEFAULT_VALUES.get(key)));
    }

    static boolean isValid(String key, String rawValue) {
        String value = rawValue.trim();
        if (BOOLEAN_KEYS.contains(key)) {
            return "true".equals(value) || "false".equals(value);
        }
        try {
            int parsed = Integer.parseInt(value);
            return POSITIVE_KEYS.contains(key) ? parsed >= 1 : parsed >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
