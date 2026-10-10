package com.ggukmoney.beanzip.domain.tap.service;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassiveRoller;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapPassiveService;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
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
import tools.jackson.databind.ObjectMapper;
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
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional
    public TapBatchSubmitResponse submitBatch(UUID userId,TapBatchSubmitRequest request) {
        if(tapPolicyConfig.rateLimitEnabled() &&
                !tryConsumeRateLimit(userId,tapPolicyConfig.rateLimitCapacity(),tapPolicyConfig.rateLimitRefillPerSecond(),clock.instant()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"TAP_RATE_LIMITED");
        var user=lock.acquire(userId);
        var now=clock.instant();
        var existing=tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId,request.tapSessionId(),request.sequence());
        String hash=requestHash(request);
        if(existing.isPresent()) {
            var saved=existing.get();
            if(!Objects.equals(hash,saved.getRequestHash()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"TAP_BATCH_REQUEST_MISMATCH");
            if(saved.getResultJson()!=null) return json.readValue(saved.getResultJson(),TapBatchSubmitResponse.class);
            // Pre-rollout batches have no effects/outcome snapshot. No re-accrual or payout on replay.
            var state=rewards.award(user,now,com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Effects.NONE,
                    0,true,saved.getPublicId(),new KeycapPassiveRoller());
            return response(saved.getAcceptedCount(),0,0,0,0,state);
        }
        var batch=TapBatch.createFor(user,request.tapSessionId(),request.sequence(),request.submittedCount(),hash);
        batch.markAccepted(request.submittedCount());
        batch=tapBatchRepository.save(batch);
        // The request owns one roller; automatic clicks bypass axis ②.
        var roller=new KeycapPassiveRoller();
        var automatic=passive.settleLocked(user,now,"tap:"+batch.getPublicId(),roller);
        var effects=passive.activeEffects(userId);
        var award=rewards.award(user,now,effects,request.submittedCount(),false,batch.getPublicId(),roller);
        var result=response(request.submittedCount(),award.effectiveCount(),automatic.autoClicksGranted(),
                automatic.pointsAwarded(),automatic.shardsDropped(),award);
        batch.confirmResult(json.writeValueAsString(effects),json.writeValueAsString(result));
        return result;
    }

    private TapBatchSubmitResponse response(int accepted,int effective,int automatic,int autoPoints,int autoShards,TapRewardService.Award award) {
        var daily=award.daily(); var session=award.session();
        int remainingShard=(int)Math.max(session.getNextBoxTarget()-session.getSessionValidTapCount(),0);
        return new TapBatchSubmitResponse(accepted,daily.getTotalValidTapCount(),award.pointsAwarded()+autoPoints,
                0,award.account().getBalance(),daily.getPointEarnedAmount()>=tapPolicyConfig.pointDailyCap(),
                session.getSessionValidTapCount(),session.getNextBoxTarget(),daily.getTapDate(),daily.getPointEarnedAmount(),
                progressService.remainingTapsToNextPoint(award.progress(),daily,tapPolicyConfig),remainingShard,0,
                false,false,false,null,award.shardsDropped()+autoShards,award.wallet().getShardBalance(),remainingShard,
                effective,automatic,daily.getTotalEffectiveTapCount());
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
