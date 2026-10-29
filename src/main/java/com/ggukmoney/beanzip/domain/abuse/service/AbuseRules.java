package com.ggukmoney.beanzip.domain.abuse.service;

import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository.AbuseStatsProjection;

import java.util.ArrayList;
import java.util.List;

/**
 * 1시간 탭 요약 한 줄을 보고 어떤 탐지 규칙에 걸리는지 판정한다.
 *
 * <p>임계값은 2026-10 운영 탭 기록 전체를 1시간 단위로 잘라 백테스트해서 정했다. 정지 후보 규칙
 * (SUSTAINED_FAST, MECHANICAL)은 BEA-354 에서 확정한 매크로 4계정만 잡았고, 보류 계정과 일반 유저는
 * 하나도 잡지 않았다. 지금은 시범 운영이라 판정 결과를 알림으로만 보낸다.
 */
public final class AbuseRules {

    /** 1분 실제 탭이 이보다 많으면 초당 25회를 넘는다. 손가락으로 오래 낼 수 없는 속도다. */
    public static final long FAST_MINUTE_TAPS = 1_500;
    /** 밀린 배치가 한꺼번에 올라온 1분을 거르기 위해, 배치가 이만큼 고르게 들어온 1분만 센다. */
    public static final int FAST_MINUTE_MIN_BATCHES = 30;
    public static final int FAST_MINUTE_MIN_SPAN_SECONDS = 30;
    /** 빠른 1분이 1시간에 1~2번이면 보류 계정에서도 나왔다. 3번 이상은 매크로 계정에서만 나왔다. */
    static final long SUSTAINED_FAST_MINUTES = 3;
    /** 탭 간격 변동계수가 이보다 작으면 기계처럼 일정하다. 사람 대조군의 최솟값은 0.076 이었다. */
    public static final double REGULAR_CV = 0.05;
    /** 일정한 간격이 이보다 오래 이어지면 정지 후보다. 짧은 구간은 사람에게서도 0.049 가 나왔다. */
    static final double MECHANICAL_BURST_MINUTES = 20;
    /** 사람 대조군의 최고 속도(초당 11.7회)를 1분으로 환산한 값. */
    static final long HUMAN_MAX_MINUTE_TAPS = 702;
    /** 사람 대조군의 최장 연속 구간. */
    static final double LONG_BURST_MINUTES = 40;

    public enum Rule {
        SUSTAINED_FAST(true),
        MECHANICAL(true),
        FAST(false),
        REGULAR(false),
        LONG(false);

        private final boolean suspendCandidate;

        Rule(boolean suspendCandidate) {
            this.suspendCandidate = suspendCandidate;
        }

        public boolean suspendCandidate() {
            return suspendCandidate;
        }
    }

    private AbuseRules() {
    }

    public static List<Rule> evaluate(AbuseStatsProjection stats) {
        List<Rule> rules = new ArrayList<>();
        if (stats.getFastMinutes() >= SUSTAINED_FAST_MINUTES) {
            rules.add(Rule.SUSTAINED_FAST);
        }
        if (stats.getLongestRegularBurstMinutes() != null && stats.getLongestRegularBurstMinutes() > MECHANICAL_BURST_MINUTES) {
            rules.add(Rule.MECHANICAL);
        }
        if (stats.getMaxMinuteTaps() > HUMAN_MAX_MINUTE_TAPS) {
            rules.add(Rule.FAST);
        }
        if (stats.getMinCv() != null && stats.getMinCv() < REGULAR_CV) {
            rules.add(Rule.REGULAR);
        }
        if (stats.getLongestBurstMinutes() > LONG_BURST_MINUTES) {
            rules.add(Rule.LONG);
        }
        return List.copyOf(rules);
    }
}
