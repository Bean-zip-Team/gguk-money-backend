package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.global.util.TokenHash;
import org.springframework.stereotype.Component;

@Component
public class KeycapBoxOpenRequestHasher {

    public String hash(KeycapBoxOpenRequest request) {
        return TokenHash.sha256Base64Url("openMethod=%s;adRewardId=%s".formatted(
                request.openMethod().name(),
                normalizeAdRewardId(request.adRewardId())
        ));
    }

    private String normalizeAdRewardId(String adRewardId) {
        return adRewardId == null ? "" : adRewardId.trim();
    }
}
