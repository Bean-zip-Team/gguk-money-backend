package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.dto.response.CheerSendResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    enum Outcome {
        SENT,
        ALREADY_SENT_TODAY,
        DAILY_LIMIT,
        NOT_FRIENDS
    }

    /** @param cheer 보냈을 때만 있다 */
    record Attempt(Outcome outcome, Cheer cheer) {
        static Attempt failed(Outcome outcome) {
            return new Attempt(outcome, null);
        }
    }
}
