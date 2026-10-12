package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 키캡 장착이 운영에서만 500 으로 실패하는 문제 (BEA-304).
 *
 * <p>운영에는 {@code manual-index-contracts.sql} 의 부분 유니크 인덱스가 걸려 있어 유저당
 * {@code equipped = true} 가 하나로 강제된다. 테스트는 {@code ddl-auto=create-drop} 으로
 * 엔티티에서 스키마를 만들기 때문에 이 인덱스가 없고, 그래서 기존 테스트가 전부 통과했다.
 *
 * <p>여기서는 그 인덱스를 직접 만들어 운영과 같은 조건을 재현한다.
 */
class KeycapEquipIndexIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private KeycapRepository keycapRepository;

    @Autowired
    private UserKeycapRepository userKeycapRepository;

    @BeforeEach
    void createProductionOnlyIndex() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS ux_user_keycap_equipped");
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX ux_user_keycap_equipped
                ON user_keycap (user_id)
                WHERE equipped = true
                """);
    }

    /**
     * {@code findByUserIdForUpdate} 에 ORDER BY 가 없어 행 순서가 곧 영속성 컨텍스트 적재
     * 순서이고, 그것이 UPDATE 발행 순서가 된다. 두 순서 모두 통과해야 한다 — 운영에서는
     * VACUUM·UPDATE 로 힙 순서가 바뀌므로 어느 쪽이 나올지 고를 수 없다.
     */
    @Test
    @DisplayName("장착 대상 행이 먼저 적재돼도 교체할 수 있다")
    void equipsWhenTargetRowComesFirst() throws Exception {
        assertEquipSucceeds("target-first", true);
    }

    @Test
    @DisplayName("기존 장착 행이 먼저 적재돼도 교체할 수 있다")
    void equipsWhenEquippedRowComesFirst() throws Exception {
        assertEquipSucceeds("equipped-first", false);
    }

    private void assertEquipSucceeds(String nickname, boolean saveTargetFirst) throws Exception {
        AppUser user = appUserRepository.save(AppUser.createActive(nickname, null));
        List<Keycap> catalog = keycapRepository.findAll().stream().limit(2).toList();
        Keycap alreadyEquipped = catalog.get(0);
        Keycap target = catalog.get(1);

        Instant now = Instant.now();
        UserKeycap current = UserKeycap.createCompletedOnboardingReward(user, alreadyEquipped, now);
        current.equip();
        UserKeycap next = UserKeycap.createCompletedOnboardingReward(user, target, now);

        if (saveTargetFirst) {
            userKeycapRepository.save(next);
            userKeycapRepository.save(current);
        } else {
            userKeycapRepository.save(current);
            userKeycapRepository.save(next);
        }

        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(put("/api/keycaps/{keycapId}/equip", target.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());
    }
}
