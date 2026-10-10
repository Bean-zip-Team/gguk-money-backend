package com.ggukmoney.beanzip.domain.keycap.service;
import com.ggukmoney.beanzip.domain.keycap.dto.response.*;
import com.ggukmoney.beanzip.domain.keycap.entity.*;
import com.ggukmoney.beanzip.domain.keycap.repository.*;
import com.ggukmoney.beanzip.domain.keycap.passive.*;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.*;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Effects;
import com.ggukmoney.beanzip.domain.tap.service.TapRewardService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserRewardLock;
import com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class KeycapPassiveService {
    private final UserRewardLock lock;
    private final UserKeycapRepository equipment;
    private final KeycapPassiveCheckpointRepository checkpoints;
    private final KeycapPassiveSettlementRepository settlements;
    private final KeycapPassivePolicyConfig config;
    private final TapRewardService rewards;
    private final ObjectMapper json;
    private final Clock clock;
    private final com.ggukmoney.beanzip.domain.point.service.PointAccountService pointAccounts;
    private final KeycapBoxAccountService wallets;
    private final KeycapAutoClickAccrual calculator=new KeycapAutoClickAccrual();

    @Transactional
    public KeycapPassiveSettleResponse settle(UUID userId,String key) {
        if (key==null || key.isBlank() || key.length()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED");
        AppUser user=lock.acquire(userId);
        String storedKey="api:"+key;
        var existing=settlements.findByUserIdAndIdempotencyKey(userId,storedKey);
        if(existing.isPresent()) return json.readValue(existing.get().getResultJson(),KeycapPassiveSettleResponse.class);
        var outcome=settleLocked(user,clock.instant(),storedKey,new KeycapPassiveRoller());
        settlements.save(KeycapPassiveSettlement.create(user,storedKey,json.writeValueAsString(outcome),outcome.settledAt()));
        return outcome;
    }

    /** All hooks enter after locking AppUser, before locking wallets/equipment or changing levels. */
    @Transactional(propagation=Propagation.MANDATORY)
    public KeycapPassiveSettleResponse settleLocked(AppUser user,Instant now,String operationKey,KeycapPassiveRoller roller) {
        var snapshot=config.snapshot();
        var row=checkpoints.findById(user.getId()).orElseGet(() -> {
            var current=currentEquipment(user.getId(),snapshot);
            return checkpoints.save(KeycapPassiveCheckpoint.create(user,
                    Checkpoint.initial(now,current.code(),current.level(),current.effects().autoClicksPerDay()),
                    json.writeValueAsString(current.effects()),snapshot.capDays()));
        });
        var calculated=calculator.calculate(row.value(),now,new Policy(snapshot.enabled(),snapshot.enabledAt(),row.getCapDays()));
        Effects old=json.readValue(row.getEffectsJson(),Effects.class);
        UUID operationId=UUID.nameUUIDFromBytes((user.getId()+":"+operationKey).getBytes(StandardCharsets.UTF_8));
        var award=calculated.grantedClicks()>0
                ? rewards.award(user,now,old,calculated.grantedClicks(),true,operationId,roller) : null;
        var current=currentEquipment(user.getId(),snapshot);
        row.update(calculated.checkpoint().withEquipment(current.code(),current.level(),current.effects().autoClicksPerDay()),
                json.writeValueAsString(current.effects()),snapshot.capDays());
        return new KeycapPassiveSettleResponse(calculated.grantedClicks(),award==null?0:award.pointsAwarded(),award==null?0:award.shardsDropped(),
                award==null?pointAccounts.getForUser(user.getId()).getBalance():award.account().getBalance(),
                award==null?wallets.getForUser(user.getId()).getShardBalance():award.wallet().getShardBalance(),calculated.capped(),now);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void refreshEquipmentLocked(UUID userId) {
        var row=checkpoints.findById(userId).orElseThrow();
        var snapshot=config.snapshot();
        var current=currentEquipment(userId,snapshot);
        row.update(row.value().withEquipment(current.code(),current.level(),current.effects().autoClicksPerDay()),
                json.writeValueAsString(current.effects()),snapshot.capDays());
    }

    public Effects activeEffects(UUID userId) {
        var snapshot=config.snapshot();
        return snapshot.enabled()?currentEquipment(userId,snapshot).effects():Effects.NONE;
    }

    @Transactional(readOnly=true)
    public KeycapPassiveStatusResponse status(UUID userId) {
        var now=clock.instant();
        var snapshot=config.snapshot();
        var current=currentEquipment(userId,snapshot);
        var row=checkpoints.findById(userId);
        var value=row.map(KeycapPassiveCheckpoint::value).orElse(Checkpoint.initial(now,current.code(),current.level(),current.effects().autoClicksPerDay()));
        int capDays=row.map(KeycapPassiveCheckpoint::getCapDays).orElse(snapshot.capDays());
        var pending=calculator.calculate(value,now,new Policy(snapshot.enabled(),snapshot.enabledAt(),capDays));
        return new KeycapPassiveStatusResponse(snapshot.enabled(),current.code(),current.code()==null?null:current.level(),
                KeycapPassiveEffectResponse.from(current.effects(),snapshot.enabled(),null),pending.grantedClicks(),
                Math.multiplyExact(value.clicksPerDay(),capDays),pending.capped(),row.map(KeycapPassiveCheckpoint::getLastActivityAt).orElse(null),now);
    }

    private Equipment currentEquipment(UUID userId,KeycapPassivePolicyConfig.Snapshot snapshot) {
        return equipment.findByUserIdAndEquippedTrue(userId)
                .filter(owned -> owned.getLevel()>=1)
                .map(owned -> new Equipment(owned.getKeycap().getCode(),owned.getLevel(),
                        snapshot.policy().effects(owned.getKeycap().getCode(),owned.getLevel())))
                .orElse(new Equipment(null,0,Effects.NONE));
    }
    private record Equipment(String code,int level,Effects effects) {}
}
