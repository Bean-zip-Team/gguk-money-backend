package com.ggukmoney.beanzip.domain.keycap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

/** Domain receipt backed by existing keycap_box_open, not a JPA table. */
@Getter
public class KeycapDraw {
    private Long id;
    private UUID publicId;
    private AppUser user;
    private Keycap keycap;
    private int shardsSpent;
    private int levelAfter;
    private boolean newlyAcquired;
    private String idempotencyKey;
    private Instant drawnAt;

    public static KeycapDraw createFor(AppUser user, Keycap keycap, int shardsSpent,
            int levelAfter, boolean newlyAcquired, String idempotencyKey, Instant drawnAt) {
        if (shardsSpent < 1 || levelAfter < 1) throw new IllegalArgumentException("Invalid draw receipt.");
        KeycapDraw draw = new KeycapDraw();
        draw.user = user; draw.keycap = keycap; draw.shardsSpent = shardsSpent;
        draw.levelAfter = levelAfter; draw.newlyAcquired = newlyAcquired;
        draw.idempotencyKey = idempotencyKey; draw.drawnAt = drawnAt;
        return draw;
    }

    public static KeycapDraw fromExisting(KeycapBoxOpen open, int price, int level, boolean newlyAcquired) {
        KeycapDraw draw = createFor(open.getUser(), open.getKeycap(), price, level,
                newlyAcquired, open.getIdempotencyKey(), open.getOpenedAt());
        draw.id = open.getId(); draw.publicId = open.getPublicId();
        return draw;
    }
}
