package com.ggukmoney.beanzip.global.config.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "app_config",
        uniqueConstraints = @UniqueConstraint(name = "uq_app_config_key_effective", columnNames = {"config_key", "effective_at"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_value", nullable = false, columnDefinition = "jsonb")
    private String configValue;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    /** 운영 화면(/ops/config)에서 바꿀 때만 채운다. SQL·시더로 넣은 행은 비어 있다. */
    @Column(name = "changed_by", length = 50)
    private String changedBy;

    @Column(name = "change_reason", length = 200)
    private String changeReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AppConfig createFor(String configKey, String configValue, Instant effectiveAt) {
        AppConfig config = new AppConfig();
        config.configKey = configKey;
        config.configValue = configValue;
        config.effectiveAt = effectiveAt;
        return config;
    }

    public static AppConfig change(String configKey, String configValue, Instant effectiveAt, String changedBy, String changeReason) {
        AppConfig config = createFor(configKey, configValue, effectiveAt);
        config.changedBy = changedBy;
        config.changeReason = changeReason;
        return config;
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
