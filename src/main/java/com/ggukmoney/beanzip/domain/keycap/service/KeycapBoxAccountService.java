package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxAccountService {

    private final KeycapBoxAccountRepository keycapBoxAccountRepository;

    public KeycapBoxAccount createFor(AppUser user) {
        return keycapBoxAccountRepository.save(KeycapBoxAccount.createFor(user));
    }

    public KeycapBoxAccount addBoxes(UUID userId, int count) {
        KeycapBoxAccount account = keycapBoxAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
        account.addBoxes(count);
        return keycapBoxAccountRepository.save(account);
    }
}
