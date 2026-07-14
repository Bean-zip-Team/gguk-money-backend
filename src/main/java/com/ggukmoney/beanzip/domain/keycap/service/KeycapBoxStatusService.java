package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxStatusService {

    private final KeycapBoxAccountRepository keycapBoxAccountRepository;
    private final UserTapProgressService userTapProgressService;
    private final KeycapBoxMapper keycapBoxMapper;

    @Transactional(readOnly = true)
    public KeycapBoxStatusResponse getStatus(UUID userId) {
        KeycapBoxAccount account = keycapBoxAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));
        BoxProgressSnapshot progress = userTapProgressService.getBoxProgress(userId);
        return keycapBoxMapper.mapToStatusResponse(account, progress);
    }
}
