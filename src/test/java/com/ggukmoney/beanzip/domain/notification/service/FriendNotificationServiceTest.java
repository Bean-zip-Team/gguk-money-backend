package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient.SendResult;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository.DueFriendDelivery;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FriendNotificationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final NotificationTemplateProperties TEMPLATES = new NotificationTemplateProperties(
            null, null, null, null, null, null, null, "clickmoney-friend-cheer", "clickmoney-friend-connected");

    private final NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
    private final NotificationDeliveryPersistenceService persistenceService =
            mock(NotificationDeliveryPersistenceService.class);
    private final NotificationBatchReadService batchReadService = mock(NotificationBatchReadService.class);
    private final TossSmartMessageClient smartMessageClient = mock(TossSmartMessageClient.class);
    private final FriendPolicyConfig friendPolicy = mock(FriendPolicyConfig.class);

    @ParameterizedTest
    @CsvSource({
            // created at (KST)          , sent at (KST)
            "2026-11-24T00:10:00+09:00, 2026-11-24T08:00:00+09:00",
            "2026-11-24T07:59:59+09:00, 2026-11-24T08:00:00+09:00",
            "2026-11-24T08:00:00+09:00, 2026-11-24T08:00:00+09:00",
            "2026-11-24T20:59:59+09:00, 2026-11-24T20:59:59+09:00",
            "2026-11-24T21:00:00+09:00, 2026-11-25T08:00:00+09:00",
            "2026-11-24T23:30:00+09:00, 2026-11-25T08:00:00+09:00",
    })
    void pushesCreatedBetweenNinePmAndEightAmWaitForEightAm(String createdAt, String expected) {
        assertThat(FriendNotificationService.sendAt(OffsetDateTime.parse(createdAt).toInstant(), SEOUL))
                .isEqualTo(OffsetDateTime.parse(expected).toInstant());
    }

    @Test
    void daytimePushIsQueuedForNowUnderTodaysKey() {
        UUID userId = UUID.randomUUID();
        Instant now = OffsetDateTime.parse("2026-11-24T14:00:00+09:00").toInstant();

        serviceAt(now).enqueue(userId, NotificationType.FRIEND_CHEER);

        verify(deliveryRepository).insertFriendPendingIfAbsent(
                any(UUID.class), eq(userId), eq("FRIEND_CHEER"),
                eq("FRIEND_CHEER:" + userId + ":2026-11-24"), eq("clickmoney-friend-cheer"), eq(now), eq(now));
    }

    @Test
    void nightPushIsQueuedForEightAmUnderTheNextDaysKey() {
        UUID userId = UUID.randomUUID();
        Instant now = OffsetDateTime.parse("2026-11-24T22:00:00+09:00").toInstant();

        serviceAt(now).enqueue(userId, NotificationType.FRIEND_INVITE_SUCCESS);

        // The day in the key is the day it is sent, so this push is the one for 11/25.
        verify(deliveryRepository).insertFriendPendingIfAbsent(
                any(UUID.class), eq(userId), eq("FRIEND_INVITE_SUCCESS"),
                eq("FRIEND_INVITE_SUCCESS:" + userId + ":2026-11-25"), eq("clickmoney-friend-connected"), eq(now),
                eq(OffsetDateTime.parse("2026-11-25T08:00:00+09:00").toInstant()));
    }

    @Test
    void nothingIsQueuedWhenTheCampaignCodeIsNotConfigured() {
        FriendNotificationService unconfigured = new FriendNotificationService(
                deliveryRepository, persistenceService, batchReadService,
                new NotificationTemplateProperties(null, null, null, null, null, null, null, null, null),
                smartMessageClient, friendPolicy, Clock.systemUTC(), SEOUL);

        unconfigured.enqueue(UUID.randomUUID(), NotificationType.FRIEND_CHEER);

        verifyNoInteractions(deliveryRepository);
    }

    @Test
    void nothingIsSentWhileTheFriendFeatureIsOff() {
        when(friendPolicy.friendsEnabled()).thenReturn(false);

        assertThat(serviceAt(Instant.parse("2026-11-24T05:00:00Z")).dispatchDue()).isZero();

        verifyNoInteractions(deliveryRepository, smartMessageClient);
    }

    @Test
    void dueRowIsClaimedThenSentThenMarkedSent() {
        Instant now = Instant.parse("2026-11-24T05:00:00Z");
        UUID userId = UUID.randomUUID();
        stubDue(now, row(11L, userId, 0, false, true, "ACTIVE"));
        when(batchReadService.loadProviderUserIds(List.of(userId))).thenReturn(Map.of(userId, "toss-user-1"));
        when(deliveryRepository.claimFriendAttempt(11L, now, now.plus(Duration.ofMinutes(1)), 3)).thenReturn(1);
        when(smartMessageClient.sendMessage("toss-user-1", "clickmoney-friend-cheer", "{}"))
                .thenReturn(new SendResult(true, "content-1", null, null, false, "SUCCESS", "{\"ok\":true}"));

        assertThat(serviceAt(now).dispatchDue()).isEqualTo(1);

        verify(persistenceService).markSent(11L, "content-1", "{\"ok\":true}");
    }

    @Test
    void rowsThatMayNoLongerBeSentAreClosedWithoutCallingToss() {
        Instant now = Instant.parse("2026-11-24T05:00:00Z");
        UUID missingIdentity = UUID.randomUUID();
        stubDue(now,
                row(1L, UUID.randomUUID(), 0, true, true, "ACTIVE"),
                row(2L, UUID.randomUUID(), 3, false, true, "ACTIVE"),
                row(3L, UUID.randomUUID(), 0, false, false, "ACTIVE"),
                row(4L, UUID.randomUUID(), 0, false, null, "ACTIVE"),
                row(5L, UUID.randomUUID(), 0, false, true, "WITHDRAWN"),
                row(6L, UUID.randomUUID(), 0, false, true, "SUSPENDED"),
                row(7L, missingIdentity, 0, false, true, "ACTIVE"));
        when(batchReadService.loadProviderUserIds(any())).thenReturn(Map.of());

        assertThat(serviceAt(now).dispatchDue()).isZero();

        verify(persistenceService).markFailed(1L, "EXPIRED", null, null);
        verify(persistenceService).markFailed(2L, "MAX_ATTEMPTS_EXCEEDED", null, null);
        verify(persistenceService).markFailed(3L, "CONSENT_REVOKED", null, null);
        verify(persistenceService).markFailed(4L, "CONSENT_REVOKED", null, null);
        verify(persistenceService).markFailed(5L, "USER_NOT_ACTIVE", null, null);
        verify(persistenceService).markFailed(6L, "USER_NOT_ACTIVE", null, null);
        verify(persistenceService).markFailed(7L, "TOSS_USER_KEY_MISSING", null, null);
        verify(deliveryRepository, never()).claimFriendAttempt(any(), any(), any(), anyInt());
        verifyNoInteractions(smartMessageClient);
    }

    @Test
    void rowClaimedByAnotherRunIsNotSentAgain() {
        Instant now = Instant.parse("2026-11-24T05:00:00Z");
        UUID userId = UUID.randomUUID();
        stubDue(now, row(11L, userId, 0, false, true, "ACTIVE"));
        when(batchReadService.loadProviderUserIds(any())).thenReturn(Map.of(userId, "toss-user-1"));
        when(deliveryRepository.claimFriendAttempt(eq(11L), any(), any(), anyInt())).thenReturn(0);

        assertThat(serviceAt(now).dispatchDue()).isZero();

        verifyNoInteractions(smartMessageClient, persistenceService);
    }

    @Test
    void retryableFailureWaitsUntilTheThirdAttemptThenFails() {
        Instant now = Instant.parse("2026-11-24T05:00:00Z");
        UUID first = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        stubDue(now,
                row(21L, first, 0, false, true, "ACTIVE"),
                row(22L, third, 2, false, true, "ACTIVE"),
                row(23L, rejected, 0, false, true, "ACTIVE"));
        when(batchReadService.loadProviderUserIds(any()))
                .thenReturn(Map.of(first, "toss-1", third, "toss-3", rejected, "toss-x"));
        // The second and third attempts lease five minutes, the first one minute.
        when(deliveryRepository.claimFriendAttempt(21L, now, now.plus(Duration.ofMinutes(1)), 3)).thenReturn(1);
        when(deliveryRepository.claimFriendAttempt(22L, now, now.plus(Duration.ofMinutes(5)), 3)).thenReturn(1);
        when(deliveryRepository.claimFriendAttempt(23L, now, now.plus(Duration.ofMinutes(1)), 3)).thenReturn(1);
        SendResult timeout = new SendResult(false, null, "TIMEOUT", "timed out", true, null, null);
        when(smartMessageClient.sendMessage(eq("toss-1"), anyString(), anyString())).thenReturn(timeout);
        when(smartMessageClient.sendMessage(eq("toss-3"), anyString(), anyString())).thenReturn(timeout);
        when(smartMessageClient.sendMessage(eq("toss-x"), anyString(), anyString()))
                .thenReturn(new SendResult(false, null, "REJECTED", "not agreed", false, "FAIL", "{}"));

        assertThat(serviceAt(now).dispatchDue()).isZero();

        verify(persistenceService).markRetryWaiting(21L, "TIMEOUT", "timed out", null);
        verify(persistenceService).markFailed(22L, "TIMEOUT", "timed out", null);
        verify(persistenceService).markFailed(23L, "REJECTED", "not agreed", "{}");
    }

    private FriendNotificationService serviceAt(Instant now) {
        return new FriendNotificationService(
                deliveryRepository, persistenceService, batchReadService, TEMPLATES, smartMessageClient,
                friendPolicy, Clock.fixed(now, SEOUL), SEOUL);
    }

    private void stubDue(Instant now, DueFriendDelivery... rows) {
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(deliveryRepository.findDueFriendDeliveries(now, now.minus(Duration.ofHours(1)), 50))
                .thenReturn(List.of(rows));
    }

    private static DueFriendDelivery row(
            Long id, UUID userId, int attemptCount, boolean expired, Boolean consented, String userStatus
    ) {
        return new DueFriendDelivery() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public String getTemplateSetCode() {
                return "clickmoney-friend-cheer";
            }

            @Override
            public Integer getAttemptCount() {
                return attemptCount;
            }

            @Override
            public Boolean getExpired() {
                return expired;
            }

            @Override
            public Boolean getConsented() {
                return consented;
            }

            @Override
            public String getUserStatus() {
                return userStatus;
            }
        };
    }
}
