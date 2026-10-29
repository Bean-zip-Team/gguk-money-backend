package com.ggukmoney.beanzip.domain.abuse.service;

import com.ggukmoney.beanzip.domain.abuse.service.AbuseDetectionService.Finding;
import com.ggukmoney.beanzip.domain.abuse.service.AbuseRules.Rule;
import com.ggukmoney.beanzip.domain.booster.entity.BoosterGrant;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.ranking.boost.SystemRankingBoostPolicy;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AbuseDetectionServiceIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private AbuseDetectionService abuseDetectionService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private BoosterGrantRepository boosterGrantRepository;

    @Autowired
    private AppConfigRepository appConfigRepository;

    private final Instant windowEnd = Instant.now().truncatedTo(ChronoUnit.HOURS);
    private final Instant windowStart = windowEnd.minus(Duration.ofHours(1));

    @Test
    void flagsMacroPatternsFromTheLastHourOnly() {
        AppUser macro = user("macro");
        AppUser mechanical = user("mechanical");
        AppUser boosted = user("boosted");
        AppUser internal = user("internal");
        AppUser human = user("human");
        AppUser suspended = user("suspended");
        AppUser earlier = user("earlier");
        suspended.suspend();
        appUserRepository.saveAndFlush(suspended);
        configureInternalUsers(internal.getId());

        Instant macroStart = windowStart.plus(Duration.ofMinutes(10));
        // 3분 동안 1초마다 30탭 = 1분에 1,800탭(초당 30회)이 세 번.
        insertBatches(macro, macroStart, 180, Duration.ofSeconds(1), 30);
        insertBatches(internal, macroStart, 180, Duration.ofSeconds(1), 30);
        insertBatches(suspended, macroStart, 180, Duration.ofSeconds(1), 30);
        // 같은 패턴이라도 부스터 2배 중이면 실제 탭은 1분에 900탭이다.
        insertBatches(boosted, macroStart, 180, Duration.ofSeconds(1), 30);
        booster(boosted, macroStart.minusSeconds(5), macroStart.plus(Duration.ofMinutes(4)));
        // 25분 동안 10초마다 정확히 20탭. 느리지만 기계처럼 일정하다.
        insertBatches(mechanical, windowStart.plus(Duration.ofMinutes(20)), 150, Duration.ofSeconds(10), 20);
        insertBatches(human, macroStart, 8, Duration.ofSeconds(3), 12);
        // 창 바로 앞의 매크로는 이번 시간에 보지 않는다.
        insertBatches(earlier, windowStart.minus(Duration.ofMinutes(5)), 180, Duration.ofSeconds(1), 30);

        AbuseDetectionService.Result result = abuseDetectionService.detect(windowStart, windowEnd);

        Map<UUID, Finding> byUser = result.findings().stream()
                .collect(Collectors.toMap(finding -> finding.stats().getUserId(), Function.identity()));
        assertThat(byUser).containsOnlyKeys(macro.getId(), mechanical.getId(), boosted.getId(), internal.getId());

        Finding macroFinding = byUser.get(macro.getId());
        assertThat(macroFinding.rules()).contains(Rule.SUSTAINED_FAST, Rule.FAST);
        assertThat(macroFinding.suspendCandidate()).isTrue();
        assertThat(macroFinding.stats().getFastMinutes()).isEqualTo(3);
        assertThat(macroFinding.stats().getMaxMinuteTaps()).isEqualTo(1_800);
        assertThat(macroFinding.stats().getPhysicalTaps()).isEqualTo(5_400);

        Finding mechanicalFinding = byUser.get(mechanical.getId());
        assertThat(mechanicalFinding.rules()).contains(Rule.MECHANICAL).doesNotContain(Rule.FAST);
        assertThat(mechanicalFinding.suspendCandidate()).isTrue();

        Finding boostedFinding = byUser.get(boosted.getId());
        assertThat(boostedFinding.stats().getMaxMinuteTaps()).isEqualTo(900);
        assertThat(boostedFinding.rules()).contains(Rule.FAST).doesNotContain(Rule.SUSTAINED_FAST);
        assertThat(boostedFinding.suspendCandidate()).isFalse();

        Finding internalFinding = byUser.get(internal.getId());
        assertThat(internalFinding.internal()).isTrue();
        assertThat(internalFinding.rules()).contains(Rule.SUSTAINED_FAST);
        assertThat(internalFinding.suspendCandidate()).isFalse();

        assertThat(result.suspendCandidates()).isEqualTo(2);
        List<String> lines = result.alertMessage().lines().toList();
        assertThat(lines.getFirst()).startsWith("**탭 어뷰징 탐지**").endsWith("4명");
        assertThat(lines.get(1)).startsWith("[정지 후보]");
        assertThat(lines.get(2)).startsWith("[정지 후보]");
        assertThat(result.alertMessage())
                .contains("`" + macro.getId().toString().substring(0, 8) + "`")
                .contains("(내부 계정)")
                // 닉네임(실명)은 외부 채널로 보내지 않는다.
                .doesNotContain("abuse-macro", "abuse-internal");
    }

    @Test
    void splitsBurstsIgnoresBacklogFlushesAndExcludesTheWindowEnd() {
        AppUser paused = user("paused");
        AppUser flushed = user("flushed");
        AppUser later = user("later");
        Instant base = windowStart.plus(Duration.ofMinutes(5));
        // 일정한 15분 구간 두 개 사이에 2분을 쉬었다. 이어 붙이면 30분이 넘어 MECHANICAL 이 된다.
        insertBatches(paused, base, 90, Duration.ofSeconds(10), 20);
        insertBatches(paused, base.plus(Duration.ofSeconds(890 + 120)), 90, Duration.ofSeconds(10), 20);
        // 밀린 배치가 1분에 10건씩 몰려 1,800탭이 된 경우가 세 번. 고르게 들어온 1분이 아니라 빠른 1분으로 세지 않는다.
        for (int minute = 0; minute < 3; minute++) {
            insertBatches(flushed, base.plus(Duration.ofMinutes(minute)), 10, Duration.ofSeconds(1), 180);
        }
        // 창 끝 시각에 들어온 배치는 다음 창의 몫이다.
        insertBatches(later, windowEnd, 1, Duration.ofSeconds(1), 800);

        AbuseDetectionService.Result result = abuseDetectionService.detect(windowStart, windowEnd);

        Map<UUID, Finding> byUser = result.findings().stream()
                .collect(Collectors.toMap(finding -> finding.stats().getUserId(), Function.identity()));
        assertThat(byUser).containsOnlyKeys(paused.getId(), flushed.getId());
        assertThat(byUser.get(paused.getId()).rules()).containsExactly(Rule.REGULAR);
        assertThat(byUser.get(flushed.getId()).rules()).contains(Rule.FAST).doesNotContain(Rule.SUSTAINED_FAST);
        assertThat(byUser.get(flushed.getId()).stats().getFastMinutes()).isZero();
        // 내부 계정 정책이 없으면 알림에 그 사실을 적는다.
        assertThat(result.alertMessage()).contains("내부 계정을 구분하지 못했어요");
    }

    @Test
    void quietHourProducesNoAlert() {
        insertBatches(user("quiet"), windowStart.plus(Duration.ofMinutes(5)), 8, Duration.ofSeconds(3), 12);

        AbuseDetectionService.Result result = abuseDetectionService.detect(windowStart, windowEnd);

        assertThat(result.findings()).isEmpty();
        assertThat(result.alertMessage()).isEmpty();
    }

    private AppUser user(String name) {
        return appUserRepository.saveAndFlush(AppUser.createActive("abuse-" + name, null));
    }

    private void insertBatches(AppUser user, Instant start, int count, Duration interval, int taps) {
        UUID session = UUID.randomUUID();
        List<Object[]> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Timestamp at = Timestamp.from(start.plus(interval.multipliedBy(index)));
            rows.add(new Object[]{UUID.randomUUID(), user.getId(), session, (long) index, taps, taps, "hash-" + index, at, at});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO tap_batch (public_id, user_id, tap_session_id, sequence, submitted_count, accepted_count,
                                       request_hash, bot_suspected, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, false, ?, ?)
                """, rows);
    }

    private void booster(AppUser user, Instant startsAt, Instant expiresAt) {
        boosterGrantRepository.saveAndFlush(BoosterGrant.activate(user, LocalDate.now(), 1, Duration.ofMinutes(5)));
        jdbcTemplate.update("UPDATE booster_grant SET starts_at = ?, expires_at = ? WHERE user_id = ?",
                Timestamp.from(startsAt), Timestamp.from(expiresAt), user.getId());
    }

    private void configureInternalUsers(UUID internalUserId) {
        appConfigRepository.saveAndFlush(AppConfig.createFor(
                SystemRankingBoostPolicy.KEY,
                "{\"enabled\":false,\"internalUserIds\":[\"" + internalUserId
                        + "\"],\"minimumLeaderScore\":1000,\"minIncrement\":200,\"maxIncrement\":500}",
                Instant.now().minusMillis(1)
        ));
    }
}
