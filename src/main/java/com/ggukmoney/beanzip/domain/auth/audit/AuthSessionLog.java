package com.ggukmoney.beanzip.domain.auth.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "auth_session_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSessionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id")
    private UUID userPublicId;

    @Column(name = "device_public_id")
    private UUID devicePublicId;

    @Column(name = "session_id_hash", length = 128)
    private String sessionIdHash;

    @Column(name = "token_family_id_hash", length = 128)
    private String tokenFamilyIdHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuthAuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private AuthAuditResult result;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "trace_id", length = 80)
    private String traceId;

    @Column(name = "ip_address_masked", length = 80)
    private String ipAddressMasked;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private AuthSessionLog(
            UUID publicId,
            UUID userPublicId,
            UUID devicePublicId,
            String sessionIdHash,
            String tokenFamilyIdHash,
            AuthAuditEventType eventType,
            AuthAuditResult result,
            String failureCode,
            String traceId,
            String ipAddressMasked,
            String userAgent,
            String metadata,
            Instant occurredAt
    ) {
        this.publicId = publicId;
        this.userPublicId = userPublicId;
        this.devicePublicId = devicePublicId;
        this.sessionIdHash = sessionIdHash;
        this.tokenFamilyIdHash = tokenFamilyIdHash;
        this.eventType = eventType;
        this.result = result;
        this.failureCode = failureCode;
        this.traceId = traceId;
        this.ipAddressMasked = ipAddressMasked;
        this.userAgent = userAgent;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
    }

    public static AuthSessionLog create(
            String userPublicId,
            String devicePublicId,
            String sessionIdHash,
            String tokenFamilyIdHash,
            AuthAuditEventType eventType,
            AuthAuditResult result,
            String failureCode,
            String traceId,
            String ipAddressMasked,
            String userAgent,
            String metadata
    ) {
        return new AuthSessionLog(
                UUID.randomUUID(),
                parseNullableUuid(userPublicId),
                parseNullableUuid(devicePublicId),
                sessionIdHash,
                tokenFamilyIdHash,
                eventType,
                result,
                failureCode,
                traceId,
                ipAddressMasked,
                userAgent,
                metadata,
                Instant.now()
        );
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    private static UUID parseNullableUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID value");
        }
    }
}

