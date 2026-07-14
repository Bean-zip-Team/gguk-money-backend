package com.ggukmoney.beanzip.domain.cashout.service;

import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutQuoteResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutSubmitResponse;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.CashoutPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashoutService {

    private static final String DEBIT_REASON_CASHOUT = "CASHOUT";

    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final CashoutRequestRepository cashoutRequestRepository;
    private final UserService userService;
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

    @Transactional
    public CashoutSubmitResponse submit(UUID userId, UUID idempotencyKey) {
        Optional<CashoutRequest> existing = cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        if (cashoutRequestRepository.existsByUserIdAndStatusIn(
                userId, List.of(CashoutRequest.Status.REQUESTED, CashoutRequest.Status.PROCESSING))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CASHOUT_ALREADY_PROCESSING");
        }

        long balance = pointAccountService.getBalance(userId);
        int minimumPoint = cashoutPolicyConfig.minimumPoint();
        if (balance < minimumPoint) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CASHOUT_MINIMUM_NOT_MET");
        }

        AppUser user = userService.getById(userId);
        long tossPointAmount = (long) Math.floor(balance * cashoutPolicyConfig.pointToKrwRate());

        PointAccount account = pointAccountService.debit(userId, balance);
        pointLedgerService.recordDebit(account, user, balance, DEBIT_REASON_CASHOUT, idempotencyKey);

        CashoutRequest request = cashoutRequestRepository.save(
                CashoutRequest.createFor(user, balance, tossPointAmount, idempotencyKey)
        );
        return toResponse(request);
    }

    private CashoutSubmitResponse toResponse(CashoutRequest request) {
        return new CashoutSubmitResponse(
                request.getPublicId(),
                request.getPointAmount(),
                request.getTossPointAmount(),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }
}
