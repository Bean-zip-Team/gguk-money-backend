package com.ggukmoney.beanzip.domain.keycap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조각 지급·사용 원장 (BEA-390).
 *
 * <p>지갑({@code keycap_box_account.box_balance})에는 잔액만 있어서 조각이 하루에 얼마나, 어떤 이유로
 * 나갔는지 알 수 없었다. 친구 기능의 가드레일("조각 발행량이 출시 전보다 20% 넘게 늘지 않을 것")을
 * 재려면 친구 보상뿐 아니라 탭·자동 클릭·뽑기까지 모두 남아야 하므로 잔액이 바뀌는 모든 곳에서 쓴다.
 *
 * <p>기록용이다. 지급 여부 판정은 각 기능의 상태 컬럼이 맡고, 이 테이블에는 유니크 제약을 두지 않는다.
 * 탭 배치처럼 자주 지나는 경로가 원장 충돌로 실패하면 안 되기 때문이다.
 */
@Getter
@Entity
@Table(
        name = "shard_ledger",
        indexes = @Index(name = "ix_shard_ledger_user_created", columnList = "user_id, created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShardLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 지급은 양수, 사용은 음수다. */
    @Column(name = "delta", nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private Reason reason;

    /** 이 변동을 만든 건의 식별자(탭 배치, 뽑기 멱등키, 초대·응원 ID). 없으면 null 이다. */
    @Column(name = "ref_id", length = 80)
    private String refId;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ShardLedger record(
            UUID userId,
            int delta,
            Reason reason,
            String refId,
            int balanceAfter,
            Instant createdAt
    ) {
        if (delta == 0) {
            throw new IllegalArgumentException("Shard ledger delta must not be zero.");
        }
        ShardLedger ledger = new ShardLedger();
        ledger.userId = userId;
        ledger.delta = delta;
        ledger.reason = reason;
        ledger.refId = refId;
        ledger.balanceAfter = balanceAfter;
        ledger.createdAt = createdAt;
        return ledger;
    }

    public enum Reason {
        /** 손으로 누른 탭에서 떨어진 조각. */
        TAP_DROP,
        /** 자동 클릭 정산에서 떨어진 조각. */
        PASSIVE_DROP,
        /** 경제 전환 때 키캡별 진행 조각을 지갑으로 옮긴 양. */
        ECONOMY_MIGRATION,
        /** 뽑기에 쓴 조각. */
        DRAW_SPEND,
        /** 초대한 사람이 초대 성공을 확인하고 받은 조각. */
        INVITE_SUCCESS_INVITER,
        /** 초대받은 사람이 가입하면서 받은 조각. */
        INVITE_SUCCESS_INVITEE,
        /** 받은 응원을 확인하고 받은 조각. */
        CHEER_RECEIVED,
        /** 하루 첫 응원을 보내고 받은 조각. */
        CHEER_SENT_FIRST
    }
}
