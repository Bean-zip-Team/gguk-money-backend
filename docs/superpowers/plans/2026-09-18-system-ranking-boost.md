# SYSTEM_RANKING_BOOST Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; user has approved implementation.

**Goal:** Implement isolated, audited daily ranking boost with existing notification delivery reuse, disabled until BEA-296 integration.
**Architecture:** Separate ranking boost component, persisted daily plan/audit/outbox, DB transaction locks, direct dynamic config, closed rollout gate. Existing ranking score projection and Redis pipeline remain authoritative.
**Tech Stack:** Java 26, Spring Boot 4.1.0, Jackson 3, PostgreSQL, Redis, Gradle 9.5.1, JUnit/Mockito/Testcontainers.
**Spec:** ../specs/2026-09-18-system-ranking-boost-design.md

## Global Constraints

- No real tap data, points, abuse whitelist, ordinary notification threshold or public API wire-shape changes.
- Config kill switch plus closed BEA-296 integration gate. No production mutation or activation.
- Date uniqueness, DB locks, immutable APPLIED audit and season-historical reward exclusion.

## Execution checklist

### 1. Ranking score decomposition
- [x] Write RankingEntry tests: real score 100 + boost 1,200 = 1,300; next real score 150 = 1,350, zero real = 1,200; reject negative/overflow and nonweekly/closed boosts.
- [x] Run entity test RED, implement rankingBoostScore and boostTo; rerun GREEN.
- [x] Adapt projection comparison to real score and finalizing reset to preserve boost, including zero-real entries; verify integration real-tap/backfill paths.

### 2. Policy, plan, audit, reward contract
- [x] Tests for malformed/missing config failing closed, threshold overrides, rollout gate, schedule bounds, lowest zero-score selection/ties, permanent season exclusion after list removal.
- [x] Implement SystemRankingBoostPolicy, RankingBoostRun, date uniqueness + repository locks, testable rollout/close boundary and reward candidate exclusions.
- [x] Run policy/entity tests RED then GREEN; repository behavior exercised on PostgreSQL.

### 3. Dedicated RANK_CHANGE entrypoint
- [x] Tests: audited 1 -> 2 emits DOWN context and existing type/campaign; natural 1 -> 2 remains silent; rejected consent/cooldown/non-1->2 skip.
- [x] Implement prepareSystemRankingBoost and post-commit existing-dispatch entrypoint; no new notification type.
- [x] Run focused notification tests RED then GREEN.

### 4. Transactional scheduler and recovery
- [x] Integration tests: same day/concurrent ticks one application; immutable schedule/restart; effective score leader; tap counters unchanged; Redis score matches DB; exact leader notification persisted/dispatched.
- [x] Implement scheduler, apply transaction, event reuse and short dispatch claim transaction. No HTTP inside apply transaction. Gate/kill switch rechecked before apply/send.
- [x] Verify skipped/expired days, missing/inactive users, weekly rollover, Redis failure recovery and fail-closed config.

### 5. Delivery audit
- [x] Manual SQL migration + disabled sample policy, operating/BEA-296 integration checklist, TODO close-window boundary.
- [x] Focused tests + full suite + diff check + self-review against all seven user requirements.
- [x] Leave branch changes ready for user; no production deployment or issue write.

## Completion evidence (2026-09-18)

- Final command: `.\gradlew.bat --no-daemon --console plain test` — exit 0, BUILD SUCCESSFUL (3m 59s); 127 suites, 686 tests, 0 failures/errors/skips.
- New boost tests: 17 PostgreSQL/Redis integration tests, 1 actual additive/idempotent manual migration integration test, 4 policy/time/gate tests; existing entity/projection/backfill/notification regressions included in full suite.
- Independent backend review identified a stale leader score/self-count race. The apply transaction now locks and validates the sampled leader before score-dependent decisions. Selected entry retains optimistic protection.
- Leader concurrency regression asserts race actually happened, real score 1600 committed, stale application rolled back, same daily schedule retried with correct global 1 -> 2 and a prepared RANK_CHANGE. Temporarily removing the validation block causes this test to fail; restored code passes.
- Actual rollover/boost concurrency, selected real-score optimistic conflict/retry, Redis failure/reconciliation, kill/consent stopping unclaimed delivery and unknown-send no-blind-retry were exercised.
- `git diff --check` exit 0; new files checked for trailing whitespace with no matches. Original develop remains clean at `79bd6bb`; isolated branch starts at fetched origin/develop `57c109a`.
- All seven approved requirements are implemented/prepared. BEA-296 reward payout integration and confirmed close guard remain explicitly deferred behind the permanently closed production gate, as requested.
- Worktree preserved at `.codex/worktrees/BEA-308`, branch `feat/BEA-308-system-ranking-boost`. Changes are uncommitted; no push, PR, Linear write, runtime DDL, deployment or activation.
