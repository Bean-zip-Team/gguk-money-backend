package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingBackfillService {

    private final UserTapProgressRepository userTapProgressRepository;
    private final RankingProjectionService rankingProjectionService;
    private final RankingProperties properties;

    @Transactional
    public long backfillActiveAllTimeFromTapProgress() {
        long processed = 0;
        int page = 0;
        while (true) {
            List<UserTapProgress> progressList = userTapProgressRepository.findActivePositiveProgress(
                    PageRequest.of(page, properties.pageSize())
            );
            if (progressList.isEmpty()) {
                return processed;
            }
            for (UserTapProgress progress : progressList) {
                rankingProjectionService.syncAllTimeScore(
                        progress.getUser().getId(),
                        progress.getCumulativeValidTapCount()
                );
                processed++;
            }
            if (progressList.size() < properties.pageSize()) {
                return processed;
            }
            page++;
        }
    }
}
