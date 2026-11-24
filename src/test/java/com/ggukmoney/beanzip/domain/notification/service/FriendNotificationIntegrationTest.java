package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient.SendResult;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository.DueFriendDelivery;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 친구 푸시의 쿼리를 실제 PostgreSQL 에서 확인한다 (BEA-390).
 *
 * <p>동의 확인, 하루 한 통, 다시 집지 않기는 모두 SQL 이 맡고 있어서 목으로는 틀려도 드러나지 않는다.
 */
class FriendNotificationIntegrationTest extends FullStackIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");

    @Autowired
    private FriendNotificationService friendNotificationService;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private NotificationPreferenceRepository preferenceRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private AuthIdentityRepository authIdentityRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private TossSmartMessageClient smartMessageClient;
    @MockitoBean
    private FriendPolicyConfig friendPolicy;

    @DynamicPropertySource
    static void registerFriendCampaigns(DynamicPropertyRegistry registry) {
        registry.add("app.smart-message.templates.friend-cheer.campaign-code", () -> "clickmoney-friend-cheer");
        registry.add(
                "app.smart-message.templates.friend-invite-success.campaign-code", () -> "clickmoney-friend-connected");
        // The scheduler runs in the test context too. At second 0 of a minute it would send the due row
        // just before the test's own call does, and that call would then find nothing.
        registry.add("app.smart-message.schedule.friend-cron", () -> "-");
    }

    @BeforeEach
    void clearDeliveries() {
        jdbcTemplate.update("DELETE FROM notification_delivery");
        when(friendPolicy.friendsEnabled()).thenReturn(true);
    }

    @Test
    void queuesOnlyForFriendConsentAndOncePerTypePerDay() {
        UUID agreed = user(friend -> { });
        UUID turnedOff = user(friend -> friend.updateEnabled(false));
        UUID neverAsked = appUserRepository.save(AppUser.createActive(name(), null)).getId();
        // Consent to another type is not consent to friend pushes.
        UUID otherConsent = appUserRepository.save(AppUser.createActive(name(), null)).getId();
        NotificationPreference rank = NotificationPreference.defaultOf(otherConsent, NotificationType.RANK_CHANGE);
        rank.applyAgreement("newAgreement");
        preferenceRepository.save(rank);

        assertThat(insert(agreed, "FRIEND_CHEER", "2026-11-24", NOW)).isEqualTo(1);
        assertThat(insert(agreed, "FRIEND_CHEER", "2026-11-24", NOW)).as("second cheer the same day").isZero();
        assertThat(insert(agreed, "FRIEND_INVITE_SUCCESS", "2026-11-24", NOW)).as("the other kind").isEqualTo(1);
        assertThat(insert(agreed, "FRIEND_CHEER", "2026-11-25", NOW)).as("the next day").isEqualTo(1);
        assertThat(insert(turnedOff, "FRIEND_CHEER", "2026-11-24", NOW)).isZero();
        assertThat(insert(neverAsked, "FRIEND_CHEER", "2026-11-24", NOW)).isZero();
        assertThat(insert(otherConsent, "FRIEND_CHEER", "2026-11-24", NOW)).isZero();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification_delivery", Long.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_delivery WHERE user_id = ? AND status = 'PENDING' "
                        + "AND attempt_count = 0 AND context_json = '{}'", Long.class, agreed)).isEqualTo(3);
    }

    @Test
    void queuedPushIsRolledBackWithTheCallersTransaction() {
        UUID userId = user(friend -> { });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            friendNotificationService.enqueue(userId, NotificationType.FRIEND_CHEER);
            status.setRollbackOnly();
        });
        assertThat(deliveryCount(userId)).as("a cancelled cheer leaves no push").isZero();

        friendNotificationService.enqueue(userId, NotificationType.FRIEND_CHEER);
        friendNotificationService.enqueue(userId, NotificationType.FRIEND_CHEER);
        assertThat(deliveryCount(userId)).isEqualTo(1);
    }

    @Test
    void dueFinderReportsWhatBlocksEachRow() {
        UUID sendable = user(friend -> { });
        UUID revoked = user(friend -> { });
        UUID withdrawn = user(friend -> { });
        UUID stale = user(friend -> { });
        UUID later = user(friend -> { });
        insert(sendable, "FRIEND_CHEER", "2026-11-24", NOW);
        insert(revoked, "FRIEND_CHEER", "2026-11-24", NOW);
        insert(withdrawn, "FRIEND_INVITE_SUCCESS", "2026-11-24", NOW);
        insert(stale, "FRIEND_CHEER", "2026-11-24", NOW.minus(Duration.ofMinutes(61)));
        insert(later, "FRIEND_CHEER", "2026-11-25", NOW.plus(Duration.ofHours(3)));
        // What changed after the row was queued must still stop it.
        NotificationPreference revokedPreference =
                preferenceRepository.findByUserIdAndType(revoked, NotificationType.FRIEND).orElseThrow();
        revokedPreference.updateEnabled(false);
        preferenceRepository.save(revokedPreference);
        AppUser withdrawnUser = appUserRepository.findById(withdrawn).orElseThrow();
        withdrawnUser.withdraw();
        appUserRepository.save(withdrawnUser);

        Map<UUID, DueFriendDelivery> due = deliveryRepository
                .findDueFriendDeliveries(NOW, NOW.minus(Duration.ofHours(1)), 50).stream()
                .collect(Collectors.toMap(DueFriendDelivery::getUserId, row -> row));

        assertThat(due).containsOnlyKeys(sendable, revoked, withdrawn, stale);
        assertThat(due.get(sendable)).satisfies(row -> {
            assertThat(row.getExpired()).isFalse();
            assertThat(row.getConsented()).isTrue();
            assertThat(row.getUserStatus()).isEqualTo("ACTIVE");
            assertThat(row.getAttemptCount()).isZero();
            assertThat(row.getTemplateSetCode()).isEqualTo("clickmoney-friend-cheer");
        });
        assertThat(due.get(revoked).getConsented()).isFalse();
        assertThat(due.get(withdrawn).getUserStatus()).isEqualTo("WITHDRAWN");
        assertThat(due.get(stale).getExpired()).isTrue();
    }

    @Test
    void claimHidesTheRowUntilItsLeaseEndsAndStopsAfterThreeAttempts() {
        UUID userId = user(friend -> { });
        insert(userId, "FRIEND_CHEER", "2026-11-24", NOW);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_delivery WHERE user_id = ?", Long.class, userId);
        Instant lease = NOW.plus(Duration.ofMinutes(1));

        assertThat(deliveryRepository.claimFriendAttempt(id, NOW, lease, 3)).isEqualTo(1);
        assertThat(deliveryRepository.claimFriendAttempt(id, NOW, lease, 3)).as("already claimed").isZero();
        assertThat(deliveryRepository.findDueFriendDeliveries(NOW, NOW.minus(Duration.ofHours(1)), 50)).isEmpty();

        // A row whose send died mid-flight comes back once the lease has passed.
        Instant afterLease = lease.plus(Duration.ofMinutes(10));
        assertThat(deliveryRepository.findDueFriendDeliveries(afterLease, afterLease.minus(Duration.ofHours(1)), 50))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getAttemptCount()).isEqualTo(1);
                    assertThat(row.getExpired()).isFalse();
                });
        // If nothing ran for hours (the feature was switched off, or an outage), the retry is stale too:
        // it is measured from its last attempt, not from having never been tried.
        Instant hoursLater = NOW.plus(Duration.ofHours(3));
        assertThat(deliveryRepository.findDueFriendDeliveries(hoursLater, hoursLater.minus(Duration.ofHours(1)), 50))
                .singleElement()
                .satisfies(row -> assertThat(row.getExpired()).isTrue());
        assertThat(deliveryRepository.claimFriendAttempt(id, afterLease, afterLease, 3)).isEqualTo(1);
        assertThat(deliveryRepository.claimFriendAttempt(id, afterLease, afterLease, 3)).isEqualTo(1);
        assertThat(deliveryRepository.claimFriendAttempt(id, afterLease, afterLease, 3)).as("fourth attempt").isZero();
    }

    @Test
    void dispatchSendsEachDuePushOnce() {
        UUID userId = user(friend -> { });
        friendNotificationService.enqueue(userId, NotificationType.FRIEND_INVITE_SUCCESS);
        // Whatever the wall clock says, make the row due so the test does not depend on the time of day.
        // A minute back rather than now(): the database and the JVM do not share a clock to the millisecond.
        jdbcTemplate.update(
                "UPDATE notification_delivery SET next_attempt_at = now() - interval '1 minute' WHERE user_id = ?",
                userId);
        when(smartMessageClient.sendMessage(anyString(), anyString(), anyString()))
                .thenReturn(new SendResult(true, "content-1", null, null, false, "SUCCESS", "{}"));

        assertThat(friendNotificationService.dispatchDue()).isEqualTo(1);
        assertThat(friendNotificationService.dispatchDue()).isZero();

        verify(smartMessageClient, times(1)).sendMessage("toss-" + userId, "clickmoney-friend-connected", "{}");
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM notification_delivery WHERE user_id = ?", String.class, userId))
                .containsExactly("SENT");
    }

    private int insert(UUID userId, String type, String sendDate, Instant sendAt) {
        return new TransactionTemplate(transactionManager).execute(status -> deliveryRepository
                .insertFriendPendingIfAbsent(
                        UUID.randomUUID(), userId, type, type + ":" + userId + ":" + sendDate,
                        "FRIEND_CHEER".equals(type) ? "clickmoney-friend-cheer" : "clickmoney-friend-connected",
                        NOW, sendAt));
    }

    /** 친구 알림에 동의한 사용자를 만든다. 넘긴 함수로 동의 상태를 바꿀 수 있다. */
    private UUID user(Consumer<NotificationPreference> adjust) {
        AppUser user = appUserRepository.save(AppUser.createActive(name(), null));
        authIdentityRepository.save(AuthIdentity.toss(user, "toss-" + user.getId()));
        NotificationPreference friend = NotificationPreference.defaultOf(user.getId(), NotificationType.FRIEND);
        friend.applyAgreement("newAgreement");
        adjust.accept(friend);
        preferenceRepository.save(friend);
        return user.getId();
    }

    private static String name() {
        return "friend-" + UUID.randomUUID();
    }

    private long deliveryCount(UUID userId) {
        List<Long> counts = jdbcTemplate.queryForList(
                "SELECT count(*) FROM notification_delivery WHERE user_id = ?", Long.class, userId);
        return counts.getFirst();
    }
}
