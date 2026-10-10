package com.ggukmoney.beanzip.domain.keycap.entity;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="keycap_passive_settlement",uniqueConstraints=@UniqueConstraint(name="uq_passive_settlement_user_key",columnNames={"user_id","idempotency_key"}))
public class KeycapPassiveSettlement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private AppUser user;
    @Column(name="idempotency_key",nullable=false,length=150) private String idempotencyKey;
    @Column(nullable=false,columnDefinition="text") private String resultJson;
    @Column(nullable=false) private Instant settledAt;
    public static KeycapPassiveSettlement create(AppUser user,String key,String result,Instant now) {
        var row=new KeycapPassiveSettlement(); row.user=user; row.idempotencyKey=key; row.resultJson=result; row.settledAt=now; return row;
    }
}
