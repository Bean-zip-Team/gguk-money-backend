package com.ggukmoney.beanzip.domain.cashout.repository;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.hibernate.Hibernate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CashoutRequestRepositoryTest extends FullStackIntegrationTestSupport {

    @Autowired
    private CashoutRequestRepository cashoutRequestRepository;

    @Autowired
    private AuthIdentityRepository authIdentityRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void pagesProcessingRequestsByAscendingIdWithoutGapsOrDuplicates() {
        AppUser user = saveUser("cashout-poll-page");
        CashoutRequest first = saveProcessing(user, "promotion-1");
        cashoutRequestRepository.save(CashoutRequest.createFor(user, 20L, 14L, UUID.randomUUID()));
        CashoutRequest third = saveProcessing(user, "promotion-3");
        CashoutRequest fourth = saveProcessing(user, "promotion-4");
        CashoutRequest failed = saveProcessing(user, "promotion-failed");
        failed.markFailed();
        cashoutRequestRepository.saveAndFlush(failed);
        entityManager.flush();
        entityManager.clear();

        List<CashoutRequest> firstPage = cashoutRequestRepository.findProcessingAfterId(
                CashoutRequest.Status.PROCESSING, 0L, PageRequest.of(0, 2));
        assertThat(firstPage).allSatisfy(request -> assertThat(Hibernate.isInitialized(request.getUser())).isTrue());
        long nextCursor = firstPage.get(firstPage.size() - 1).getId();
        entityManager.clear();
        List<CashoutRequest> secondPage = cashoutRequestRepository.findProcessingAfterId(
                CashoutRequest.Status.PROCESSING,
                nextCursor,
                PageRequest.of(0, 2)
        );

        assertThat(firstPage).extracting(CashoutRequest::getId)
                .containsExactly(first.getId(), third.getId());
        assertThat(secondPage).extracting(CashoutRequest::getId)
                .containsExactly(fourth.getId());
        assertThat(secondPage).allSatisfy(request -> assertThat(Hibernate.isInitialized(request.getUser())).isTrue());
    }

    @Test
    void loadsTossIdentityProjectionForRequestedUsersInOneBatch() {
        AppUser first = saveUser("cashout-identity-1");
        AppUser second = saveUser("cashout-identity-2");
        AppUser excluded = saveUser("cashout-identity-excluded");
        authIdentityRepository.save(AuthIdentity.toss(first, "provider-1"));
        authIdentityRepository.save(AuthIdentity.toss(second, "provider-2"));
        authIdentityRepository.saveAndFlush(AuthIdentity.toss(excluded, "provider-excluded"));

        List<AuthIdentityRepository.UserProviderIdentity> rows =
                authIdentityRepository.findProviderIdentitiesByUserIds(
                        Set.of(first.getId(), second.getId()), AuthIdentity.Provider.TOSS);
        Map<UUID, String> identities = rows.stream().collect(Collectors.toMap(
                AuthIdentityRepository.UserProviderIdentity::getUserId,
                AuthIdentityRepository.UserProviderIdentity::getProviderUserId,
                (left, right) -> left
        ));

        assertThat(identities).containsExactlyInAnyOrderEntriesOf(Map.of(
                first.getId(), "provider-1",
                second.getId(), "provider-2"
        ));
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.save(AppUser.createActive(prefix + "-" + UUID.randomUUID(), null));
    }

    private CashoutRequest saveProcessing(AppUser user, String promotionKey) {
        CashoutRequest request = CashoutRequest.createFor(user, 10L, 7L, UUID.randomUUID());
        request.markProcessing(promotionKey);
        return cashoutRequestRepository.saveAndFlush(request);
    }
}
