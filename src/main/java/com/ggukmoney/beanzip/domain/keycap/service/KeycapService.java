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

        // 해제를 먼저 확정한 뒤에 장착한다. ux_user_keycap_equipped 가 유저당 equipped = true 를
        // 하나로 제한하는데, 두 변경을 모두 더티 체킹에 맡기면 UPDATE 발행 순서가 영속성 컨텍스트
        // 적재 순서를 따른다. 장착이 먼저 나가는 순간 두 행이 true 가 되어 인덱스를 위반한다.
        userKeycapRepository.findEquippedByUserIdForUpdate(userId)
                .filter(current -> current != target)
                .ifPresent(current -> {
                    current.unequip();
                    userKeycapRepository.flush();
                });

        target.equip();
        return keycapMapper.mapToKeycapEquipResponse(target);
    }
}
