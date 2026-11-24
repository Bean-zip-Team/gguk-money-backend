package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.keycap.entity.ShardLedger;
import com.ggukmoney.beanzip.domain.keycap.repository.ShardLedgerRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxAccountServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

    private final KeycapBoxAccountRepository keycapBoxAccountRepository = mock(KeycapBoxAccountRepository.class);
    private final com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository userKeycaps = mock(com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository.class);
    private final com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig economy = mock(com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig.class);
    private final ShardLedgerRepository shardLedgers = mock(ShardLedgerRepository.class);
    private final KeycapBoxAccountService keycapBoxAccountService = new KeycapBoxAccountService(keycapBoxAccountRepository, clock, userKeycaps, economy, shardLedgers);

    @Test
    void createsEmptyWalletForNewUser() {
        AppUser user = AppUser.createActive("Bean", null);
        when(keycapBoxAccountRepository.save(any(KeycapBoxAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KeycapBoxAccount result = keycapBoxAccountService.createFor(user);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getShardBalance()).isZero();
        assertThat(result.getOpenCycleStartedAt()).isEqualTo(clock.instant());
        verify(keycapBoxAccountRepository).save(result);
    }

    @Test
    void locksWalletRowForUpdate() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        assertThat(keycapBoxAccountService.getForUserForUpdate(userId)).isSameAs(wallet);
        verify(keycapBoxAccountRepository).findByUserIdForUpdate(userId);
    }

    @Test
    void missingWalletIsNotFound() {
        UUID userId = UUID.randomUUID();
        when(keycapBoxAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keycapBoxAccountService.getForUser(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_BOX_ACCOUNT_NOT_FOUND");
        assertThatThrownBy(() -> keycapBoxAccountService.getForUserForUpdate(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_BOX_ACCOUNT_NOT_FOUND");
    }

    @Test
    void legacyTapDoesNotConvertOrDeletePerKeycapProgress() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
        wallet.addBoxes(7);
        var collecting = com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap.createInProgress(null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(collecting, "shardCount", 3);
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        org.mockito.Mockito.lenient().when(userKeycaps.findLegacyInProgressForUpdate(userId))
                .thenReturn(java.util.List.of(collecting));

        var result = keycapBoxAccountService.getForUserForUpdate(userId);

        assertThat(result.getBoxBalance()).isEqualTo(7);
        assertThat(collecting.getShardCount()).isEqualTo(3);
        verify(userKeycaps, org.mockito.Mockito.never()).deleteAll(any());
    }
    @Test void rawWalletLockDoesNotReadEconomicPolicyOrLegacyProgress() {
        UUID userId=UUID.randomUUID();
        var policy=mock(com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig.class);
        var service=new KeycapBoxAccountService(keycapBoxAccountRepository,clock,userKeycaps,policy,shardLedgers);
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(KeycapBoxAccount.createFor(null, clock.instant())));
        service.getForUserForUpdate(userId);
        org.mockito.Mockito.verifyNoInteractions(policy,userKeycaps);
    }

    @Test
    void recordsShardChangeWithBalanceAfterInShardEconomy() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
        wallet.addShards(7);
        when(economy.shardEconomyAtLastRefresh()).thenReturn(true);

        keycapBoxAccountService.recordShardChange(userId, wallet, 7, ShardLedger.Reason.TAP_DROP, "batch-1");

        ArgumentCaptor<ShardLedger> saved = ArgumentCaptor.forClass(ShardLedger.class);
        verify(shardLedgers).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getDelta()).isEqualTo(7);
        assertThat(saved.getValue().getReason()).isEqualTo(ShardLedger.Reason.TAP_DROP);
        assertThat(saved.getValue().getRefId()).isEqualTo("batch-1");
        assertThat(saved.getValue().getBalanceAfter()).isEqualTo(7);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void confirmsAgainstTheConfigBeforeTrustingACacheThatSaysTheEconomyIsOff() {
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
        wallet.addShards(3);
        // Right after boot, a failed first read leaves the cache at its default for up to a minute.
        when(economy.shardEconomyAtLastRefresh()).thenReturn(false);
        when(economy.isShardEconomyEnabled()).thenReturn(true);

        keycapBoxAccountService.recordShardChange(UUID.randomUUID(), wallet, 3, ShardLedger.Reason.TAP_DROP, "batch-1");

        // A hole here would be a hole in the shard-issuance baseline.
        verify(shardLedgers).save(org.mockito.ArgumentMatchers.any(ShardLedger.class));
    }

    @Test
    void doesNotReadTheConfigAgainOnceTheCacheSaysTheEconomyIsOn() {
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
        wallet.addShards(3);
        when(economy.shardEconomyAtLastRefresh()).thenReturn(true);

        keycapBoxAccountService.recordShardChange(UUID.randomUUID(), wallet, 3, ShardLedger.Reason.TAP_DROP, "batch-1");

        // This runs on every tap batch.
        verify(economy, org.mockito.Mockito.never()).isShardEconomyEnabled();
    }

    @Test
    void theLedgerReferenceColumnHoldsTheLongestDrawIdempotencyKey() throws NoSuchFieldException {
        int columnLength = ShardLedger.class.getDeclaredField("refId")
                .getAnnotation(jakarta.persistence.Column.class).length();

        // A draw stores its Idempotency-Key as the reference. A shorter column turns a valid draw into a 500.
        assertThat(columnLength).isGreaterThanOrEqualTo(KeycapDrawService.MAX_IDEMPOTENCY_KEY_LENGTH);
    }

    @Test
    void doesNotRecordBoxesBeforeTheShardEconomy() {
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
        wallet.addShards(1);
        when(economy.shardEconomyAtLastRefresh()).thenReturn(false);

        keycapBoxAccountService.recordShardChange(UUID.randomUUID(), wallet, 1, ShardLedger.Reason.TAP_DROP, "batch-1");

        org.mockito.Mockito.verifyNoInteractions(shardLedgers);
    }

    @Test
    void zeroDeltaLeavesNoLedgerRow() {
        when(economy.shardEconomyAtLastRefresh()).thenReturn(true);

        keycapBoxAccountService.recordShardChange(UUID.randomUUID(), KeycapBoxAccount.createFor(null, clock.instant()), 0,
                ShardLedger.Reason.DRAW_SPEND, null);

        org.mockito.Mockito.verifyNoInteractions(shardLedgers);
    }

    @Test
    void grantShardsAddsToTheLockedWalletAndRecordsTheReason() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
        wallet.addShards(5);
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(economy.shardEconomyAtLastRefresh()).thenReturn(true);

        KeycapBoxAccount result = keycapBoxAccountService.grantShards(userId, 10, ShardLedger.Reason.INVITE_SUCCESS_INVITEE, "invite-1");

        assertThat(result.getShardBalance()).isEqualTo(15);
        verify(keycapBoxAccountRepository).save(wallet);
        ArgumentCaptor<ShardLedger> saved = ArgumentCaptor.forClass(ShardLedger.class);
        verify(shardLedgers).save(saved.capture());
        assertThat(saved.getValue().getDelta()).isEqualTo(10);
        assertThat(saved.getValue().getReason()).isEqualTo(ShardLedger.Reason.INVITE_SUCCESS_INVITEE);
        assertThat(saved.getValue().getBalanceAfter()).isEqualTo(15);
    }
}
