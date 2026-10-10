package com.ggukmoney.beanzip.domain.keycap.dto.response;
import java.time.Instant;
public record KeycapPassiveSettleResponse(int autoClicksGranted,int pointsAwarded,int shardsDropped,
        long balance,int shardBalance,boolean capped,Instant settledAt) {}
