package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository.DueFriendDelivery;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 친구 푸시(응원 도착, 초대 성공·친구 연결)를 발송 대기로 넣고 보낸다 (BEA-390).
 *
 * <ul>
 *   <li>친구 알림({@link NotificationType#FRIEND})에 동의하고 켜 둔 사람에게만 보낸다.</li>
 *   <li>한 사람에게 종류별로 하루 한 통이다. 하루는 보내는 날 기준이다.</li>
 *   <li>밤 9시부터 아침 8시 사이에 생긴 푸시는 아침 8시에 보낸다.</li>
 * </ul>
 *
 * <p>푸시는 고정 문구라서 템플릿에 넘길 값이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendNotificationService {

    static final LocalTime QUIET_FROM = LocalTime.of(21, 0);
    static final LocalTime QUIET_UNTIL = LocalTime.of(8, 0);
    static final int MAX_ATTEMPTS = 3;
    /**
     * 한 번 실행에 집는 최대 건수. 매분 실행하므로 분당 상한이기도 하다. 주간 리셋 알림(분당 100)과 같은
     * 분에 겹쳐도 넘치지 않게 잡았다.
     */
    static final int SENDS_PER_RUN = 50;
    /**
     * 보내려던 시각에서 이만큼 지나도록 한 번도 시도하지 못한 푸시는 버린다. 장애가 풀린 뒤에 묵은 푸시가
     * 밤에 나가거나, 어제 몫과 오늘 몫이 같은 날 두 통 나가는 일을 막는다.
     */
    static final Duration STALE_AFTER = Duration.ofHours(1);

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDeliveryPersistenceService persistenceService;
    private final NotificationBatchReadService batchReadService;
    private final NotificationTemplateProperties templateProperties;
    private final TossSmartMessageClient smartMessageClient;
    private final FriendPolicyConfig friendPolicy;
    private final Clock clock;
    private final ZoneId businessZoneId;

    /**
     * 받는 사람 앞으로 푸시를 한 건 대기시킨다. 동의하지 않았거나 같은 날 같은 종류가 이미 있으면 아무
     * 일도 일어나지 않는다.
     *
     * <p>호출한 쪽의 트랜잭션에 참여한다. 응원이나 초대가 취소되면 푸시도 함께 취소된다. 한 트랜잭션에서
     * 여러 사람에게 넣을 때는 받는 사람 ID 순으로 불러야 한다. 순서가 엇갈리면 같은 사람에게 넣으려는 두
     * 트랜잭션이 서로를 기다린다.
     */
    @Transactional
    public void enqueue(UUID toUserId, NotificationType type) {
        String campaignCode = templateProperties.campaignCode(type);
        if (campaignCode == null) {
            return;
        }
        Instant now = clock.instant();
        Instant sendAt = sendAt(now, businessZoneId);
        deliveryRepository.insertFriendPendingIfAbsent(
                UUID.randomUUID(),
                toUserId,
                type.name(),
                "%s:%s:%s".formatted(type.name(), toUserId, sendAt.atZone(businessZoneId).toLocalDate()),
                campaignCode,
                now,
                sendAt
        );
    }

    /** 밤 9시~아침 8시에 생긴 푸시는 그 뒤에 오는 아침 8시, 그 밖에는 지금이다. */
    static Instant sendAt(Instant now, ZoneId zone) {
        ZonedDateTime local = now.atZone(zone);
        LocalTime time = local.toLocalTime();
        if (time.isBefore(QUIET_UNTIL)) {
            return local.toLocalDate().atTime(QUIET_UNTIL).atZone(zone).toInstant();
        }
        if (!time.isBefore(QUIET_FROM)) {
            return local.toLocalDate().plusDays(1).atTime(QUIET_UNTIL).atZone(zone).toInstant();
        }
        return now;
    }

    /**
     * 보낼 시각이 된 푸시를 보낸다. 매분 실행한다.
     *
     * @return 보낸 건수
     */
    public int dispatchDue() {
        if (!friendPolicy.friendsEnabled()) {
            // 기능을 끈 동안에는 보내지 않는다. 그동안 쌓인 행은 다시 켰을 때 묵은 것으로 버려진다.
            return 0;
        }
        Instant now = clock.instant();
        List<DueFriendDelivery> due =
                deliveryRepository.findDueFriendDeliveries(now, now.minus(STALE_AFTER), SENDS_PER_RUN);
        if (due.isEmpty()) {
            return 0;
        }
        Map<UUID, String> providerUserIds =
                batchReadService.loadProviderUserIds(due.stream().map(DueFriendDelivery::getUserId).toList());

        int sent = 0;
        int retryWaiting = 0;
        int failed = 0;
        for (DueFriendDelivery row : due) {
            String providerUserId = providerUserIds.get(row.getUserId());
            String blockedBy = blockedBy(row, providerUserId);
            if (blockedBy != null) {
                persistenceService.markFailed(row.getId(), blockedBy, null, null);
                failed++;
                continue;
            }
            int attempt = row.getAttemptCount() + 1;
            if (deliveryRepository.claimFriendAttempt(row.getId(), now, now.plus(retryDelay(attempt)), MAX_ATTEMPTS) == 0) {
                continue;
            }
            TossSmartMessageClient.SendResult result =
                    smartMessageClient.sendMessage(providerUserId, row.getTemplateSetCode(), "{}");
            if (result.succeeded()) {
                persistenceService.markSent(row.getId(), result.contentId(), result.responseBody());
                sent++;
            } else if (result.retryable() && attempt < MAX_ATTEMPTS) {
                // 다음 시도 시각은 위에서 잡아 둔 시각 그대로다.
                persistenceService.markRetryWaiting(row.getId(), result.errorCode(), result.reason(), result.responseBody());
                retryWaiting++;
            } else {
                persistenceService.markFailed(row.getId(), result.errorCode(), result.reason(), result.responseBody());
                failed++;
            }
        }
        log.info("FRIEND_NOTIFICATION_DISPATCH due={} sent={} retryWaiting={} failed={}",
                due.size(), sent, retryWaiting, failed);
        return sent;
    }

    private static String blockedBy(DueFriendDelivery row, String providerUserId) {
        if (Boolean.TRUE.equals(row.getExpired())) {
            return "EXPIRED";
        }
        if (row.getAttemptCount() >= MAX_ATTEMPTS) {
            // 세 번째 시도 중에 서버가 죽은 행이다. 그대로 두면 매분 다시 집힌다.
            return "MAX_ATTEMPTS_EXCEEDED";
        }
        if (!Boolean.TRUE.equals(row.getConsented())) {
            return "CONSENT_REVOKED";
        }
        if (!"ACTIVE".equals(row.getUserStatus())) {
            return "USER_NOT_ACTIVE";
        }
        if (providerUserId == null) {
            return "TOSS_USER_KEY_MISSING";
        }
        return null;
    }

    private static Duration retryDelay(int attempt) {
        return attempt <= 1 ? Duration.ofMinutes(1) : Duration.ofMinutes(5);
    }
}
