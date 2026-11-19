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
import java.util.UUID;

/**
 * 초대 한 건 (BEA-390).
 *
 * <p>초대 링크로 앱이 열리면 {@code PENDING} 으로 생기고, 그 사람이 새로 가입하면 {@code SUCCESS} 가
 * 된다. 만료는 없다. 가입하지 않으면 PENDING 으로 남는다.
 *
 * <ul>
 *   <li>{@code visit_key} 는 앱이 기기에서 한 번 만든 값이다. 같은 링크를 여러 번 눌러도 한 건으로 세려고
 *       (초대한 사람, visit_key) 를 유일하게 둔다. 방문 기록 없이 가입한 건은 null 이다.</li>
 *   <li>{@code invitee_id} 는 유일하다. 한 사람은 한 번만 초대받은 것으로 센다.</li>
 *   <li>{@code inviter_acked_at} 은 초대한 사람이 성공 모달을 본 시각이다. 초대한 사람의 조각은 이때 준다.</li>
 * </ul>
 */
@Getter
@Entity
@Table(
        name = "invite",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_invite_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uq_invite_invitee", columnNames = "invitee_id"),
                @UniqueConstraint(name = "uq_invite_inviter_visit", columnNames = {"inviter_id", "visit_key"})
        },
        indexes = @Index(name = "ix_invite_inviter_status", columnList = "inviter_id, status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "inviter_id", nullable = false, updatable = false)
    private UUID inviterId;

    @Column(name = "invitee_id")
    private UUID inviteeId;

    @Column(name = "visit_key", updatable = false)
    private UUID visitKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @Column(name = "inviter_acked_at")
    private Instant inviterAckedAt;

    public enum Status {
        PENDING,
        SUCCESS
    }
}
