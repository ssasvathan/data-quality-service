# Deferred Work

Items deferred during code reviews, noted here for future sprint planning.

## Deferred from: code review of 1-2-implement-core-schema-with-temporal-pattern (2026-04-03)

- No Java unit test for `DqsConstants.java` (`dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`) — AC3 only requires the constant to be "documented" in dqs-spark; no Java test task was included in story 1-2 scope. Consider adding a JUnit test for the constant value in a future story or as part of story 1-3 when more Java model content is added.

## Deferred from: code review of 1-3-implement-metric-storage-schema (2026-04-03)

- No indexes on FK columns `dq_run_id` in `dq_metric_numeric` and `dq_metric_detail` — full table scans on join operations. Explicitly deferred to story 1-6 (Implement Active Record Views & Indexing) per story spec.
- `id SERIAL` verification in integration tests checks only `data_type = 'integer'`, not that the column has a sequence-backed DEFAULT. AC1/AC2 require only SERIAL PK (integer type), so this is a test coverage gap, not a defect. Could be enhanced in a future test quality story.

## Deferred from: code review of 4-1-mui-theme-design-system-foundation (2026-04-03)

- `getDqsColor` and `getDqsColorLight` have no guard for invalid inputs (`NaN`, `Infinity`, negative scores) — return error color by default. No AC requirement for input validation; consumer components should validate scores before calling. Consider adding guards when score sourcing is finalized in Story 4.6 (DqsScoreChip).
- `fontFamilySans` and `fontFamilyMono` constants in `theme.ts` are not exported — downstream components needing direct font stack references must use `theme.typography.fontFamily` or repeat the string. Not yet a problem; revisit if Story 4.11/4.12 require inline monospace outside of MUI Typography.
