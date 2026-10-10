package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapDrawResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapDraw;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapDrawRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.promotion.service.KeycapFiveCompletionTrigger;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionGrantIssuer;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionTriggerContext;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapDrawServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T01:00:00Z");

    private final KeycapBoxAccountService keycapBoxAccountService = mock(KeycapBoxAccountService.class);
    private final KeycapDrawRepository keycapDrawRepository = mock(KeycapDrawRepository.class);
    private final KeycapRepository keycapRepository = mock(KeycapRepository.class);
    private final UserKeycapRepository userKeycapRepository = mock(UserKeycapRepository.class);
    private final UserService userService = mock(UserService.class);
    private final KeycapRewardSelector keycapRewardSelector = mock(KeycapRewardSelector.class);
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig = mock(KeycapBoxPolicyConfig.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final PromotionGrantIssuer promotionGrantIssuer = mock(PromotionGrantIssuer.class);
    private final KeycapFiveCompletionTrigger keycapFiveCompletionTrigger = mock(KeycapFiveCompletionTrigger.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final com.ggukmoney.beanzip.domain.user.service.UserRewardLock rewardLock = mock(com.ggukmoney.beanzip.domain.user.service.UserRewardLock.class);
    private final KeycapPassiveService passiveService = mock(KeycapPassiveService.class);
    private final KeycapDrawService service = new KeycapDrawService(
            keycapBoxAccountService,
            keycapDrawRepository,
            keycapRepository,
            userKeycapRepository,
            userService,
            keycapRewardSelector,
            keycapBoxPolicyConfig,
            pointAccountService,
            pointLedgerService,
            promotionGrantIssuer,
            keycapFiveCompletionTrigger,
            transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC),rewardLock,passiveService
    );

    private final UUID userId = UUID.randomUUID();
    private final AppUser user = user(userId);
    private final Keycap keycap = keycap(1L, "BASIC_001");
    private final KeycapBoxAccount wallet = KeycapBoxAccount.createFor(user);

    @BeforeEach
    void stubHappyPath() {
        when(rewardLock.acquire(any())).thenReturn(user);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(keycapBoxPolicyConfig.drawPrice()).thenReturn(5);
        when(keycapBoxAccountService.getForUserForUpdate(userId)).thenReturn(wallet);
        when(keycapBoxAccountService.getForUser(userId)).thenReturn(wallet);
        when(keycapRepository.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX))
                .thenReturn(List.of(keycap));
        when(keycapRewardSelector.select(List.of(keycap))).thenReturn(keycap);
        when(userService.getById(userId)).thenReturn(user);
        when(userKeycapRepository.save(any(UserKeycap.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(keycapDrawRepository.save(any(KeycapDraw.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(keycapDrawRepository.findByUserIdAndIdempotencyKeyWithKeycap(eq(userId), anyString())).thenReturn(Optional.empty());
        // 도감이 10종이라 1종을 얻어도 전체 완성 보너스는 나가지 않는다.
        when(keycapRepository.countByAcquisitionTypeAndActiveTrue(Keycap.AcquisitionType.BOX)).thenReturn(10L);
    }

    @Test
    void drawsNewKeycapAtLevelOneAndSpendsPrice() {
        wallet.addShards(7);
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, 1L)).thenReturn(Optional.empty());
        when(userKeycapRepository.countByUserIdAndStatus(userId, UserKeycap.Status.COMPLETED)).thenReturn(3L);

        KeycapDrawResponse response = service.draw(userId, "draw-1");

        assertThat(response.keycapId()).isEqualTo(keycap.getPublicId());
        assertThat(response.code()).isEqualTo("BASIC_001");
        assertThat(response.grade()).isEqualTo("COMMON");
        assertThat(response.newlyAcquired()).isTrue();
        assertThat(response.level()).isEqualTo(1);
        assertThat(response.shardsSpent()).isEqualTo(5);
        assertThat(response.shardBalance()).isEqualTo(2);
        assertThat(response.drawnAt()).isEqualTo(NOW);
        assertThat(wallet.getShardBalance()).isEqualTo(2);

        ArgumentCaptor<UserKeycap> saved = ArgumentCaptor.forClass(UserKeycap.class);
        verify(userKeycapRepository).save(saved.capture());
        assertThat(saved.getValue().getLevel()).isEqualTo(1);
        assertThat(saved.getValue().getCompletedAt()).isEqualTo(NOW);
        verify(promotionGrantIssuer).issueIfEligible(eq(keycapFiveCompletionTrigger), any(PromotionTriggerContext.class));
        verify(keycapDrawRepository).save(any(KeycapDraw.class));
        verify(transactionManager).commit(any());
    }

    @Test
    void levelsUpOwnedKeycapWithoutTouchingCompletedAtOrPromotion() {
        wallet.addShards(5);
        Instant firstAcquiredAt = NOW.minusSeconds(86_400);
        UserKeycap owned = UserKeycap.createOwned(user, keycap, firstAcquiredAt);
        owned.levelUp();
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, 1L)).thenReturn(Optional.of(owned));

        KeycapDrawResponse response = service.draw(userId, "draw-2");

        assertThat(response.newlyAcquired()).isFalse();
        assertThat(response.level()).isEqualTo(3);
        assertThat(response.shardBalance()).isZero();
        assertThat(owned.getLevel()).isEqualTo(3);
        // 중복 획득은 미션 집계 기준(completed_at)을 건드리지 않는다.
        assertThat(owned.getCompletedAt()).isEqualTo(firstAcquiredAt);
        verify(userKeycapRepository, never()).save(any());
        verify(promotionGrantIssuer, never()).issueIfEligible(any(), any());
        verify(pointAccountService, never()).credit(any(), anyLong());
    }

    @Test
    void rejectsDrawWhenShardBalanceIsBelowPrice() {
        wallet.addShards(4);

        assertThatThrownBy(() -> service.draw(userId, "draw-3"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_SHARD_INSUFFICIENT");

        assertThat(wallet.getShardBalance()).isEqualTo(4);
        verify(keycapRewardSelector, never()).select(any());
        verify(keycapDrawRepository, never()).save(any());
    }

    @Test
    void rejectsDrawBeforeSpendingWhenNoBoxKeycapIsActive() {
        wallet.addShards(5);
        when(keycapRepository.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.draw(userId, "draw-4"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_REWARD_NOT_AVAILABLE");

        assertThat(wallet.getShardBalance()).isEqualTo(5);
        verify(keycapDrawRepository, never()).save(any());
    }

    @Test
    void replaysExistingDrawWithoutSpendingAgain() {
        wallet.addShards(9);
        KeycapDraw existing = KeycapDraw.createFor(user, keycap, 5, 4, false, "draw-5", NOW.minusSeconds(60));
        when(keycapDrawRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, "draw-5")).thenReturn(Optional.of(existing));

        KeycapDrawResponse response = service.draw(userId, "draw-5");

        assertThat(response.keycapId()).isEqualTo(keycap.getPublicId());
        assertThat(response.newlyAcquired()).isFalse();
        assertThat(response.level()).isEqualTo(4);
        assertThat(response.shardsSpent()).isEqualTo(5);
        assertThat(response.shardBalance()).isEqualTo(9);
        assertThat(response.drawnAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(wallet.getShardBalance()).isEqualTo(9);
        verify(keycapBoxAccountService, never()).getForUserForUpdate(any());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void replaysWinnerWhenConcurrentDrawCollidesOnIdempotencyKey() {
        wallet.addShards(5);
        KeycapDraw winner = KeycapDraw.createFor(user, keycap, 5, 2, false, "draw-6", NOW);
        when(keycapDrawRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, "draw-6"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(winner));
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, 1L)).thenReturn(Optional.empty());
        when(keycapDrawRepository.save(any(KeycapDraw.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        KeycapDrawResponse response = service.draw(userId, "draw-6");

        assertThat(response.level()).isEqualTo(2);
        assertThat(response.newlyAcquired()).isFalse();
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void requiresIdempotencyKeyOfAtMostOneHundredCharacters() {
        for (String key : new String[]{null, "", "   ", "x".repeat(101)}) {
            assertThatThrownBy(() -> service.draw(userId, key))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(exception -> ((ResponseStatusException) exception).getReason())
                    .isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        }
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void awardsAllCompleteBonusOnceWhenEveryBoxKeycapIsOwned() {
        wallet.addShards(5);
        PointAccount pointAccount = mock(PointAccount.class);
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, 1L)).thenReturn(Optional.empty());
        when(keycapRepository.countByAcquisitionTypeAndActiveTrue(Keycap.AcquisitionType.BOX)).thenReturn(1L);
        when(userKeycapRepository.countByUserIdAndStatusAndKeycapAcquisitionType(
                userId, UserKeycap.Status.COMPLETED, Keycap.AcquisitionType.BOX)).thenReturn(1L);
        when(pointLedgerService.isAlreadyRecorded(eq(userId), any(UUID.class))).thenReturn(false);
        when(pointAccountService.credit(userId, 1L)).thenReturn(pointAccount);

        service.draw(userId, "draw-7");

        verify(pointAccountService).credit(userId, 1L);
        verify(pointLedgerService).recordCredit(eq(pointAccount), eq(user), eq(1L), eq("KEYCAP_ALL_COMPLETE_BONUS"), any(UUID.class));
    }

    private static AppUser user(UUID userId) {
        AppUser user = AppUser.createActive("Bean", null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private static Keycap keycap(Long id, String code) {
        Keycap keycap = Keycap.createFor(code, code, Keycap.Grade.COMMON, 10, 1, "https://example.com/" + code + ".png", null, 1);
        ReflectionTestUtils.setField(keycap, "id", id);
        ReflectionTestUtils.setField(keycap, "publicId", UUID.randomUUID());
        return keycap;
    }
}
