package com.ggukmoney.beanzip.domain.friend;

import com.ggukmoney.beanzip.domain.auth.service.AuthLoginTransactionService;
import com.ggukmoney.beanzip.domain.friend.entity.Friendship;
import com.ggukmoney.beanzip.domain.friend.service.FriendshipService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 친구 기능 통합 테스트의 공통 준비 (BEA-390).
 *
 * <p>운영과 같은 상태로 맞춘다. 조각 경제가 켜져 있고 친구 스위치도 켜져 있다. 사용자는 실제 가입 경로로
 * 만들어서 지갑과 탭 기록이 운영과 같은 모양이다.
 */
public abstract class FriendIntegrationTestSupport extends FullStackIntegrationTestSupport {

    @Autowired
    protected AppConfigRepository appConfigs;
    @Autowired
    protected KeycapBoxPolicyConfig economy;
    @Autowired
    protected FriendPolicyConfig friendPolicy;
    @Autowired
    protected AuthLoginTransactionService logins;
    @Autowired
    protected FriendshipService friendships;
    @Autowired
    protected NotificationPreferenceRepository preferences;
    @Autowired
    protected KeycapBoxAccountService wallets;

    @DynamicPropertySource
    static void registerFriendCampaigns(DynamicPropertyRegistry registry) {
        // 캠페인 코드가 없으면 친구 푸시는 대기 행조차 만들지 않는다.
        registry.add("app.smart-message.templates.friend-cheer.campaign-code", () -> "clickmoney-friend-cheer");
        registry.add(
                "app.smart-message.templates.friend-invite-success.campaign-code", () -> "clickmoney-friend-connected");
    }

    @BeforeEach
    void enableFriends() {
        setConfig(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED, "true");
        setConfig(FriendPolicyConfig.KEY_ENABLED, "true");
    }

    /** 설정 값을 바꾸고 바로 반영한다. 운영에서는 최대 60초 뒤에 반영된다. */
    protected void setConfig(String key, String value) {
        jdbcTemplate.update("DELETE FROM app_config WHERE config_key = ?", key);
        appConfigs.save(AppConfig.createFor(key, value, Instant.now()));
        economy.refresh();
        friendPolicy.refresh();
    }

    /** 토스 로그인으로 새로 가입한 사용자. */
    protected UUID signUp(String name) {
        return logins.loginWithTossUser("toss-" + UUID.randomUUID(), name, null, null).userId();
    }

    protected void befriend(UUID userId, UUID otherUserId) {
        friendships.connect(userId, otherUserId, Friendship.Source.INVITE_SIGNUP);
    }

    protected void agreeToFriendPushes(UUID userId) {
        NotificationPreference friend = NotificationPreference.defaultOf(userId, NotificationType.FRIEND);
        friend.applyAgreement("newAgreement");
        preferences.save(friend);
    }

    /** 그 날짜에 누른 수(자동 클릭 포함)를 정한다. */
    protected void setTaps(UUID userId, LocalDate date, long effectiveTaps) {
        jdbcTemplate.update("""
                INSERT INTO user_tap_daily (
                    public_id, user_id, tap_date, valid_tap_count, total_valid_tap_count,
                    total_effective_tap_count, point_earned_amount, version, created_at, updated_at
                ) VALUES (?, ?, ?, 0, 0, ?, 0, 0, now(), now())
                ON CONFLICT (user_id, tap_date)
                    DO UPDATE SET total_effective_tap_count = EXCLUDED.total_effective_tap_count
                """, UUID.randomUUID(), userId, date, effectiveTaps);
    }

    protected LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    protected int shards(UUID userId) {
        return wallets.getAvailableShardBalance(userId);
    }

    /** 이 사용자의 액세스 토큰이 든 Authorization 헤더 값. */
    protected String bearer(UUID userId) {
        return "Bearer " + saveTokenBackedSession(userId, "device-" + userId).accessToken();
    }
}
