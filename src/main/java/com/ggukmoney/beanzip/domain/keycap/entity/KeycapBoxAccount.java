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

    @Column(name = "free_open_ticket_count", nullable = false)
    private Integer freeOpenTicketCount = 0;

    @Column(name = "last_free_ticket_granted_at", nullable = false)
    private Instant lastFreeTicketGrantedAt;

    @Column(name = "ad_open_count", nullable = false)
    private Integer adOpenCount = 0;

    @Column(name = "ad_open_count_date")
    private LocalDate adOpenCountDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static KeycapBoxAccount createFor(AppUser user) {
        KeycapBoxAccount account = new KeycapBoxAccount();
        account.user = user;
        account.lastFreeTicketGrantedAt = Instant.now();
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
}
