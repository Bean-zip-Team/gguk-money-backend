package com.ggukmoney.beanzip.domain.friend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자의 초대 코드 (BEA-390). 한 사람에 하나이고 바뀌지 않는다.
 *
 * <p>행은 {@code InviteCodeRepository.insertIfAbsent} 로만 만든다. 같은 사용자가 동시에 두 번 요청해도
 * 한 행만 남아야 하기 때문이다.
 */
@Getter
@Entity
@Table(
        name = "invite_code",
        uniqueConstraints = @UniqueConstraint(name = "uq_invite_code_code", columnNames = "code")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode {

    public static final int LENGTH = 8;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code", nullable = false, updatable = false, length = LENGTH)
    private String code;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
