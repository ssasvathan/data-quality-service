# Story 7.3: Lineage, Orphan Detection & Cross-Destination Consistency

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform operator**,
I want lineage checks, orphan detection, and cross-destination consistency validation,
so that data flow integrity is verified beyond individual dataset quality.

## Acceptance Criteria

1. **Given** a dataset whose `dataset_name` contains a `src_sys_nm=` segment
   **When** the Lineage check (`LINEAGE`) executes
   **Then** it identifies potential upstream datasets (other `src_sys_nm` datasets that ran earlier on the same `partition_date`) by querying `v_dq_run_active`
   **And** it determines whether any upstream dataset had `dqs_score < 60.0` (FAIL threshold) or `check_status = 'FAIL'` on the same partition date
   **And** it writes a `MetricNumeric` for `upstream_dataset_count` (total number of upstream datasets found)
   **And** it writes a `MetricNumeric` for `upstream_failed_count` (number of upstream datasets with failing health)
   **And** it writes a `MetricDetail` with `status=WARN` if any upstream dataset is unhealthy and the current dataset has `dqs_score < 75.0`, `status=PASS` if upstreams are healthy or current dataset is healthy regardless, and `reason=no_upstream_datasets` if no upstream peers found

2. **Given** a dataset whose `dataset_name` does NOT contain a `src_sys_nm=` segment
   **When** the Lineage check executes
   **Then** it returns a `MetricDetail` with `status=PASS` and `reason=no_source_system_segment` (graceful skip — lineage requires `src_sys_nm` for peer identification)

3. **Given** a null context or null `dataset_name` is passed to the Lineage check
   **When** the check executes
   **Then** it returns a `MetricDetail` with `status=NOT_RUN` and does NOT propagate an exception

4. **Given** dataset directories tracked in `v_dq_run_active` over the last 30 days
   **When** the Orphan Detection check (`ORPHAN_DETECTION`) executes for a dataset
   **Then** it queries `v_check_config_active` to determine whether any `check_config` rows exist for this dataset (using LIKE pattern match against `dataset_name`)
   **And** it writes a `MetricDetail` with `status=FAIL` and `reason=no_check_config` if no enabled `check_config` entries exist for this dataset (orphaned — tracked in runs but not configured for quality checks)
   **And** it writes a `MetricDetail` with `status=PASS` and `reason=check_config_present` if at least one enabled `check_config` row matches

5. **Given** a null context or null `dataset_name` is passed to the Orphan Detection check
   **When** the check executes
   **Then** it returns a `MetricDetail` with `status=NOT_RUN` and does NOT propagate an exception

6. **Given** datasets replicated to multiple destinations (same `dataset_name` suffix appearing under multiple distinct `parent_path` values in recent `dq_run` history)
   **When** the Cross-Destination Consistency check (`CROSS_DESTINATION`) executes
   **Then** it extracts the `dataset_name` suffix (everything after the first path separator following `partition_date=`) and queries `v_dq_run_active` for all runs with matching suffix on the same `partition_date` across different `parent_path` values
   **And** it writes a `MetricNumeric` for `destination_count` (total distinct parent paths where this dataset appears on this partition date)
   **And** it writes a `MetricNumeric` for `inconsistent_destination_count` (destinations whose `dqs_score` deviates from the mean by more than `SCORE_DEVIATION_THRESHOLD` = 15.0 points)
   **And** it writes a `MetricDetail` with `status=FAIL` if `inconsistent_destination_count > 0`, `status=PASS` if all destinations are consistent or only one destination exists

7. **Given** a dataset that appears in only one destination (no replication detected)
   **When** the Cross-Destination Consistency check executes
   **Then** it returns a `MetricDetail` with `status=PASS` and `reason=single_destination`

8. **Given** a null context or null `dataset_name` is passed to the Cross-Destination Consistency check
   **When** the check executes
   **Then** it returns a `MetricDetail` with `status=NOT_RUN` and does NOT propagate an exception

9. **And** all three checks implement `DqCheck`, are registered in `DqsJob.buildCheckFactory()` AFTER `InferredSlaCheck` and BEFORE `DqsScoreCheck`, and require zero changes to serve/API/dashboard

## Tasks / Subtasks

- [x] Task 1: Create `LineageCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 1–3, 9)
  - [x] Declare `public final class LineageCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "LINEAGE"`
  - [x] Define constants:
    - `METRIC_UPSTREAM_DATASET_COUNT = "upstream_dataset_count"`
    - `METRIC_UPSTREAM_FAILED_COUNT = "upstream_failed_count"`
    - `DETAIL_TYPE_STATUS = "lineage_status"`
    - `UPSTREAM_FAIL_SCORE_THRESHOLD = 60.0` — same as SourceSystemHealthCheck FAIL_THRESHOLD
    - `DOWNSTREAM_WARN_SCORE_THRESHOLD = 75.0` — same as SourceSystemHealthCheck WARN_THRESHOLD
    - `SRC_SYS_SEGMENT_PREFIX = "src_sys_nm="` — consistent with SourceSystemHealthCheck and CorrelationCheck
  - [x] Define inner `@FunctionalInterface LineageStatsProvider` interface:
    - Method: `Optional<LineageStats> getStats(String srcSysNm, LocalDate partitionDate) throws Exception`
  - [x] Define inner `record LineageStats(int upstreamDatasetCount, int upstreamFailedCount)`
  - [x] Define inner `JdbcLineageStatsProvider implements LineageStatsProvider`:
    - Query 1 — total upstream dataset count (other src_sys_nm datasets on same partition_date):
      ```sql
      SELECT COUNT(DISTINCT dataset_name)
      FROM v_dq_run_active
      WHERE dataset_name NOT LIKE ?
        AND partition_date = ?
        AND dqs_score IS NOT NULL
      ```
      NOT LIKE pattern: `'%src_sys_nm=<value>/%'` — excludes the current source system itself
    - Query 2 — count of upstream datasets with failing health:
      ```sql
      SELECT COUNT(DISTINCT dataset_name)
      FROM v_dq_run_active
      WHERE dataset_name NOT LIKE ?
        AND partition_date = ?
        AND (dqs_score < ? OR check_status = 'FAIL')
        AND dqs_score IS NOT NULL
      ```
      Parameters: NOT LIKE pattern, partitionDate, `UPSTREAM_FAIL_SCORE_THRESHOLD`
    - Return `Optional.empty()` if `upstreamCount == 0` (no other datasets on this partition_date)
    - Use `try-with-resources` for Connection, PreparedStatement, ResultSet
  - [x] Define inner `NoOpLineageStatsProvider` (returns `Optional.empty()`)
  - [x] Implement no-arg constructor: `public LineageCheck() { this(new NoOpLineageStatsProvider()); }`
  - [x] Implement testable constructor: `public LineageCheck(LineageStatsProvider statsProvider)`
    - Null check with `IllegalArgumentException`
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - Wrap entire body in try/catch — catch Exception → `metrics.clear()` + return `errorDetail(e)` list
    - Guard null context → return `notRunDetail("missing_context")` list
    - Extract `src_sys_nm` from `context.getDatasetName()` using same static helper as `SourceSystemHealthCheck` (duplicate locally — do NOT create a shared utility)
    - If no `src_sys_nm` found → return PASS + `reason=no_source_system_segment`
    - Call `statsProvider.getStats(srcSysNm, context.getPartitionDate())`
    - If empty → return PASS + `reason=no_upstream_datasets`
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_UPSTREAM_DATASET_COUNT, stats.upstreamDatasetCount())`
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_UPSTREAM_FAILED_COUNT, stats.upstreamFailedCount())`
    - Determine status: `stats.upstreamFailedCount() > 0` AND current dataset context implies degraded quality → emit WARN; else PASS
    - **Note on WARN logic**: Because `execute()` has no access to the current DQS score (DqsScoreCheck runs last and accumulates, the LINEAGE check cannot read it from context), simplify to: if `upstreamFailedCount > 0` → WARN; else PASS
    - Emit `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, payload with status, src_sys_nm, upstream_dataset_count, upstream_failed_count, upstream_fail_threshold)`

- [x] Task 2: Create `OrphanDetectionCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 4–5, 9)
  - [x] Declare `public final class OrphanDetectionCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "ORPHAN_DETECTION"`
  - [x] Define constants:
    - `DETAIL_TYPE_STATUS = "orphan_detection_status"`
    - `REASON_NO_CHECK_CONFIG = "no_check_config"`
    - `REASON_CHECK_CONFIG_PRESENT = "check_config_present"`
  - [x] Define inner `@FunctionalInterface CheckConfigProvider` interface:
    - Method: `boolean hasEnabledCheckConfig(String datasetName) throws Exception`
  - [x] Define inner `JdbcCheckConfigProvider implements CheckConfigProvider`:
    - Query: checks for at least one enabled `check_config` row matching the dataset:
      ```sql
      SELECT COUNT(*)
      FROM v_check_config_active
      WHERE ? LIKE dataset_pattern
        AND enabled = TRUE
      ```
      Parameter: `context.getDatasetName()` (the dataset name is matched against stored `dataset_pattern` — same LIKE reversal pattern as `SlaCountdownCheck` and `InferredSlaCheck`)
    - Return `true` if `COUNT(*) > 0`, `false` otherwise
    - Use `try-with-resources` for Connection, PreparedStatement, ResultSet
  - [x] Define inner `NoOpCheckConfigProvider` (returns `false` — default to orphan assumption for safety; production ALWAYS uses JdbcCheckConfigProvider)
  - [x] Implement no-arg constructor: `public OrphanDetectionCheck() { this(new NoOpCheckConfigProvider()); }`
  - [x] Implement testable constructor: `public OrphanDetectionCheck(CheckConfigProvider configProvider)`
    - Null check with `IllegalArgumentException`
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - Wrap entire body in try/catch — catch Exception → `metrics.clear()` + return `errorDetail(e)` list
    - Guard null context → return `notRunDetail("missing_context")` list
    - Call `configProvider.hasEnabledCheckConfig(context.getDatasetName())`
    - If `false` → return FAIL + `reason=no_check_config`
    - If `true` → return PASS + `reason=check_config_present`

- [x] Task 3: Create `CrossDestinationCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 6–8, 9)
  - [x] Declare `public final class CrossDestinationCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "CROSS_DESTINATION"`
  - [x] Define constants:
    - `METRIC_DESTINATION_COUNT = "destination_count"`
    - `METRIC_INCONSISTENT_DESTINATION_COUNT = "inconsistent_destination_count"`
    - `DETAIL_TYPE_STATUS = "cross_destination_status"`
    - `SCORE_DEVIATION_THRESHOLD = 15.0` — max allowed DQS score deviation from mean across destinations
  - [x] Define inner `@FunctionalInterface DestinationStatsProvider` interface:
    - Method: `Optional<DestinationStats> getStats(String datasetName, LocalDate partitionDate) throws Exception`
  - [x] Define inner `record DestinationStats(int destinationCount, int inconsistentDestinationCount, double meanScore, double maxDeviation)`
  - [x] Define inner `JdbcDestinationStatsProvider implements DestinationStatsProvider`:
    - Strategy: use `dataset_name` directly as an equality match (the `dataset_name` in `dq_run` IS the full path segment e.g. `lob=retail/src_sys_nm=alpha/dataset=sales_daily`). Cross-destination replication means the same `dataset_name` appears under different `parent_path` values in `dq_run`.
    - Query — fetch all DQS scores for this dataset_name on this partition_date across all parent_paths:
      ```sql
      SELECT dqs_score
      FROM v_dq_run_active
      WHERE dataset_name = ?
        AND partition_date = ?
        AND dqs_score IS NOT NULL
      ```
      Parameters: `context.getDatasetName()`, `partitionDate`
    - Collect all `dqs_score` values into a `List<Double>`
    - If list size <= 1 → return `Optional.empty()` (single destination — no cross-destination comparison possible)
    - Compute `meanScore = sum / count`
    - Count `inconsistentCount` = number of scores where `Math.abs(score - meanScore) > SCORE_DEVIATION_THRESHOLD`
    - Compute `maxDeviation = max(Math.abs(score - meanScore))` across all scores
    - Return `Optional.of(new DestinationStats(list.size(), inconsistentCount, meanScore, maxDeviation))`
    - Use `try-with-resources` for Connection, PreparedStatement, ResultSet
  - [x] Define inner `NoOpDestinationStatsProvider` (returns `Optional.empty()`)
  - [x] Implement no-arg constructor: `public CrossDestinationCheck() { this(new NoOpDestinationStatsProvider()); }`
  - [x] Implement testable constructor: `public CrossDestinationCheck(DestinationStatsProvider statsProvider)`
    - Null check with `IllegalArgumentException`
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - Wrap entire body in try/catch — catch Exception → `metrics.clear()` + return `errorDetail(e)` list
    - Guard null context → return `notRunDetail("missing_context")` list
    - Call `statsProvider.getStats(context.getDatasetName(), context.getPartitionDate())`
    - If empty (single or no destination) → return PASS + `reason=single_destination`
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_DESTINATION_COUNT, stats.destinationCount())`
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_INCONSISTENT_DESTINATION_COUNT, stats.inconsistentDestinationCount())`
    - Determine status: `inconsistentCount > 0` → FAIL; else PASS
    - Emit `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, payload with status, destination_count, inconsistent_destination_count, mean_score, max_deviation, threshold)`

- [x] Task 4: Register all three checks in `DqsJob.buildCheckFactory()` (AC: 9)
  - [x] Add AFTER `InferredSlaCheck` and BEFORE `DqsScoreCheck`:
    ```java
    f.register(new LineageCheck(                                               // Tier 3 — Epic 7, Story 7.3
            new LineageCheck.JdbcLineageStatsProvider(
                    () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
    f.register(new OrphanDetectionCheck(                                       // Tier 3 — Epic 7, Story 7.3
            new OrphanDetectionCheck.JdbcCheckConfigProvider(
                    () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
    f.register(new CrossDestinationCheck(                                      // Tier 3 — Epic 7, Story 7.3
            new CrossDestinationCheck.JdbcDestinationStatsProvider(
                    () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
    ```
  - [x] Add imports for all three new classes at the top of `DqsJob.java`
  - [x] Update Javadoc for `buildCheckFactory()` — add Tier 3 Story 7.3 mention and all three new check classes
  - [x] Update the comment listing checks to include all three

- [x] Task 5: Write `LineageCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 1–3)
  - [x] Test: `executeWritesWarnMetricWhenUpstreamDatasetsFailing` — provider returns stats with upstreamDatasetCount=5, upstreamFailedCount=2 → MetricNumeric upstream_failed_count=2, MetricDetail status=WARN
  - [x] Test: `executeWritesPassMetricWhenAllUpstreamsHealthy` — upstreamFailedCount=0 → MetricDetail status=PASS
  - [x] Test: `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName` — dataset without `src_sys_nm=` → MetricDetail reason=no_source_system_segment
  - [x] Test: `executeReturnsPassWhenNoUpstreamDatasetsFound` — provider returns empty → MetricDetail reason=no_upstream_datasets
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeHandlesExceptionGracefully` — provider throws RuntimeException → errorDetail returned, no exception propagation
  - [x] Test: `getCheckTypeReturnsLineage` — `assertEquals("LINEAGE", check.getCheckType())`
  - [x] Test: `executeWarnDetailContainsAllRequiredContextFields` — WARN payload contains src_sys_nm, upstream_dataset_count, upstream_failed_count, upstream_fail_threshold
  - [x] Test: `executeEmitsCorrectMetricNumericsForUpstreamCounts` — verify both MetricNumeric for upstream_dataset_count and upstream_failed_count emitted with correct values
  - [x] **No SparkSession required** — purely JDBC-based; use `DatasetContext` with `null` df

- [x] Task 6: Write `OrphanDetectionCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 4–5)
  - [x] Test: `executeWritesFailMetricWhenNoCheckConfigExists` — provider returns `false` → MetricDetail status=FAIL, reason=no_check_config
  - [x] Test: `executeWritesPassMetricWhenCheckConfigExists` — provider returns `true` → MetricDetail status=PASS, reason=check_config_present
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeHandlesExceptionGracefully` — provider throws RuntimeException → errorDetail returned, no exception propagation
  - [x] Test: `getCheckTypeReturnsOrphanDetection` — `assertEquals("ORPHAN_DETECTION", check.getCheckType())`
  - [x] Test: `executeFailDetailContainsReasonField` — FAIL payload contains `reason=no_check_config`
  - [x] Test: `executePassDetailContainsReasonField` — PASS payload contains `reason=check_config_present`
  - [x] **No SparkSession required** — purely JDBC-based; use `DatasetContext` with `null` df

- [x] Task 7: Write `CrossDestinationCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 6–8)
  - [x] Test: `executeWritesFailMetricWhenDestinationsAreInconsistent` — provider returns stats with destinationCount=3, inconsistentDestinationCount=1, meanScore=80.0, maxDeviation=18.0 → MetricDetail status=FAIL
  - [x] Test: `executeWritesPassMetricWhenAllDestinationsConsistent` — inconsistentDestinationCount=0 → MetricDetail status=PASS
  - [x] Test: `executeReturnsPassWhenSingleDestination` — provider returns empty → MetricDetail reason=single_destination
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeHandlesExceptionGracefully` — provider throws RuntimeException → errorDetail returned, no exception propagation
  - [x] Test: `getCheckTypeReturnsCrossDestination` — `assertEquals("CROSS_DESTINATION", check.getCheckType())`
  - [x] Test: `executeFailDetailContainsAllRequiredContextFields` — FAIL payload contains destination_count, inconsistent_destination_count, mean_score, max_deviation, threshold
  - [x] Test: `executeEmitsCorrectMetricNumericsForDestinationCounts` — verify MetricNumeric for destination_count and inconsistent_destination_count emitted with correct values
  - [x] **No SparkSession required** — purely JDBC-based; use `DatasetContext` with `null` df

## Dev Notes

### Architecture Overview: Three JDBC-Only, No-DataFrame Checks

All three checks are Tier 3 intelligence checks that follow the same JDBC-only pattern as Stories 7.1 and 7.2. **None of these checks calls `context.getDf()`.** All three query Postgres exclusively.

**Execution order in `DqsJob.buildCheckFactory()` after this story:**
```
FreshnessCheck → VolumeCheck → SchemaCheck → OpsCheck →
SlaCountdownCheck → ZeroRowCheck → BreakingChangeCheck →
DistributionCheck → TimestampSanityCheck →
ClassificationWeightedCheck → SourceSystemHealthCheck →
CorrelationCheck → InferredSlaCheck →       ← Story 7.2 (done)
LineageCheck → OrphanDetectionCheck → CrossDestinationCheck →   ← NEW (Story 7.3)
DqsScoreCheck (LAST)
```

### LineageCheck: Upstream Health Influence Detection

**What it detects:** When multiple datasets from the same HDFS zone (`src_sys_nm`) have failed, downstream datasets in OTHER source systems may be impacted. This provides a systemic upstream health signal.

**Design simplification**: True lineage (explicit upstream/downstream mapping) does not exist in the current schema — there is no `lineage` or `pipeline_dependency` table. The check uses a **zone-based proxy**: if OTHER `src_sys_nm` groups on the same `partition_date` show failures, it signals potential upstream issues.

**Algorithm:**
1. Extract `src_sys_nm` from `context.getDatasetName()`
2. Query `v_dq_run_active` for runs on `partitionDate` where `dataset_name NOT LIKE '%src_sys_nm=<value>/%'` (other source systems)
3. Count total other-source-system datasets (`upstream_dataset_count`)
4. Count those with `dqs_score < 60.0 OR check_status = 'FAIL'` (`upstream_failed_count`)
5. If `upstreamFailedCount > 0` → WARN; else PASS

**CRITICAL — No access to current DQS score in execute():** The `DqsScoreCheck` runs LAST and is the only one that computes the final composite score. The `LineageCheck.execute()` method receives only `DatasetContext` which has the raw DataFrame and path metadata — it does NOT have a final DQS score. Simplify the AC-1 logic: if any upstream is failing → WARN (regardless of current dataset's score).

**src_sys_nm extraction — copy the same static helper from `SourceSystemHealthCheck` and `CorrelationCheck`:**
```java
private static Optional<String> extractSrcSysNm(String datasetName) {
    if (datasetName == null) return Optional.empty();
    int start = datasetName.indexOf("src_sys_nm=");
    if (start < 0) return Optional.empty();
    int valueStart = start + "src_sys_nm=".length();
    int end = datasetName.indexOf('/', valueStart);
    String value = end < 0 ? datasetName.substring(valueStart) : datasetName.substring(valueStart, end);
    return value.isBlank() ? Optional.empty() : Optional.of(value);
}
```
**IMPORTANT**: Do NOT extract this to a shared utility class — each check class is self-contained. This is the established project pattern from Stories 7.1 and 7.2.

**SQL for LineageCheck:**
```java
private static final String UPSTREAM_TOTAL_QUERY =
    "SELECT COUNT(DISTINCT dataset_name) " +
    "FROM v_dq_run_active " +
    "WHERE dataset_name NOT LIKE ? " +
    "  AND partition_date = ? " +
    "  AND dqs_score IS NOT NULL";

private static final String UPSTREAM_FAILED_QUERY =
    "SELECT COUNT(DISTINCT dataset_name) " +
    "FROM v_dq_run_active " +
    "WHERE dataset_name NOT LIKE ? " +
    "  AND partition_date = ? " +
    "  AND (dqs_score < ? OR check_status = 'FAIL') " +
    "  AND dqs_score IS NOT NULL";
```

**Parameter binding:**
- Total query: `(notLikePattern, partitionDate)` where `notLikePattern = '%src_sys_nm=' + srcSysNm + '/%'`
- Failed query: `(notLikePattern, partitionDate, UPSTREAM_FAIL_SCORE_THRESHOLD)`

**Metric output — WARN case (upstreamDatasetCount=5, upstreamFailedCount=2, srcSysNm=alpha):**
```
MetricNumeric(LINEAGE, "upstream_dataset_count", 5.0)
MetricNumeric(LINEAGE, "upstream_failed_count", 2.0)
MetricDetail(LINEAGE, "lineage_status",
  {"status":"WARN","src_sys_nm":"alpha","upstream_dataset_count":5,
   "upstream_failed_count":2,"upstream_fail_threshold":60.0})
```

**Metric output — PASS, no upstream data:**
```
MetricDetail(LINEAGE, "lineage_status",
  {"status":"PASS","reason":"no_upstream_datasets"})
```

### OrphanDetectionCheck: Missing Check Config Detection

**What it detects:** Datasets that have DQS run history but no active `check_config` entries — meaning they're being processed by the Spark job but have no quality checks configured. This can happen when datasets are added to HDFS paths without corresponding `check_config` registration.

**Algorithm:**
1. Query `v_check_config_active` using the LIKE reversal pattern: `WHERE ? LIKE dataset_pattern AND enabled = TRUE`
2. If no rows found → FAIL (orphan)
3. If rows found → PASS

**CRITICAL — LIKE reversal pattern**: In `check_config`, `dataset_pattern` is a GLOB/LIKE pattern like `%src_sys_nm=alpha/%`. The query tests whether the SPECIFIC dataset name matches the STORED pattern. This is the same reversal used by `SlaCountdownCheck`, `BreakingChangeCheck`, and `InferredSlaCheck`. The dataset name is passed as the `?` bind parameter and is tested against `dataset_pattern` column value using SQL `LIKE`.

**SQL for OrphanDetectionCheck:**
```java
private static final String CHECK_CONFIG_QUERY =
    "SELECT COUNT(*) " +
    "FROM v_check_config_active " +
    "WHERE ? LIKE dataset_pattern " +
    "  AND enabled = TRUE";
```
Parameter: `context.getDatasetName()`

**Metric output — FAIL (orphan detected):**
```
MetricDetail(ORPHAN_DETECTION, "orphan_detection_status",
  {"status":"FAIL","reason":"no_check_config","dataset_name":"<actual_name>"})
```

**Metric output — PASS:**
```
MetricDetail(ORPHAN_DETECTION, "orphan_detection_status",
  {"status":"PASS","reason":"check_config_present"})
```

**No MetricNumeric emitted by OrphanDetectionCheck** — it's a binary check (present/absent). Only one `MetricDetail` per dataset run.

### CrossDestinationCheck: Multi-Destination Consistency Validation

**What it detects:** The same logical dataset replicated to multiple HDFS parent paths (different consumer zones, environments, or geo regions). If the same `dataset_name` appears across multiple `dq_run` rows on the same `partition_date` (from different Spark job invocations for different parent paths), this check compares their DQS scores and flags significant divergence.

**Algorithm:**
1. Query `v_dq_run_active` for all rows with `dataset_name = context.getDatasetName()` AND `partition_date = partitionDate`
2. Note: `dq_run` has `dataset_name` but not `parent_path` directly exposed — however, the same `dataset_name` appearing multiple times on the same `partition_date` IS the cross-destination signal (due to `UNIQUE(dataset_name, partition_date, rerun_number, expiry_date)`, multiple rows can exist only if rerun_number differs OR they were written in separate transactions before the uniqueness is enforced... **CRITICAL SCHEMA NOTE**: Given the unique constraint `uq_dq_run_dataset_name_partition_date_rerun_number_expiry_date`, multiple active rows for the same dataset+date would only exist if `rerun_number` differs. This means the "multiple destinations" assumption breaks down for the existing schema.

**REVISED DESIGN**: Given the actual schema, `CrossDestinationCheck` should detect whether a dataset has been **rerun** (rerun_number > 0), comparing scores across reruns as a proxy for consistency. OR, use `parent_path` in `dq_run` — but `parent_path` is NOT a column in `dq_run` (only in `dq_orchestration_run`).

**FINAL DESIGN**: For datasets with multiple runs across different `orchestration_run_id` values on the same `partition_date` (possible when jobs for different parent paths run on the same date), query `v_dq_run_active` for `dataset_name = ? AND partition_date = ?` and compare ALL active scores. The unique constraint allows multiple rows only with different `rerun_number` values, so:
- `destination_count` = `COUNT(DISTINCT rerun_number) + 1` or `COUNT(*)` on the query result
- `inconsistent_destination_count` = number of scores deviating from mean by > `SCORE_DEVIATION_THRESHOLD`
- If only 1 row → single_destination → PASS

**SQL for CrossDestinationCheck:**
```java
private static final String DESTINATION_SCORES_QUERY =
    "SELECT dqs_score " +
    "FROM v_dq_run_active " +
    "WHERE dataset_name = ? " +
    "  AND partition_date = ? " +
    "  AND dqs_score IS NOT NULL";
```
Parameters: `context.getDatasetName()`, `java.sql.Date.valueOf(context.getPartitionDate())`

**Metric output — FAIL (inconsistency detected, 3 destinations, 1 inconsistent, mean=80, maxDev=18):**
```
MetricNumeric(CROSS_DESTINATION, "destination_count", 3.0)
MetricNumeric(CROSS_DESTINATION, "inconsistent_destination_count", 1.0)
MetricDetail(CROSS_DESTINATION, "cross_destination_status",
  {"status":"FAIL","destination_count":3,"inconsistent_destination_count":1,
   "mean_score":80.0,"max_deviation":18.0,"threshold":15.0})
```

**Metric output — single destination:**
```
MetricDetail(CROSS_DESTINATION, "cross_destination_status",
  {"status":"PASS","reason":"single_destination"})
```

### `dq_metric_numeric` and `dq_metric_detail` Uniqueness Constraints

```
dq_metric_numeric: UNIQUE(dq_run_id, check_type, metric_name, expiry_date)
dq_metric_detail:  UNIQUE(dq_run_id, check_type, detail_type, expiry_date)
```

**LineageCheck per dataset run:**
- `MetricNumeric(LINEAGE, "upstream_dataset_count")` — 1 row (when upstream datasets exist)
- `MetricNumeric(LINEAGE, "upstream_failed_count")` — 1 row (when upstream datasets exist)
- `MetricDetail(LINEAGE, "lineage_status")` — 1 row always

**OrphanDetectionCheck per dataset run:**
- `MetricDetail(ORPHAN_DETECTION, "orphan_detection_status")` — 1 row always (no MetricNumeric)

**CrossDestinationCheck per dataset run:**
- `MetricNumeric(CROSS_DESTINATION, "destination_count")` — 1 row (when multiple destinations found)
- `MetricNumeric(CROSS_DESTINATION, "inconsistent_destination_count")` — 1 row (when multiple destinations)
- `MetricDetail(CROSS_DESTINATION, "cross_destination_status")` — 1 row always

No constraint violations possible — one entry per metric_name and one per detail_type per `dq_run_id`.

### Java Patterns — Follow Exactly (Same as Stories 7.1 and 7.2)

- **`private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`** — shared static instance per class, one per class, NEVER inside methods
- **`private static final Logger LOG = LoggerFactory.getLogger(ClassName.class)`**
- **`try-with-resources`** for ALL JDBC: Connection, PreparedStatement, ResultSet — NO manual `.close()`
- **`PreparedStatement` with `?` placeholders** — NEVER string concatenation in SQL
- **`metrics.clear()` before adding error detail** in catch block — prevents partial results mixing with error detail
- **`@FunctionalInterface`** on provider interfaces — enables lambda injection in tests without mock frameworks
- **No-arg constructor delegates to JDBC-provider constructor** — never duplicates logic
- **Full generic types**: `List<DqMetric>`, `Map<String, Object>`, `Optional<String>`, `List<Double>`
- **Package**: `com.bank.dqs.checks`
- **No SparkSession dependency** — none of the three checks have any Spark DataFrame operations
- **`public record`** for inner result types
- Use `java.sql.Date.valueOf(localDate)` for JDBC date parameter binding
- **`declare throws SQLException`** on JDBC method signatures

### No-Arg Constructor Pattern (Same as Stories 7.1 and 7.2)

```java
public LineageCheck() {
    this(new NoOpLineageStatsProvider());
}

public LineageCheck(LineageStatsProvider statsProvider) {
    if (statsProvider == null) {
        throw new IllegalArgumentException("statsProvider must not be null");
    }
    this.statsProvider = statsProvider;
}
```

**In `DqsJob.buildCheckFactory()`**, ALWAYS use the JDBC constructor (never no-arg). The no-arg exists only for tests or future no-JDBC scenarios.

### DqsJob Registration — Exact Location

After `InferredSlaCheck` and before `DqsScoreCheck`:

```java
f.register(new InferredSlaCheck(                                           // Tier 3 — Epic 7, Story 7.2
        new InferredSlaCheck.JdbcSlaHistoryProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
f.register(new LineageCheck(                                               // Tier 3 — Epic 7, Story 7.3
        new LineageCheck.JdbcLineageStatsProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
f.register(new OrphanDetectionCheck(                                       // Tier 3 — Epic 7, Story 7.3
        new OrphanDetectionCheck.JdbcCheckConfigProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
f.register(new CrossDestinationCheck(                                      // Tier 3 — Epic 7, Story 7.3
        new CrossDestinationCheck.JdbcDestinationStatsProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
// DqsScoreCheck is registered LAST — always runs after all other checks
f.register(new DqsScoreCheck(ctx -> accumulator));
```

### Test Pattern — No SparkSession Required (Same as Stories 7.1 and 7.2)

None of the three checks has Spark operations. Use the same pattern as `SourceSystemHealthCheckTest`:

```java
private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 4);

private DatasetContext context(String datasetName) {
    return new DatasetContext(
            datasetName,
            "LOB_RETAIL",
            PARTITION_DATE,
            "/prod/data",
            null,         // df=null is valid in test contexts (per DatasetContext Javadoc)
            DatasetContext.FORMAT_PARQUET
    );
}
```

Inject mock providers via lambdas:
```java
// LineageCheck — upstreamDatasetCount=5, upstreamFailedCount=2
LineageCheck check = new LineageCheck(
        (srcSysNm, date) -> Optional.of(new LineageCheck.LineageStats(5, 2))
);

// OrphanDetectionCheck — no check_config
OrphanDetectionCheck check = new OrphanDetectionCheck(
        datasetName -> false
);

// CrossDestinationCheck — 3 destinations, 1 inconsistent
CrossDestinationCheck check = new CrossDestinationCheck(
        (datasetName, date) -> Optional.of(
                new CrossDestinationCheck.DestinationStats(3, 1, 80.0, 18.0))
);
```

### H2 Compatibility Note (Same as Stories 7.1 and 7.2)

Since tests inject mock providers via lambdas, **no H2 database is required** for check unit tests. The JDBC SQL only matters for the `JdbcXxxProvider` classes, which are exercised through integration tests if those exist, but for check unit tests, mock providers suffice.

**Views used by these checks:**
- `v_dq_run_active` — LineageCheck, CrossDestinationCheck
- `v_check_config_active` — OrphanDetectionCheck

All these views already exist. No new schema DDL is required for this story.

### No Schema DDL Changes Required

All three checks use existing views:
- `v_dq_run_active` — already exists (used by multiple checks)
- `v_check_config_active` — already exists (managed by `CheckFactory.getEnabledChecks()`)
- No new tables or views needed

### File Structure — Exact Locations

```
dqs-spark/
  src/main/java/com/bank/dqs/
    checks/
      LineageCheck.java               ← NEW file
      OrphanDetectionCheck.java       ← NEW file
      CrossDestinationCheck.java      ← NEW file
    DqsJob.java                       ← MODIFY: add imports + register 3 checks + update Javadoc
  src/test/java/com/bank/dqs/
    checks/
      LineageCheckTest.java           ← NEW file
      OrphanDetectionCheckTest.java   ← NEW file
      CrossDestinationCheckTest.java  ← NEW file
```

**Do NOT touch:** `CheckFactory.java`, `DqCheck.java`, any existing check files (including 7.1 and 7.2 checks), model files, writer, scanner, serve-layer files, schema DDL/views, fixtures, or any dashboard/API components.

### Anti-Patterns — NEVER Do These

- **NEVER call `context.getDf()`** in any of the three checks — none uses the DataFrame
- **NEVER query raw `dq_run`, `check_config` tables** — always use `v_*_active` views
- **NEVER let exceptions propagate from `execute()`** — entire body wrapped in try/catch; `metrics.clear()` before error detail in catch
- **NEVER add check-type-specific logic to serve/API/dashboard** — generic metric consumption requires zero serve/dashboard changes
- **NEVER hardcode `9999-12-31 23:59:59`** — these checks use views that already filter active records; no sentinel needed in these queries
- **NEVER create a shared utility class** for `extractSrcSysNm` — duplicate locally per class as established in 7.1 and 7.2
- **NEVER use raw types**: always use `List<DqMetric>`, `Optional<String>`, `Map<String, Object>`
- **NEVER return partial metric lists** — on any exception, `metrics.clear()` and return exactly one `errorDetail`
- **NEVER use the no-arg constructor in `DqsJob.buildCheckFactory()`** — always use the JDBC provider constructor in production

### Previous Story Learnings (Epic 7, Stories 7.1 and 7.2)

From Story 7.2 completion (CorrelationCheck + InferredSlaCheck):
- **Test suite was at 264 tests, 0 failures after 7.2** — do not break regressions
- **`DqsJob.buildCheckFactory()` now has all Tier 3 Story 7.1 and 7.2 checks** registered: `ClassificationWeightedCheck`, `SourceSystemHealthCheck`, `CorrelationCheck`, `InferredSlaCheck`, then `DqsScoreCheck` LAST
- **Review findings from 7.2** to avoid repeating: keep ALL public constants actually used in logic; do not declare constants not referenced in code
- **ATDD tests may be pre-generated** — check if test files already exist (`LineageCheckTest.java`, `OrphanDetectionCheckTest.java`, `CrossDestinationCheckTest.java`) before creating; if they exist and have `@Disabled`, remove the annotations and fill them in
- **`@FunctionalInterface` on provider interfaces** enables lambda-injection in tests without Mockito — keep this pattern
- **`record` for inner result types** keeps the code clean and immutable
- **Two-connection vs single-connection pattern**: `LineageCheck.JdbcLineageStatsProvider` runs two queries; use a SINGLE `ConnectionProvider.getConnection()` call and run both queries on the same connection with separate `PreparedStatement`s (both `try-with-resources`). This is the pattern from `InferredSlaCheck.JdbcSlaHistoryProvider`.

From Story 7.1 completion (ClassificationWeightedCheck + SourceSystemHealthCheck):
- **`metrics.clear()` in catch block is mandatory** — prevents partial column results mixing with error detail
- **No-arg constructor delegates to JDBC-provider constructor** — never duplicates logic
- **`notRunDetail` returns a list with single item** — `List.of(new MetricDetail(...))`
- **SparkSession is NOT needed** for JDBC-only checks — no `@BeforeAll`/`@AfterAll` SparkSession lifecycle in test classes
- Dead public constants (declared but never referenced in logic) were flagged in code review — avoid declaring constants that aren't used

From Story 6.1 (`SlaCountdownCheck`) — LIKE reversal pattern used by `OrphanDetectionCheck`:
- Pattern: `WHERE ? LIKE dataset_pattern` — the dataset name binds as `?`, tested against stored `dataset_pattern` glob
- Same pattern used in `InferredSlaCheck.JdbcSlaHistoryProvider` for explicit SLA check

### Comparison With Existing Checks

| Check | DataFrame? | External Deps? | Cross-Dataset? | Complexity |
|---|---|---|---|---|
| SourceSystemHealthCheck | NO | JDBC (v_dq_run_active) | YES (aggregate) | Medium |
| CorrelationCheck | NO | JDBC (v_dq_run_active) | YES (co-degradation) | Medium-High |
| InferredSlaCheck | NO | JDBC (v_dq_metric_numeric + dataset_enrichment) | No (history) | Medium |
| LineageCheck | **NO** | JDBC (v_dq_run_active) | **YES (other source systems)** | Medium |
| OrphanDetectionCheck | **NO** | JDBC (v_check_config_active) | No (single dataset) | Low |
| CrossDestinationCheck | **NO** | JDBC (v_dq_run_active) | **YES (multi-run)** | Low-Medium |

`OrphanDetectionCheck` is the simplest of the three — single query, binary result, no MetricNumeric. `LineageCheck` is similar in complexity to `SourceSystemHealthCheck`. `CrossDestinationCheck` is straightforward — collect scores, compute mean, count deviations.

### References

- Epic 7 Story 7.3 AC: `/home/sas/workspace/data-quality-service/_bmad-output/planning-artifacts/epics/epic-7-tier-3-intelligence-executive-reporting-phase-3.md`
- Project Context — Java rules + anti-patterns: `_bmad-output/project-context.md`
- Architecture — Check extensibility + unidirectional dependency: `_bmad-output/planning-artifacts/architecture.md`
- Previous story (7.2) for JDBC-only patterns: `_bmad-output/implementation-artifacts/7-2-correlation-inferred-sla-checks.md`
- Schema DDL (no new tables needed): `dqs-serve/src/serve/schema/ddl.sql`
- Views (use only v_*_active): `dqs-serve/src/serve/schema/views.sql`

### Review Findings

- [x] [Review][Patch] Remove dead `DOWNSTREAM_WARN_SCORE_THRESHOLD` public constant from `LineageCheck` — constant declared but never referenced in `execute()` logic; misleads callers about WARN threshold behavior [LineageCheck.java:49] — **Fixed**: removed constant; WARN logic correctly uses `upstreamFailedCount > 0` per dev-note simplification
- [x] [Review][Patch] `OrphanDetectionCheck.failDetail()` included `dataset_name` path value in FAIL payload — violates data sensitivity boundary rule ("detail metrics contain field names, never source data values") [OrphanDetectionCheck.java:119] — **Fixed**: removed `dataset_name` field from failDetail payload
- [x] [Review][Patch] Null `context.getDatasetName()` not guarded in `OrphanDetectionCheck.execute()` — null name passed to provider would silently return FAIL (orphan) instead of NOT_RUN [OrphanDetectionCheck.java:94] — **Fixed**: added explicit null guard returning `notRunDetail("missing_dataset_name")`
- [x] [Review][Defer] LIKE wildcard injection in `LineageCheck.JdbcLineageStatsProvider` — `srcSysNm` interpolated directly into LIKE pattern without escaping `%`/`_` wildcards; same pre-existing deferred pattern as 7-2 CorrelationCheck [LineageCheck.java:293] — deferred, pre-existing

## Dev Agent Record

### Implementation Plan

Implemented three JDBC-only Tier 3 checks following the same pattern established in Stories 7.1 and 7.2 (CorrelationCheck, InferredSlaCheck). No SparkSession dependency. All checks are self-contained with inner `@FunctionalInterface` provider interfaces enabling lambda injection in tests.

1. `LineageCheck` — detects upstream health signals by querying `v_dq_run_active` for other source systems on the same partition_date. Uses two-query pattern on a single JDBC connection (same as InferredSlaCheck). Emits MetricNumeric for upstream_dataset_count and upstream_failed_count, plus MetricDetail with WARN/PASS status.

2. `OrphanDetectionCheck` — binary check querying `v_check_config_active` using LIKE reversal pattern. Emits only MetricDetail (no MetricNumeric). Returns FAIL if no enabled check_config entries match, PASS otherwise.

3. `CrossDestinationCheck` — collects all DQS scores for a dataset_name on a partition_date, computes mean, counts deviations exceeding SCORE_DEVIATION_THRESHOLD (15.0). Emits MetricNumeric for destination_count and inconsistent_destination_count, plus MetricDetail with FAIL/PASS status.

All three registered in DqsJob.buildCheckFactory() after InferredSlaCheck and before DqsScoreCheck.

### Completion Notes

- Implemented `LineageCheck.java`, `OrphanDetectionCheck.java`, `CrossDestinationCheck.java` — all in `com.bank.dqs.checks` package
- Registered all three in `DqsJob.buildCheckFactory()` in correct order (after InferredSlaCheck, before DqsScoreCheck)
- Added imports and updated Javadoc in DqsJob.java
- All 27 pre-generated ATDD tests pass (10 LineageCheckTest + 8 OrphanDetectionCheckTest + 9 CrossDestinationCheckTest)
- Full regression suite: 291 tests, 0 failures, 0 skipped (baseline was 264)
- No schema DDL changes required — all three checks use existing views
- No serve/dashboard changes required — check types are transparent to API layer

## File List

- `dqs-spark/src/main/java/com/bank/dqs/checks/LineageCheck.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/checks/OrphanDetectionCheck.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/checks/CrossDestinationCheck.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` (MODIFIED — imports, factory registration, Javadoc)

## Change Log

- 2026-04-04: Implemented LineageCheck, OrphanDetectionCheck, CrossDestinationCheck (Epic 7, Story 7.3). All 27 new tests pass. Total test suite: 291 tests, 0 failures.
