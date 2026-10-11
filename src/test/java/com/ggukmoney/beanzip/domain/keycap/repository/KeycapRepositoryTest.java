package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
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

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsEachRequestedUsersEquippedKeycapWithTheKeycapAlreadyLoaded() {
        AppUser switched = appUserRepository.save(AppUser.createActive("switched", null));
        AppUser single = appUserRepository.save(AppUser.createActive("single", null));
        AppUser neverClaimed = appUserRepository.save(AppUser.createActive("never-claimed", null));
        AppUser notListed = appUserRepository.save(AppUser.createActive("not-listed", null));
        Keycap first = keycapRepository.save(keycap("BASIC_001", "First", true, 1));
        Keycap second = keycapRepository.save(keycap("BASIC_002", "Second", true, 2));
        userKeycapRepository.save(userKeycap(switched, first, 10, UserKeycap.Status.COMPLETED, false));
        userKeycapRepository.save(userKeycap(switched, second, 10, UserKeycap.Status.COMPLETED, true));
        userKeycapRepository.save(userKeycap(single, first, 10, UserKeycap.Status.COMPLETED, true));
        userKeycapRepository.save(userKeycap(notListed, first, 10, UserKeycap.Status.COMPLETED, true));
        entityManager.flush();
        entityManager.clear();

        List<UserKeycap> result = userKeycapRepository.findEquippedWithKeycapByUserIds(
                List.of(switched.getId(), single.getId(), neverClaimed.getId()));

        assertThat(result).extracting(userKeycap -> userKeycap.getUser().getId(), userKeycap -> userKeycap.getKeycap().getCode())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(switched.getId(), "BASIC_002"),
                        org.assertj.core.groups.Tuple.tuple(single.getId(), "BASIC_001"));
        // 랭킹 목록 수십 명의 이미지를 채우는 동안 키캡을 한 명씩 다시 읽지 않아야 한다.
        assertThat(result).allSatisfy(userKeycap -> assertThat(Hibernate.isInitialized(userKeycap.getKeycap())).isTrue());
    }

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

        Keycap legacy = keycapRepository.save(keycap("BASIC_000", "Legacy", true, 0));
        userKeycapRepository.save(userKeycap(currentUser, second, 3, UserKeycap.Status.COMPLETED, false));
        userKeycapRepository.save(userKeycap(currentUser, first, 10, UserKeycap.Status.COMPLETED, true));
        // 구 코드가 남긴 진행 중 행은 미보유라 목록에 나오지 않는다 (무중단 배포 구간).
        userKeycapRepository.save(userKeycap(currentUser, legacy, 1, UserKeycap.Status.IN_PROGRESS, false));
        userKeycapRepository.save(userKeycap(otherUser, first, 7, UserKeycap.Status.COMPLETED, false));

        List<UserKeycap> result = userKeycapRepository.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(currentUser.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(userKeycap -> userKeycap.getKeycap().getCode())
                .containsExactly("BASIC_001", "BASIC_002");
        assertThat(result).extracting(UserKeycap::getLevel)
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

    /**
     * BEA-329 뽑기 후보이자 공개 도감 목록. 이미 보유한 키캡도 남고(중복은 레벨로 쌓인다), 시즌 키캡과
     * 비활성 키캡은 빠진다. 시즌 키캡은 같은 등급으로 두 상시 키캡 사이에 넣어 필터가 빠지면 바로 드러나게 했다.
     */
    @Test
    void listsEveryActiveBoxKeycapIncludingOwnedOnesAsDrawCandidates() {
        AppUser user = appUserRepository.save(AppUser.createActive("draw-user", null));
        Keycap owned = keycapRepository.save(keycap("BOX_A", "Box A", true, 1));
        keycapRepository.save(keycap("SONGPYEON", "Songpyeon", true, 2, Keycap.AcquisitionType.EVENT));
        keycapRepository.save(keycap("BOX_B", "Box B", true, 3));
        keycapRepository.save(keycap("BOX_INACTIVE", "Box Inactive", false, 4));
        keycapRepository.flush();
        userKeycapRepository.save(userKeycap(user, owned, 7, UserKeycap.Status.COMPLETED, false));

        List<Keycap> result = keycapRepository.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX);

        assertThat(codesOf(result)).containsExactly("BOX_A", "BOX_B");
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
        keycapRepository.save(keycap("BOX_NOT_OWNED", "Box Not Owned", true, 2));
        Keycap completedEvent = keycapRepository.save(keycap("EVENT_DONE", "Event Done", true, 3, Keycap.AcquisitionType.EVENT));
        keycapRepository.flush();
        userKeycapRepository.save(userKeycap(user, completedBox, 10, UserKeycap.Status.COMPLETED, false));
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
            int level,
            UserKeycap.Status status,
            boolean equipped
    ) {
        UserKeycap userKeycap = newInstance(UserKeycap.class);
        ReflectionTestUtils.setField(userKeycap, "user", user);
        ReflectionTestUtils.setField(userKeycap, "keycap", keycap);
        ReflectionTestUtils.setField(userKeycap, "level", level);
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
