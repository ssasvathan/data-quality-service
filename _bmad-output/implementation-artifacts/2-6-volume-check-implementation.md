# Story 2.6: Volume Check Implementation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want the Volume check (#3) to detect anomalous row count changes compared to historical baseline,
so that I can catch unexpected data drops or spikes before downstream consumers are impacted.

## Acceptance Criteria

1. **Given** a dataset DataFrame and historical row counts in `dq_metric_numeric` **When** the Volume check executes **Then** it computes the current row count and the percentage change from the historical mean
2. **And** it writes a `MetricNumeric` with check_type=`volume`, including row_count, pct_change, and stddev metrics
3. **And** it determines pass/warn/fail based on deviation from historical baseline (e.g., >2 stddev = fail)
4. **Given** a dataset with no historical baseline (first run) **When** the Volume check executes **Then** it records the current row count as the baseline and passes (no comparison possible)

## Tasks / Subtasks

- [x] Task 1: Implement `VolumeCheck` strategy class (AC: #1, #2, #3, #4)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java`
  - [x] Implement `DqCheck` with canonical check type `VOLUME` (uppercase, matching `check_config`)
  - [x] In `execute(DatasetContext context)`, validate `context` and `context.getDf()`, preserving per-dataset isolation (return diagnostic metrics/details instead of throwing)
  - [x] Compute current row count from the dataset DataFrame as a `double` metric value
  - [x] Emit `MetricNumeric` metrics for `row_count`, `pct_change`, and `row_count_stddev`
  - [x] Emit one structured `MetricDetail` status payload for classification (`PASS`/`WARN`/`FAIL` or `NOT_RUN`)

- [x] Task 2: Add historical baseline retrieval abstraction for volume (AC: #1, #3, #4)
  - [x] Add `VolumeCheck.BaselineProvider` (or equivalent package-local interface) so classification logic is unit-testable without hard JDBC coupling
  - [x] Add baseline stats model with at minimum: historical mean row count, stddev, sample count
  - [x] Add default/no-op baseline provider (no baseline available path for first-run behavior)
  - [x] Add JDBC baseline query contract for historical `VOLUME/row_count` retrieval (active records only, partition dates older than current run)

- [x] Task 3: Implement baseline comparison and classification policy (AC: #1, #3, #4)
  - [x] Compute `pct_change` against historical mean when baseline exists and mean is non-zero
  - [x] Classify `PASS` when within normal variance, `WARN` for elevated variance, `FAIL` for severe variance
  - [x] Enforce explicit fail threshold: absolute deviation greater than `2 * stddev` (or equivalent deterministic threshold when stddev is 0)
  - [x] Handle first-run/low-history path explicitly: return `PASS` with `baseline_unavailable` reason and still persist current metrics
  - [x] Handle zero-mean baseline safely (no divide-by-zero for percentage change)

- [x] Task 4: Register and verify Volume check via factory tests (AC: #2, #3)
  - [x] Update `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java`
  - [x] Add registration and enablement tests for real `VolumeCheck` implementation
  - [x] Verify `check_config` rows with `VOLUME` enable retrieval for matching dataset patterns
  - [x] Keep existing `CheckFactory` behavior unchanged (SQL matching semantics, active record filtering, unregistered check ignore behavior)

- [x] Task 5: Add dedicated unit tests for Volume check behavior (AC: #1, #2, #3, #4)
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java`
  - [x] Use SparkSession lifecycle pattern consistent with `FreshnessCheckTest`
  - [x] Cover at minimum:
  - [x] `executeComputesRowCountAndPctChangeMetricsWhenBaselinePresent`
  - [x] `executeClassifiesFailWhenDeviationExceedsTwoStdDev`
  - [x] `executeClassifiesWarnWhenDeviationExceedsWarnThresholdButNotFailThreshold`
  - [x] `executeUsesBaselineInitializationModeWhenHistoryUnavailable`
  - [x] `executeHandlesZeroMeanBaselineWithoutDivisionByZero`
  - [x] `executeReturnsNotRunDetailWhenContextOrDataframeMissing`
  - [x] `getCheckTypeReturnsVolume`

- [x] Task 6: Keep scope boundaries aligned with Epic 2 sequencing
  - [x] Do **not** implement `SchemaCheck`, `OpsCheck`, or `DqsScoreCheck` in this story
  - [x] Do **not** wire end-to-end check execution into `DqsJob` beyond existing TODO boundaries (Story 2.10 covers orchestration and batch write)
  - [x] Do **not** modify schema DDL for this story; use existing metric tables and conventions
  - [x] Do **not** add serve/API/dashboard changes for volume logic

### Review Findings

- [x] [Review][Patch] Preserve observed baseline metadata in low-history mode so detail payloads do not erase available `baseline_mean_row_count` and `baseline_count` [`dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java:93`]
- [x] [Review][Patch] Sanitize execution-error detail payloads by storing only exception type instead of raw exception messages [`dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java:174`]
- [x] [Review][Patch] Add regression tests for low-history baseline metadata and sanitized error payload behavior [`dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java:143`]

## Dev Notes

### Story Context and Cross-Story Dependencies

- Story 2.1 established `DqCheck`, `MetricNumeric`, `MetricDetail`, `DatasetContext`, and `CheckFactory`.
- Story 2.2 and 2.4 established dataset discovery + lookup-code enrichment feeding `DatasetContext`.
- Story 2.3 established DataFrame loading and per-dataset failure isolation loop in `DqsJob`.
- Story 2.5 implemented `FreshnessCheck` with a reusable baseline-provider/testing pattern that should be mirrored for volume behavior.
- Story 2.6 should establish a repeatable baseline/statistical template for remaining Tier 1 checks.

### Existing Code to Reuse (Do Not Reinvent)

- `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`

Reuse established patterns:
- Constructor guards with `IllegalArgumentException`
- Try-with-resources for JDBC code
- `PreparedStatement` placeholders for all SQL params
- Per-dataset isolation by returning diagnostic metrics/details rather than crashing execution

### Volume Metric Contract

- `check_type`: `VOLUME`
- Numeric metrics written by this check:
  - `row_count` (current dataset row count)
  - `pct_change` (percent change vs historical mean row count)
  - `row_count_stddev` (historical standard deviation used for thresholding)
- Status detail metric:
  - `detail_type`: `volume_status`
  - `detail_value`: JSON payload including status, reason, row_count, baseline mean/stddev/sample count, and pct_change

Suggested detail payload shape (JSON string in `MetricDetail.detailValue`):

```json
{
  "status": "PASS|WARN|FAIL|NOT_RUN",
  "reason": "baseline_unavailable|within_baseline|above_warn_threshold|above_fail_threshold|missing_context|missing_dataframe|execution_error",
  "row_count": 0.0,
  "pct_change": 0.0,
  "baseline_mean_row_count": 0.0,
  "baseline_stddev_row_count": 0.0,
  "baseline_count": 0
}
```

### Baseline Retrieval SQL Contract (for check implementation)

Use active rows only and dataset-specific historical volume metrics:

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
- `check_type` = `VOLUME`
- `metric_name` = `row_count`
- `partition_date` = current run date from `context.getPartitionDate()`
- `expiry_date` values = `DqsConstants.EXPIRY_SENTINEL`

### Statistical Classification Guidance

Deterministic policy aligned to AC expectations:
- Calculate historical mean and stddev from baseline row counts
- Deviation: `abs(current_row_count - baseline_mean)`
- Thresholds:
  - `warn_threshold = max(stddev, max(1.0, baseline_mean * 0.05))`
  - `fail_threshold = max(2 * stddev, max(1.0, baseline_mean * 0.10))`
- Classification:
  - `deviation <= warn_threshold` -> `PASS`
  - `warn_threshold < deviation <= fail_threshold` -> `WARN`
  - `deviation > fail_threshold` -> `FAIL`

`pct_change` formula:
- If `baseline_mean > 0`: `((current - baseline_mean) / baseline_mean) * 100`
- If `baseline_mean <= 0`: `0.0` and mark reason accordingly (avoid divide-by-zero)

First-run / low-history behavior:
- If no baseline or insufficient sample count (<2), return `PASS` with `baseline_unavailable`
- Still emit `row_count`, `pct_change` (0.0), and `row_count_stddev` (0.0)

### Project Structure Notes

Implementation should stay within `dqs-spark`:

```text
dqs-spark/src/main/java/com/bank/dqs/checks/
  VolumeCheck.java                   <-- NEW
  (optional) VolumeBaseline*.java    <-- NEW, if split out

dqs-spark/src/test/java/com/bank/dqs/checks/
  VolumeCheckTest.java               <-- NEW
  CheckFactoryTest.java              <-- MODIFY
```

Do not create files outside `dqs-spark/`.

### What Not To Do In This Story

- Do not change `DqCheck` interface method signatures.
- Do not change `DatasetContext` fields to support volume logic.
- Do not hardcode sentinel timestamp values; use `DqsConstants.EXPIRY_SENTINEL`.
- Do not write raw source data values to detail metrics.
- Do not add check-specific behavior to serve/API/dashboard components.
- Do not implement final batch-write orchestration here (Story 2.10).

### Testing Commands

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="VolumeCheckTest,CheckFactoryTest"
```

Full regression:

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test
```

### References

- Story definition and ACs: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.6)
- Functional requirements: `_bmad-output/planning-artifacts/prd.md` (FR9, FR32, FR34)
- dqs-spark boundaries and mapping: `_bmad-output/planning-artifacts/architecture.md` (FR8-22 mapping, Project Structure & Boundaries)
- Coding/runtime guardrails: `_bmad-output/project-context.md`
- Existing check and factory contracts:
  - `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
  - `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
  - `dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java`
- Metric schemas and fixture naming precedent:
  - `dqs-serve/src/serve/schema/ddl.sql` (`dq_metric_numeric`, `dq_metric_detail`, `check_config`)
  - `dqs-serve/src/serve/schema/fixtures.sql` (`VOLUME` + `row_count` examples)
- Legacy prototype reference for conceptual variance policy only (do not copy directly):
  - `spark-job/src/main/java/com/dqs/checks/CheckExecutor.java`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `cd dqs-spark && mvn test -Dtest="VolumeCheckTest,CheckFactoryTest"` (intentional RED-phase `testCompile` failure confirming missing `VolumeCheck` implementation contract)
- `cd dqs-spark && mvn test -Dtest="VolumeCheckTest,CheckFactoryTest"` (GREEN: 22 tests passed after implementing `VolumeCheck`)
- `cd dqs-spark && mvn test` (regression: 116 tests passed, 0 failures)
- `cd dqs-spark && mvn test -Dtest="VolumeCheckTest,CheckFactoryTest"` (code review fixes regression: 24 tests passed, 0 failures)
- `cd dqs-spark && mvn test` (code review full regression: 118 tests passed, 0 failures)

### Completion Notes List

- Story context prepared with implementation guardrails, statistical policy, and test plan for Volume check.
- Previous-story learnings (Freshness baseline abstraction/testing pattern) incorporated to reduce implementation risk.
- Scope boundaries explicitly constrained to prevent accidental cross-story implementation.
- ATDD RED-phase acceptance tests created for `VolumeCheck` behavior and `CheckFactory` real-registration coverage.
- RED verification captured expected compile-time failures due to missing `VolumeCheck` class/symbols.
- ATDD checklist generated at `_bmad-output/test-artifacts/atdd-checklist-2-6-volume-check-implementation.md`.
- Implemented `VolumeCheck` with per-dataset isolation and canonical `VOLUME` check type, emitting `row_count`, `pct_change`, `row_count_stddev`, and a structured `volume_status` detail JSON payload.
- Added baseline abstraction components in `VolumeCheck` (`BaselineProvider`, `BaselineStats`, `NoOpBaselineProvider`, `JdbcBaselineProvider`) including the historical row-count JDBC contract using active-record sentinel filtering.
- Implemented deterministic PASS/WARN/FAIL classification with warn/fail thresholds derived from stddev and baseline mean, plus explicit first-run handling (`baseline_unavailable`) and zero-mean safe percentage logic.
- Validated story behavior end-to-end with targeted tests (`VolumeCheckTest`, `CheckFactoryTest`) and full `dqs-spark` regression suite.
- Completed code review triage/fixes: preserved low-history baseline metadata in status detail payloads, sanitized execution error details, and added regression coverage for both behaviors.

### File List

- `_bmad-output/implementation-artifacts/2-6-volume-check-implementation.md` (updated: tasks, status, dev record, file list, changelog)
- `_bmad-output/implementation-artifacts/code-review-2-6-volume-check-implementation.md` (created review report)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated: story status set to done)
- `_bmad-output/test-artifacts/atdd-checklist-2-6-volume-check-implementation.md` (created)
- `dqs-spark/src/main/java/com/bank/dqs/checks/VolumeCheck.java` (created)
- `dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java` (created; RED-phase acceptance tests)
- `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` (updated; added `VolumeCheck` registration/enablement RED tests)

## Change Log

- 2026-04-03: Story created and status set to ready-for-dev.
- 2026-04-03: ATDD workflow completed. RED-phase acceptance tests generated and story status set to in-progress.
- 2026-04-03: Implemented `VolumeCheck`, passed targeted and full regression tests, and advanced story status to review.
- 2026-04-03: Code review completed, findings fixed, tests re-run, and story status moved to done.
