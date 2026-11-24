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
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.UUID;

/**
 * 친구 관계 (BEA-390). 두 사람에 한 행이다.
 *
 * <p>누가 먼저 맺었는지와 관계없이 같은 행이 되도록 ID 가 작은 쪽을 {@code user_low} 에 둔다. 대소는
 * PostgreSQL 의 uuid 비교(바이트 순서)를 따른다. 자바의 {@code UUID.compareTo} 는 부호 있는 정수로
 * 비교해서 순서가 다르므로 쓰지 않는다. {@code FriendshipService} 가 문자열로 비교한다.
 *
 * <p>탈퇴해도 지우지 않는다. 목록에서만 숨기고, 재가입하면 다시 보인다.
 */
@Getter
@Entity
@Table(
        name = "friendship",
        uniqueConstraints = @UniqueConstraint(name = "uq_friendship_pair", columnNames = {"user_low", "user_high"}),
        indexes = @Index(name = "ix_friendship_user_high", columnList = "user_high")
)
@Check(constraints = "user_low < user_high")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_low", nullable = false, updatable = false)
    private UUID userLow;

    @Column(name = "user_high", nullable = false, updatable = false)
    private UUID userHigh;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private Source source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Source {
        /** 초대 링크로 새로 가입해서 맺어졌다. 초대 성공으로 센다. */
        INVITE_SIGNUP,
        /** 이미 회원인 사람이 초대 링크로 들어와서 맺어졌다. 보상이 없다. */
        INVITE_LINK
    }
}
