package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapDrawResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 뽑기의 DB 수준 동시성 (BEA-329). 지갑 FOR UPDATE 와 (user_id, idempotency_key) 유니크 제약이
 * 실제 Postgres 에서 이중 차감과 음수 잔액을 막는지 본다. 단위 테스트는 mock 이라 이 둘을 증명하지 못한다.
 */
class KeycapDrawConcurrencyIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private KeycapDrawService keycapDrawService;

    @Autowired
    private KeycapBoxAccountRepository keycapBoxAccountRepository;

    @Autowired
    private KeycapRepository keycapRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private UserKeycapRepository userKeycapRepository;

    @Test
    void sameIdempotencyKeyFromTwoThreadsDrawsOnceAndSpendsOnce() throws Exception {
        AppUser user = registerUserWithShards(10);

        List<Object> results = drawConcurrently(user.getId(), "same-key", "same-key");

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(KeycapDrawResponse.class));
        assertThat(results).extracting(result -> ((KeycapDrawResponse) result).drawId()).containsOnly(((KeycapDrawResponse) results.get(0)).drawId());
        assertThat(drawCount(user.getId())).isEqualTo(1);
        assertThat(keycapBoxAccountRepository.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(5);
    }

    @Test
    void differentKeysCannotOverdrawTheWallet() throws Exception {
        AppUser user = registerUserWithShards(5);

        List<Object> results = drawConcurrently(user.getId(), "key-1", "key-2");

        assertThat(results).filteredOn(result -> result instanceof KeycapDrawResponse).hasSize(1);
        assertThat(results).filteredOn(result -> result instanceof ResponseStatusException)
                .singleElement()
                .extracting(result -> ((ResponseStatusException) result).getReason())
                .isEqualTo("KEYCAP_SHARD_INSUFFICIENT");
        assertThat(drawCount(user.getId())).isEqualTo(1);
        assertThat(keycapBoxAccountRepository.findByUserId(user.getId()).orElseThrow().getShardBalance()).isZero();
    }

    /**
     * 무중단 배포 구간에 구 코드가 남긴 진행 중 행의 종별 조각을 읽는 네이티브 쿼리. 테스트 스키마는 엔티티에서
     * 만들어져 옛 컬럼이 없으므로, 운영 DB(정리 SQL C 실행 전) 상태를 흉내 내어 컬럼을 추가한 뒤 확인한다.
     */
    @Test
    void readsLegacyShardCountFromTheOldColumnOnPostgres() {
        AppUser user = registerUserWithShards(0);
        Keycap keycap = keycapRepository.save(Keycap.createFor("DRAW_LEGACY_001", "Legacy", Keycap.Grade.COMMON, 10, 1, null, null, 9));
        UserKeycap legacy = UserKeycap.createOwned(user, keycap, java.time.Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(legacy, "status", UserKeycap.Status.IN_PROGRESS);
        legacy = userKeycapRepository.saveAndFlush(legacy);
        jdbcTemplate.execute("alter table user_keycap add column if not exists shard_count integer not null default 0");
        jdbcTemplate.update("update user_keycap set shard_count = 4 where id = ?", legacy.getId());

        assertThat(userKeycapRepository.findLegacyShardCount(legacy.getId())).isEqualTo(4);
        assertThat(userKeycapRepository.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(user.getId())).isEmpty();
    }

    private List<Object> drawConcurrently(UUID userId, String firstKey, String secondKey) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> drawAfterStart(ready, start, userId, firstKey)),
                    executor.submit(() -> drawAfterStart(ready, start, userId, secondKey))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        }
    }

    private Object drawAfterStart(CountDownLatch ready, CountDownLatch start, UUID userId, String idempotencyKey) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent test did not start");
        }
        try {
            return keycapDrawService.draw(userId, idempotencyKey);
        } catch (ResponseStatusException exception) {
            return exception;
        }
    }

    private long drawCount(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM keycap_draw WHERE user_id = ?", Long.class, userId);
    }

    private AppUser registerUserWithShards(int shards) {
        AppUser user = appUserRepository.save(AppUser.createActive("draw-" + UUID.randomUUID(), null));
        pointAccountRepository.save(PointAccount.createFor(user));
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(user);
        if (shards > 0) {
            wallet.addShards(shards);
        }
        keycapBoxAccountRepository.save(wallet);
        // 뽑기 후보가 두 종 이상이어야 한 번 뽑아 전체 완성 보너스 경로로 빠지지 않는다.
        if (keycapRepository.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX).size() < 2) {
            keycapRepository.save(Keycap.createFor("DRAW_CC_001", "Draw A", Keycap.Grade.COMMON, 10, 1, null, null, 1));
            keycapRepository.save(Keycap.createFor("DRAW_CC_002", "Draw B", Keycap.Grade.COMMON, 10, 1, null, null, 2));
        }
        return user;
    }
}
