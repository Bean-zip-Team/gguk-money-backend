package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.mission.repository.MissionDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionDefinitionCatalogTest {

    private final MissionDefinitionRepository repository = mock(MissionDefinitionRepository.class);
    private final MissionDefinitionCatalog catalog = new MissionDefinitionCatalog(repository);

    @Test
    void cachesActiveDefinitionsInDatabaseOrder() {
        when(repository.findByActiveTrueOrderBySortOrderAscCodeAsc()).thenReturn(List.of(
                definition("ATTENDANCE", MissionDefinition.MissionType.ATTENDANCE, 1, 100, 10),
                definition("TAP_100", MissionDefinition.MissionType.TAP_COUNT, 100, 10, 20)
        ));

        catalog.refresh();

        assertThat(catalog.activeDefinitions())
                .extracting(MissionDefinitionView::code)
                .containsExactly("ATTENDANCE", "TAP_100");
        assertThat(catalog.findByCode("TAP_100")).get()
                .extracting(MissionDefinitionView::targetValue, MissionDefinitionView::rewardPointAmount)
                .containsExactly(100L, 10L);
    }

    @Test
    void keepsTheLastKnownDefinitionsWhenRefreshFails() {
        when(repository.findByActiveTrueOrderBySortOrderAscCodeAsc()).thenReturn(List.of(
                definition("ATTENDANCE", MissionDefinition.MissionType.ATTENDANCE, 1, 100, 10)
        ));
        catalog.refresh();

        when(repository.findByActiveTrueOrderBySortOrderAscCodeAsc())
                .thenThrow(new IllegalStateException("database is down"));
        catalog.refresh();

        // DB 가 잠깐 흔들렸다고 미션 목록이 통째로 비면 유저 화면이 빈 채로 뜬다.
        assertThat(catalog.activeDefinitions()).extracting(MissionDefinitionView::code).containsExactly("ATTENDANCE");
    }

    private static MissionDefinition definition(
            String code,
            MissionDefinition.MissionType missionType,
            long targetValue,
            long rewardPointAmount,
            int sortOrder
    ) {
        MissionDefinition definition;
        try {
            Constructor<MissionDefinition> constructor = MissionDefinition.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            definition = constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create MissionDefinition", exception);
        }
        ReflectionTestUtils.setField(definition, "code", code);
        ReflectionTestUtils.setField(definition, "missionType", missionType);
        ReflectionTestUtils.setField(definition, "periodType", MissionDefinition.PeriodType.DAILY);
        ReflectionTestUtils.setField(definition, "name", code);
        ReflectionTestUtils.setField(definition, "targetValue", targetValue);
        ReflectionTestUtils.setField(definition, "rewardPointAmount", rewardPointAmount);
        ReflectionTestUtils.setField(definition, "sortOrder", sortOrder);
        ReflectionTestUtils.setField(definition, "active", true);
        return definition;
    }
}
