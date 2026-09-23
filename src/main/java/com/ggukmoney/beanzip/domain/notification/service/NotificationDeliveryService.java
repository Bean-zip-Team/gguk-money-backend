package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.mission.service.DailyMissionNudgeService;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.event.WeeklyRewardAvailableEvent;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private static final int KEYCAP_BOX_BATCH_SIZE = 200;

    private final NotificationDeliveryPersistenceService persistenceService;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final BoosterGrantRepository boosterGrantRepository;
    private final TapPolicyConfig tapPolicyConfig;
    private final KeycapBoxAccountRepository keycapBoxAccountRepository;
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig;
    private final NotificationTemplateProperties templateProperties;
    private final TossSmartMessageClient smartMessageClient;
    private final NotificationBatchReadService batchReadService;
    private final DailyMissionNudgeService dailyMissionNudgeService;
    private final Clock clock;

    public Optional<NotificationDelivery> handleWeeklyRewardAvailable(WeeklyRewardAvailableEvent event) {
        return send(
                event.userId(),
                NotificationType.RANK_CHANGE,
                "RANK_CHANGE:WEEKLY_REWARD_AVAILABLE:" + event.userId() + ":" + event.rewardCycleKey(),
                "{\"rewardCycleKey\":\"%s\",\"availableAt\":\"%s\"}".formatted(event.rewardCycleKey(), event.availableAt())
        );
    }

    public Optional<NotificationDelivery> evaluateRankChange(UUID userId) {
        return dispatch(persistenceService.prepareRankChange(userId));
    }

    public Optional<NotificationDelivery> dispatchPreparedRankChange(Long deliveryId) {
        return dispatch(persistenceService.findPendingRankChange(deliveryId));
    }

    public List<NotificationDelivery> sendMorningNotifications(LocalDate today) {
        List<NotificationDelivery> deliveries = new ArrayList<>();
        Set<UUID> rechargedCandidates = new HashSet<>(boosterGrantRepository.findUserIdsWhoExhaustedDailyBoosters(
                today.minusDays(1), tapPolicyConfig.boosterDailyLimit()));
        deliveries.addAll(sendBoosterRechargedPages(today, rechargedCandidates));
        long cursor = 0L;
        while (true) {
            List<NotificationPreferenceRepository.SendableCandidate> candidates =
                    batchReadService.findCandidates(NotificationType.DAILY_REMINDER, cursor);
            if (candidates.isEmpty()) {
                break;
            }
            cursor = candidates.getLast().getPreferenceId();
            List<UUID> userIds = candidateUserIds(candidates);
            NotificationBatchReadService.NotificationPageData page = batchReadService.loadPage(userIds, today);
            for (NotificationPreferenceRepository.SendableCandidate candidate : candidates) {
                UUID userId = candidate.getUserId();
                if (rechargedCandidates.contains(userId)) {
                    continue;
                }
                try {
                    if (!page.validTapUserIds().contains(userId) && page.boosterCount(userId) == 0) {
                        sendScheduled(
                                userId,
                                NotificationType.DAILY_REMINDER,
                                "DAILY_REMINDER:" + userId + ":" + compactDate(today),
                                "{}",
                                page.providerUserIds().get(userId)
                        ).ifPresent(deliveries::add);
                    }
                } catch (Exception exception) {
                    log.error("Failed to process morning reminder. userId={}, date={}", userId, today, exception);
                }
            }
            if (candidates.size() < NotificationBatchReadService.PAGE_SIZE) {
                break;
            }
        }
        return deliveries;
    }

    public List<NotificationDelivery> sendEveningNotifications(LocalDate today) {
        List<NotificationDelivery> deliveries = new ArrayList<>();
        Set<UUID> rankSelected = new HashSet<>();
        sendRankChangePages(today, deliveries, rankSelected);
        int dailyLimit = tapPolicyConfig.boosterDailyLimit();
        long cursor = 0L;
        while (true) {
            List<NotificationPreferenceRepository.SendableCandidate> candidates =
                    batchReadService.findCandidates(NotificationType.BOOSTER_UNUSED, cursor);
            if (candidates.isEmpty()) {
                break;
            }
            cursor = candidates.getLast().getPreferenceId();
            List<UUID> userIds = candidateUserIds(candidates);
            Set<UUID> rechargedSentUsers = batchReadService.findSentUserIdsSinceStartOfDay(
                    userIds,
                    NotificationType.BOOSTER_RECHARGED,
                    today
            );
            NotificationBatchReadService.NotificationPageData page = batchReadService.loadPage(userIds, today);
            for (NotificationPreferenceRepository.SendableCandidate candidate : candidates) {
                UUID userId = candidate.getUserId();
                if (rankSelected.contains(userId) || rechargedSentUsers.contains(userId)) {
                    continue;
                }
                try {
                    if (!page.validTapUserIds().contains(userId) && page.boosterCount(userId) < dailyLimit) {
                        sendScheduled(
                                userId,
                                NotificationType.BOOSTER_UNUSED,
                                "BOOSTER_UNUSED:" + userId + ":" + compactDate(today),
                                "{}",
                                page.providerUserIds().get(userId)
                        ).ifPresent(deliveries::add);
                    }
                } catch (Exception exception) {
                    log.error("Failed to process booster unused notification. userId={}, date={}", userId, today, exception);
                }
            }
            if (candidates.size() < NotificationBatchReadService.PAGE_SIZE) {
                break;
            }
        }
        return deliveries;
    }

    /**
     * 저녁에 데일리 미션을 상기시킨다 (BEA-299).
     *
     * <p>오늘 받을 보상이 남았거나 아직 끝내지 못한 미션이 있는 유저에게만 보낸다. 대상을 고르는
     * 기준은 {@link DailyMissionNudgeService} 가 정한다. 오늘 할 일을 다 끝낸 유저에게 보내면 할 일이
     * 없는데 오는 알림이 되고, 그게 알림 동의를 해제하는 이유가 된다.
     */
    public List<NotificationDelivery> sendDailyMissionNotifications(LocalDate today) {
        if (!templateProperties.isConfigured(NotificationType.DAILY_MISSION)) {
            // 보낼 수 없는데 매일 밤 알림 설정 전체를 훑을 이유가 없다.
            log.warn("DAILY_MISSION_NOTIFICATION_SKIPPED reason=CAMPAIGN_CODE_MISSING date={}", today);
            return List.of();
        }

        List<NotificationDelivery> deliveries = new ArrayList<>();
        Instant startedAt = clock.instant();
        // 판정 기준은 배치마다 한 번만 세운다. 페이지마다 다시 세우면 발송이 길어질 때 앞뒤 페이지가
        // 서로 다른 기준으로 판정된다.
        DailyMissionNudgeService.NudgeCriteria criteria = dailyMissionNudgeService.criteriaOf(today);
        long cursor = 0L;
        while (true) {
            List<NotificationPreferenceRepository.SendableCandidate> candidates =
                    batchReadService.findCandidates(NotificationType.DAILY_MISSION, cursor);
            if (candidates.isEmpty()) {
                break;
            }
            cursor = candidates.getLast().getPreferenceId();
            List<UUID> userIds = candidateUserIds(candidates);
            // 페이지마다 시각을 다시 읽는다. 발송이 길어져 자정을 넘기면 뒤쪽 페이지는 이미 소멸한
            // 보상을 들고 "지금 받으세요" 라고 보내게 된다.
            Set<UUID> needsNudge = dailyMissionNudgeService.usersNeedingNudge(criteria, userIds, clock.instant());
            Map<UUID, String> providerUserIds = batchReadService.loadProviderUserIds(userIds);
            for (NotificationPreferenceRepository.SendableCandidate candidate : candidates) {
                UUID userId = candidate.getUserId();
                if (!needsNudge.contains(userId)) {
                    continue;
                }
                try {
                    sendScheduled(
                            userId,
                            NotificationType.DAILY_MISSION,
                            "DAILY_MISSION:" + userId + ":" + compactDate(today),
                            "{}",
                            providerUserIds.get(userId)
                    ).ifPresent(deliveries::add);
                } catch (Exception exception) {
                    log.error("Failed to process daily mission notification. userId={}, date={}", userId, today, exception);
                }
            }
            if (candidates.size() < NotificationBatchReadService.PAGE_SIZE) {
                break;
            }
        }
        // 자정까지 시간이 남아 있을 때 끝났는지를 이 줄로 확인한다. 유저가 늘어 발송이 자정을 넘기면
        // 뒤쪽 유저는 이미 소멸한 보상을 들고 "지금 받으세요" 라는 알림을 받게 된다.
        log.info("DAILY_MISSION_NOTIFICATION_DONE date={} sentCount={} elapsedMillis={}",
                today, deliveries.size(), Duration.between(startedAt, clock.instant()).toMillis());
        return deliveries;
    }

    private List<NotificationDelivery> sendBoosterRechargedPages(LocalDate today, Set<UUID> exhaustedUsers) {
        if (exhaustedUsers.isEmpty()) {
            return List.of();
        }
        List<NotificationDelivery> deliveries = new ArrayList<>();
        long cursor = 0L;
        while (true) {
            List<NotificationPreferenceRepository.SendableCandidate> candidates =
                    batchReadService.findCandidates(NotificationType.BOOSTER_RECHARGED, cursor);
            if (candidates.isEmpty()) {
                break;
            }
            cursor = candidates.getLast().getPreferenceId();
            List<UUID> userIds = candidateUserIds(candidates);
            NotificationBatchReadService.NotificationPageData page = batchReadService.loadPage(userIds, today);
            for (NotificationPreferenceRepository.SendableCandidate candidate : candidates) {
                UUID userId = candidate.getUserId();
                if (!exhaustedUsers.contains(userId) || page.boosterCount(userId) > 0) {
                    continue;
                }
                try {
                    sendScheduled(
                            userId,
                            NotificationType.BOOSTER_RECHARGED,
                            "BOOSTER_RECHARGED:" + userId + ":" + compactDate(today),
                            "{}",
                            page.providerUserIds().get(userId)
                    ).ifPresent(deliveries::add);
                } catch (Exception exception) {
                    log.error("Failed to send booster recharged notification. userId={}, date={}", userId, today, exception);
                }
            }
            if (candidates.size() < NotificationBatchReadService.PAGE_SIZE) {
                break;
            }
        }
        return deliveries;
    }

    private void sendRankChangePages(
            LocalDate today,
            List<NotificationDelivery> deliveries,
            Set<UUID> rankSelected
    ) {
        long cursor = 0L;
        while (true) {
            List<NotificationPreferenceRepository.SendableCandidate> candidates =
                    batchReadService.findCandidates(NotificationType.RANK_CHANGE, cursor);
            if (candidates.isEmpty()) {
                break;
            }
            cursor = candidates.getLast().getPreferenceId();
            List<UUID> userIds = candidateUserIds(candidates);
            NotificationBatchReadService.NotificationPageData page = batchReadService.loadPage(userIds, today);
            Optional<NotificationBatchReadService.RankPageData> rankPage = batchReadService.loadRankPage(userIds);
            if (rankPage.isEmpty()) {
                log.error("Rank notification page skipped because both Redis and DB rank reads were unavailable. cursor={}, userCount={}",
                        cursor, userIds.size());
            } else {
                NotificationBatchReadService.RankPageData ranks = rankPage.get();
                for (NotificationPreferenceRepository.SendableCandidate candidate : candidates) {
                    UUID userId = candidate.getUserId();
                    Long currentRank = ranks.ranks().get(userId);
                    if (currentRank == null) {
                        continue;
                    }
                    try {
                        dispatch(
                                persistenceService.prepareScheduledRankChange(
                                        userId,
                                        ranks.season(),
                                        currentRank,
                                        ranks.states().get(userId),
                                        ranks.cooldownUsers().contains(userId)
                                ),
                                page.providerUserIds().get(userId)
                        ).ifPresent(delivery -> {
                            deliveries.add(delivery);
                            rankSelected.add(userId);
                        });
                    } catch (Exception exception) {
                        log.error("Failed to process rank notification. userId={}, date={}", userId, today, exception);
                    }
                }
            }
            if (candidates.size() < NotificationBatchReadService.PAGE_SIZE) {
                break;
            }
        }
    }

    public List<NotificationDelivery> sendKeycapBoxOpenAvailableNotifications(Instant now) {
        NotificationType type = NotificationType.KEYCAP_BOX_OPEN_AVAILABLE;
        if (!templateProperties.isConfigured(type)) {
            return List.of();
        }

        Duration cycleDuration = keycapBoxPolicyConfig.openCycleDuration();
        int freeOpenLimit = keycapBoxPolicyConfig.freeOpenLimit();
        int adOpenLimit = keycapBoxPolicyConfig.adOpenLimit();
        if (freeOpenLimit == 0 && adOpenLimit == 0) {
            return List.of();
        }

        Instant cutoff = now.minus(cycleDuration);
        long lastAccountId = 0L;
        List<NotificationDelivery> deliveries = new ArrayList<>();
        while (true) {
            List<KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate> candidates =
                    keycapBoxAccountRepository.findKeycapBoxOpenAvailableCandidates(
                            type,
                            lastAccountId,
                            freeOpenLimit,
                            adOpenLimit,
                            cutoff,
                            PageRequest.of(0, KEYCAP_BOX_BATCH_SIZE)
                    );
            if (candidates.isEmpty()) {
                break;
            }

            for (KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate candidate : candidates) {
                lastAccountId = candidate.getAccountId();
                try {
                    dispatch(persistenceService.prepareKeycapBoxOpenAvailable(candidate.getUserId(), now))
                            .ifPresent(deliveries::add);
                } catch (Exception exception) {
                    log.error(
                            "Failed to process keycap box open available notification. userId={}, accountId={}, now={}",
                            candidate.getUserId(),
                            candidate.getAccountId(),
                            now,
                            exception
                    );
                }
            }

            if (candidates.size() < KEYCAP_BOX_BATCH_SIZE) {
                break;
            }
        }
        return deliveries;
    }

    private Optional<NotificationDelivery> send(UUID userId, NotificationType type, String dedupeKey, String contextJson) {
        if (!templateProperties.isConfigured(type) || !isSendable(userId, type)) {
            return Optional.empty();
        }
        return dispatch(persistenceService.createPending(userId, type, dedupeKey, templateProperties.campaignCode(type), contextJson));
    }

    private Optional<NotificationDelivery> sendScheduled(
            UUID userId,
            NotificationType type,
            String dedupeKey,
            String contextJson,
            String providerUserId
    ) {
        if (!templateProperties.isConfigured(type)) {
            return Optional.empty();
        }
        return dispatch(
                persistenceService.createPending(
                        userId, type, dedupeKey, templateProperties.campaignCode(type), contextJson),
                providerUserId
        );
    }

    private Optional<NotificationDelivery> dispatch(Optional<NotificationDelivery> pending) {
        if (pending.isEmpty()) {
            return Optional.empty();
        }

        NotificationDelivery delivery = pending.get();
        Optional<AuthIdentity> identity = authIdentityRepository.findByUserIdAndProvider(delivery.getUserId(), AuthIdentity.Provider.TOSS);
        return dispatch(pending, identity.map(AuthIdentity::getProviderUserId).orElse(null));
    }

    private Optional<NotificationDelivery> dispatch(Optional<NotificationDelivery> pending, String providerUserId) {
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        NotificationDelivery delivery = pending.get();
        if (providerUserId == null) {
            return Optional.of(persistenceService.markFailed(
                    delivery.getId(),
                    "TOSS_USER_KEY_MISSING",
                    "Toss auth identity was not found",
                    null
            ));
        }

        TossSmartMessageClient.SendResult result = smartMessageClient.sendMessage(
                providerUserId,
                delivery.getTemplateSetCode(),
                delivery.getContextJson()
        );
        NotificationDelivery updated;
        if (result.succeeded()) {
            updated = persistenceService.markSent(delivery.getId(), result.contentId(), result.responseBody());
        } else if (result.retryable()) {
            updated = persistenceService.markRetryWaiting(
                    delivery.getId(), result.errorCode(), result.reason(), result.responseBody()
            );
        } else {
            updated = persistenceService.markFailed(
                    delivery.getId(), result.errorCode(), result.reason(), result.responseBody()
            );
        }
        log.info(
                "Smart message delivery result. deliveryId={}, notificationType={}, status={}, tossResultType={}, errorCode={}",
                updated.getId(),
                updated.getType(),
                updated.getStatus(),
                result.providerResultType(),
                result.errorCode()
        );
        return Optional.of(updated);
    }

    private List<UUID> candidateUserIds(
            List<NotificationPreferenceRepository.SendableCandidate> candidates
    ) {
        return candidates.stream().map(NotificationPreferenceRepository.SendableCandidate::getUserId).toList();
    }

    private boolean isSendable(UUID userId, NotificationType type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .map(NotificationPreference::isSendable)
                .orElse(false);
    }

    private String compactDate(LocalDate date) {
        return date.toString().replace("-", "");
    }
}
