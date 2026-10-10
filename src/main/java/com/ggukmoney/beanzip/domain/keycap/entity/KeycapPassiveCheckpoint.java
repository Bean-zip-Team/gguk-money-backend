package com.ggukmoney.beanzip.domain.keycap.entity;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.Checkpoint;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Getter @Entity @Table(name="keycap_passive_checkpoint")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class KeycapPassiveCheckpoint {
    @Id private java.util.UUID userId;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @MapsId @JoinColumn(name="user_id")
    private AppUser user;
    @Column(nullable=false) private Instant lastActivityAt;
    @Column(nullable=false) private long remainderNumerator;
    private String equippedKeycapCode;
    @Column(nullable=false) private int equippedLevel;
    @Column(nullable=false) private int clicksPerDay;
    @Column(nullable=false,columnDefinition="text") private String effectsJson;
    @Version private long version;
    public static KeycapPassiveCheckpoint create(AppUser user, Checkpoint value, String effects) {
        var row=new KeycapPassiveCheckpoint(); row.user=user; row.userId=user.getId(); row.update(value,effects); return row;
    }
    public Checkpoint value() { return new Checkpoint(lastActivityAt,remainderNumerator,equippedKeycapCode,equippedLevel,clicksPerDay); }
    public void update(Checkpoint value,String effects) {
        lastActivityAt=value.lastActivityAt(); remainderNumerator=value.remainderNumerator();
        equippedKeycapCode=value.equippedKeycapCode(); equippedLevel=value.equippedLevel();
        clicksPerDay=value.clicksPerDay(); effectsJson=effects;
    }
}
