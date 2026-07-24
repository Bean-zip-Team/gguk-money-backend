package com.ggukmoney.beanzip.domain.ranking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalTime;

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
    private DayOfWeek weeklyResetDayOfWeek = DayOfWeek.MONDAY;
    private LocalTime weeklyResetTime = LocalTime.MIDNIGHT;
    private Duration weeklyFinalizationDelay = Duration.ofMinutes(10);
    private Duration weeklyRolloverInterval = Duration.ofMinutes(1);
    private long weeklyAdvisoryLockKey = 1_580_001L;

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

    @Value("${ranking.weekly.reset-day-of-week:MONDAY}")
    void setWeeklyResetDayOfWeek(DayOfWeek weeklyResetDayOfWeek) {
        if (weeklyResetDayOfWeek != DayOfWeek.MONDAY) {
            throw new IllegalStateException("ranking.weekly.reset-day-of-week must be MONDAY");
        }
        this.weeklyResetDayOfWeek = weeklyResetDayOfWeek;
    }

    @Value("${ranking.weekly.reset-time:00:00}")
    void setWeeklyResetTime(String weeklyResetTime) {
        LocalTime parsed = LocalTime.parse(weeklyResetTime);
        if (!LocalTime.MIDNIGHT.equals(parsed)) {
            throw new IllegalStateException("ranking.weekly.reset-time must be 00:00");
        }
        this.weeklyResetTime = parsed;
    }

    @Value("${ranking.weekly.finalization-delay:10m}")
    void setWeeklyFinalizationDelay(Duration weeklyFinalizationDelay) {
        if (weeklyFinalizationDelay.isNegative()) {
            throw new IllegalStateException("ranking.weekly.finalization-delay must not be negative");
        }
        this.weeklyFinalizationDelay = weeklyFinalizationDelay;
    }

    @Value("${ranking.weekly.rollover-interval:1m}")
    void setWeeklyRolloverInterval(Duration weeklyRolloverInterval) {
        if (weeklyRolloverInterval.isNegative() || weeklyRolloverInterval.isZero()) {
            throw new IllegalStateException("ranking.weekly.rollover-interval must be positive");
        }
        this.weeklyRolloverInterval = weeklyRolloverInterval;
    }

    @Value("${ranking.weekly.advisory-lock-key:1580001}")
    void setWeeklyAdvisoryLockKey(long weeklyAdvisoryLockKey) {
        this.weeklyAdvisoryLockKey = weeklyAdvisoryLockKey;
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

    public DayOfWeek weeklyResetDayOfWeek() {
        return weeklyResetDayOfWeek;
    }

    public LocalTime weeklyResetTime() {
        return weeklyResetTime;
    }

    public Duration weeklyFinalizationDelay() {
        return weeklyFinalizationDelay;
    }

    public Duration weeklyRolloverInterval() {
        return weeklyRolloverInterval;
    }

    public long weeklyAdvisoryLockKey() {
        return weeklyAdvisoryLockKey;
    }
}
