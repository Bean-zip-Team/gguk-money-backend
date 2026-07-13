package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KeycapRepositoryTest {

    @Autowired
    private KeycapRepository keycapRepository;

    @Autowired
    private UserKeycapRepository userKeycapRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void findsOnlyActiveKeycapsOrderedBySortOrderAndCode() {
        Keycap second = keycap("BASIC_002", "Second", true, 2);
        Keycap firstB = keycap("BASIC_001_B", "First B", true, 1);
        Keycap firstA = keycap("BASIC_001_A", "First A", true, 1);
        Keycap inactive = keycap("INACTIVE_001", "Inactive", false, 0);
        keycapRepository.saveAll(List.of(second, firstB, firstA, inactive));

        List<Keycap> result = keycapRepository.findByActiveTrueOrderBySortOrderAscCodeAsc();

        assertThat(result).extracting(Keycap::getCode)
                .containsExactly("BASIC_001_A", "BASIC_001_B", "BASIC_002");
    }

    @Test
    void findsOnlyCurrentUsersKeycapsWithKeycapOrdering() {
        AppUser currentUser = appUserRepository.save(AppUser.createActive("current-user", null));
        AppUser otherUser = appUserRepository.save(AppUser.createActive("other-user", null));
        Keycap second = keycapRepository.save(keycap("BASIC_002", "Second", true, 2));
        Keycap first = keycapRepository.save(keycap("BASIC_001", "First", true, 1));

        userKeycapRepository.save(userKeycap(currentUser, second, 3, UserKeycap.Status.IN_PROGRESS, false));
        userKeycapRepository.save(userKeycap(currentUser, first, 10, UserKeycap.Status.COMPLETED, true));
        userKeycapRepository.save(userKeycap(otherUser, first, 7, UserKeycap.Status.IN_PROGRESS, false));

        List<UserKeycap> result = userKeycapRepository.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(currentUser.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(userKeycap -> userKeycap.getKeycap().getCode())
                .containsExactly("BASIC_001", "BASIC_002");
        assertThat(result).extracting(UserKeycap::getShardCount)
                .containsExactly(10, 3);
    }

    private static Keycap keycap(String code, String name, boolean active, int sortOrder) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "code", code);
        ReflectionTestUtils.setField(keycap, "name", name);
        ReflectionTestUtils.setField(keycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(keycap, "requiredShardCount", 10);
        ReflectionTestUtils.setField(keycap, "season", 1);
        ReflectionTestUtils.setField(keycap, "active", active);
        ReflectionTestUtils.setField(keycap, "sortOrder", sortOrder);
        return keycap;
    }

    private static UserKeycap userKeycap(
            AppUser user,
            Keycap keycap,
            int shardCount,
            UserKeycap.Status status,
            boolean equipped
    ) {
        UserKeycap userKeycap = newInstance(UserKeycap.class);
        ReflectionTestUtils.setField(userKeycap, "user", user);
        ReflectionTestUtils.setField(userKeycap, "keycap", keycap);
        ReflectionTestUtils.setField(userKeycap, "shardCount", shardCount);
        ReflectionTestUtils.setField(userKeycap, "status", status);
        ReflectionTestUtils.setField(userKeycap, "equipped", equipped);
        return userKeycap;
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
}
