# Story 2.8: Ops Check Implementation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want the Ops check (#24) to detect operational anomalies (null rates, empty strings, data type consistency),
so that I can catch data quality issues at the field level across all columns.

## Acceptance Criteria

1. **Given** a dataset DataFrame **When** the Ops check executes **Then** it computes per-column null rate percentages and writes `MetricNumeric` entries with check_type=`ops`.
2. **And** it computes per-column empty string rates for string columns.
3. **And** it determines pass/warn/fail based on null rate thresholds compared to historical patterns.
4. **And** columns with null rates exceeding historical baseline by a significant margin are flagged in `MetricDetail`.
5. **Given** a dataset with all-null columns **When** the Ops check executes **Then** it flags those columns as critical anomalies in the detail output.

## Tasks / Subtasks

- [x] Task 1: Implement `OpsCheck` strategy class in `dqs-spark` (AC: #1, #2, #3, #4, #5)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/checks/OpsCheck.java`.
  - [x] Implement `DqCheck` with canonical check type `OPS` (uppercase; must match `check_config.check_type`).
  - [x] Guard `execute(DatasetContext context)` for null `context` and null `context.getDf()`, returning deterministic `NOT_RUN` detail payloads.
  - [x] Keep per-dataset failure isolation: catch internal exceptions, return diagnostic detail payload, never throw from `execute()`.

- [x] Task 2: Compute per-column null/empty metrics using deterministic naming (AC: #1, #2)
  - [x] Compute null rate percentage for each column in the DataFrame.
  - [x] Compute empty-string rate percentage for string-compatible columns only (`StringType`).
  - [x] Emit `MetricNumeric` entries with deterministic metric names:
  - [x] `null_rate_pct::<column_path>`
  - [x] `empty_string_rate_pct::<column_path>`
  - [x] Preserve stable ordering in emitted metrics (schema order) for deterministic tests and downstream behavior.

- [x] Task 3: Add historical-baseline comparison for null-rate anomaly classification (AC: #3, #4, #5)
  - [x] Add `OpsCheck.BaselineProvider` abstraction (same pattern as Freshness/Volume/Schema checks).
  - [x] Add default/no-op baseline provider for first-run behavior.
  - [x] Add JDBC baseline provider that reads prior null-rate metrics for the same dataset/column from active records only.
  - [x] Classify per-column null-rate behavior with deterministic thresholds:
  - [x] `PASS` when within baseline or baseline unavailable.
  - [x] `WARN` when above warn threshold.
  - [x] `FAIL` when above fail threshold.
  - [x] Force `FAIL` + `critical` anomaly classification for all-null columns (`null_rate_pct == 100.0`).

- [x] Task 4: Emit detail payloads with explicit contract (AC: #3, #4, #5)
  - [x] Emit `MetricDetail` `ops_status` JSON with summary status, reason, total_columns, flagged_columns, baseline_sample_count.
  - [x] Emit `MetricDetail` `ops_anomalies` JSON array of flagged columns with:
  - [x] column name/path
  - [x] current null rate %
  - [x] baseline mean/stddev/count
  - [x] severity (`warn`/`fail`/`critical`)
  - [x] reason (`above_warn_threshold`, `above_fail_threshold`, `all_null_column`)
  - [x] Include field names and aggregate rates only; never include source row values.
  - [x] Sanitize execution error payloads (`error_type` only, no raw messages with sensitive values).

- [x] Task 5: Register in factory tests and add dedicated unit test suite (AC: #1, #2, #3, #4, #5)
  - [x] Update `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` with real `OpsCheck` registration and enablement coverage.
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/checks/OpsCheckTest.java`.
  - [x] Cover minimum cases:
  - [x] `executeComputesPerColumnNullRateMetrics`
  - [x] `executeComputesEmptyStringRatesForStringColumnsOnly`
  - [x] `executeClassifiesWarnWhenNullRateExceedsWarnThreshold`
  - [x] `executeClassifiesFailWhenNullRateExceedsFailThreshold`
  - [x] `executeFlagsAllNullColumnsAsCriticalAnomalies`
  - [x] `executeUsesBaselineInitializationModeWhenHistoryUnavailable`
  - [x] `executeReturnsNotRunForMissingContextOrDataFrame`
  - [x] `executeSanitizesExecutionErrorPayload`
  - [x] `getCheckTypeReturnsOps`

- [x] Task 6: Maintain epic sequencing boundaries
  - [x] Do **not** implement DQS score logic in this story (Story 2.9 owns this).
  - [x] Do **not** wire full check execution/write orchestration in `DqsJob` (Story 2.10 owns this).
  - [x] Do **not** modify Postgres schema DDL in this story.
  - [x] Do **not** add serve/API/dashboard check-type-specific logic for Ops.

### Review Findings

- [x] [Review][Patch] Avoid duplicate Spark scans by computing row count in the aggregate query instead of separate `df.count()` plus `df.agg(...)` [dqs-spark/src/main/java/com/bank/dqs/checks/OpsCheck.java:102]

## Dev Notes

### Story Context and Cross-Story Dependencies

- Story 2.1 established `DqCheck`, `MetricNumeric`, `MetricDetail`, `DatasetContext`, and `CheckFactory` contracts.
- Stories 2.5/2.6/2.7 established baseline-provider abstraction, deterministic status payloads, and sanitized error handling patterns.
- Story 2.8 should follow the same Tier-1 check shape so Story 2.9 (score computation) can consume outputs consistently.

### Existing Code to Reuse (Do Not Reinvent)

- `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/SchemaCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`

Reuse these implementation patterns from existing checks:
- Constructor argument guards with `IllegalArgumentException`.
- `LinkedHashMap` JSON payload assembly for deterministic field order.
- Try-with-resources + prepared statements in JDBC baseline providers.
- Temporal active-record filters using `DqsConstants.EXPIRY_SENTINEL`.
- Deterministic `NOT_RUN` payloads for missing context/dataframe.

### Architecture Compliance Requirements

- Keep all Ops-check logic in `dqs-spark`; no code in `dqs-serve`, `dqs-orchestrator`, or `dqs-dashboard`.
- Write outputs only to generic metric contracts (`dq_metric_numeric`, `dq_metric_detail`) through `MetricNumeric`/`MetricDetail`.
- Preserve check-type agnostic downstream behavior (no serve/API/dashboard coupling to `OPS` internals).
- Enforce data-sensitivity boundary: aggregate metrics only; no source values persisted.

### Baseline Retrieval Contract (Null-Rate History)

Use active records only and read recent prior samples for each column metric:

```sql
SELECT mn.metric_value
FROM dq_metric_numeric mn
JOIN dq_run r ON r.id = mn.dq_run_id
WHERE r.dataset_name = ?
  AND mn.check_type = ?
  AND mn.metric_name = ?
  AND r.partition_date < ?
  AND r.expiry_date = ?
  AND mn.expiry_date = ?
ORDER BY r.partition_date DESC
LIMIT 30
```

Bindings:
- `dataset_name` = `context.getDatasetName()`
- `check_type` = `OPS`
- `metric_name` = `null_rate_pct::<column_path>`
- `partition_date` = `context.getPartitionDate()`
- `expiry_date` values = `DqsConstants.EXPIRY_SENTINEL`

### Suggested Classification Policy

- Baseline unavailable or insufficient history (`sample_count < 2`): `PASS` with `baseline_unavailable`.
- With baseline:
- `warn_threshold = mean + max(stddev, 1.0)`
- `fail_threshold = mean + max(2 * stddev, 5.0)`
- `WARN` if current > warn threshold and <= fail threshold.
- `FAIL` if current > fail threshold.
- `FAIL` + `critical` reason for all-null columns (`null_rate_pct == 100.0`) regardless of baseline.

### Data Type and Column Handling Guardrails

- Null-rate metrics apply to all top-level columns in DataFrame schema.
- Empty-string rates apply only to string columns.
- Treat empty string detection as trimmed-empty (`trim(value) == ""`) to avoid whitespace-only false negatives.
- Keep numeric percentages in `[0.0, 100.0]`.
- Keep emitted metric names deterministic and parseable (prefix + canonical column path).

### File Structure Requirements

```text
dqs-spark/src/main/java/com/bank/dqs/checks/
  OpsCheck.java                   <-- NEW

dqs-spark/src/test/java/com/bank/dqs/checks/
  OpsCheckTest.java               <-- NEW
  CheckFactoryTest.java           <-- MODIFY
```

No files outside `dqs-spark/` are required for this story.

### Testing Requirements

Targeted suite:

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="OpsCheckTest,CheckFactoryTest"
```

Full regression:

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test
```

### Previous Story Intelligence (2.7) to Apply

- Handle degraded baseline payload scenarios deterministically (avoid accidental `NOT_RUN` for valid drift conditions).
- Add explicit regression tests for every review-driven edge case.
- Keep payload sanitization strict (`error_type` instead of raw exception messages with contextual details).
- Maintain story scope discipline; avoid opportunistic work from later stories.

### Git Intelligence Summary (Recent Pattern)

- Last 5 commits are all Epic 2 quality-check workflow completions (2.5 -> 2.7), confirming stable implementation cadence:
- add one check class + check-specific tests + CheckFactory tests + story/sprint artifact sync.
- Maintain this pattern for Story 2.8 to reduce integration risk.

### Latest Technical Notes

- Architecture and project context currently pin this component to Spark `3.5.0`; keep dependency versions unchanged in this story.
- Spark 4.x introduces additional SQL/type surface area, but this story should stay runtime-compatible with Spark 3.5.x and existing project contracts.
- Focus implementation on stable Spark Dataset/DataFrame APIs already used in existing checks.

### References

- Story source and ACs: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.8).
- Functional requirements: `_bmad-output/planning-artifacts/prd.md` (FR11, FR22, FR32, FR33, FR34, FR57).
- Architecture constraints: `_bmad-output/planning-artifacts/architecture.md` (Core Architectural Decisions, Structure Patterns, Data Boundaries, Data Flow).
- Project guardrails: `_bmad-output/project-context.md` (Spark rules, temporal pattern, data sensitivity boundary, anti-patterns).
- UX extensibility constraints: `_bmad-output/planning-artifacts/ux-design-specification/executive-summary.md` (Extensibility without UI rework).
- Prior story learnings: `_bmad-output/implementation-artifacts/2-7-schema-check-implementation.md`, `_bmad-output/implementation-artifacts/code-review-2-7-schema-check-implementation.md`.

## Dev Agent Record

### Agent Model Used

GPT-5 (Codex)

### Debug Log References

- `cd /home/sas/workspace/data-quality-service/dqs-spark && mvn test -Dtest="OpsCheckTest,CheckFactoryTest"` (intentional RED-phase `testCompile` failure confirming missing `OpsCheck` implementation contract)
- `cd /home/sas/workspace/data-quality-service/dqs-spark && mvn test -Dtest="OpsCheckTest,CheckFactoryTest"` (GREEN-phase validation after implementing `OpsCheck`)
- `cd /home/sas/workspace/data-quality-service/dqs-spark && mvn test` (full regression validation)
- `cd /home/sas/workspace/data-quality-service/dqs-spark && mvn test -Dtest="OpsCheckTest,CheckFactoryTest"` (post-review validation after fixing duplicate DataFrame scan in `OpsCheck`)
- `cd /home/sas/workspace/data-quality-service/dqs-spark && mvn test` (post-review full regression validation)

### Completion Notes List

- Story context generated via `bmad-create-story` workflow.
- ATDD workflow completed via `bmad-testarch-atdd`; RED-phase acceptance tests generated for `OpsCheck`.
- Implemented `OpsCheck` with per-column null-rate and string empty-string-rate metrics, baseline comparison, deterministic classification, anomaly payloads, and sanitized `NOT_RUN` error handling.
- Added `OpsCheck` default/no-op and JDBC baseline providers with active-record historical query filtering.
- Validated targeted suite (`OpsCheckTest`, `CheckFactoryTest`) and full `dqs-spark` regression (`mvn test`) successfully.
- Completed code review and resolved all actionable findings.
- Story status moved to `done`.
- Sprint status synchronized for `2-8-ops-check-implementation: done`.

### File List

- _bmad-output/implementation-artifacts/2-8-ops-check-implementation.md
- _bmad-output/implementation-artifacts/code-review-2-8-ops-check-implementation.md
- _bmad-output/implementation-artifacts/sprint-status.yaml
- dqs-spark/src/main/java/com/bank/dqs/checks/OpsCheck.java
- dqs-spark/src/test/java/com/bank/dqs/checks/OpsCheckTest.java
- dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java

## Change Log

- 2026-04-03: Implemented Ops check end-to-end in `dqs-spark`, validated targeted and full regression tests, and advanced story/sprint status to `review`.
- 2026-04-03: Completed code review, resolved all findings, revalidated targeted and full regression suites, and advanced story/sprint status to `done`.
