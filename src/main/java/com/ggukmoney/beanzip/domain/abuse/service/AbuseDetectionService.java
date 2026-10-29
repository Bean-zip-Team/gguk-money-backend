package com.ggukmoney.beanzip.domain.abuse.service;

import com.ggukmoney.beanzip.domain.abuse.service.AbuseRules.Rule;
import com.ggukmoney.beanzip.domain.ranking.boost.SystemRankingBoostPolicy;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository.AbuseStatsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 한 시간 창의 탭 기록에서 어뷰징 의심 유저를 찾고, 운영자 알림 문구를 만든다.
 *
 * <p>시범 운영 단계라 유저 상태는 바꾸지 않는다. 알림에는 실명인 닉네임을 넣지 않고 사용자 ID 앞 8자리만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class AbuseDetectionService {

    private static final DateTimeFormatter WINDOW_START = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final DateTimeFormatter WINDOW_END = DateTimeFormatter.ofPattern("HH:mm");

    private final TapBatchRepository tapBatchRepository;
    private final SystemRankingBoostPolicy systemRankingBoostPolicy;
    private final ZoneId businessZoneId;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Result detect(Instant windowStart, Instant windowEnd) {
        // 내부 계정 목록은 탭을 친 시각이 아니라 지금 기준으로 본다. 나중에 추가된 내부 계정도 빠지지 않게 하기 위해서다.
        Optional<Set<UUID>> loadedInternalUserIds = systemRankingBoostPolicy.load(clock.instant())
                .map(SystemRankingBoostPolicy.Snapshot::internalUserIds);
        Set<UUID> internalUserIds = loadedInternalUserIds.orElse(Set.of());
        List<Finding> findings = new ArrayList<>();
        for (AbuseStatsProjection stats : tapBatchRepository.findAbuseStats(
                windowStart,
                windowEnd,
                AbuseRules.FAST_MINUTE_TAPS,
                AbuseRules.FAST_MINUTE_MIN_BATCHES,
                AbuseRules.FAST_MINUTE_MIN_SPAN_SECONDS,
                AbuseRules.REGULAR_CV
        )) {
            List<Rule> rules = AbuseRules.evaluate(stats);
            if (!rules.isEmpty()) {
                findings.add(new Finding(stats, internalUserIds.contains(stats.getUserId()), rules));
            }
        }
        return new Result(List.copyOf(findings),
                alertMessage(windowStart, windowEnd, findings, loadedInternalUserIds.isPresent()));
    }

    private String alertMessage(Instant windowStart, Instant windowEnd, List<Finding> findings,
                                boolean internalUsersKnown) {
        if (findings.isEmpty()) {
            return "";
        }
        StringBuilder message = new StringBuilder()
                .append("**탭 어뷰징 탐지** ")
                .append(WINDOW_START.format(windowStart.atZone(businessZoneId)))
                .append('~')
                .append(WINDOW_END.format(windowEnd.atZone(businessZoneId)))
                .append(" KST · ")
                .append(findings.size())
                .append("명");
        if (!internalUsersKnown) {
            // 정책을 못 읽으면 내부 계정도 정지 후보로 보인다. 사람이 보고 잘못 정지하지 않게 알린다.
            message.append("\n내부 계정 목록(ranking.systemBoost.policy)을 읽지 못해 내부 계정을 구분하지 못했어요.");
        }
        // 정지 후보를 먼저 보여 준다.
        findings.stream()
                .sorted((left, right) -> Boolean.compare(right.suspendCandidate(), left.suspendCandidate()))
                .forEach(finding -> message.append('\n').append(line(finding)));
        return message.toString();
    }

    private static String line(Finding finding) {
        AbuseStatsProjection stats = finding.stats();
        return String.format(
                "%s `%s`%s %s · 실제 탭 %,d · 최대 %,d/분 · 빠른 1분 %d번 · 최장 구간 %.1f분 · 최소 cv %s",
                finding.suspendCandidate() ? "[정지 후보]" : "[주의]",
                stats.getUserId().toString().substring(0, 8),
                finding.internal() ? " (내부 계정)" : "",
                finding.rules().stream().map(Enum::name).collect(Collectors.joining(",")),
                stats.getPhysicalTaps(),
                stats.getMaxMinuteTaps(),
                stats.getFastMinutes(),
                stats.getLongestBurstMinutes(),
                stats.getMinCv() == null ? "-" : String.format("%.3f", stats.getMinCv())
        );
    }

    public record Finding(AbuseStatsProjection stats, boolean internal, List<Rule> rules) {

        /** 내부 계정은 매크로 속도로 쳐도 정지 후보로 보지 않는다. 테스트 목적으로 빠르게 치는 경우가 있다. */
        public boolean suspendCandidate() {
            return !internal && rules.stream().anyMatch(Rule::suspendCandidate);
        }
    }

    public record Result(List<Finding> findings, String alertMessage) {

        public long suspendCandidates() {
            return findings.stream().filter(Finding::suspendCandidate).count();
        }
    }
}
