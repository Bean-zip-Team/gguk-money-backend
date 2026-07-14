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

import java.time.Instant;
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
