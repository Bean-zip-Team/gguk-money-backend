package com.ggukmoney.beanzip.domain.friend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 응원 한 건 (BEA-390).
 *
 * <p>하루 응원 수와 같은 친구 하루 한 번은 이 테이블로 판정한다. 받는 사람이 확인하면 {@code read_at} 과
 * 그때 받은 조각 수({@code reward_shards})가 채워진다. 하루 상한 때문에 조각을 받지 못한 건은 0 으로
 * 남고 다시 받을 수 없다.
 */
@Getter
@Entity
@Table(
        name = "cheer",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_cheer_public_id", columnNames = "public_id"),
                // 보낸 사람·날짜가 앞에 있어서 "오늘 몇 명에게 보냈는지"도 이 인덱스로 센다.
                @UniqueConstraint(
                        name = "uq_cheer_from_date_to",
                        columnNames = {"from_user_id", "cheer_date", "to_user_id"})
        },
        indexes = @Index(name = "ix_cheer_to_read", columnList = "to_user_id, read_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cheer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "from_user_id", nullable = false, updatable = false)
    private UUID fromUserId;

    @Column(name = "to_user_id", nullable = false, updatable = false)
    private UUID toUserId;

    /** 보낸 날짜(한국 시간). */
    @Column(name = "cheer_date", nullable = false, updatable = false)
    private LocalDate cheerDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private Kind kind;

    /** 응원한 시점에 받는 사람이 오늘 누른 수. 박수 모달의 '오늘 N번'에 쓴다. */
    @Column(name = "tap_count_snapshot", nullable = false, updatable = false)
    private long tapCountSnapshot;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "reward_shards")
    private Integer rewardShards;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Cheer send(
            UUID fromUserId, UUID toUserId, LocalDate cheerDate, Kind kind, long tapCountSnapshot, Instant now
    ) {
        Cheer cheer = new Cheer();
        cheer.publicId = UUID.randomUUID();
        cheer.fromUserId = fromUserId;
        cheer.toUserId = toUserId;
        cheer.cheerDate = cheerDate;
        cheer.kind = kind;
        cheer.tapCountSnapshot = tapCountSnapshot;
        cheer.createdAt = now;
        return cheer;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 받는 사람이 확인했다. 한 번만 할 수 있다. */
    public void markRead(Instant readAt, int rewardShards) {
        if (isRead()) {
            throw new IllegalStateException("cheer is already read; id=" + id);
        }
        this.readAt = readAt;
        this.rewardShards = rewardShards;
    }

    /** 받는 사람의 상황. 모달 문구는 앱이 정한다. 위에 있는 것이 먼저다. */
    public enum Kind {
        /** 받는 사람이 오늘 먼저 응원했고, 그 응원을 돌려준 것이다. */
        RETURN,
        /** 받는 사람이 며칠째 들어오지 않았다. */
        GIFT,
        /** 받는 사람이 오늘 많이 눌렀다. */
        CLAP,
        DEFAULT
    }
}
