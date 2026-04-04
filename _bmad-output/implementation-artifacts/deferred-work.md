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
