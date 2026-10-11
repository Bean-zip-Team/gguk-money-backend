package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.global.config.OnboardingRewardConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeycapService {

    private final KeycapRepository keycapRepository;
    private final UserKeycapRepository userKeycapRepository;
    private final KeycapMapper keycapMapper;
    private final OnboardingRewardConfig onboardingRewardConfig;

    /**
     * 공개 도감은 상시({@code BOX}) 키캡만 내려준다 (BEA-329). 시즌 키캡은 보유자의 내 키캡 목록에서만 보인다.
     * 안 거르면 미보유 카드에 이름이 노출되는 지금 구조에서 아직 시작도 안 한 이벤트가 새어 나가고,
     * 뽑아도 절대 안 나오는 키캡이 목록과 진행도 분모에 남는다.
     */
    public KeycapListResponse getKeycaps() {
        return keycapMapper.mapToKeycapListResponse(
                keycapRepository.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX)
        );
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

    /**
     * 여러 유저의 장착 키캡을 한 번에 읽는다. 랭킹 목록처럼 수십 명을 함께 보여 줄 때 쓴다.
     *
     * <p>장착한 키캡이 없는 유저는 온보딩 보상 키캡으로 채운다. 온보딩 보상을 받으면 그 키캡이 자동으로
     * 장착되고, 장착 해제는 다른 키캡으로 바꿀 때만 일어난다. 그러니 비어 있는 유저는 온보딩 보상을
     * 받지 않은 유저뿐이고, 받았다면 장착했을 키캡을 보여 주는 것이 가장 자연스럽다.
     *
     * <p>기본 키캡을 정하지 못하면 그 유저는 결과에서 빠진다. 그림 한 칸 때문에 화면 전체를 실패시키지 않는다.
     */
    public Map<UUID, EquippedKeycapResponse> getEquippedKeycaps(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EquippedKeycapResponse> result = new HashMap<>();
        for (UserKeycap userKeycap : userKeycapRepository.findEquippedWithKeycapByUserIds(userIds)) {
            result.put(userKeycap.getUser().getId(), keycapMapper.mapToEquippedKeycapResponse(userKeycap));
        }
        if (result.size() < userIds.size()) {
            defaultKeycap().ifPresent(keycap -> userIds.forEach(userId -> result.putIfAbsent(userId, keycap)));
        }
        return result;
    }

    private Optional<EquippedKeycapResponse> defaultKeycap() {
        String code;
        try {
            code = onboardingRewardConfig.resolve().rewardKeycapCode();
        } catch (ResponseStatusException exception) {
            return Optional.empty();
        }
        return keycapRepository.findByCode(code)
                .filter(Keycap::isActive)
                .map(keycap -> new EquippedKeycapResponse(
                        keycap.getPublicId(), keycap.getCode(), keycap.getName(), keycap.getImageUrl()));
    }

    @Transactional
    public KeycapEquipResponse equipKeycap(UUID userId, UUID keycapId) {
        userKeycapRepository.findByUserIdForUpdate(userId);
        UserKeycap target = userKeycapRepository.findByUserIdAndKeycapPublicIdWithKeycap(userId, keycapId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_KEYCAP_NOT_FOUND"));

        // 구 코드가 남긴 진행 중 행은 미보유다 (무중단 배포 구간에만 잠깐 존재한다).
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
