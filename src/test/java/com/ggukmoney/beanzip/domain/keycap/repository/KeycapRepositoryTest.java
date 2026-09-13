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
import java.util.Optional;

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

    @Test
    void findsCurrentUsersKeycapByKeycapPublicIdWithJoinedKeycap() {
        AppUser currentUser = appUserRepository.save(AppUser.createActive("current-user", null));
        AppUser otherUser = appUserRepository.save(AppUser.createActive("other-user", null));
        Keycap keycap = keycapRepository.save(keycap("BASIC_001", "First", true, 1));
        keycapRepository.flush();

        userKeycapRepository.save(userKeycap(currentUser, keycap, 10, UserKeycap.Status.COMPLETED, false));
        userKeycapRepository.save(userKeycap(otherUser, keycap, 10, UserKeycap.Status.COMPLETED, true));

        Optional<UserKeycap> result = userKeycapRepository.findByUserIdAndKeycapPublicIdWithKeycap(
                currentUser.getId(),
                keycap.getPublicId()
        );

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getId()).isEqualTo(currentUser.getId());
        assertThat(result.get().getKeycap().getPublicId()).isEqualTo(keycap.getPublicId());
    }

    @Test
    void doesNotFindOtherUsersKeycapByKeycapPublicId() {
        AppUser currentUser = appUserRepository.save(AppUser.createActive("current-user", null));
        AppUser otherUser = appUserRepository.save(AppUser.createActive("other-user", null));
        Keycap keycap = keycapRepository.save(keycap("BASIC_001", "First", true, 1));
        keycapRepository.flush();
        userKeycapRepository.save(userKeycap(otherUser, keycap, 10, UserKeycap.Status.COMPLETED, true));

        Optional<UserKeycap> result = userKeycapRepository.findByUserIdAndKeycapPublicIdWithKeycap(
                currentUser.getId(),
                keycap.getPublicId()
        );

        assertThat(result).isEmpty();
    }

    @Test
    void findsActiveIncompleteRewardCandidatesForCurrentUserOnly() {
        AppUser currentUser = appUserRepository.save(AppUser.createActive("current-user", null));
        AppUser otherUser = appUserRepository.save(AppUser.createActive("other-user", null));
        Keycap notOwned = keycapRepository.save(keycap("NOT_OWNED", "Not Owned", true, 1));
        Keycap inProgress = keycapRepository.save(keycap("IN_PROGRESS", "In Progress", true, 2));
        Keycap completed = keycapRepository.save(keycap("COMPLETED", "Completed", true, 3));
        Keycap inactive = keycapRepository.save(keycap("INACTIVE", "Inactive", false, 4));
        Keycap completedByOtherUser = keycapRepository.save(keycap("OTHER_COMPLETED", "Other Completed", true, 5));
        keycapRepository.flush();

        userKeycapRepository.save(userKeycap(currentUser, inProgress, 3, UserKeycap.Status.IN_PROGRESS, false));
        userKeycapRepository.save(userKeycap(currentUser, completed, 10, UserKeycap.Status.COMPLETED, false));
        userKeycapRepository.save(userKeycap(currentUser, inactive, 3, UserKeycap.Status.IN_PROGRESS, false));
        userKeycapRepository.save(userKeycap(otherUser, completedByOtherUser, 10, UserKeycap.Status.COMPLETED, false));

        List<Keycap> result = keycapRepository.findIncompleteActiveRewardCandidates(currentUser.getId());

        assertThat(result).extracting(Keycap::getCode)
                .containsExactly("NOT_OWNED", "IN_PROGRESS", "OTHER_COMPLETED");
    }

    /**
     * BEA-285 완료 조건. 추첨기는 후보 목록만 보고 확률을 정하므로, 이벤트 키캡을 추가해도 후보 목록이
     * 그대로면 상시 키캡의 확률도 그대로다. 이벤트 키캡은 같은 등급이면서 두 상시 키캡 사이 정렬 순서에
     * 넣어, 필터가 빠지면 곧바로 결과에 끼어들게 했다.
     */
    @Test
    void rewardCandidatesStayTheSameWhenEventKeycapIsAdded() {
        AppUser user = appUserRepository.save(AppUser.createActive("event-user", null));
        keycapRepository.save(keycap("BOX_A", "Box A", true, 1));
        keycapRepository.save(keycap("BOX_B", "Box B", true, 3));
        keycapRepository.flush();
        List<String> before = codesOf(keycapRepository.findIncompleteActiveRewardCandidates(user.getId()));

        keycapRepository.saveAndFlush(keycap("SONGPYEON", "Songpyeon", true, 2, Keycap.AcquisitionType.EVENT));
        List<String> after = codesOf(keycapRepository.findIncompleteActiveRewardCandidates(user.getId()));

        assertThat(before).containsExactly("BOX_A", "BOX_B");
        assertThat(after).isEqualTo(before);
    }

    @Test
    void excludesEventKeycapsFromOnboardingBonusCandidates() {
        keycapRepository.save(keycap("BOX_COMMON", "Box Common", true, 1));
        keycapRepository.save(keycap("EVENT_COMMON", "Event Common", true, 2, Keycap.AcquisitionType.EVENT));
        keycapRepository.flush();

        List<Keycap> result = keycapRepository.findByGradeAndAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(
                Keycap.Grade.COMMON, Keycap.AcquisitionType.BOX);

        assertThat(result).extracting(Keycap::getCode).containsExactly("BOX_COMMON");
    }

    @Test
    void countsOnlyActiveBoxKeycapsAsAllCompleteCatalog() {
        keycapRepository.save(keycap("BOX_ACTIVE_1", "Box Active 1", true, 1));
        keycapRepository.save(keycap("BOX_ACTIVE_2", "Box Active 2", true, 2));
        keycapRepository.save(keycap("BOX_INACTIVE", "Box Inactive", false, 3));
        keycapRepository.save(keycap("EVENT_ACTIVE", "Event Active", true, 4, Keycap.AcquisitionType.EVENT));
        keycapRepository.flush();

        assertThat(keycapRepository.countByAcquisitionTypeAndActiveTrue(Keycap.AcquisitionType.BOX)).isEqualTo(2);
    }

    @Test
    void countsCompletedKeycapsByAcquisitionType() {
        AppUser user = appUserRepository.save(AppUser.createActive("count-user", null));
        Keycap completedBox = keycapRepository.save(keycap("BOX_DONE", "Box Done", true, 1));
        Keycap inProgressBox = keycapRepository.save(keycap("BOX_WIP", "Box Wip", true, 2));
        Keycap completedEvent = keycapRepository.save(keycap("EVENT_DONE", "Event Done", true, 3, Keycap.AcquisitionType.EVENT));
        keycapRepository.flush();
        userKeycapRepository.save(userKeycap(user, completedBox, 10, UserKeycap.Status.COMPLETED, false));
        userKeycapRepository.save(userKeycap(user, inProgressBox, 3, UserKeycap.Status.IN_PROGRESS, false));
        userKeycapRepository.save(userKeycap(user, completedEvent, 10, UserKeycap.Status.COMPLETED, false));
        userKeycapRepository.flush();

        assertThat(userKeycapRepository.countByUserIdAndStatusAndKeycapAcquisitionType(
                user.getId(), UserKeycap.Status.COMPLETED, Keycap.AcquisitionType.BOX)).isEqualTo(1);
        assertThat(userKeycapRepository.countByUserIdAndStatus(user.getId(), UserKeycap.Status.COMPLETED)).isEqualTo(2);
    }

    private static List<String> codesOf(List<Keycap> keycaps) {
        return keycaps.stream().map(Keycap::getCode).toList();
    }

    private static Keycap keycap(String code, String name, boolean active, int sortOrder) {
        return keycap(code, name, active, sortOrder, Keycap.AcquisitionType.BOX);
    }

    private static Keycap keycap(
            String code,
            String name,
            boolean active,
            int sortOrder,
            Keycap.AcquisitionType acquisitionType
    ) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "acquisitionType", acquisitionType);
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
