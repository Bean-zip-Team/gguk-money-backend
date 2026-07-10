package com.ggukmoney.beanzip.domain.user.service;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;

    public AppUser createActive(String nickname, String profileImageUrl) {
        return appUserRepository.save(AppUser.createActive(nickname, profileImageUrl));
    }

    public AppUser getById(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_USER_NOT_FOUND"));
    }

    public AppUser recordLogin(AppUser user, String nickname, String profileImageUrl) {
        user.recordLogin(nickname, profileImageUrl);
        return appUserRepository.save(user);
    }

    public AppUser withdraw(AppUser user) {
        user.withdraw();
        return appUserRepository.save(user);
    }
}
