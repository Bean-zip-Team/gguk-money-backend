package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxOpenServiceTest {

    private final KeycapBoxAccountRepository keycapBoxAccountRepository = mock(KeycapBoxAccountRepository.class);
    private final KeycapBoxOpenRepository keycapBoxOpenRepository = mock(KeycapBoxOpenRepository.class);
    private final KeycapRepository keycapRepository = mock(KeycapRepository.class);
    private final UserKeycapRepository userKeycapRepository = mock(UserKeycapRepository.class);
    private final UserService userService = mock(UserService.class);
    private final KeycapRewardSelector keycapRewardSelector = mock(KeycapRewardSelector.class);
    private final KeycapBoxOpenRequestHasher requestHasher = new KeycapBoxOpenRequestHasher();
    private final KeycapBoxMapper keycapBoxMapper = mock(KeycapBoxMapper.class);
    private final KeycapBoxOpenService service = new KeycapBoxOpenService(
            keycapBoxAccountRepository,
            keycapBoxOpenRepository,
            keycapRepository,
            userKeycapRepository,
            userService,
            keycapRewardSelector,
            requestHasher,
            keycapBoxMapper,
            new NoOpTransactionManager()
    );

    private final UUID userId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();

    @Test
    void opensFreeBoxAndCreatesUserKeycapWhenRewardWasNotOwned() {
        AppUser user = user(userId);
        KeycapBoxAccount account = account(user, 2, 1);
        Keycap keycap = keycap(10);
        KeycapBoxOpenRequest request = new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.FREE, null);
        KeycapBoxOpenResponse mapped = response(false);
        when(keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(keycapRepository.findIncompleteActiveRewardCandidates(userId)).thenReturn(List.of(keycap));
        when(keycapRewardSelector.select(List.of(keycap))).thenReturn(keycap);
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, keycap.getId()))
                .thenReturn(Optional.empty());
        when(userService.getById(userId)).thenReturn(user);
        when(keycapBoxOpenRepository.save(any(KeycapBoxOpen.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(keycapBoxMapper.mapToOpenResponse(any(KeycapBoxOpen.class))).thenReturn(mapped);

        KeycapBoxOpenResponse response = service.open(userId, idempotencyKey, request);

        assertThat(response).isEqualTo(mapped);
        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getFreeOpenTicketCount()).isZero();
        verify(userKeycapRepository).save(any(UserKeycap.class));
        verify(keycapBoxOpenRepository).save(any(KeycapBoxOpen.class));
    }

    @Test
    void completesExistingInProgressKeycapAndReportsCompletedNow() {
        AppUser user = user(userId);
        KeycapBoxAccount account = account(user, 1, 1);
        Keycap keycap = keycap(3);
        UserKeycap userKeycap = UserKeycap.createInProgress(user, keycap);
        ReflectionTestUtils.setField(userKeycap, "shardCount", 2);
        KeycapBoxOpenRequest request = new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.FREE, null);
        when(keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(keycapRepository.findIncompleteActiveRewardCandidates(userId)).thenReturn(List.of(keycap));
        when(keycapRewardSelector.select(List.of(keycap))).thenReturn(keycap);
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, keycap.getId()))
                .thenReturn(Optional.of(userKeycap));
        when(userService.getById(userId)).thenReturn(user);
        when(keycapBoxOpenRepository.save(any(KeycapBoxOpen.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(keycapBoxMapper.mapToOpenResponse(any(KeycapBoxOpen.class))).thenReturn(response(true));

        service.open(userId, idempotencyKey, request);

        assertThat(userKeycap.getShardCount()).isEqualTo(3);
        assertThat(userKeycap.getStatus()).isEqualTo(UserKeycap.Status.COMPLETED);
        assertThat(userKeycap.getCompletedAt()).isNotNull();
    }

    @Test
    void replaysExistingResultForSameIdempotencyKeyAndRequestHash() {
        KeycapBoxOpenRequest request = new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.FREE, null);
        KeycapBoxOpen existing = existingOpen(requestHasher.hash(request));
        KeycapBoxOpenResponse mapped = response(false);
        when(keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey))
                .thenReturn(Optional.of(existing));
        when(keycapBoxMapper.mapToOpenResponse(existing)).thenReturn(mapped);

        KeycapBoxOpenResponse response = service.open(userId, idempotencyKey, request);

        assertThat(response).isEqualTo(mapped);
        verify(keycapBoxAccountRepository, never()).findByUserIdForUpdate(userId);
        verify(keycapBoxOpenRepository, never()).save(any());
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentRequestHash() {
        KeycapBoxOpen existing = existingOpen("different-hash");
        when(keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.open(userId, idempotencyKey,
                new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.FREE, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void rejectsAdvertisementWithoutMutatingResources() {
        KeycapBoxOpenRequest request = new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.ADVERTISEMENT, "ad-1");

        assertThatThrownBy(() -> service.open(userId, idempotencyKey, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("ADVERTISEMENT_OPEN_NOT_SUPPORTED");

        verify(keycapBoxAccountRepository, never()).findByUserIdForUpdate(userId);
        verify(keycapBoxOpenRepository, never()).save(any());
    }

    @Test
    void rejectsMissingRewardCandidatesWithoutConsumingResources() {
        AppUser user = user(userId);
        KeycapBoxAccount account = account(user, 1, 1);
        when(keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(keycapRepository.findIncompleteActiveRewardCandidates(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.open(userId, idempotencyKey,
                new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.FREE, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_REWARD_NOT_AVAILABLE");

        assertThat(account.getBoxBalance()).isEqualTo(1);
        assertThat(account.getFreeOpenTicketCount()).isEqualTo(1);
        verify(userKeycapRepository, never()).save(any());
        verify(keycapBoxOpenRepository, never()).save(any());
    }

    @Test
    void rejectsMissingBoxBalanceBeforeRewardSelection() {
        AppUser user = user(userId);
        KeycapBoxAccount account = account(user, 0, 1);
        when(keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.open(userId, idempotencyKey,
                new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.FREE, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_BOX_NOT_AVAILABLE");

        verify(keycapRepository, never()).findIncompleteActiveRewardCandidates(userId);
    }

    @Test
    void rejectsMissingFreeTicketBeforeRewardSelection() {
        AppUser user = user(userId);
        KeycapBoxAccount account = account(user, 1, 0);
        when(keycapBoxOpenRepository.findByUserIdAndIdempotencyKeyWithKeycap(userId, idempotencyKey))
                .thenReturn(Optional.empty());
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.open(userId, idempotencyKey,
                new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.FREE, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("FREE_OPEN_TICKET_NOT_AVAILABLE");

        verify(keycapRepository, never()).findIncompleteActiveRewardCandidates(userId);
    }

    private static KeycapBoxOpenResponse response(boolean completed) {
        return new KeycapBoxOpenResponse(UUID.randomUUID(), UUID.randomUUID(), 1, completed, Instant.now());
    }

    private static KeycapBoxOpen existingOpen(String requestHash) {
        KeycapBoxOpen open = newInstance(KeycapBoxOpen.class);
        ReflectionTestUtils.setField(open, "requestHash", requestHash);
        return open;
    }

    private static KeycapBoxAccount account(AppUser user, int boxBalance, int freeOpenTicketCount) {
        KeycapBoxAccount account = newInstance(KeycapBoxAccount.class);
        ReflectionTestUtils.setField(account, "user", user);
        ReflectionTestUtils.setField(account, "boxBalance", boxBalance);
        ReflectionTestUtils.setField(account, "freeOpenTicketCount", freeOpenTicketCount);
        return account;
    }

    private static AppUser user(UUID userId) {
        AppUser user = AppUser.createActive("Bean", null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private static Keycap keycap(int requiredShardCount) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "id", 100L + requiredShardCount);
        ReflectionTestUtils.setField(keycap, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(keycap, "requiredShardCount", requiredShardCount);
        return keycap;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create test entity " + type.getSimpleName(), exception);
        }
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
