package com.ggukmoney.beanzip.domain.ranking.reward;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyRankingRewardAlphaToolConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    WeeklyRankingRewardAlphaToolService.class,
                    WeeklyRankingRewardAlphaToolController.class
            );

    @Test
    void productionProfileDoesNotRegisterAlphaToolsEvenWhenPropertyIsTrue() {
        contextRunner
                .withPropertyValues("app.ranking.weekly-reward.alpha-tools-enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WeeklyRankingRewardAlphaToolService.class);
                    assertThat(context).doesNotHaveBean(WeeklyRankingRewardAlphaToolController.class);
                });
    }
}
