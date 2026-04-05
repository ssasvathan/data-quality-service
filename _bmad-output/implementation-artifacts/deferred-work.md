# Deferred Work

Items deferred during code reviews, noted here for future sprint planning.

## Deferred from: code review of 1-2-implement-core-schema-with-temporal-pattern (2026-04-03)

- No Java unit test for `DqsConstants.java` (`dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`) — AC3 only requires the constant to be "documented" in dqs-spark; no Java test task was included in story 1-2 scope. Consider adding a JUnit test for the constant value in a future story or as part of story 1-3 when more Java model content is added.

## Deferred from: code review of 1-3-implement-metric-storage-schema (2026-04-03)

- No indexes on FK columns `dq_run_id` in `dq_metric_numeric` and `dq_metric_detail` — full table scans on join operations. Explicitly deferred to story 1-6 (Implement Active Record Views & Indexing) per story spec.
- `id SERIAL` verification in integration tests checks only `data_type = 'integer'`, not that the column has a sequence-backed DEFAULT. AC1/AC2 require only SERIAL PK (integer type), so this is a test coverage gap, not a defect. Could be enhanced in a future test quality story.

## Deferred from: code review of 4-7-datasetcard-lob-card-component (2026-04-03)

- `previousScore` passthrough to `DqsScoreChip` not explicitly tested in `DatasetCard.test.tsx` — the implementation correctly passes `previousScore={previousScore}` to the chip, but the test mock only captures `score`. Minor coverage gap; no AC requirement for this specific prop passthrough. Consider adding a passthrough assertion test in a future test-quality pass.

## Deferred from: code review of 4-1-mui-theme-design-system-foundation (2026-04-03)

- `getDqsColor` and `getDqsColorLight` have no guard for invalid inputs (`NaN`, `Infinity`, negative scores) — return error color by default. No AC requirement for input validation; consumer components should validate scores before calling. Consider adding guards when score sourcing is finalized in Story 4.6 (DqsScoreChip).
- `fontFamilySans` and `fontFamilyMono` constants in `theme.ts` are not exported — downstream components needing direct font stack references must use `theme.typography.fontFamily` or repeat the string. Not yet a problem; revisit if Story 4.11/4.12 require inline monospace outside of MUI Typography.

## Deferred from: code review of 5-2-mcp-trending-comparison-tools (2026-04-04)

- `_CHECK_TYPES_SQL` in `dqs-serve/src/serve/mcp/tools.py` (line 91) joins `v_dq_metric_numeric_active` using `m.run_id` but the actual view/table column is `dq_run_id`. This pre-existing Story 5.1 bug means `query_failures` will silently return no check types for failed datasets at runtime. Fix in a follow-up patch: change `m.run_id = r.id` to `m.dq_run_id = r.id` in `_CHECK_TYPES_SQL`.

## Deferred from: code review of 6-1-sla-countdown-check (2026-04-04)

- `ZoneId.systemDefault()` timezone portability — `SlaCountdownCheck` computes partition-date midnight using `context.getPartitionDate().atStartOfDay(ZoneId.systemDefault())`. If the Spark cluster JVM timezone differs from the expected Eastern timezone, the elapsed-hours calculation will shift. Pre-existing design decision explicitly specified in story 6.1 spec. Consider switching to a named zone (`ZoneId.of("America/New_York")`) in a future hardening story once the cluster timezone policy is confirmed.

## Deferred from: code review of 6-5-timestamp-sanity-check (2026-04-04)

- Column names containing `.` or spaces create ambiguous metric name keys — the `future_pct.<columnName>` and `stale_pct.<columnName>` metric naming pattern (also used by `DistributionCheck`) is ambiguous when a column name contains a `.` character (e.g., `event.ts` → `future_pct.event.ts`). Pre-existing pattern inherited from DistributionCheck; downstream DB uniqueness constraints are unaffected but metric name parsing by dotted-path splitting would be incorrect. Consider quoting or escaping column names in metric keys when non-alphanumeric column names are encountered.

## Deferred from: code review of 7-2-correlation-inferred-sla-checks (2026-04-04)

- LIKE wildcard (`%`, `_`) special character injection in `JdbcCorrelationStatsProvider.getStats` — the `srcSysNm` value is interpolated directly into a LIKE pattern (`%src_sys_nm=<value>/%`) without escaping JDBC LIKE wildcards. If a source system name contains `%` or `_`, the query would match unintended datasets, inflating correlation counts. Pre-existing pattern consistent with `SourceSystemHealthCheck` (Story 7.1). Consider escaping LIKE wildcards in a future hardening story.

## Deferred from: code review of 7-3-lineage-orphan-detection-cross-destination-consistency (2026-04-04)

- LIKE wildcard (`%`, `_`) special character injection in `LineageCheck.JdbcLineageStatsProvider.getStats` — `srcSysNm` is interpolated directly into `%src_sys_nm=<value>/%` LIKE pattern without escaping. If a source system name contains `%` or `_`, the NOT LIKE query would exclude unintended datasets, skewing upstream counts. Pre-existing pattern consistent with `CorrelationCheck` (Story 7.2) and `SourceSystemHealthCheck` (Story 7.1). Consider escaping LIKE wildcards in a future hardening story.

## Deferred from: code review of 7-4-executive-reporting-suite (2026-04-04)

- `useExecutiveReport` has no `staleTime` configured — may cause unnecessary refetches on every mount for a heavy 3-query executive report endpoint. Pre-existing pattern: no other hooks in `queries.ts` configure explicit `staleTime`. Consider setting a `staleTime` (e.g., 5 minutes) for the executive report hook in a future performance hardening story.
- 'WARN' status datasets are uncounted in `source_system_scores` healthy/critical totals — the source system accountability SQL counts only `PASS` as healthy and `FAIL` as critical; 'WARN' datasets fall through uncounted. Spec-compliant (AC6 only specifies PASS=healthy, FAIL=critical), but creates a gap in accountability totals when degraded datasets exist. Consider adding a `degraded_count` field in a future enhancement.
