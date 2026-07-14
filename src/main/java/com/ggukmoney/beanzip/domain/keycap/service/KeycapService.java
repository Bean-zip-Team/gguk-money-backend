package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeycapService {

    private final KeycapRepository keycapRepository;
    private final UserKeycapRepository userKeycapRepository;
    private final KeycapMapper keycapMapper;

    public KeycapListResponse getKeycaps() {
        return keycapMapper.mapToKeycapListResponse(keycapRepository.findByActiveTrueOrderBySortOrderAscCodeAsc());
    }

    public MyKeycapListResponse getMyKeycaps(UUID userId) {
        return keycapMapper.mapToMyKeycapListResponse(
                userKeycapRepository.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(userId)
        );
    }

    public EquippedKeycapResponse getEquippedKeycap(UUID userId) {
        return userKeycapRepository.findByUserIdAndEquippedTrue(userId)
                .map(keycapMapper::mapToEquippedKeycapResponse)
                .orElse(null);
    }

    @Transactional
    public KeycapEquipResponse equipKeycap(UUID userId, UUID keycapId) {
        userKeycapRepository.findByUserIdForUpdate(userId);
        UserKeycap target = userKeycapRepository.findByUserIdAndKeycapPublicIdWithKeycap(userId, keycapId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_KEYCAP_NOT_FOUND"));

        if (!target.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KEYCAP_NOT_COMPLETED");
        }

        userKeycapRepository.findEquippedByUserIdForUpdate(userId)
                .filter(current -> current != target)
                .ifPresent(UserKeycap::unequip);

        target.equip();
        return keycapMapper.mapToKeycapEquipResponse(target);
    }
}
