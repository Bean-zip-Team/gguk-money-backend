package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.dto.response.CheerAckResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerReplyResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerReplyResponse.Outcome;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerSendResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerUnreadListResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendProfileResponse;
import com.ggukmoney.beanzip.domain.friend.entity.Cheer;
import com.ggukmoney.beanzip.domain.friend.repository.CheerRepository;
import com.ggukmoney.beanzip.domain.friend.service.FriendTapReader.TapState;
import com.ggukmoney.beanzip.domain.keycap.entity.ShardLedger;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.service.FriendNotificationService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.domain.user.service.UserRewardLock;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 응원 보내기 (BEA-390).
 *
 * <p>하루에 정해진 수의 친구에게, 같은 친구에게는 하루 한 번 보낼 수 있다. 하루는 한국 시간 기준이다.
 * 보낸 사람은 자기 행만 잠근다. 받는 사람 쪽에는 응원 행과 푸시 대기 행을 넣기만 하고, 받는 사람의
 * 조각은 받는 사람이 확인할 때 준다.
 */
@Service
@RequiredArgsConstructor
public class CheerService {

    private final CheerRepository cheers;
    private final FriendshipService friendships;
    private final FriendTapReader tapReader;
    private final AppUserRepository users;
    private final UserRewardLock userRewardLock;
    private final FriendAccess friendAccess;
    private final KeycapBoxAccountService wallets;
    private final FriendNotificationService friendNotifications;
    private final FriendPolicyConfig policy;
    private final Clock clock;
    private final ZoneId businessZoneId;

    @Transactional
    public CheerSendResponse send(UUID fromUserId, UUID toUserId) {
        // 내 행을 잠가서 내 응원끼리는 차례로 처리한다. 하루 수와 같은 친구 중복을 세는 기준이 흔들리지 않는다.
        AppUser sender = userRewardLock.acquire(fromUserId);
        friendAccess.requireNotSuspended(sender);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, businessZoneId);
        int sentBefore = Math.toIntExact(cheers.countByFromUserIdAndCheerDate(fromUserId, today));

        Attempt attempt = attempt(fromUserId, toUserId, today, sentBefore, now);
        switch (attempt.outcome()) {
            case NOT_FRIENDS -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_FRIENDS");
            case ALREADY_SENT_TODAY -> throw new ResponseStatusException(HttpStatus.CONFLICT, "CHEER_ALREADY_SENT_TODAY");
            case DAILY_LIMIT -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "CHEER_DAILY_LIMIT_EXCEEDED");
            case SENT -> { }
        }

        boolean firstOfDay = sentBefore == 0;
        int granted = firstOfDay ? rewardFirstOfDay(fromUserId, attempt.cheer()) : 0;
        friendNotifications.enqueue(toUserId, NotificationType.FRIEND_CHEER);
        return new CheerSendResponse(
                attempt.cheer().getPublicId(),
                firstOfDay,
                granted,
                Math.max(0, policy.cheerDailyLimit() - sentBefore - 1)
        );
    }

    /**
     * 받은 응원을 보낸 사람들에게 한 번에 답 응원을 보낸다.
     *
     * <p>한 사람씩 {@link #send} 와 같은 규칙으로 처리하고 사람마다 결과를 돌려준다. 남은 횟수를 다 쓰면
     * 나머지는 {@code DAILY_LIMIT} 이다. 확인(ack)을 대신하지 않는다. 받은 조각은 확인을 따로 불러야 받는다.
     */
    @Transactional
    public CheerReplyResponse reply(UUID userId, Collection<UUID> cheerIds) {
        AppUser sender = userRewardLock.acquire(userId);
        friendAccess.requireNotSuspended(sender);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, businessZoneId);
        int sentBefore = Math.toIntExact(cheers.countByFromUserIdAndCheerDate(userId, today));
        List<UUID> receivers = cheerIds.isEmpty()
                ? List.of()
                : cheers.findSenderIds(userId, cheerIds).stream().distinct().toList();

        List<CheerReplyResponse.Result> results = new ArrayList<>();
        List<Cheer> sent = new ArrayList<>();
        for (UUID receiver : receivers) {
            Attempt attempt = attempt(userId, receiver, today, sentBefore + sent.size(), now);
            results.add(new CheerReplyResponse.Result(receiver, attempt.outcome()));
            if (attempt.outcome() == Outcome.SENT) {
                sent.add(attempt.cheer());
            }
        }

        boolean firstOfDay = sentBefore == 0 && !sent.isEmpty();
        int granted = firstOfDay ? rewardFirstOfDay(userId, sent.getFirst()) : 0;
        // 받는 사람 ID 순으로 넣는다. 순서가 엇갈리면 같은 사람들에게 답하는 두 요청이 서로를 기다린다.
        sent.stream()
                .map(Cheer::getToUserId)
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(receiver -> friendNotifications.enqueue(receiver, NotificationType.FRIEND_CHEER));
        return new CheerReplyResponse(
                results, firstOfDay, granted, Math.max(0, policy.cheerDailyLimit() - sentBefore - sent.size()));
    }

    /** 아직 확인하지 않은 응원. */
    @Transactional(readOnly = true)
    public CheerUnreadListResponse unread(UUID userId) {
        return new CheerUnreadListResponse(cheers
                .findUnread(userId, PageRequest.of(0, InviteService.MAX_IDS_PER_REQUEST)).stream()
                .map(row -> new CheerUnreadListResponse.Item(
                        row.getCheerId(),
                        new FriendProfileResponse(row.getUserId(), row.getName()),
                        row.getKind(),
                        row.getTapCountSnapshot(),
                        row.getCreatedAt()))
                .toList());
    }

    /**
     * 받은 응원을 확인하고 조각을 받는다.
     *
     * <p>앱은 응원 모달을 띄우기 직전에 부른다. 한 건에 정해진 수를 주고, 확인한 날(한국 시간) 기준 하루
     * 상한까지 준다. 상한을 넘는 건은 읽음으로만 바뀌고 조각은 0 이며 다시 받을 수 없다. 내 행을 잠그고
     * 아직 확인하지 않은 건만 처리하므로 같은 요청이 두 번 와도 한 번만 준다.
     */
    @Transactional
    public CheerAckResponse ack(UUID userId, Collection<UUID> cheerIds) {
        AppUser receiver = userRewardLock.acquire(userId);
        List<Cheer> ackable = cheerIds.isEmpty() ? List.of() : cheers.findAckable(userId, cheerIds);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, businessZoneId);
        boolean payable = receiver.getStatus() != AppUser.Status.SUSPENDED;
        int rewardEach = policy.cheerReceiveReward();
        int remaining = policy.cheerReceiveDailyCap() - Math.toIntExact(cheers.sumRewardsReadBetween(
                userId,
                today.atStartOfDay(businessZoneId).toInstant(),
                today.plusDays(1).atStartOfDay(businessZoneId).toInstant()));

        int granted = 0;
        boolean capped = false;
        for (Cheer cheer : ackable) {
            int reward = 0;
            if (payable && rewardEach > 0) {
                if (remaining >= rewardEach) {
                    reward = rewardEach;
                    remaining -= rewardEach;
                } else {
                    capped = true;
                }
            }
            cheer.markRead(now, reward);
            if (reward > 0) {
                wallets.grantShards(userId, reward, ShardLedger.Reason.CHEER_RECEIVED, cheer.getPublicId().toString());
                granted += reward;
            }
        }
        return new CheerAckResponse(granted, capped, wallets.getAvailableShardBalance(userId));
    }

    /**
     * 한 사람에게 응원을 시도한다. 호출한 쪽이 보낸 사람의 행 잠금을 잡고 있어야 한다.
     *
     * <p>이미 보낸 친구인지를 하루 한도보다 먼저 본다. 열 번째 응원을 다시 보낸 요청이 '한도 초과'가
     * 아니라 '이미 보냄'으로 돌아와야 앱이 버튼을 완료로 바꿀 수 있다.
     *
     * @param sentToday 오늘 이미 보낸 수
     */
    Attempt attempt(UUID fromUserId, UUID toUserId, LocalDate today, int sentToday, Instant now) {
        if (!isFriendWhoCanReceive(fromUserId, toUserId)) {
            return Attempt.failed(Outcome.NOT_FRIENDS);
        }
        if (cheers.existsByFromUserIdAndToUserIdAndCheerDate(fromUserId, toUserId, today)) {
            return Attempt.failed(Outcome.ALREADY_SENT_TODAY);
        }
        if (sentToday >= policy.cheerDailyLimit()) {
            return Attempt.failed(Outcome.DAILY_LIMIT);
        }
        TapState receiver = tapReader.read(List.of(toUserId), today).getOrDefault(toUserId, TapState.NONE);
        Cheer.Kind kind = kindOf(
                cheers.existsByFromUserIdAndToUserIdAndCheerDate(toUserId, fromUserId, today),
                receiver,
                today,
                policy.cheerGiftLapsedDays(),
                policy.cheerClapThreshold()
        );
        Cheer cheer = cheers.save(Cheer.send(fromUserId, toUserId, today, kind, receiver.todayTapCount(), now));
        return new Attempt(Outcome.SENT, cheer);
    }

    /** 오늘 첫 응원을 보낸 사람에게 조각을 준다. */
    int rewardFirstOfDay(UUID fromUserId, Cheer firstCheer) {
        int reward = policy.cheerSendFirstReward();
        if (reward > 0) {
            wallets.grantShards(
                    fromUserId, reward, ShardLedger.Reason.CHEER_SENT_FIRST, firstCheer.getPublicId().toString());
        }
        return reward;
    }

    /** 받는 사람의 상황으로 응원의 종류를 정한다. 돌려줌 > 선물 > 박수 > 기본 순이다. */
    static Cheer.Kind kindOf(
            boolean receiverCheeredMeToday, TapState receiver, LocalDate today, int giftLapsedDays, int clapThreshold
    ) {
        if (receiverCheeredMeToday) {
            return Cheer.Kind.RETURN;
        }
        // 날짜 차이로 센다. 오늘이 11/24 이고 마지막이 11/21 이면 3일 전이다. 한 번도 누르지 않았으면 해당 없다.
        if (receiver.lastTapDate() != null
                && ChronoUnit.DAYS.between(receiver.lastTapDate(), today) >= giftLapsedDays) {
            return Cheer.Kind.GIFT;
        }
        if (receiver.todayTapCount() >= clapThreshold) {
            return Cheer.Kind.CLAP;
        }
        return Cheer.Kind.DEFAULT;
    }

    /** 친구이고 탈퇴하지 않았는지. 정지된 친구에게는 보낼 수 있다(받는 조각만 0 이다). */
    private boolean isFriendWhoCanReceive(UUID fromUserId, UUID toUserId) {
        return friendships.areFriends(fromUserId, toUserId)
                && users.findById(toUserId)
                .filter(receiver -> receiver.getStatus() != AppUser.Status.WITHDRAWN)
                .isPresent();
    }

    /** @param cheer 보냈을 때만 있다 */
    record Attempt(Outcome outcome, Cheer cheer) {
        static Attempt failed(Outcome outcome) {
            return new Attempt(outcome, null);
        }
    }
}
