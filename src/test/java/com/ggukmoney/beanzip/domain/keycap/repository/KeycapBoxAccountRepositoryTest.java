package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KeycapBoxAccountRepositoryTest {

    private static final Instant CUTOFF = Instant.parse("2026-08-03T00:00:00Z");

    @Autowired
    private KeycapBoxAccountRepository accountRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void findsCandidatesByAccountIdKeysetInAscendingOrder() {
        KeycapBoxAccount first = saveAccount("first", 1, 2, 2, CUTOFF.minusSeconds(60), true);
        KeycapBoxAccount second = saveAccount("second", 1, 2, 2, CUTOFF.minusSeconds(30), true);

        List<KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate> firstBatch = findCandidates(0L, 1);
        List<KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate> secondBatch =
                findCandidates(firstBatch.getFirst().getAccountId(), 1);

        assertThat(firstBatch).extracting(KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate::getAccountId)
                .containsExactly(first.getId());
        assertThat(secondBatch).extracting(KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate::getAccountId)
                .containsExactly(second.getId());
        assertThat(firstBatch.getFirst().getUserId()).isEqualTo(first.getUser().getId());
    }

    @Test
    void excludesAccountsThatDoNotSatisfyEveryDeliveryCondition() {
        KeycapBoxAccount eligible = saveAccount("eligible", 1, 2, 2, CUTOFF, true);
        saveAccount("no-box", 0, 2, 2, CUTOFF, true);
        saveAccount("free-remains", 1, 1, 2, CUTOFF, true);
        saveAccount("ad-remains", 1, 2, 1, CUTOFF, true);
        saveAccount("not-expired", 1, 2, 2, CUTOFF.plusMillis(1), true);
        saveAccount("not-agreed", 1, 2, 2, CUTOFF, false);

        assertThat(findCandidates(0L, 20))
                .extracting(KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate::getAccountId)
                .containsExactly(eligible.getId());
    }

    private List<KeycapBoxAccountRepository.KeycapBoxOpenAvailableCandidate> findCandidates(long lastAccountId, int size) {
        return accountRepository.findKeycapBoxOpenAvailableCandidates(
                NotificationType.KEYCAP_BOX_OPEN_AVAILABLE,
                lastAccountId,
                2,
                2,
                CUTOFF,
                PageRequest.of(0, size)
        );
    }

    private KeycapBoxAccount saveAccount(
            String nickname,
            int boxBalance,
            int freeOpenUsedCount,
            int adOpenUsedCount,
            Instant openCycleStartedAt,
            boolean agreed
    ) {
        AppUser user = userRepository.save(AppUser.createActive(nickname, null));
        KeycapBoxAccount account = KeycapBoxAccount.createFor(user, openCycleStartedAt);
        ReflectionTestUtils.setField(account, "boxBalance", boxBalance);
        ReflectionTestUtils.setField(account, "freeOpenUsedCount", freeOpenUsedCount);
        ReflectionTestUtils.setField(account, "adOpenUsedCount", adOpenUsedCount);
        account = accountRepository.saveAndFlush(account);
        if (agreed) {
            NotificationPreference preference = NotificationPreference.defaultOf(
                    user.getId(),
                    NotificationType.KEYCAP_BOX_OPEN_AVAILABLE
            );
            preference.applyAgreement("newAgreement");
            preferenceRepository.saveAndFlush(preference);
        }
        return account;
    }
}
