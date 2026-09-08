package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.domain.promotion.repository.PromotionGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 유저용 미션 목록 (BEA-292).
 *
 * <p>미션 정의를 별도 테이블이나 설정에 두지 않는다. {@link PromotionTrigger} 구현체가 곧 미션
 * 정의다 — 코드·이름·보상·임계치·진행도를 이미 전부 알고 있다. 새 미션을 추가하면 트리거 하나만
 * 만들면 목록에도 자동으로 잡힌다. 운영 조회({@code /api/ops/promotions/grants})가 같은 방식이다.
 */
@Service
@RequiredArgsConstructor
public class MissionQueryService {

    private final List<PromotionTrigger> promotionTriggers;
    private final PromotionGrantRepository promotionGrantRepository;

    @Transactional(readOnly = true)
    public MissionListResponse missionsOf(UUID userId) {
        Map<String, PromotionGrant> grants = promotionGrantRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        PromotionGrant::getPromotionCode,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));

        List<MissionListResponse.Mission> missions = promotionTriggers.stream()
                .filter(trigger -> isVisible(trigger, grants))
                .map(trigger -> toMission(trigger, userId, grants.get(trigger.promotionCode())))
                .toList();

        return new MissionListResponse(missions);
    }

    /**
     * 꺼진 미션은 감춘다. 다만 <b>이미 받은 미션은 계속 보인다</b> — 받은 뒤 미션이 내려갔다고
     * 목록에서 사라지면 유저는 자기가 받은 적이 있는지 확인할 수 없다.
     */
    private boolean isVisible(PromotionTrigger trigger, Map<String, PromotionGrant> grants) {
        return trigger.issuingEnabled() || grants.containsKey(trigger.promotionCode());
    }

    private MissionListResponse.Mission toMission(PromotionTrigger trigger, UUID userId, PromotionGrant grant) {
        MissionProgress progress = trigger.progressOf(userId);
        return new MissionListResponse.Mission(
                trigger.promotionCode(),
                trigger.missionName(),
                trigger.amount(),
                progress.current(),
                progress.target(),
                statusOf(grant)
        );
    }

    /**
     * 지급 건이 있으면 이미 달성한 것이다. 1인 1회라 다시 진행 중으로 돌아가지 않는다.
     *
     * <p>FAILED 도 ACHIEVED 로 본다. 자격은 얻었고 지급만 막힌 상태이므로 유저에게 "아직 진행
     * 중"이라고 보이면 다시 모으면 받을 수 있다는 잘못된 신호가 된다.
     */
    private MissionListResponse.Status statusOf(PromotionGrant grant) {
        if (grant == null) {
            return MissionListResponse.Status.IN_PROGRESS;
        }
        return grant.getStatus() == PromotionGrant.Status.SUCCEEDED
                ? MissionListResponse.Status.REWARDED
                : MissionListResponse.Status.ACHIEVED;
    }
}
