package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapDraw;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Draw storage adapter using the existing audit table and its user/key uniqueness. */
@Repository
@RequiredArgsConstructor
public class KeycapDrawRepository {
    public static final String RECEIPT_PREFIX = "draw-v1:";
    private final KeycapBoxOpenRepository opens;
    private final ObjectMapper mapper;

    public Optional<KeycapDraw> findByUserIdAndIdempotencyKeyWithKeycap(UUID userId, String key) {
        return opens.findByUserIdAndIdempotencyKeyWithKeycap(userId, storageKey(key))
                .map(this::decode);
    }

    public KeycapDraw save(KeycapDraw draw) {
        String receipt = RECEIPT_PREFIX + mapper.writeValueAsString(new Receipt(
                draw.getShardsSpent(), draw.getLevelAfter(), draw.isNewlyAcquired()));
        if (receipt.length() > 255) throw new IllegalStateException("Draw receipt exceeds legacy storage.");
        KeycapBoxOpen saved = opens.save(KeycapBoxOpen.createFor(draw.getUser(), KeycapBoxOpen.OpenMethod.FREE,
                draw.getKeycap(), 1, storageKey(draw.getIdempotencyKey()), receipt, null,
                draw.isNewlyAcquired(), draw.getDrawnAt()));
        return decode(saved);
    }

    private KeycapDraw decode(KeycapBoxOpen open) {
        if (!open.getRequestHash().startsWith(RECEIPT_PREFIX))
            throw new IllegalStateException("Idempotency key belongs to a legacy box opening.");
        Receipt receipt = mapper.readValue(open.getRequestHash().substring(RECEIPT_PREFIX.length()), Receipt.class);
        return KeycapDraw.fromExisting(open, receipt.price(), receipt.level(), receipt.acquired());
    }

    private String storageKey(String key) {
        try {
            return "draw:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private record Receipt(int price, int level, boolean acquired) {}
}
