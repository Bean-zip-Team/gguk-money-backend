package com.ggukmoney.beanzip.domain.keycap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "keycap_box_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeycapBoxAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "box_balance", nullable = false)
    private Integer boxBalance = 0;

    @Deprecated
    @Column(name = "free_open_ticket_count", nullable = false)
    private Integer freeOpenTicketCount = 0;

    @Deprecated
    @Column(name = "last_free_ticket_granted_at", nullable = false)
    private Instant lastFreeTicketGrantedAt;

    @Deprecated
    @Column(name = "ad_open_count", nullable = false)
    private Integer adOpenCount = 0;

    @Deprecated
    @Column(name = "ad_open_count_date")
    private LocalDate adOpenCountDate;

    @Column(name = "free_open_used_count", nullable = false)
    private Integer freeOpenUsedCount = 0;

    @Column(name = "ad_open_used_count", nullable = false)
    private Integer adOpenUsedCount = 0;

    @Column(name = "open_cycle_started_at", nullable = false)
    private Instant openCycleStartedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Deprecated
    public static KeycapBoxAccount createFor(AppUser user) {
        KeycapBoxAccount account = new KeycapBoxAccount();
        account.user = user;
        account.lastFreeTicketGrantedAt = Instant.now();
        account.openCycleStartedAt = account.lastFreeTicketGrantedAt;
        return account;
    }

    public static KeycapBoxAccount createFor(AppUser user, Instant createdAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        KeycapBoxAccount account = new KeycapBoxAccount();
        account.user = user;
        account.lastFreeTicketGrantedAt = createdAt;
        account.openCycleStartedAt = createdAt;
        return account;
    }

    public void addBoxes(int count) {
        this.boxBalance += count;
    }

    public boolean hasBox() {
        return boxBalance > 0;
    }

    public boolean hasFreeOpenTicket() {
        return freeOpenTicketCount > 0;
    }

    public void consumeFreeOpen() {
        if (!hasBox()) {
            throw new IllegalStateException("Keycap box balance is insufficient.");
        }
        if (!hasFreeOpenTicket()) {
            throw new IllegalStateException("Free open ticket is insufficient.");
        }
        boxBalance -= 1;
        freeOpenTicketCount -= 1;
    }

    public OpenCycleSnapshot calculateOpenCycleSnapshot(
            Instant now,
            Duration cycleDuration,
            int freeLimit,
            int adLimit
    ) {
        validateOpenCyclePolicy(now, cycleDuration, freeLimit, adLimit);
        CycleState cycleState = currentCycleState(now, cycleDuration);
        boolean hasAvailableBox = hasBox();
        boolean canFreeOpen = hasAvailableBox && cycleState.freeOpenUsedCount() < freeLimit;
        boolean canAdOpen = hasAvailableBox && cycleState.adOpenUsedCount() < adLimit;
        boolean charging = hasAvailableBox && !canFreeOpen && !canAdOpen;
        Instant nextRechargeAt = charging ? cycleState.openCycleStartedAt().plus(cycleDuration) : null;
        return new OpenCycleSnapshot(canFreeOpen, canAdOpen, charging, nextRechargeAt);
    }

    public void refreshOpenCycle(Instant now, Duration cycleDuration) {
        validateOpenCyclePolicy(now, cycleDuration, 0, 0);
        CycleState cycleState = currentCycleState(now, cycleDuration);
        if (!cycleState.openCycleStartedAt().equals(openCycleStartedAt)) {
            openCycleStartedAt = cycleState.openCycleStartedAt();
            freeOpenUsedCount = 0;
            adOpenUsedCount = 0;
        }
    }

    public boolean canUseFreeOpen(int freeLimit) {
        validateLimit("freeLimit", freeLimit);
        return freeOpenUsedCount < freeLimit;
    }

    public boolean canUseAdOpen(int adLimit) {
        validateLimit("adLimit", adLimit);
        return adOpenUsedCount < adLimit;
    }

    public void consumeFreeOpen(int freeLimit) {
        validateLimit("freeLimit", freeLimit);
        if (!hasBox()) {
            throw new IllegalStateException("Keycap box balance is insufficient.");
        }
        if (!canUseFreeOpen(freeLimit)) {
            throw new IllegalStateException("Free open limit exceeded.");
        }
        boxBalance -= 1;
        freeOpenUsedCount += 1;
    }

    public void consumeAdOpen(int adLimit) {
        validateLimit("adLimit", adLimit);
        if (!hasBox()) {
            throw new IllegalStateException("Keycap box balance is insufficient.");
        }
        if (!canUseAdOpen(adLimit)) {
            throw new IllegalStateException("Ad open limit exceeded.");
        }
        boxBalance -= 1;
        adOpenUsedCount += 1;
    }

    /**
     * 마지막 충전 이후 경과한 시간만큼 무료 개봉권을 충전한다(상한 초과분은 버림).
     * 상한에 도달해 충전량이 0이어도 시계는 항상 전진시킨다 — 그렇지 않으면 오래 상한에
     * 머물다 나중에 소비했을 때 그동안 누적된 시간이 한꺼번에 몰아서 충전되는 부작용이 생긴다.
     */
    public void grantElapsedFreeTickets(Instant now, int refillPerHour, int cap) {
        long elapsedHours = Duration.between(lastFreeTicketGrantedAt, now).toHours();
        if (elapsedHours <= 0) {
            return;
        }
        int granted = (int) Math.min(elapsedHours * refillPerHour, Math.max(cap - freeOpenTicketCount, 0));
        freeOpenTicketCount += granted;
        lastFreeTicketGrantedAt = lastFreeTicketGrantedAt.plus(Duration.ofHours(elapsedHours));
    }

    public boolean hasAdOpenQuota(LocalDate today, int dailyLimit) {
        if (adOpenCountDate == null || !adOpenCountDate.equals(today)) {
            return dailyLimit > 0;
        }
        return adOpenCount < dailyLimit;
    }

    /**
     * 광고 시청 완료를 트러스트 기반으로 인정하고(서버 검증 불가, §11.10) 상자 하나를 소비한다.
     * 무료 개봉권(freeOpenTicketCount)과는 별개의 일일 카운터를 쓴다 — 광고 개봉은 무료권을 소비하지 않는다.
     */
    public void consumeAdOpen(LocalDate today, int dailyLimit) {
        if (!hasBox()) {
            throw new IllegalStateException("Keycap box balance is insufficient.");
        }
        if (adOpenCountDate == null || !adOpenCountDate.equals(today)) {
            adOpenCount = 0;
            adOpenCountDate = today;
        }
        if (adOpenCount >= dailyLimit) {
            throw new IllegalStateException("Ad open daily limit exceeded.");
        }
        boxBalance -= 1;
        adOpenCount += 1;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    private CycleState currentCycleState(Instant now, Duration cycleDuration) {
        if (now.isBefore(openCycleStartedAt)) {
            return new CycleState(openCycleStartedAt, freeOpenUsedCount, adOpenUsedCount);
        }
        try {
            long elapsedCycleCount = Duration.between(openCycleStartedAt, now).dividedBy(cycleDuration);
            if (elapsedCycleCount <= 0) {
                return new CycleState(openCycleStartedAt, freeOpenUsedCount, adOpenUsedCount);
            }
            Instant effectiveCycleStartedAt = openCycleStartedAt.plus(cycleDuration.multipliedBy(elapsedCycleCount));
            return new CycleState(effectiveCycleStartedAt, 0, 0);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Open cycle calculation overflowed.", exception);
        }
    }

    private void validateOpenCyclePolicy(Instant now, Duration cycleDuration, int freeLimit, int adLimit) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        if (openCycleStartedAt == null) {
            throw new IllegalStateException("openCycleStartedAt is required");
        }
        if (cycleDuration == null || cycleDuration.isZero() || cycleDuration.isNegative()) {
            throw new IllegalArgumentException("cycleDuration must be positive");
        }
        validateLimit("freeLimit", freeLimit);
        validateLimit("adLimit", adLimit);
    }

    private void validateLimit(String name, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    public record OpenCycleSnapshot(
            boolean canFreeOpen,
            boolean canAdOpen,
            boolean charging,
            Instant nextRechargeAt
    ) {
    }

    private record CycleState(
            Instant openCycleStartedAt,
            int freeOpenUsedCount,
            int adOpenUsedCount
    ) {
    }
}
