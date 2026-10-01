package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("alpha")
@ConditionalOnProperty(name = "app.ranking.weekly-reward.alpha-tools-enabled", havingValue = "true")
@RequiredArgsConstructor
@RequestMapping("/api/internal/alpha/ranking-rewards")
public class WeeklyRankingRewardAlphaToolController {

    private final WeeklyRankingRewardAlphaToolService service;

    @PostMapping("/{seasonCode}/reset")
    public ApiResponse<Void> reset(@PathVariable String seasonCode) {
        service.reset(seasonCode);
        return ApiResponse.success(null);
    }

    @PostMapping("/{seasonCode}/snapshot")
    public ApiResponse<Integer> snapshot(@PathVariable String seasonCode) {
        return ApiResponse.success(service.snapshot(seasonCode));
    }
}
