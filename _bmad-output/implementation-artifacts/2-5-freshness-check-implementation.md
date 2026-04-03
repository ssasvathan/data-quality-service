# Story 2.5: Freshness Check Implementation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want the Freshness check (#1) to detect staleness by comparing the latest `source_event_timestamp` against expected recency,
so that I can identify datasets receiving stale or delayed data.

## Acceptance Criteria

1. **Given** a dataset DataFrame with a `source_event_timestamp` column **When** the Freshness check executes **Then** it computes the staleness metric (hours since latest timestamp) and writes a `MetricNumeric` with check_type=`freshness`
2. **And** it determines pass/warn/fail based on staleness compared to historical baseline
3. **And** the check implements the `DqCheck` interface and is registered in `CheckFactory`
4. **Given** a dataset with no `source_event_timestamp` column **When** the Freshness check executes **Then** it returns a metric indicating the check could not run, with an appropriate detail message

## Tasks / Subtasks

- [x] Task 1: Implement `FreshnessCheck` strategy class (AC: #1, #2, #3, #4)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java`
  - [x] Implement `DqCheck` with `getCheckType()` returning canonical check type string `FRESHNESS` (uppercase, matching existing `check_config` usage)
  - [x] In `execute(DatasetContext context)`, validate `context` and `context.getDf()`; preserve per-dataset isolation by catching unexpected exceptions and returning diagnostic metrics instead of throwing
  - [x] If `source_event_timestamp` column is missing, return a non-empty metric list containing an explanatory `MetricDetail` (check-not-run path)
  - [x] If column exists, compute latest event timestamp and staleness in hours (`hours_since_update`) as a `MetricNumeric`
  - [x] Clamp negative staleness to `0.0` for future-dated timestamps (clock skew protection)
  - [x] Emit a status detail payload (`PASS` / `WARN` / `FAIL`) derived from baseline comparison logic

- [x] Task 2: Add historical baseline retrieval abstraction for freshness (AC: #2)
  - [x] Add `FreshnessCheck.BaselineProvider` (or equivalent package-local interface) to keep check logic testable without hard-coupling to JDBC
  - [x] Add baseline stats model with at minimum: historical mean hours, stddev hours, sample count
  - [x] Add default/no-op baseline provider used by unit tests and pre-integration paths (returns no baseline -> baseline establishment mode)
  - [x] Define JDBC baseline query contract for future wiring in Story 2.10 (do not wire DqsJob runtime execution yet)

- [x] Task 3: Implement baseline comparison policy (AC: #2)
  - [x] Compare current staleness against historical baseline and classify:
  - [x] `PASS` when staleness is within normal baseline range
  - [x] `WARN` when staleness is above normal but below fail threshold
  - [x] `FAIL` when staleness exceeds fail threshold
  - [x] Handle low-history / first-run case explicitly (no baseline available): produce staleness metric + `PASS`/baseline-initialized status detail
  - [x] Document threshold policy in code comments and tests so downstream checks (2.6-2.9) can follow a consistent statistical pattern

- [x] Task 4: Register and verify Freshness check via factory tests (AC: #3)
  - [x] Update `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java`
  - [x] Add a test registering a real `FreshnessCheck` implementation and verifying it is returned when `check_config` enables `FRESHNESS`
  - [x] Keep existing `CheckFactory` behavior unchanged (SQL pattern matching, active-record filtering, unregistered check ignore behavior)

- [x] Task 5: Add dedicated unit tests for Freshness check behavior (AC: #1, #2, #4)
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/checks/FreshnessCheckTest.java`
  - [x] Use SparkSession test lifecycle (`@BeforeAll`/`@AfterAll`) and deterministic test data with timestamp values
  - [x] Test cases:
  - [x] `executeComputesHoursSinceUpdateMetricWhenTimestampColumnPresent`
  - [x] `executeReturnsCheckNotRunDetailWhenTimestampColumnMissing`
  - [x] `executeClassifiesWarnWhenStalenessExceedsWarnThreshold`
  - [x] `executeClassifiesFailWhenStalenessExceedsFailThreshold`
  - [x] `executeUsesBaselineInitializationModeWhenHistoryUnavailable`
  - [x] `executeClampsNegativeStalenessToZeroForFutureTimestamps`
  - [x] `getCheckTypeReturnsFreshness`
  - [x] Avoid brittle wall-clock assertions: inject a fixed `Clock` into `FreshnessCheck` for deterministic hour calculations

- [x] Task 6: Keep scope boundaries aligned with Epic 2 sequencing
  - [x] Do **not** implement `VolumeCheck`, `SchemaCheck`, `OpsCheck`, or `DqsScoreCheck` in this story
  - [x] Do **not** add `BatchWriter` or final DB write orchestration in this story (Story 2.10)
  - [x] Do **not** introduce serve-layer/API/dashboard changes for check-specific logic (Spark check only)
  - [x] Do **not** modify schema DDL for this story; use existing metric tables and naming conventions

### Review Findings

- [x] [Review][Patch] Compute staleness from Spark `Timestamp` instant to avoid timezone reinterpretation drift [`dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java:144`]
- [x] [Review][Patch] Exclude null baseline samples from JDBC history and statistical aggregation to prevent baseline skew [`dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java:282`]
- [x] [Review][Patch] Add regression coverage for timezone-independent staleness and null-sample baseline handling [`dqs-spark/src/test/java/com/bank/dqs/checks/FreshnessCheckTest.java:206`]

## Dev Notes

### Story Context and Cross-Story Dependencies

- Story 2.1 established `DqCheck`, `MetricNumeric`, `MetricDetail`, `DatasetContext`, and `CheckFactory`.
- Story 2.2 established scanner output and per-dataset isolation conventions.
- Story 2.3 loads DataFrames and leaves TODOs for checks (2.5-2.9) and batch write (2.10).
- Story 2.4 resolved lookup-code enrichment and reinforced deterministic SQL + exception-isolation patterns.
- This story is the first concrete Tier-1 check implementation and becomes the template for 2.6/2.7/2.8/2.9.

### Existing Code to Reuse (Do Not Reinvent)

- `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`

Leverage these patterns:
- Constructor argument guards with `IllegalArgumentException`
- JDBC with `PreparedStatement` and try-with-resources
- Preserve per-dataset failure isolation (return metrics; do not crash Spark job)
- Keep check behavior encapsulated in Spark component only

### Freshness Metric Contract

- `check_type`: `FRESHNESS` (canonical Spark-side constant)
- Primary numeric metric:
  - `metric_name`: `hours_since_update`
  - `metric_value`: non-negative double hours
- Detail metric(s):
  - include structured status payload containing at least classification and reason
  - include check-not-run payload when column is absent or no parseable timestamps exist

Suggested detail payload shape (JSON string in `MetricDetail.detailValue`):

```json
{
  "status": "PASS|WARN|FAIL|NOT_RUN",
  "reason": "baseline_unavailable|within_baseline|above_warn_threshold|above_fail_threshold|missing_source_event_timestamp|no_parseable_timestamps",
  "staleness_hours": 0.0,
  "baseline_mean_hours": 0.0,
  "baseline_stddev_hours": 0.0,
  "baseline_count": 0
}
```

### Baseline Retrieval SQL Contract (for check implementation)

Use active rows only and dataset-specific history:

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
- `check_type` = `FRESHNESS`
- `metric_name` = `hours_since_update`
- `partition_date` = current run date from `context.getPartitionDate()`
- `expiry_date` = `DqsConstants.EXPIRY_SENTINEL`

### Statistical Classification Guidance

Use a deterministic rule for pass/warn/fail:
- Compute historical mean and stddev from retrieved baseline hours
- Recommended thresholds:
  - `warn_threshold = mean + max(stddev, 0.5)`
  - `fail_threshold = mean + max(2 * stddev, 1.0)`
- Classification:
  - `<= warn_threshold` -> `PASS`
  - `> warn_threshold && <= fail_threshold` -> `WARN`
  - `> fail_threshold` -> `FAIL`

If baseline history is missing/too small:
- return `PASS` with reason `baseline_unavailable` (first-run initialization mode)
- still persist current `hours_since_update` metric

### Timezone and Timestamp Handling

- `source_event_timestamp` in source data is UTC (architecture requirement).
- Convert to Spark `timestamp` safely (`to_timestamp`) and compute max valid timestamp.
- Compare against injected `Clock` (UTC-based instant) for deterministic test behavior.
- Store only computed staleness metric; do not persist raw source timestamps.

### Project Structure Notes

Implementation should stay within `dqs-spark`:

```
dqs-spark/src/main/java/com/bank/dqs/checks/
  FreshnessCheck.java                 <-- NEW
  (optional) FreshnessBaseline*.java  <-- NEW, if split out

dqs-spark/src/test/java/com/bank/dqs/checks/
  FreshnessCheckTest.java             <-- NEW
  CheckFactoryTest.java               <-- MODIFY
```

Do not create files outside `dqs-spark/`.

### What Not To Do In This Story

- Do not modify `DqCheck` interface signature (`execute(DatasetContext)` remains unchanged).
- Do not modify `DatasetContext` fields for freshness logic.
- Do not hardcode sentinel timestamp values; use `DqsConstants.EXPIRY_SENTINEL`.
- Do not persist raw source data values in detail metrics (aggregate/diagnostic only).
- Do not add check-specific behavior to serve/API/dashboard components.
- Do not wire end-to-end check execution into `DqsJob` yet beyond existing TODO boundary.

### Testing Commands

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="FreshnessCheckTest,CheckFactoryTest"
```

Full regression:

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test
```

### References

- Story definition and ACs: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.5)
- FR8 definition: `_bmad-output/planning-artifacts/prd.md` (FR8)
- dqs-spark check placement and FR mapping: `_bmad-output/planning-artifacts/architecture.md` (Project Structure & Boundaries, FR8-22 mapping)
- Timezone and freshness rule context: `_bmad-output/planning-artifacts/architecture.md` (Technical Constraints & Dependencies, Timezone note)
- Java guardrails and Spark failure isolation: `_bmad-output/project-context.md` (Language-Specific Rules, Framework-Specific Rules)
- Existing check contracts and factory:
  - `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
  - `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- Metric schemas:
  - `dqs-serve/src/serve/schema/ddl.sql` (`dq_metric_numeric`, `dq_metric_detail`, `check_config`, `dq_run`)
- Existing metric naming fixture precedent (`hours_since_update`, `FRESHNESS`):
  - `dqs-serve/src/serve/schema/fixtures.sql`
- Legacy prototype reference for conceptual behavior only (do not copy directly):
  - `spark-job/src/main/java/com/dqs/checks/CheckExecutor.java`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `cd dqs-spark && mvn test -Dtest="FreshnessCheckTest,CheckFactoryTest"` (initial RED compile failure before implementation; GREEN pass after implementation)
- `cd dqs-spark && mvn test` (full regression pass)
- `cd dqs-spark && mvn test -Dtest="FreshnessCheckTest,CheckFactoryTest"` (code review regression pass after timezone/null-baseline fixes)
- `cd dqs-spark && mvn test` (code review full regression pass)

### Completion Notes List

- Story context prepared with implementation guardrails, baseline policy, and test plan for Freshness check.
- ATDD red-phase tests created for Freshness check behavior and CheckFactory integration coverage.
- ATDD checklist generated at `_bmad-output/test-artifacts/atdd-checklist-2-5-freshness-check-implementation.md`.
- Implemented `FreshnessCheck` with `DqCheck` contract, canonical `FRESHNESS` type, and per-dataset failure isolation.
- Added freshness staleness calculation (`hours_since_update`) from latest `source_event_timestamp`, including future timestamp clamp to `0.0`.
- Added structured freshness status payloads for PASS/WARN/FAIL/NOT_RUN with deterministic baseline-threshold policy.
- Added baseline abstractions (`BaselineProvider`, `BaselineStats`, no-op default provider) plus JDBC baseline provider/query contract for Story 2.10 integration.
- Verified factory compatibility and acceptance behavior through `FreshnessCheckTest` and `CheckFactoryTest`.
- Executed targeted and full Maven test suites successfully with no regressions.

### File List

- `_bmad-output/implementation-artifacts/2-5-freshness-check-implementation.md` (updated: status/tasks/dev record)
- `_bmad-output/implementation-artifacts/code-review-2-5-freshness-check-implementation.md` (created review report)
- `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java` (new implementation)
- `dqs-spark/src/test/java/com/bank/dqs/checks/FreshnessCheckTest.java` (existing ATDD tests executed/passing)
- `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` (existing factory tests including Freshness registration executed/passing)
- `_bmad-output/test-artifacts/atdd-checklist-2-5-freshness-check-implementation.md` (created)

## Change Log

- 2026-04-03: Story created and status set to ready-for-dev.
- 2026-04-03: ATDD workflow completed. RED-phase acceptance tests generated and story status set to in-progress.
- 2026-04-03: Implemented `FreshnessCheck` end-to-end, passed targeted/full tests, and moved story status to review.
- 2026-04-03: Code review completed, timezone/null-baseline issues fixed, tests re-run, and story status moved to done.
