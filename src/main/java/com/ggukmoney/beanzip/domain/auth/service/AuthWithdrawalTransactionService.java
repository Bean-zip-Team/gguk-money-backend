package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthWithdrawalTransactionService {

    private final AppUserRepository appUserRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final TossLoginConsentHistoryService tossLoginConsentHistoryService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public WithdrawalPreparation prepare(UUID userId) {
        AppUser user = findUser(userId);
        if (user.isWithdrawn()) {
            return new WithdrawalPreparation(userId, true, null);
        }
        String providerUserId = authIdentityRepository
                .findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)
                .map(AuthIdentity::getProviderUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TOSS_IDENTITY_NOT_FOUND"));
        return new WithdrawalPreparation(userId, false, providerUserId);
    }

    @Transactional
    public void complete(UUID userId, String eventSource) {
        AppUser user = findUserForUpdate(userId);
        if (user.isWithdrawn()) {
            return;
        }
        tossLoginConsentHistoryService.withdrawActiveAgreements(userId, eventSource);
        userService.withdraw(user);
    }

    @Transactional
    public Optional<UUID> completeFromWebhook(String providerUserId, String eventSource) {
        Optional<AuthIdentity> identity = authIdentityRepository.findByProviderAndProviderUserId(
                AuthIdentity.Provider.TOSS,
                providerUserId
        );
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        UUID userId = identity.get().getUser().getId();
        AppUser user = findUserForUpdate(userId);
        tossLoginConsentHistoryService.withdrawActiveAgreements(userId, eventSource);
        if (!user.isWithdrawn()) {
            userService.withdraw(user);
        }
        return Optional.of(userId);
    }

    private AppUser findUser(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_USER_NOT_FOUND"));
    }

    private AppUser findUserForUpdate(UUID userId) {
        return appUserRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_USER_NOT_FOUND"));
    }

    public record WithdrawalPreparation(
            UUID userId,
            boolean alreadyWithdrawn,
            String providerUserId
    ) {
    }
}
