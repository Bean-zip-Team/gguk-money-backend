package com.ggukmoney.beanzip.domain.user.service;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class UserRewardLock {
    private final AppUserRepository users;
    @Transactional(propagation=Propagation.MANDATORY)
    public AppUser acquire(UUID userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND"));
    }
}
