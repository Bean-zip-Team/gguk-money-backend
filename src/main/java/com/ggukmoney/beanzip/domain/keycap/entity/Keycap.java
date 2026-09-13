package com.ggukmoney.beanzip.domain.keycap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "keycap",
        indexes = @Index(name = "ix_keycap_active_sort", columnList = "active, sort_order")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Keycap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 20)
    private Grade grade;

    @Column(name = "required_shard_count", nullable = false)
    private Integer requiredShardCount;

    @Column(name = "season", nullable = false)
    private Integer season = 1;

    /**
     * 키캡을 얻는 경로. 상자 추첨·온보딩 보너스 추첨·전체 완성 보너스는 {@link AcquisitionType#BOX} 만 대상으로 삼는다.
     *
     * <p>이벤트 키캡을 상시 키캡과 같은 테이블에 두면서 확률을 희석하지 않기 위한 구분이다(BEA-285).
     * {@code season} 에 이 의미를 싣지 않는다 — 시즌은 도감 회차("시즌1 24종")를 뜻하는 별개의 축이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_type", nullable = false, length = 20)
    private AcquisitionType acquisitionType = AcquisitionType.BOX;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "sound_url", columnDefinition = "TEXT")
    private String soundUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Keycap createFor(
            String code,
            String name,
            Grade grade,
            int requiredShardCount,
            int season,
            String imageUrl,
            String soundUrl,
            int sortOrder
    ) {
        Keycap keycap = new Keycap();
        keycap.code = code;
        keycap.name = name;
        keycap.grade = grade;
        keycap.requiredShardCount = requiredShardCount;
        keycap.season = season;
        keycap.imageUrl = imageUrl;
        keycap.soundUrl = soundUrl;
        keycap.sortOrder = sortOrder;
        return keycap;
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

    public enum Grade {
        COMMON,
        RARE,
        EPIC,
        LEGENDARY
    }

    public enum AcquisitionType {
        /** 상자에서 추첨으로 얻는 상시 키캡. */
        BOX,
        /** 이벤트 경로로만 지급하는 키캡. 상자·온보딩 추첨 후보와 전체 완성 보너스 판정에서 빠진다. */
        EVENT
    }
}
