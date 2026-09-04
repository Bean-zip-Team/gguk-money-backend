package com.ggukmoney.beanzip.domain.promotion.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 토스 프로모션 지급 1건.
 *
 * <p>중복 지급 방어는 {@code uq_promotion_grant_user_code} 하나가 전부다. 토스는 중복 지급을
 * 막아주지 않으므로 1인 1회는 우리가 보장해야 한다. 이 저장소에는 Migration 도구가 없어
 * 제약 생성이 수동 DDL 이므로, 배포 후 제약이 실제로 존재하는지 확인해야 한다
 * ({@code docs/sql/promotion-grant.sql} 하단 참고).
 *
 * <p>종료 판정은 {@link Status} 하나로만 한다. {@code nextAttemptAt} 은 NOT NULL 이며 종료
 * 여부를 인코딩하지 않는다 — 두 컬럼에 이중으로 넣으면 개봉 트랜잭션이 넣은 행이 스케줄러
 * 조건에 걸리지 않아 유실 복구 경로가 막힌다.
 */
@Getter
@Entity
@Table(
        name = "promotion_grant",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_promotion_grant_user_code",
                columnNames = {"user_id", "promotion_code"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** 우리가 정한 논리 코드. unique 키의 축이라 콘솔 코드가 바뀌어도 고정이다. */
    @Column(name = "promotion_code", nullable = false, length = 80)
    private String promotionCode;

    /**
     * 실제 호출에 쓴 앱인토스 콘솔 코드.
     * execution-result 바디에 execute 때와 동일한 값을 보내야 해서 스냅샷으로 남긴다.
     */
    @Column(name = "toss_promotion_code", length = 80)
    private String tossPromotionCode;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** 사람이 지울 때까지 자동 진행 정지. NULL 이 아니면 스케줄러가 집지 않는다. */
    @Column(name = "hold_reason", length = 40)
    private String holdReason;

    /** 확정되지 않은 채 사람이 확인해야 하는 건. 상태가 아니라 플래그다. */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview = false;

    @Column(name = "toss_promotion_key", length = 255)
    private String tossPromotionKey;

    @Column(name = "toss_error_code", length = 40)
    private String tossErrorCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "poll_count", nullable = false)
    private Integer pollCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    /** 판정 당시 커트오프 이후 완성 수. 사후 감사용. */
    @Column(name = "trigger_snapshot")
    private Integer triggerSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "granted_at")
    private Instant grantedAt;

    public static PromotionGrant createPending(
            AppUser user,
            String promotionCode,
            long amount,
            Integer triggerSnapshot,
            Instant now
    ) {
        Objects.requireNonNull(user, "user must not be null.");
        Objects.requireNonNull(now, "now must not be null.");
        if (promotionCode == null || promotionCode.isBlank()) {
            throw new IllegalArgumentException("promotionCode must not be blank.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive.");
        }

        PromotionGrant grant = new PromotionGrant();
        grant.publicId = UUID.randomUUID();
        grant.user = user;
        grant.promotionCode = promotionCode;
        grant.amount = amount;
        grant.status = Status.PENDING;
        grant.triggerSnapshot = triggerSnapshot;
        // 즉시 처리 대상. Executor 가 유실돼도 스케줄러가 같은 행을 집어야 한다.
        grant.nextAttemptAt = now;
        return grant;
    }

    /** get-key 결과를 execute 이전에 확정한다. 자동 재시도가 같은 key 를 재사용하는 근거다. */
    public void assignTossKey(String tossPromotionKey, String tossPromotionCode, Instant now) {
        if (tossPromotionKey == null || tossPromotionKey.isBlank()) {
            throw new IllegalArgumentException("tossPromotionKey must not be blank.");
        }
        this.tossPromotionKey = tossPromotionKey;
        this.tossPromotionCode = tossPromotionCode;
        touch(now);
    }

    public void markProcessing(Instant now) {
        this.status = Status.PROCESSING;
        this.tossErrorCode = null;
        touch(now);
    }

    public void markSucceeded(Instant now) {
        this.status = Status.SUCCEEDED;
        this.grantedAt = now;
        this.holdReason = null;
        this.needsReview = false;
        touch(now);
    }

    public void markFailed(String tossErrorCode, String failureReason, Instant now) {
        this.status = Status.FAILED;
        this.tossErrorCode = tossErrorCode;
        this.failureReason = truncate(failureReason);
        touch(now);
    }

    /** 재시도 가능한 실패. 같은 key 를 유지한 채 뒤로 민다. */
    public void deferAttempt(Instant nextAttemptAt, String tossErrorCode, Instant now) {
        this.attemptCount = this.attemptCount + 1;
        this.nextAttemptAt = nextAttemptAt;
        this.tossErrorCode = tossErrorCode;
        touch(now);
    }

    /** 설정 오류처럼 우리 쪽 문제인 지연. attempt 를 소모하지 않는다. */
    public void deferWithoutAttempt(Instant nextAttemptAt, String tossErrorCode, Instant now) {
        this.nextAttemptAt = nextAttemptAt;
        this.tossErrorCode = tossErrorCode;
        touch(now);
    }

    public void deferPoll(Instant nextAttemptAt, Instant now) {
        this.pollCount = this.pollCount + 1;
        this.nextAttemptAt = nextAttemptAt;
        touch(now);
    }

    /** 사람이 지울 때까지 자동 진행에서 제외한다. */
    public void hold(String holdReason, Instant now) {
        this.holdReason = holdReason;
        touch(now);
    }

    public void flagForReview(Instant now) {
        this.needsReview = true;
        touch(now);
    }

    public boolean hasTossKey() {
        return tossPromotionKey != null && !tossPromotionKey.isBlank();
    }

    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now must not be null.");
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = Instant.now();
    }

    public enum Status {
        PENDING,
        PROCESSING,
        SUCCEEDED,
        FAILED
    }
}
