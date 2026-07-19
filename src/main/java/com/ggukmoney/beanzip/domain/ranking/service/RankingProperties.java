package com.ggukmoney.beanzip.domain.ranking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RankingProperties {

    private int defaultLimit = 50;
    private int maxLimit = 100;
    private int schemaVersion = 1;
    private int pageSize = 500;
    private Duration reconciliationInterval = Duration.ofSeconds(60);
    private Duration reconciliationLockTtl = Duration.ofMinutes(5);
    private Duration initializationLockTtl = Duration.ofMinutes(10);
    private Duration maxStaleness = Duration.ofSeconds(120);
    private Duration deltaOverlap = Duration.ofSeconds(5);
    private Duration rebuildLockTtl = Duration.ofMinutes(5);

    @Value("${ranking.default-limit:50}")
    void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    @Value("${ranking.max-limit:100}")
    void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    @Value("${ranking.schema-version:1}")
    void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    @Value("${ranking.page-size:500}")
    void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    @Value("${ranking.reconciliation-interval:60s}")
    void setReconciliationInterval(Duration reconciliationInterval) {
        this.reconciliationInterval = reconciliationInterval;
    }

    @Value("${ranking.reconciliation-lock-ttl:5m}")
    void setReconciliationLockTtl(Duration reconciliationLockTtl) {
        this.reconciliationLockTtl = reconciliationLockTtl;
    }

    @Value("${ranking.initialization-lock-ttl:10m}")
    void setInitializationLockTtl(Duration initializationLockTtl) {
        this.initializationLockTtl = initializationLockTtl;
    }

    @Value("${ranking.max-staleness:120s}")
    void setMaxStaleness(Duration maxStaleness) {
        this.maxStaleness = maxStaleness;
    }

    @Value("${ranking.delta-overlap:5s}")
    void setDeltaOverlap(Duration deltaOverlap) {
        this.deltaOverlap = deltaOverlap;
    }

    @Value("${ranking.rebuild-lock-ttl:5m}")
    void setRebuildLockTtl(Duration rebuildLockTtl) {
        this.rebuildLockTtl = rebuildLockTtl;
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    public int maxLimit() {
        return maxLimit;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public int pageSize() {
        return pageSize;
    }

    public Duration reconciliationInterval() {
        return reconciliationInterval;
    }

    public Duration reconciliationLockTtl() {
        return reconciliationLockTtl;
    }

    public Duration initializationLockTtl() {
        return initializationLockTtl;
    }

    public Duration maxStaleness() {
        return maxStaleness;
    }

    public Duration deltaOverlap() {
        return deltaOverlap;
    }

    public Duration rebuildLockTtl() {
        return rebuildLockTtl;
    }
}
