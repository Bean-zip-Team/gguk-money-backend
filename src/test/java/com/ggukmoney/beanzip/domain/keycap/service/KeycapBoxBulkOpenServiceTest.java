package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxBulkOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxBulkOpenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

    private final KeycapBoxOpenService keycapBoxOpenService = mock(KeycapBoxOpenService.class);
    private final KeycapBoxAccountService keycapBoxAccountService = mock(KeycapBoxAccountService.class);
    private final KeycapBoxOpenRepository keycapBoxOpenRepository = mock(KeycapBoxOpenRepository.class);
    private final NotificationPreferenceRepository notificationPreferenceRepository =
            mock(NotificationPreferenceRepository.class);
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig = mock(KeycapBoxPolicyConfig.class);
    private final UserService userService = mock(UserService.class);

    private final KeycapBoxBulkOpenService service = new KeycapBoxBulkOpenService(
            keycapBoxOpenService, keycapBoxAccountService, keycapBoxOpenRepository,
            notificationPreferenceRepository, keycapBoxPolicyConfig, userService,
            new NoOpTransactionManager(), Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID userId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();

    @BeforeEach
    void stubDefaults() {
        lenient().when(notificationPreferenceRepository
                .existsByUserIdAndAgreementStatus(userId, NotificationAgreementStatus.AGREED)).thenReturn(true);
        lenient().when(keycapBoxPolicyConfig.bulkOpenLimit()).thenReturn(30);
        lenient().when(keycapBoxOpenRepository
                .existsByUserIdAndOpenMethod(userId, KeycapBoxOpen.OpenMethod.BULK_REWARD)).thenReturn(false);

        AppUser user = mock(AppUser.class);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(userService.getById(userId)).thenReturn(user);
        lenient().when(keycapBoxOpenService.requireRewardCandidates(userId)).thenReturn(List.of());

        KeycapBoxOpen defaultOpened = openedBox(1, false);
        lenient().when(keycapBoxOpenService.drawAndRecord(any(), any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(defaultOpened);
    }

    private KeycapBoxAccount accountWith(int boxBalance) {
        KeycapBoxAccount account = KeycapBoxAccount.createFor(mock(AppUser.class), NOW);
        account.addBoxes(boxBalance);
        when(keycapBoxAccountService.getForUserForUpdate(userId)).thenReturn(account);
        lenient().when(keycapBoxAccountService.getForUser(userId)).thenReturn(account);
        return account;
    }

    private KeycapBoxOpen openedBox(int shardCount, boolean completed) {
        Keycap keycap = mock(Keycap.class);
        lenient().when(keycap.getPublicId()).thenReturn(UUID.randomUUID());
        lenient().when(keycap.getName()).thenReturn("응원");
        lenient().when(keycap.getImageUrl()).thenReturn("https://example.com/cheer.webp");

        KeycapBoxOpen boxOpen = mock(KeycapBoxOpen.class);
        lenient().when(boxOpen.getShardCount()).thenReturn(shardCount);
        lenient().when(boxOpen.isCompleted()).thenReturn(completed);
        lenient().when(boxOpen.getKeycap()).thenReturn(keycap);
        return boxOpen;
    }

    @Test
    void opensUpToTheConfiguredLimitAndLeavesTheRest() {
        KeycapBoxAccount account = accountWith(110);

        KeycapBoxBulkOpenResponse response = service.bulkOpen(userId, idempotencyKey);

        assertThat(response.openedCount()).isEqualTo(30);
        // 전량을 열면 재고가 0 이 되어 이후 광고 개봉 동기까지 사라진다. 80 개를 남긴다.
        assertThat(response.remainingBoxBalance()).isEqualTo(80);
        assertThat(account.getBoxBalance()).isEqualTo(80);
        verify(keycapBoxOpenService, times(30))
                .drawAndRecord(any(), eq(KeycapBoxOpen.OpenMethod.BULK_REWARD), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void opensEverythingWhenBalanceIsBelowTheLimit() {
        accountWith(5);

        KeycapBoxBulkOpenResponse response = service.bulkOpen(userId, idempotencyKey);

        assertThat(response.openedCount()).isEqualTo(5);
        assertThat(response.remainingBoxBalance()).isZero();
    }

    @Test
    void doesNotConsumeTheOneTimeRightWhenThereIsNothingToOpen() {
        accountWith(0);

        KeycapBoxBulkOpenResponse response = service.bulkOpen(userId, idempotencyKey);

        assertThat(response.openedCount()).isZero();
        assertThat(response.completedKeycaps()).isEmpty();
        // 준 게 없는데 수령 처리하면 나중에 상자가 쌓여도 영영 못 받는다.
        verify(keycapBoxOpenService, never()).drawAndRecord(any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void rejectsWhenNotificationConsentIsNotRecorded() {
        when(notificationPreferenceRepository
                .existsByUserIdAndAgreementStatus(userId, NotificationAgreementStatus.AGREED)).thenReturn(false);

        assertThatThrownBy(() -> service.bulkOpen(userId, idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NOTIFICATION_CONSENT_REQUIRED");
    }

    @Test
    void doesNotOpenAgainForAUserWhoAlreadyClaimed() {
        when(keycapBoxOpenRepository.existsByUserIdAndOpenMethod(userId, KeycapBoxOpen.OpenMethod.BULK_REWARD))
                .thenReturn(true);
        List<KeycapBoxOpen> alreadyOpened = List.of(openedBox(2, true), openedBox(1, false));
        when(keycapBoxOpenRepository.findAllByUserIdAndOpenMethodWithKeycap(userId, KeycapBoxOpen.OpenMethod.BULK_REWARD))
                .thenReturn(alreadyOpened);
        accountWith(80);

        KeycapBoxBulkOpenResponse response = service.bulkOpen(userId, idempotencyKey);

        // 1회성 판정과 멱등 재생이 같은 근거다. 두 번째 호출은 그때 연 결과를 그대로 돌려준다.
        assertThat(response.openedCount()).isEqualTo(2);
        assertThat(response.totalShardCount()).isEqualTo(3);
        assertThat(response.completedKeycaps()).hasSize(1);
        verify(keycapBoxOpenService, never()).drawAndRecord(any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void bypassesOpenCycleAndFreeAdLimits() {
        accountWith(10);

        service.bulkOpen(userId, idempotencyKey);

        // 주기 갱신을 거치지 않는다. 그 제한을 우회하는 것이 이 보상의 내용이다.
        verify(keycapBoxAccountService, never()).refreshOpenCycleForUpdate(any(), any(), any());
        verify(keycapBoxPolicyConfig, never()).freeOpenLimit();
        verify(keycapBoxPolicyConfig, never()).adOpenLimit();
    }

    @Test
    void summarizesShardsAndCompletedKeycaps() {
        accountWith(3);
        KeycapBoxOpen first = openedBox(2, false);
        KeycapBoxOpen second = openedBox(1, true);
        KeycapBoxOpen third = openedBox(2, false);
        when(keycapBoxOpenService.drawAndRecord(any(), any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(first, second, third);

        KeycapBoxBulkOpenResponse response = service.bulkOpen(userId, idempotencyKey);

        assertThat(response.totalShardCount()).isEqualTo(5);
        assertThat(response.completedKeycaps()).hasSize(1);
        assertThat(response.completedKeycaps().getFirst().name()).isEqualTo("응원");
    }

    @Test
    void requiresIdempotencyKey() {
        assertThatThrownBy(() -> service.bulkOpen(userId, "  "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void givesEachOpenItsOwnIdempotencyKey() {
        accountWith(3);

        service.bulkOpen(userId, idempotencyKey);

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(keycapBoxOpenService, times(3))
                .drawAndRecord(any(), any(), keys.capture(), anyString(), anyString(), any(), any());
        // uq_keycap_box_open_user_idempotency 가 (user_id, idempotency_key) 유니크다.
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void limitIsReadFromConfigurationNotHardcoded() {
        when(keycapBoxPolicyConfig.bulkOpenLimit()).thenReturn(10);
        accountWith(110);

        assertThat(service.bulkOpen(userId, idempotencyKey).openedCount()).isEqualTo(10);
        verify(keycapBoxPolicyConfig, times(1)).bulkOpenLimit();
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
