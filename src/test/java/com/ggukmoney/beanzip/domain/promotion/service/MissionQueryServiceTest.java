package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.domain.promotion.repository.PromotionGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionQueryServiceTest {

    private final PromotionGrantRepository promotionGrantRepository = mock(PromotionGrantRepository.class);
    private final PromotionTrigger keycapTrigger = mock(PromotionTrigger.class);
    private final PromotionTrigger tapTrigger = mock(PromotionTrigger.class);

    private final MissionQueryService service =
            new MissionQueryService(List.of(keycapTrigger, tapTrigger), promotionGrantRepository);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubTriggers() {
        lenient().when(keycapTrigger.promotionCode()).thenReturn("KEYCAP_FIVE_COMPLETE");
        lenient().when(keycapTrigger.missionName()).thenReturn("키캡 5개 모으기");
        lenient().when(keycapTrigger.amount()).thenReturn(500L);
        lenient().when(keycapTrigger.issuingEnabled()).thenReturn(true);
        lenient().when(keycapTrigger.progressOf(userId)).thenReturn(MissionProgress.of(2, 5));

        lenient().when(tapTrigger.promotionCode()).thenReturn("TAP_THOUSAND_COMPLETE");
        lenient().when(tapTrigger.missionName()).thenReturn("1,000번 누르기");
        lenient().when(tapTrigger.amount()).thenReturn(5L);
        lenient().when(tapTrigger.issuingEnabled()).thenReturn(true);
        lenient().when(tapTrigger.progressOf(userId)).thenReturn(MissionProgress.of(1000, 1000));

        lenient().when(promotionGrantRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
    }

    private PromotionGrant grantWith(String promotionCode, PromotionGrant.Status status) {
        PromotionGrant grant = mock(PromotionGrant.class);
        lenient().when(grant.getPromotionCode()).thenReturn(promotionCode);
        lenient().when(grant.getStatus()).thenReturn(status);
        return grant;
    }

    @Test
    void returnsDefinitionAndProgressTogether() {
        MissionListResponse response = service.missionsOf(userId);

        assertThat(response.missions()).hasSize(2);
        MissionListResponse.Mission keycap = response.missions().getFirst();
        assertThat(keycap.code()).isEqualTo("KEYCAP_FIVE_COMPLETE");
        assertThat(keycap.name()).isEqualTo("키캡 5개 모으기");
        assertThat(keycap.rewardAmount()).isEqualTo(500L);
        assertThat(keycap.current()).isEqualTo(2L);
        assertThat(keycap.target()).isEqualTo(5L);
        assertThat(keycap.status()).isEqualTo(MissionListResponse.Status.IN_PROGRESS);
    }

    @Test
    void marksRewardedWhenGrantSucceeded() {
        List<PromotionGrant> grants = List.of(grantWith("TAP_THOUSAND_COMPLETE", PromotionGrant.Status.SUCCEEDED));
        when(promotionGrantRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(grants);

        MissionListResponse.Status status = service.missionsOf(userId).missions().stream()
                .filter(mission -> mission.code().equals("TAP_THOUSAND_COMPLETE"))
                .findFirst().orElseThrow().status();

        assertThat(status).isEqualTo(MissionListResponse.Status.REWARDED);
    }

    @Test
    void marksAchievedWhilePayoutIsStillPending() {
        List<PromotionGrant> grants = List.of(grantWith("TAP_THOUSAND_COMPLETE", PromotionGrant.Status.PENDING));
        when(promotionGrantRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(grants);

        MissionListResponse.Status status = service.missionsOf(userId).missions().stream()
                .filter(mission -> mission.code().equals("TAP_THOUSAND_COMPLETE"))
                .findFirst().orElseThrow().status();

        assertThat(status).isEqualTo(MissionListResponse.Status.ACHIEVED);
    }

    @Test
    void treatsFailedPayoutAsAchievedNotInProgress() {
        List<PromotionGrant> grants = List.of(grantWith("TAP_THOUSAND_COMPLETE", PromotionGrant.Status.FAILED));
        when(promotionGrantRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(grants);

        MissionListResponse.Status status = service.missionsOf(userId).missions().stream()
                .filter(mission -> mission.code().equals("TAP_THOUSAND_COMPLETE"))
                .findFirst().orElseThrow().status();

        // 자격은 얻었고 지급만 막힌 상태다. 진행 중으로 보이면 다시 모으면 받는다는 잘못된 신호다.
        assertThat(status).isEqualTo(MissionListResponse.Status.ACHIEVED);
    }

    @Test
    void hidesMissionsThatAreTurnedOff() {
        when(tapTrigger.issuingEnabled()).thenReturn(false);

        assertThat(service.missionsOf(userId).missions())
                .extracting(MissionListResponse.Mission::code)
                .containsExactly("KEYCAP_FIVE_COMPLETE");
    }

    @Test
    void keepsShowingAMissionTheUserAlreadyReceivedEvenAfterItIsTurnedOff() {
        when(tapTrigger.issuingEnabled()).thenReturn(false);
        List<PromotionGrant> grants = List.of(grantWith("TAP_THOUSAND_COMPLETE", PromotionGrant.Status.SUCCEEDED));
        when(promotionGrantRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(grants);

        // 받은 뒤 미션이 내려갔다고 목록에서 사라지면 받은 적이 있는지 확인할 수 없다.
        assertThat(service.missionsOf(userId).missions())
                .extracting(MissionListResponse.Mission::code)
                .contains("TAP_THOUSAND_COMPLETE");
    }

    @Test
    void readsGrantsOnceRegardlessOfMissionCount() {
        service.missionsOf(userId);

        org.mockito.Mockito.verify(promotionGrantRepository, org.mockito.Mockito.times(1))
                .findByUserIdOrderByCreatedAtDesc(userId);
    }
}
