package com.ggukmoney.beanzip.domain.cashout.service;

import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutQuoteResponse;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.global.config.CashoutPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashoutService {

    private final PointAccountService pointAccountService;
    private final CashoutPolicyConfig cashoutPolicyConfig;

    public CashoutQuoteResponse getQuote(UUID userId) {
        long balance = pointAccountService.getBalance(userId);
        int minimumPoint = cashoutPolicyConfig.minimumPoint();
        double rate = cashoutPolicyConfig.pointToKrwRate();
        long tossPointAmount = (long) Math.floor(balance * rate);
        boolean eligible = balance >= minimumPoint;

        return new CashoutQuoteResponse(
                balance,
                tossPointAmount,
                minimumPoint,
                new CashoutQuoteResponse.RateInfo(rate),
                eligible
        );
    }
}
