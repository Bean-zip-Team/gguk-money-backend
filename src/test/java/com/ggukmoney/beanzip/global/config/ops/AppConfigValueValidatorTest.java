package com.ggukmoney.beanzip.global.config.ops;

import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppConfigValueValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final KeycapRepository keycapRepository = mock(KeycapRepository.class);
    private final PolicyValueRules rules = new PolicyValueRules(keycapRepository, mapper);
    private final AppConfigValueValidator validator = new AppConfigValueValidator(mapper);

    @Test
    void acceptsValidValuesAndNormalizesThem() {
        assertThat(change("tap.point.dailyCap", "150", " 200 ")).isEqualTo("200");
        assertThat(change("cashout.pointToKrwRate", "0.02", "0.05")).isEqualTo("0.05");
        assertThat(change("tap.bot.enabled", "false", "true")).isEqualTo("true");
        assertThat(change("ranking.weeklyReward.policy", "{\"enabled\":true,\"rewards\":{\"1\":10000}}",
                "{ \"enabled\": false, \"rewards\": {\"1\": 10000, \"2\": 5000} }"))
                .isEqualTo("{\"enabled\":false,\"rewards\":{\"1\":10000,\"2\":5000}}");
    }

    @Test
    void aDecimalKeyStoredAsAnIntegerCanStillTakeADecimal() {
        assertThat(change("tap.bot.stddevThresholdMs", "12", "12.5")).isEqualTo("12.5");
    }

    @Test
    void rejectsZeroWhereTheLoaderNeedsAPositiveValue() {
        assertRejected("onboarding.reward.requiredTapCount", "45", "0");
        assertRejected("cashout.pointToKrwRate", "0.02", "0");
        assertRejected("cashout.minimumPoint", "50", "0");
        assertRejected("keycapBox.openCycle.durationSeconds", "3600", "0");
        assertRejected("notification.rankChange.minimumDifference", "1", "0");
    }

    @Test
    void rejectsNumbersTheLoadersCannotRead() {
        assertRejected("tap.point.dailyCap", "150", "3000000000");
        assertRejected("tap.point.dailyCap", "150", "99999999999999999999");
        assertRejected("tap.point.dailyCap", "150", "1.5");
        assertRejected("tap.point.dailyCap", "150", "-1");
        assertRejected("cashout.pointToKrwRate", "0.02", "1e400");
        assertRejected("cashout.pointToKrwRate", "0.02", "\"0.05\"");
        assertRejected("tap.bot.enabled", "false", "yes");
    }

    @Test
    void rejectsPoliciesTheirLoaderWouldDisable() {
        assertRejected("ranking.weeklyReward.policy", "{\"enabled\":true,\"rewards\":{\"1\":10000}}", "{}");
        assertRejected("ranking.weeklyReward.policy", "{\"enabled\":true,\"rewards\":{\"1\":10000}}",
                "{\"enabled\":true,\"rewards\":{\"1\":10000,\"3\":1}}");
        assertRejected("ranking.systemBoost.policy", "{\"enabled\":true}", "{\"enabled\":false}");
        assertRejected("promotion.excludedUserIds", "[]", "[\"not-a-uuid\"]");
        assertRejected("promotion.excludedUserIds", "[]", "{}");
    }

    @Test
    void onboardingKeycapMustExistAndBeActive() {
        when(keycapRepository.findByCode(anyString())).thenReturn(Optional.empty());
        assertRejected("onboarding.reward.keycapCode", "\"main\"", "song");

        assertRejected("onboarding.reward.bonusKeycapGrade", "\"COMMON\"", "SUPER");
        assertThat(change("onboarding.reward.bonusKeycapGrade", "\"COMMON\"", "RARE")).isEqualTo("\"RARE\"");
    }

    @Test
    void capsMoneyValuesAgainstFatFingerMistakes() {
        assertRejected("cashout.pointToKrwRate", "0.02", "2");
        assertRejected("promotion.keycapFive.amount", "500", "100000");
        assertRejected("onboarding.reward.pointAmount", "70", "100000");
        assertRejected("ranking.weeklyReward.policy", "{\"enabled\":true,\"rewards\":{\"1\":10000}}",
                "{\"enabled\":true,\"rewards\":{\"1\":100000000}}");
        assertThat(change("cashout.pointToKrwRate", "0.02", "1")).isEqualTo("1");
    }

    @Test
    void rejectsAnUnchangedValue() {
        assertRejected("tap.point.dailyCap", "150", "150");
        assertRejected("ranking.weeklyReward.policy", "{\"enabled\":true,\"rewards\":{\"1\":10000}}",
                "{ \"enabled\": true, \"rewards\": {\"1\": 10000} }");
    }

    @Test
    void keysWithoutARuleAreNotEditable() {
        assertThat(rules.find("keycapBox.freeTicket.cap")).isEmpty();
    }

    private String change(String key, String current, String input) {
        return validator.normalize(rules.find(key).orElseThrow(), current, input);
    }

    private void assertRejected(String key, String current, String input) {
        assertThatThrownBy(() -> change(key, current, input))
                .as("%s <- %s", key, input)
                .isInstanceOf(IllegalArgumentException.class);
    }
}
