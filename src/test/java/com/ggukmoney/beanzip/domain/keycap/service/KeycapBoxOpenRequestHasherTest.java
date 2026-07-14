package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.global.util.TokenHash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycapBoxOpenRequestHasherTest {

    private final KeycapBoxOpenRequestHasher hasher = new KeycapBoxOpenRequestHasher();

    @Test
    void hashesOpenMethodAndNormalizedAdRewardId() {
        KeycapBoxOpenRequest request = new KeycapBoxOpenRequest(
                KeycapBoxOpen.OpenMethod.ADVERTISEMENT,
                "  ad-reward-1  "
        );

        String hash = hasher.hash(request);

        assertThat(hash).isEqualTo(TokenHash.sha256Base64Url("openMethod=ADVERTISEMENT;adRewardId=ad-reward-1"));
    }

    @Test
    void treatsNullAdRewardIdAsEmptyString() {
        KeycapBoxOpenRequest request = new KeycapBoxOpenRequest(KeycapBoxOpen.OpenMethod.FREE, null);

        String hash = hasher.hash(request);

        assertThat(hash).isEqualTo(TokenHash.sha256Base64Url("openMethod=FREE;adRewardId="));
    }
}
