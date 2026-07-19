package com.ggukmoney.beanzip.domain.ranking.redis;

import org.springframework.stereotype.Component;

@Component
public class RankingRedisKeys {

    private static final String PREFIX = "ggukmoney:ranking:v1:";

    public String global(Long seasonId) {
        return PREFIX + "{" + seasonId + "}:global";
    }

    public String region(Long seasonId, String regionCode) {
        return PREFIX + "{" + seasonId + "}:region:" + regionCode;
    }

    public String meta(Long seasonId) {
        return PREFIX + "{" + seasonId + "}:meta";
    }

    public String rebuildLock(Long seasonId) {
        return PREFIX + "{" + seasonId + "}:rebuild-lock";
    }

    public String reconcileLock(Long seasonId) {
        return PREFIX + "{" + seasonId + "}:reconcile-lock";
    }

    public String initializationLock() {
        return PREFIX + "initialization-lock";
    }

    public String globalBuild(Long seasonId, String buildId) {
        return PREFIX + "{" + seasonId + "}:global:build:" + buildId;
    }
}
