package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeycapService {

    private final UserKeycapRepository userKeycapRepository;
    private final KeycapMapper keycapMapper;

    public EquippedKeycapResponse getEquippedKeycap(UUID userId) {
        return userKeycapRepository.findByUserIdAndEquippedTrue(userId)
                .map(keycapMapper::mapToEquippedKeycapResponse)
                .orElse(null);
    }
}
