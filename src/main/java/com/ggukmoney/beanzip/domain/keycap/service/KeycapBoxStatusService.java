package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxStatusService {

    private final KeycapBoxAccountService keycapBoxAccountService;
    private final UserTapProgressService userTapProgressService;
    private final KeycapBoxMapper keycapBoxMapper;

    @Transactional
    public KeycapBoxStatusResponse getStatus(UUID userId) {
        KeycapBoxAccount account = keycapBoxAccountService.refillFreeTickets(userId);
        BoxProgressSnapshot progress = userTapProgressService.getBoxProgress(userId);
        return keycapBoxMapper.mapToStatusResponse(account, progress);
    }
}
