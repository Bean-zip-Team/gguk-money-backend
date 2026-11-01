package com.ggukmoney.beanzip.domain.tap.service;
import com.ggukmoney.beanzip.domain.booster.service.BoosterGrantService;
import com.ggukmoney.beanzip.domain.keycap.passive.ClickCriticalSchedule;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassiveRoller;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapPassiveService;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.ClickCriticalScheduleResponse;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import com.ggukmoney.beanzip.domain.user.service.UserRewardLock;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.service.RedisService;
import com.ggukmoney.beanzip.global.util.TokenHash;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class TapBatchService {
    private static final Logger log=LoggerFactory.getLogger(TapBatchService.class);
    private static final RedisScript<Long> TOKEN_BUCKET_SCRIPT=RedisScript.of(new ClassPathResource("scripts/tap-token-bucket.lua"),Long.class);
    private final TapBatchRepository tapBatchRepository;
    private final TapRewardService rewards;
    private final KeycapPassiveService passive;
    private final UserRewardLock lock;
    private final UserTapProgressService progressService;
    private final RedisService redisService;
    private final TapPolicyConfig tapPolicyConfig;
    private final Clock clock;
    private final com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig economy;
    private final ClickCriticalSchedule clickCriticals;
    private final BoosterGrantService boosters;

    @Transactional
    public TapBatchSubmitResponse submitBatch(UUID userId,TapBatchSubmitRequest request) {
        if(tapPolicyConfig.rateLimitEnabled() &&
                !tryConsumeRateLimit(userId,tapPolicyConfig.rateLimitCapacity(),tapPolicyConfig.rateLimitRefillPerSecond(),clock.instant()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"TAP_RATE_LIMITED");
        var user=lock.acquire(userId);
        var now=clock.instant();
        boolean shard=economy.isShardEconomyEnabled();
        var existing=tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId,request.tapSessionId(),request.sequence());
        String hash=requestHash(request);
        if(existing.isPresent()) {
            var saved=existing.get();
            // Existing apps identify a batch by session/sequence, even if a retry changes count.
            // Return current state with zero awards; no result JSON is persisted.
            // Replaying neither settles new automatic clicks nor rolls another reward.
            var state=rewards.award(user,now,com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Effects.NONE,
                    0,true,saved.getPublicId(),new KeycapPassiveRoller());
            var click=passive.displayEffects(userId).click();
            // acceptedCount echoes the submitted taps; accepted_count holds the points they scored.
            return response(saved.getSubmittedCount(),0,0,0,0,state,shard,userId,now,click);
        }
        // The server, not the app, applies the booster: one press scores pressSize points.
        int pressSize=pressSize(userId,request,now);
        int points=Math.multiplyExact(request.submittedCount(),pressSize);
        var batch=TapBatch.createFor(user,request.tapSessionId(),request.sequence(),request.submittedCount(),hash);
        // accepted_count stays in points: abuse detection divides it by the booster multiplier.
        batch.markAccepted(points);
        batch=tapBatchRepository.save(batch);
        var context=passive.requestPolicy(now,shard);
        // The request owns one roller; automatic clicks bypass axis ②.
        var roller=new KeycapPassiveRoller();
        var automatic=passive.settleLocked(user,now,"tap:"+batch.getPublicId(),roller,context);
        var equipment=passive.activeEquipment(userId,context);
        var effects=equipment.effects();
        var award=rewards.award(user,now,effects,points,false,batch.getPublicId(),roller,pressSize);
        long startTapNumber=award.progress().getCumulativeMissionTapCount()-points+1;
        batch.recordClickCriticals(startTapNumber,equipment.code(),effects.click(),award.clickCriticalCount());
        return response(request.submittedCount(),award.effectiveCount(),automatic.autoClicksGranted(),
                automatic.pointsAwarded(),automatic.shardsDropped(),award,shard,userId,now,effects.click());
    }

    private TapBatchSubmitResponse response(int accepted,int effective,int automatic,int autoPoints,int autoShards,TapRewardService.Award award,boolean shard,UUID userId,Instant now,Critical click) {
        var daily=award.daily(); var session=award.session();
        int remainingShard=(int)Math.max(session.getNextBoxTarget()-session.getSessionValidTapCount(),0);
        var cycle=shard ? null : award.wallet().calculateOpenCycleSnapshot(now,economy.openCycleDuration(),
                economy.freeOpenLimit(),economy.adOpenLimit());
        int drops=award.shardsDropped()+autoShards;
        return new TapBatchSubmitResponse(accepted,daily.getTotalValidTapCount(),award.pointsAwarded()+autoPoints,
                shard?0:drops,award.account().getBalance(),daily.getPointEarnedAmount()>=tapPolicyConfig.pointDailyCap(),
                session.getSessionValidTapCount(),session.getNextBoxTarget(),daily.getTapDate(),daily.getPointEarnedAmount(),
                progressService.remainingTapsToNextPoint(award.progress(),daily,tapPolicyConfig),remainingShard,shard?0:award.wallet().getBoxBalance(),
                !shard && cycle.canFreeOpen(),!shard && cycle.canAdOpen(),!shard && cycle.charging(),shard?null:cycle.nextRechargeAt(),
                shard?drops:0,shard?rewards.availableShardBalance(userId):0,remainingShard,
                effective,automatic,daily.getTotalEffectiveTapCount(),
                ClickCriticalScheduleResponse.of(clickCriticals,userId,award.progress().getCumulativeMissionTapCount(),click));
    }

    /** Legacy apps omit the multiplier and send points already multiplied; a claimed multiplier needs a booster that ran. */
    private int pressSize(UUID userId,TapBatchSubmitRequest request,Instant now) {
        Integer multiplier=request.boosterMultiplier();
        return multiplier!=null && multiplier>1 && boosters.wasActive(userId,multiplier,now) ? multiplier : 1;
    }

    private String requestHash(TapBatchSubmitRequest request) {
        return TokenHash.sha256Base64Url(request.tapSessionId()+":"+request.sequence()+":"+request.submittedCount());
    }

    boolean tryConsumeRateLimit(UUID userId,int capacity,double refillPerSecond,Instant now) {
        try {
            Long allowed=redisService.executeScript(TOKEN_BUCKET_SCRIPT,List.of("ggukmoney:tap:bucket:"+userId),
                    String.valueOf(capacity),String.valueOf(refillPerSecond),String.valueOf(now.toEpochMilli()));
            return allowed!=null && allowed==1L;
        } catch(RuntimeException exception) {
            log.error("Failed to evaluate tap rate limit for userId={}",userId,exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"TAP_REDIS_UNAVAILABLE",exception);
        }
    }
}
