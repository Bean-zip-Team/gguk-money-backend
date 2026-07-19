package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingBackfillServiceTest {

    private final UserTapProgressRepository progressRepository = mock(UserTapProgressRepository.class);
    private final RankingProjectionService projectionService = mock(RankingProjectionService.class);
    private final RankingProperties properties = new RankingProperties();
    private final RankingBackfillService service = new RankingBackfillService(progressRepository, projectionService, properties);

    @Test
    void backfillsOnlyRowsProvidedByActivePositiveProgressQuery() {
        UUID userId = UUID.randomUUID();
        UserTapProgress progress = mock(UserTapProgress.class);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(progress.getUser()).thenReturn(user);
        when(progress.getCumulativeValidTapCount()).thenReturn(123L);
        when(progressRepository.findActivePositiveProgress(PageRequest.of(0, properties.pageSize())))
                .thenReturn(List.of(progress));

        service.backfillActiveAllTimeFromTapProgress();

        verify(projectionService).syncAllTimeScore(userId, 123L);
    }

    @Test
    void backfillCanRunAgainBecauseProjectionSetsCumulativeScore() {
        UUID userId = UUID.randomUUID();
        UserTapProgress progress = mock(UserTapProgress.class);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(progress.getUser()).thenReturn(user);
        when(progress.getCumulativeValidTapCount()).thenReturn(123L);
        when(progressRepository.findActivePositiveProgress(PageRequest.of(0, properties.pageSize())))
                .thenReturn(List.of(progress))
                .thenReturn(List.of(progress));

        service.backfillActiveAllTimeFromTapProgress();
        service.backfillActiveAllTimeFromTapProgress();

        verify(projectionService, org.mockito.Mockito.times(2)).syncAllTimeScore(userId, 123L);
    }
}
