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

/**
 * 유저의 조각 지갑 (BEA-329).
 *
 * <p>조각은 등급 없는 단일 화폐다. 탭 드롭 곡선에서 1개씩 쌓이고 뽑기에서 가격만큼 빠진다.
 * 테이블과 클래스 이름은 상자 시절({@code keycap_box_account})의 것을 그대로 쓴다 — 유저당 1행,
 * 가입 시 생성, 낙관적 락이 모두 그대로 필요해서 새로 만들 이유가 없다.
 */
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

    @Column(name = "shard_balance", nullable = false)
    private Integer shardBalance = 0;

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

    public void addShards(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Shard count must be positive.");
        }
        this.shardBalance += count;
    }

    public boolean canAfford(int price) {
        validatePrice(price);
        return shardBalance >= price;
    }

    public void consumeShards(int price) {
        validatePrice(price);
        if (shardBalance < price) {
            throw new IllegalStateException("Keycap shard balance is insufficient.");
        }
        shardBalance -= price;
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

    private void validatePrice(int price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Draw price must be positive.");
        }
    }
}
