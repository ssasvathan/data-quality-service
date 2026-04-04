# Story 7.2: Correlation & Inferred SLA Checks

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want correlation analysis across related datasets and inferred SLA detection for datasets without explicit SLA configuration,
so that cross-dataset quality patterns and implicit delivery expectations are surfaced.

## Acceptance Criteria

1. **Given** datasets that share a source system or LOB (grouped by `src_sys_nm` extracted from `dataset_name`)
   **When** the Correlation check (`CORRELATION`) executes
   **Then** it identifies correlated quality shifts by querying recent `dq_run` history for datasets sharing the same `src_sys_nm`
   **And** it computes a correlation score: the proportion of datasets in the source system that degraded simultaneously (DQS score dropped by >= configurable threshold compared to prior period)
   **And** it writes a `MetricNumeric` for `correlated_dataset_count` (number of co-degrading datasets in the window)
   **And** it writes a `MetricNumeric` for `correlation_ratio` (proportion of source system datasets that degraded simultaneously, range 0.0–1.0)
   **And** it writes a `MetricDetail` with `status=FAIL` if `correlation_ratio >= 0.5` (majority of source system degraded), `status=WARN` if `correlation_ratio >= 0.25`, `status=PASS` otherwise

2. **Given** a dataset whose `dataset_name` does not contain a `src_sys_nm=` segment
   **When** the Correlation check executes
   **Then** it returns a `MetricDetail` with `status=PASS` and `reason=no_source_system_segment` (graceful skip)

3. **Given** a dataset with 30+ days of historical freshness data (`FRESHNESS` / `hours_since_update` metric) but no configured SLA in `dataset_enrichment`
   **When** the Inferred SLA check (`INFERRED_SLA`) executes
   **Then** it queries the last 30 days of `dq_metric_numeric` rows for `check_type=FRESHNESS` and `metric_name=hours_since_update` for this dataset
   **And** it computes the typical delivery window: mean and standard deviation of `hours_since_update` values
   **And** it writes a `MetricNumeric` for `inferred_sla_hours` (mean + 2*stddev — upper-bound estimate)
   **And** it writes a `MetricNumeric` for `deviation_from_inferred` (current `hours_since_update` minus `inferred_sla_hours`; negative = within window, positive = late)
   **And** it writes a `MetricDetail` with `status=FAIL` if `deviation_from_inferred > 0` (current delivery is later than inferred window), `status=WARN` if deviation is within 20% of `inferred_sla_hours`, `status=PASS` otherwise

4. **Given** a dataset that already has an explicit SLA configured in `dataset_enrichment`
   **When** the Inferred SLA check executes
   **Then** it returns an empty list (check not applicable — explicit SLA takes precedence; let `SlaCountdownCheck` handle it)

5. **Given** a dataset with fewer than 7 days of freshness history (insufficient baseline)
   **When** the Inferred SLA check executes
   **Then** it returns a `MetricDetail` with `status=PASS` and `reason=insufficient_history` (graceful skip — minimum 7 days required to infer a pattern)

6. **Given** a null context or null `dataset_name` is passed to either check
   **When** the check executes
   **Then** it returns a `MetricDetail` with `status=NOT_RUN` and does NOT propagate an exception

7. **And** both checks implement `DqCheck`, are registered in `DqsJob.buildCheckFactory()` AFTER `SourceSystemHealthCheck` and BEFORE `DqsScoreCheck`, and require zero changes to serve/API/dashboard

## Tasks / Subtasks

- [x] Task 1: Create `CorrelationCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 1–2, 6–7)
  - [x] Declare `public final class CorrelationCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "CORRELATION"`
  - [x] Define constants:
    - `METRIC_CORRELATED_DATASET_COUNT = "correlated_dataset_count"`
    - `METRIC_CORRELATION_RATIO = "correlation_ratio"`
    - `DETAIL_TYPE_STATUS = "correlation_status"`
    - `HISTORY_DAYS = 7` — lookback window for co-degradation detection
    - `DEGRADATION_THRESHOLD = 10.0` — DQS score drop (in points) vs. prior period mean to classify as "degraded"
    - `FAIL_RATIO = 0.50` — proportion threshold for FAIL
    - `WARN_RATIO = 0.25` — proportion threshold for WARN
    - `SRC_SYS_SEGMENT_PREFIX = "src_sys_nm="` — consistent with `SourceSystemHealthCheck`
  - [x] Define inner `@FunctionalInterface CorrelationStatsProvider` interface:
    - Method: `Optional<CorrelationStats> getStats(String srcSysNm, LocalDate partitionDate) throws Exception`
  - [x] Define inner `record CorrelationStats(int correlatedDatasetCount, int totalDatasetCount, double correlationRatio)`
  - [x] Define inner `JdbcCorrelationStatsProvider implements CorrelationStatsProvider`:
    - Query 1 — total dataset count in source system:
      ```sql
      SELECT COUNT(DISTINCT dataset_name)
      FROM v_dq_run_active
      WHERE dataset_name LIKE ?
        AND partition_date = ?
        AND dqs_score IS NOT NULL
      ```
      LIKE pattern: `'%src_sys_nm=<value>/%'`
    - Query 2 — correlated (co-degraded) dataset count:
      ```sql
      SELECT COUNT(DISTINCT r.dataset_name)
      FROM v_dq_run_active r
      WHERE r.dataset_name LIKE ?
        AND r.partition_date = ?
        AND r.dqs_score IS NOT NULL
        AND r.dqs_score < (
            SELECT AVG(h.dqs_score) - ?
            FROM v_dq_run_active h
            WHERE h.dataset_name = r.dataset_name
              AND h.partition_date BETWEEN ? AND ?
              AND h.dqs_score IS NOT NULL
        )
      ```
      Parameters: LIKE pattern, current date, `DEGRADATION_THRESHOLD`, `partitionDate.minusDays(HISTORY_DAYS + 1)`, `partitionDate.minusDays(1)`
    - Compute `correlationRatio = correlatedCount / max(totalCount, 1)` (avoid division by zero)
    - Return `Optional.empty()` if `totalCount == 0` (no current-day data for this source system)
    - Use `try-with-resources` for Connection, PreparedStatement, ResultSet
  - [x] Define inner `NoOpCorrelationStatsProvider` (returns `Optional.empty()`)
  - [x] Implement no-arg constructor: `public CorrelationCheck() { this(new NoOpCorrelationStatsProvider()); }`
  - [x] Implement testable constructor: `public CorrelationCheck(CorrelationStatsProvider statsProvider)`
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - Wrap entire body in try/catch — catch Exception → `metrics.clear()` + return `errorDetail(e)` list
    - Guard null context → return `notRunDetail("missing_context")` list
    - Extract `src_sys_nm` from `context.getDatasetName()` — use the same static extraction logic as `SourceSystemHealthCheck` (see Dev Notes for exact method)
    - If no `src_sys_nm` found → return PASS + reason=no_source_system_segment
    - Call `statsProvider.getStats(srcSysNm, context.getPartitionDate())`
    - If empty → return PASS + reason=no_current_day_data
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_CORRELATED_DATASET_COUNT, stats.correlatedDatasetCount())`
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_CORRELATION_RATIO, stats.correlationRatio())`
    - Determine status: `ratio >= FAIL_RATIO` → FAIL; `ratio >= WARN_RATIO` → WARN; else PASS
    - Emit `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, payload with status, src_sys_nm, correlated_dataset_count, total_dataset_count, correlation_ratio, history_days, threshold_fail, threshold_warn)`

- [x] Task 2: Create `InferredSlaCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 3–6)
  - [x] Declare `public final class InferredSlaCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "INFERRED_SLA"`
  - [x] Define constants:
    - `METRIC_INFERRED_SLA_HOURS = "inferred_sla_hours"`
    - `METRIC_DEVIATION_FROM_INFERRED = "deviation_from_inferred"`
    - `DETAIL_TYPE_STATUS = "inferred_sla_status"`
    - `HISTORY_DAYS = 30` — lookback window for SLA inference
    - `MIN_DATA_POINTS = 7` — minimum history required to infer a pattern (graceful skip below this)
    - `WARN_DEVIATION_RATIO = 0.20` — deviation within 20% of inferred SLA → WARN (approaching breach)
  - [x] Define inner `@FunctionalInterface SlaHistoryProvider` interface:
    - Method: `Optional<SlaHistory> getHistory(DatasetContext ctx) throws Exception`
  - [x] Define inner `record SlaHistory(List<Double> hoursHistory, boolean hasExplicitSla)`
    - `hoursHistory`: list of `hours_since_update` values from historical `dq_metric_numeric` rows (ordered by `partition_date ASC`, most recent = current day's value at index `size()-1`)
    - `hasExplicitSla`: true if `dataset_enrichment` has a non-null `sla_hours` for this dataset
  - [x] Define inner `JdbcSlaHistoryProvider implements SlaHistoryProvider`:
    - Query 1 — check for explicit SLA in `dataset_enrichment`:
      ```sql
      SELECT sla_hours FROM v_dataset_enrichment_active
      WHERE ? LIKE dataset_pattern AND sla_hours IS NOT NULL
      ORDER BY id ASC LIMIT 1
      ```
      (same pattern as `SlaCountdownCheck.JdbcSlaProvider`) — if row found, `hasExplicitSla=true`
    - Query 2 — fetch freshness history from `dq_metric_numeric` (joined to `dq_run`):
      ```sql
      SELECT mn.metric_value
      FROM v_dq_metric_numeric_active mn
      JOIN v_dq_run_active r ON mn.dq_run_id = r.id
      WHERE r.dataset_name = ?
        AND mn.check_type = 'FRESHNESS'
        AND mn.metric_name = 'hours_since_update'
        AND r.partition_date BETWEEN ? AND ?
        AND mn.metric_value IS NOT NULL
      ORDER BY r.partition_date ASC
      ```
      Parameters: `context.getDatasetName()`, `partitionDate.minusDays(HISTORY_DAYS)`, `partitionDate`
    - Return `Optional.of(new SlaHistory(values, hasExplicitSla))`
    - Return `Optional.empty()` only on connection failure (let caller handle empty list case)
  - [x] Define inner `NoOpSlaHistoryProvider` (returns `Optional.empty()`)
  - [x] Implement no-arg constructor: `public InferredSlaCheck() { this(new NoOpSlaHistoryProvider()); }`
  - [x] Implement testable constructor: `public InferredSlaCheck(SlaHistoryProvider historyProvider)`
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - Wrap entire body in try/catch — catch Exception → `metrics.clear()` + return `errorDetail(e)` list
    - Guard null context → return `notRunDetail("missing_context")` list
    - Call `historyProvider.getHistory(context)`
    - If empty (provider returned empty) → return empty list (no data available, not even graceful skip metric)
    - If `history.hasExplicitSla()` → return empty list (SlaCountdownCheck handles this dataset)
    - If `history.hoursHistory().size() < MIN_DATA_POINTS` → return PASS + reason=insufficient_history
    - Compute statistics from historical values (all except current/last entry):
      - `mean = sum / n`
      - `stddev = sqrt(sum(sq_diff) / n)` (population stddev; no Spark needed — pure Java math)
      - `inferredSlaHours = mean + 2.0 * stddev`
    - The current delivery value is the LAST entry in `hoursHistory` (most recent partition_date):
      - `currentHours = history.hoursHistory().get(history.hoursHistory().size() - 1)`
    - `deviationFromInferred = currentHours - inferredSlaHours`
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_INFERRED_SLA_HOURS, inferredSlaHours)`
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_DEVIATION_FROM_INFERRED, deviationFromInferred)`
    - Determine status:
      - `deviationFromInferred > 0` → FAIL (current delivery later than inferred window)
      - `deviationFromInferred > -inferredSlaHours * WARN_DEVIATION_RATIO` → WARN (approaching breach)
      - else PASS
    - Emit `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, payload with status, current_hours, inferred_sla_hours, deviation_from_inferred, mean_hours, stddev_hours, data_points)`

- [x] Task 3: Register both checks in `DqsJob.buildCheckFactory()` (AC: 7)
  - [x] Add AFTER `SourceSystemHealthCheck` and BEFORE `DqsScoreCheck`:
    ```java
    f.register(new CorrelationCheck(                                           // Tier 3 — Epic 7, Story 7.2
            new CorrelationCheck.JdbcCorrelationStatsProvider(
                    () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
    f.register(new InferredSlaCheck(                                           // Tier 3 — Epic 7, Story 7.2
            new InferredSlaCheck.JdbcSlaHistoryProvider(
                    () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
    ```
  - [x] Add imports for both new classes at the top of `DqsJob.java`
  - [x] Update Javadoc for `buildCheckFactory()` — add Tier 3 Story 7.2 mention and both new check classes
  - [x] Update the comment listing checks to include CorrelationCheck and InferredSlaCheck

- [x] Task 4: Write `CorrelationCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 1–2, 6)
  - [x] Test: `executeWritesFailMetricsWhenMajorityOfSourceSystemDegraded` — provider returns stats with correlationRatio=0.6, correlatedCount=3, total=5 → MetricNumeric correlation_ratio=0.6, MetricDetail status=FAIL
  - [x] Test: `executeWritesWarnMetricsWhenMinorityDegraded` — ratio=0.33 → status=WARN
  - [x] Test: `executeWritesPassMetricsWhenNoDegradationCorrelation` — ratio=0.10 → status=PASS
  - [x] Test: `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName` — dataset without `src_sys_nm=` → MetricDetail reason=no_source_system_segment
  - [x] Test: `executeReturnsPassWhenNoCurrentDayData` — provider returns empty → MetricDetail reason=no_current_day_data
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeHandlesExceptionGracefully` — provider throws RuntimeException → errorDetail returned, no exception propagation
  - [x] Test: `getCheckTypeReturnsCorrelation` — `assertEquals("CORRELATION", check.getCheckType())`
  - [x] Test: `executeFailDetailContainsAllRequiredContextFields` — FAIL payload contains src_sys_nm, correlated_dataset_count, total_dataset_count, correlation_ratio, history_days, threshold_fail, threshold_warn
  - [x] **No SparkSession required** — purely JDBC-based; use `DatasetContext` with `null` df (safe per Javadoc)

- [x] Task 5: Write `InferredSlaCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 3–6)
  - [x] Test: `executeWritesFailMetricsWhenCurrentDeliveryLaterThanInferredWindow` — 15 history values with mean=10.0, stddev=2.0 → inferredSlaHours=14.0; current=16.0 (last in list) → deviation=2.0 > 0 → status=FAIL
  - [x] Test: `executeWritesWarnMetricsWhenApproachingInferredWindow` — current delivery just below inferredSlaHours but within 20% → status=WARN
  - [x] Test: `executeWritesPassMetricsWhenDeliveryWellWithinInferredWindow` — current=5.0 with inferredSlaHours=14.0 → status=PASS
  - [x] Test: `executeReturnsEmptyWhenExplicitSlaIsConfigured` — `hasExplicitSla=true` → empty list returned (SlaCountdownCheck handles this)
  - [x] Test: `executeReturnsPassWhenInsufficientHistory` — fewer than 7 data points → MetricDetail reason=insufficient_history
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeHandlesExceptionGracefully` — provider throws RuntimeException → errorDetail, no exception propagation
  - [x] Test: `getCheckTypeReturnsInferredSla` — `assertEquals("INFERRED_SLA", check.getCheckType())`
  - [x] Test: `executePassDetailContainsRequiredStatisticsFields` — PASS payload contains current_hours, inferred_sla_hours, deviation_from_inferred, mean_hours, stddev_hours, data_points
  - [x] Test: `executeEmitsCorrectMetricNumericsForInferredWindow` — verify MetricNumeric for `inferred_sla_hours` and `deviation_from_inferred` are emitted with correct values
  - [x] **No SparkSession required** — purely JDBC-based; use null-df `DatasetContext`

## Dev Notes

### Architecture Overview: Two JDBC-Only, No-DataFrame Checks

Both `CorrelationCheck` and `InferredSlaCheck` are Tier 3 intelligence checks that follow the same JDBC-only pattern as `ClassificationWeightedCheck` and `SourceSystemHealthCheck` (Story 7.1). **Neither check calls `context.getDf()`.** Both query Postgres exclusively.

**Execution order in `DqsJob.buildCheckFactory()`:**
```
FreshnessCheck → VolumeCheck → SchemaCheck → OpsCheck →
SlaCountdownCheck → ZeroRowCheck → BreakingChangeCheck →
DistributionCheck → TimestampSanityCheck →
ClassificationWeightedCheck → SourceSystemHealthCheck →
CorrelationCheck → InferredSlaCheck →       ← NEW (Story 7.2)
DqsScoreCheck (LAST)
```

### CorrelationCheck: Cross-Dataset Co-Degradation Detection

**What it detects:** When multiple datasets from the same source system simultaneously experience DQS score drops compared to their own recent baseline, this signals a systemic source system problem (not isolated dataset issues).

**Algorithm:**
1. Find all datasets sharing `src_sys_nm` that have runs on `partitionDate` (current day)
2. For each such dataset, compare its current DQS score against its own 7-day prior mean
3. Count how many degraded by >= `DEGRADATION_THRESHOLD` (10 points)
4. `correlationRatio = degradedCount / totalCount`

**CRITICAL:** The correlation is per-dataset scoped — it runs for every dataset in the source system on every DQS run day. When `src_sys_nm=alpha` has 5 datasets and 3 degraded simultaneously, each of those 5 datasets will report `correlated_dataset_count=3, total_dataset_count=5, correlation_ratio=0.60`. This is intentional — it surfaces the systemic problem from each dataset's perspective.

**src_sys_nm extraction — copy the static helper from `SourceSystemHealthCheck`:**
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
(Note: this logic is duplicated per class — do NOT introduce a shared utility class; self-containment per class is the project pattern.)

**SQL for co-degradation — correlated dataset count:**
```java
private static final String CORRELATED_COUNT_QUERY =
    "SELECT COUNT(DISTINCT r.dataset_name) " +
    "FROM v_dq_run_active r " +
    "WHERE r.dataset_name LIKE ? " +
    "  AND r.partition_date = ? " +
    "  AND r.dqs_score IS NOT NULL " +
    "  AND r.dqs_score < (" +
    "      SELECT AVG(h.dqs_score) - ? " +
    "      FROM v_dq_run_active h " +
    "      WHERE h.dataset_name = r.dataset_name " +
    "        AND h.partition_date BETWEEN ? AND ? " +
    "        AND h.dqs_score IS NOT NULL" +
    "  )";
```

**SQL for total dataset count (current day):**
```java
private static final String TOTAL_COUNT_QUERY =
    "SELECT COUNT(DISTINCT dataset_name) " +
    "FROM v_dq_run_active " +
    "WHERE dataset_name LIKE ? " +
    "  AND partition_date = ? " +
    "  AND dqs_score IS NOT NULL";
```

**Parameter binding order:**
- Total count query: `(likePattern, partitionDate)`
- Correlated count query: `(likePattern, partitionDate, DEGRADATION_THRESHOLD, partitionDate.minusDays(HISTORY_DAYS + 1), partitionDate.minusDays(1))`

**NOTE on `partitionDate.minusDays(HISTORY_DAYS + 1)` vs `minusDays(HISTORY_DAYS)`:** The prior-period baseline excludes the current day itself. We look at days T-8 through T-1 (7 days of prior data) to compute the baseline mean for comparison against day T.

**LIKE pattern:** `'%src_sys_nm=' + srcSysNm + '/%'` — same as `SourceSystemHealthCheck`.

**Return `Optional.empty()`** from `JdbcCorrelationStatsProvider` when `totalCount == 0` (no current-day runs for this source system). Map to `reason=no_current_day_data` in `execute()`.

### InferredSlaCheck: Statistical SLA Inference from Freshness History

**What it detects:** Datasets that have never had an explicit SLA configured may still have a de-facto delivery expectation based on historical arrival patterns. This check infers the "normal" delivery window from 30 days of `hours_since_update` freshness metrics.

**Algorithm:**
1. Check if dataset has an explicit SLA in `dataset_enrichment` — if yes, return empty list
2. Fetch last 30 days of `FRESHNESS` / `hours_since_update` metrics from `dq_metric_numeric`
3. If fewer than 7 data points, return PASS + `reason=insufficient_history`
4. Compute mean and population stddev of the history
5. `inferredSlaHours = mean + 2.0 * stddev` — covers ~95% of historical deliveries
6. Current delivery = last value in the history list (most recent `partition_date`)
7. `deviationFromInferred = currentHours - inferredSlaHours`
8. Status: positive deviation → FAIL; within 20% of inferred SLA → WARN; else PASS

**Critical: WARN threshold direction:**
- `WARN` applies when: `currentHours > inferredSlaHours * (1.0 - WARN_DEVIATION_RATIO)` AND `currentHours <= inferredSlaHours`
- Equivalently: `deviationFromInferred > -inferredSlaHours * WARN_DEVIATION_RATIO`
- Simplified check logic:
  ```java
  if (deviationFromInferred > 0.0) {
      status = STATUS_FAIL;   // already late
  } else if (deviationFromInferred > -inferredSlaHours * WARN_DEVIATION_RATIO) {
      status = STATUS_WARN;   // approaching (within 20% of inferred window)
  } else {
      status = STATUS_PASS;
  }
  ```

**Statistics computation (pure Java, no Spark):**
```java
private static double computeMean(List<Double> values) {
    return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
}

private static double computeStdDev(List<Double> values, double mean) {
    double sumSqDiff = values.stream()
            .mapToDouble(v -> (v - mean) * (v - mean))
            .sum();
    return Math.sqrt(sumSqDiff / values.size());
}
```

**SQL for freshness history:**
```java
private static final String FRESHNESS_HISTORY_QUERY =
    "SELECT mn.metric_value " +
    "FROM v_dq_metric_numeric_active mn " +
    "JOIN v_dq_run_active r ON mn.dq_run_id = r.id " +
    "WHERE r.dataset_name = ? " +
    "  AND mn.check_type = 'FRESHNESS' " +
    "  AND mn.metric_name = 'hours_since_update' " +
    "  AND r.partition_date BETWEEN ? AND ? " +
    "  AND mn.metric_value IS NOT NULL " +
    "ORDER BY r.partition_date ASC";
```

**SQL for explicit SLA check:**
```java
private static final String EXPLICIT_SLA_QUERY =
    "SELECT sla_hours FROM v_dataset_enrichment_active " +
    "WHERE ? LIKE dataset_pattern AND sla_hours IS NOT NULL " +
    "ORDER BY id ASC LIMIT 1";
```
(exact copy from `SlaCountdownCheck.JdbcSlaProvider`)

**Why explicit SLA → empty list (not PASS):** An empty list signals "check not applicable" to `CheckFactory`. This is the same pattern as `SlaCountdownCheck` when no SLA is configured — return empty instead of a do-nothing metric. The `dq_metric_numeric` and `dq_metric_detail` uniqueness constraints don't get violated because no rows are inserted.

**`SlaHistory` record design — `hasExplicitSla` is a flag inside the record:**
```java
public record SlaHistory(List<Double> hoursHistory, boolean hasExplicitSla) {}
```
The provider returns both in a single object so `execute()` makes one provider call. The two queries inside `JdbcSlaHistoryProvider` can run sequentially in the same JDBC connection.

**Two connections vs one connection in JdbcSlaHistoryProvider:** Use a single `ConnectionProvider.getConnection()` call, run both queries on the same connection with separate `PreparedStatement`s (both wrapped in try-with-resources). This is the pattern used by `SourceSystemHealthCheck.JdbcSourceSystemStatsProvider`.

### Metric Output Structure

**CorrelationCheck — FAIL case (ratio=0.6, correlated=3, total=5, src_sys_nm=alpha):**
```
MetricNumeric(CORRELATION, "correlated_dataset_count", 3.0)
MetricNumeric(CORRELATION, "correlation_ratio", 0.6)
MetricDetail(CORRELATION, "correlation_status",
  {"status":"FAIL","src_sys_nm":"alpha","correlated_dataset_count":3,"total_dataset_count":5,
   "correlation_ratio":0.6,"history_days":7,"threshold_fail":0.5,"threshold_warn":0.25})
```

**CorrelationCheck — no src_sys_nm:**
```
MetricDetail(CORRELATION, "correlation_status",
  {"status":"PASS","reason":"no_source_system_segment"})
```

**CorrelationCheck — no current-day data:**
```
MetricDetail(CORRELATION, "correlation_status",
  {"status":"PASS","reason":"no_current_day_data"})
```

**InferredSlaCheck — FAIL case (mean=10.0, stddev=2.0 → inferredSlaHours=14.0; current=16.0):**
```
MetricNumeric(INFERRED_SLA, "inferred_sla_hours", 14.0)
MetricNumeric(INFERRED_SLA, "deviation_from_inferred", 2.0)
MetricDetail(INFERRED_SLA, "inferred_sla_status",
  {"status":"FAIL","current_hours":16.0,"inferred_sla_hours":14.0,
   "deviation_from_inferred":2.0,"mean_hours":10.0,"stddev_hours":2.0,"data_points":15})
```

**InferredSlaCheck — explicit SLA configured:**
- Returns empty list (no metrics emitted — `SlaCountdownCheck` handles this dataset)

**InferredSlaCheck — insufficient history:**
```
MetricDetail(INFERRED_SLA, "inferred_sla_status",
  {"status":"PASS","reason":"insufficient_history","data_points":<n>})
```

### `dq_metric_numeric` and `dq_metric_detail` Uniqueness Constraints

```
dq_metric_numeric: UNIQUE(dq_run_id, check_type, metric_name, expiry_date)
dq_metric_detail: UNIQUE(dq_run_id, check_type, detail_type, expiry_date)
```

**CorrelationCheck per dataset run:**
- `MetricNumeric(CORRELATION, "correlated_dataset_count")` — 1 row
- `MetricNumeric(CORRELATION, "correlation_ratio")` — 1 row
- `MetricDetail(CORRELATION, "correlation_status")` — 1 row

**InferredSlaCheck per dataset run:**
- `MetricNumeric(INFERRED_SLA, "inferred_sla_hours")` — 1 row (when applicable)
- `MetricNumeric(INFERRED_SLA, "deviation_from_inferred")` — 1 row (when applicable)
- `MetricDetail(INFERRED_SLA, "inferred_sla_status")` — 1 row (when applicable)

No constraint violations possible — one entry per metric_name and one per detail_type per `dq_run_id`.

### Java Patterns — Follow Exactly

All patterns from Story 7.1 apply identically:

- **`private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`** — shared static instance per class (one per class, never instantiated inside methods)
- **`private static final Logger LOG = LoggerFactory.getLogger(ClassName.class)`**
- **`try-with-resources`** for ALL JDBC: Connection, PreparedStatement, ResultSet — no manual `.close()`
- **`PreparedStatement` with `?` placeholders** — NEVER string concatenation in SQL
- **`metrics.clear()` before adding error detail** in catch block — prevents partial results mixing with error detail
- **`@FunctionalInterface`** on provider interfaces — enables lambda injection in tests without mock frameworks
- **No-arg constructor delegates to provider constructor** — never duplicates logic
- **Full generic types**: `List<DqMetric>`, `Map<String, Object>`, `Optional<String>`, `List<Double>`
- **Package**: `com.bank.dqs.checks`
- **No SparkSession dependency** — neither check has any Spark DataFrame operations
- **`public record`** for inner result types (same as `SourceSystemHealthCheck.SourceSystemStats`)
- Use `java.sql.Date.valueOf(localDate)` for JDBC date parameter binding

### No-Arg Constructor Pattern

Same pattern as Story 7.1:

```java
public CorrelationCheck() {
    this(new NoOpCorrelationStatsProvider());
}

public CorrelationCheck(CorrelationStatsProvider statsProvider) {
    if (statsProvider == null) {
        throw new IllegalArgumentException("statsProvider must not be null");
    }
    this.statsProvider = statsProvider;
}
```

**BUT** — in `DqsJob.buildCheckFactory()`, ALWAYS use the JDBC constructor (never no-arg). The no-arg exists only for tests or future no-JDBC scenarios.

### DqsJob Registration — Exact Location

After `SourceSystemHealthCheck` and before `DqsScoreCheck`:

```java
f.register(new SourceSystemHealthCheck(                                    // Tier 3 — Epic 7, Story 7.1
        new SourceSystemHealthCheck.JdbcSourceSystemStatsProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
f.register(new CorrelationCheck(                                           // Tier 3 — Epic 7, Story 7.2
        new CorrelationCheck.JdbcCorrelationStatsProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
f.register(new InferredSlaCheck(                                           // Tier 3 — Epic 7, Story 7.2
        new InferredSlaCheck.JdbcSlaHistoryProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
// DqsScoreCheck is registered LAST — always runs after all other checks
f.register(new DqsScoreCheck(ctx -> accumulator));
```

### Test Pattern — No SparkSession Required

Both checks have no Spark operations. Use the same pattern as `SourceSystemHealthCheckTest`:

```java
private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

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
// CorrelationCheck with injected provider
CorrelationCheck check = new CorrelationCheck(
        (srcSysNm, date) -> Optional.of(new CorrelationCheck.CorrelationStats(3, 5, 0.60))
);

// InferredSlaCheck with injected provider (explicit SLA=false, 15 history values)
List<Double> history = List.of(9.0, 11.0, 10.5, 9.5, 10.0, 11.5, 9.8, 10.2, 9.7, 11.0,
                                10.3, 9.9, 10.1, 10.8, 16.0);  // last value = current
InferredSlaCheck check = new InferredSlaCheck(
        ctx -> Optional.of(new InferredSlaCheck.SlaHistory(history, false))
);
```

### H2 Compatibility Note

These checks use `v_dq_run_active`, `v_dq_metric_numeric_active`, and `v_dataset_enrichment_active` views. Since tests inject mock providers via lambdas, **no H2 database is required** for check unit tests. The JDBC SQL only matters for the `JdbcXxxProvider` classes, which are not directly tested in unit tests — they are exercised through integration tests (real Postgres) if those exist, but for check unit tests mock providers suffice.

If `JdbcCorrelationStatsProvider` is tested directly (optional integration test), be aware:
- The correlated-count subquery uses a correlated subquery (`WHERE h.dataset_name = r.dataset_name`) — this is standard SQL and H2 supports it
- `v_dq_run_active` is a view; H2 test DDL must include it if testing providers directly

### Comparison With Existing Checks

| Check | DataFrame? | External Deps? | Cross-Dataset? | Complexity |
|---|---|---|---|---|
| SlaCountdownCheck | NO | JDBC (dataset_enrichment) | No | Low |
| ClassificationWeightedCheck | **NO** | JDBC (lob_lookup) | No | Low |
| SourceSystemHealthCheck | **NO** | JDBC (v_dq_run_active) | **YES (aggregate)** | Medium |
| CorrelationCheck | **NO** | JDBC (v_dq_run_active) | **YES (co-degradation)** | Medium-High |
| InferredSlaCheck | **NO** | JDBC (v_dq_metric_numeric_active + v_dataset_enrichment_active) | No (single dataset history) | Medium |

`CorrelationCheck` is slightly more complex than `SourceSystemHealthCheck` because it uses a correlated subquery to detect per-dataset degradation relative to each dataset's own baseline. `InferredSlaCheck` is simpler in SQL (single dataset history) but introduces statistics computation (mean/stddev in Java).

### No Schema DDL Changes Required

No new tables or views are needed. The checks use:
- `v_dq_run_active` — already exists (all check types use this)
- `v_dq_metric_numeric_active` — already exists (used by `DqsScoreCheck` and `BreakingChangeCheck`)
- `v_dataset_enrichment_active` — already exists (used by `SlaCountdownCheck`)

### File Structure — Exact Locations

```
dqs-spark/
  src/main/java/com/bank/dqs/
    checks/
      CorrelationCheck.java        ← NEW file
      InferredSlaCheck.java        ← NEW file
    DqsJob.java                    ← MODIFY: add imports + register both checks + update Javadoc
  src/test/java/com/bank/dqs/
    checks/
      CorrelationCheckTest.java    ← NEW file
      InferredSlaCheckTest.java    ← NEW file
```

**Do NOT touch:** `CheckFactory.java`, `DqCheck.java`, any existing check files, model files, writer, scanner, serve-layer files, schema DDL/views, fixtures, or any dashboard/API components.

### Anti-Patterns — NEVER Do These

- **NEVER call `context.getDf()`** in either check — neither uses the DataFrame
- **NEVER query raw `dq_run`, `dq_metric_numeric`, or `dataset_enrichment` tables** — always use the `v_*_active` views
- **NEVER let exceptions propagate from `execute()`** — entire body wrapped in try/catch; `metrics.clear()` before error detail in catch
- **NEVER add check-type-specific logic to serve/API/dashboard** — generic metric consumption requires zero serve/dashboard changes
- **NEVER hardcode `9999-12-31 23:59:59`** — both checks use views that already filter active records; no sentinel needed in these queries
- **NEVER bundle Spark JARs** — Spark is `provided` scope; these checks don't use Spark at all
- **NEVER use raw types**: `List<DqMetric>`, `Optional<String>`, `List<Double>`, `Map<String, Object>`
- **NEVER use `Math.sqrt` on a negative argument** — `variance = sumSqDiff / n` is always >= 0 by definition; no guard needed but document it
- **NEVER return partial metric lists** — on any exception, `metrics.clear()` and return exactly one `errorDetail`

### Previous Story Learnings (Epic 7, Story 7.1)

From Story 7.1 completion (ClassificationWeightedCheck + SourceSystemHealthCheck):
- **Test suite is at 245 tests, 0 failures** — do not break regressions
- **`DqsJob.buildCheckFactory()` now has all Tier 3 Story 7.1 checks** registered after `TimestampSanityCheck`: `ClassificationWeightedCheck`, `SourceSystemHealthCheck`, then `DqsScoreCheck` LAST
- **`metrics.clear()` in catch block is mandatory** — prevents partial column results mixing with error detail
- **`private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`** — one per class, never instantiated inside methods (thread-safe after construction)
- **Inner provider interfaces as `@FunctionalInterface`** — enables lambda injection in tests without mock frameworks
- **No-arg constructor delegates to JDBC-provider constructor** — never duplicates logic
- **`notRunDetail` returns a list with single item** — `List.of(new MetricDetail(...))`
- **SparkSession is NOT needed** for JDBC-only checks — no `@BeforeAll`/`@AfterAll` SparkSession lifecycle in test classes
- **Review findings from 7.1** to avoid repeating: dead public constants never used in logic, dead method parameters with `includeXxx` flags that are never read, unused import leftovers from ATDD phase — keep all public constants actually used in logic; do not declare constants not referenced in code
- **ATDD tests may be pre-generated** — check if test files already exist before creating; if they exist and have `@Disabled`, remove the annotations

From Story 6.1 (`SlaCountdownCheck`) — most relevant pattern for `InferredSlaCheck`:
- JDBC provider with `ConnectionProvider` functional interface
- `Optional<T>` return from provider to signal "not applicable"
- The `SLA_QUERY` uses `WHERE ? LIKE dataset_pattern` (reversal pattern) — exact SQL reused in `InferredSlaCheck.JdbcSlaHistoryProvider` for explicit SLA check

From Story 6.3 (`BreakingChangeCheck`) — relevant for querying `dq_metric_detail`:
- Pattern for querying metric history during check execution
- Always use `v_*_active` views, never raw tables

### References

- Epic 7 Story 7.2 AC: `/home/sas/workspace/data-quality-service/_bmad-output/planning-artifacts/epics/epic-7-tier-3-intelligence-executive-reporting-phase-3.md`
- Project Context — Java rules + anti-patterns: `_bmad-output/project-context.md`
- Architecture — Check extensibility + unidirectional dependency: `_bmad-output/planning-artifacts/architecture.md`
- Previous story (most recent, same tier): `_bmad-output/implementation-artifacts/7-1-classification-weighted-alerting-source-system-health.md`
- DqCheck interface: `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- CheckFactory: `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- SourceSystemHealthCheck (closest pattern — JDBC provider, no DataFrame, cross-dataset): `dqs-spark/src/main/java/com/bank/dqs/checks/SourceSystemHealthCheck.java`
- SlaCountdownCheck (explicit SLA lookup pattern for InferredSlaCheck): `dqs-spark/src/main/java/com/bank/dqs/checks/SlaCountdownCheck.java`
- BreakingChangeCheck (historical metric query pattern): `dqs-spark/src/main/java/com/bank/dqs/checks/BreakingChangeCheck.java`
- ClassificationWeightedCheck (simplest JDBC check — reference for structure): `dqs-spark/src/main/java/com/bank/dqs/checks/ClassificationWeightedCheck.java`
- DqsJob (register here): `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`
- DatasetContext (getDatasetName(), getPartitionDate(), getLookupCode()): `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- MetricDetail: `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- MetricNumeric: `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java`
- DDL schema: `dqs-serve/src/serve/schema/ddl.sql`
- Views (v_dq_run_active, v_dq_metric_numeric_active, v_dataset_enrichment_active): `dqs-serve/src/serve/schema/views.sql`
- Fixtures (sample data for src_sys_nm=alpha, freshness metrics): `dqs-serve/src/serve/schema/fixtures.sql`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Test data in HISTORY_FAIL/WARN/PASS had mathematically incorrect values (pre-generated ATDD phase). The values produced stddev≈1.57 instead of stddev=2.0, yielding inferred≈13.76 instead of 14.0. Fixed by replacing with values where base 14 historical entries have sum=140 (mean=10.0) and sum(sq_diff)=56 (pop-stddev=2.0 exactly). Implementation updated to compute mean/stddev from historical values only (excluding the current/last entry), which both matches the mathematical intent and is semantically correct (you should not infer the SLA baseline from today's potentially anomalous delivery value).

### Completion Notes List

- Task 1: CorrelationCheck.java created. Implements JDBC-only cross-dataset co-degradation detection. Uses two SQL queries against v_dq_run_active: total count + correlated count via correlated subquery. All 9 CorrelationCheckTest tests pass.
- Task 2: InferredSlaCheck.java created. Implements JDBC-only single-dataset SLA inference from 30 days of freshness history. Statistics (mean, population stddev) computed from historical values only (all except the last/current entry). All 10 InferredSlaCheckTest tests pass.
- Task 3: DqsJob.java updated — added imports for CorrelationCheck and InferredSlaCheck, registered both checks after SourceSystemHealthCheck and before DqsScoreCheck, updated Javadoc.
- Tasks 4 & 5: Test files were pre-generated by ATDD phase. Corrected test data constants (HISTORY_FAIL/WARN/PASS) to use values that mathematically satisfy mean=10.0, stddev=2.0 → inferredSlaHours=14.0. No test method logic was changed.
- Final test result: 264 tests, 0 failures, 0 skipped (up from 245 baseline, +19 new tests).

### File List

- dqs-spark/src/main/java/com/bank/dqs/checks/CorrelationCheck.java (NEW)
- dqs-spark/src/main/java/com/bank/dqs/checks/InferredSlaCheck.java (NEW)
- dqs-spark/src/main/java/com/bank/dqs/DqsJob.java (MODIFIED — imports + registrations + Javadoc)
- dqs-spark/src/test/java/com/bank/dqs/checks/CorrelationCheckTest.java (pre-existing, no changes needed)
- dqs-spark/src/test/java/com/bank/dqs/checks/InferredSlaCheckTest.java (MODIFIED — corrected test data constants HISTORY_FAIL/WARN/PASS)

### Review Findings

Review conducted: 2026-04-04 by bmad-code-review (claude-sonnet-4-6). All findings fixed. Tests: 264/0/0.

- [x] [Review][Patch] `SRC_SYS_SEGMENT_PREFIX` constant declared but unused in `extractSrcSysNm` [CorrelationCheck.java:170,174] — Fixed: replaced hardcoded `"src_sys_nm="` literals with the constant reference.
- [x] [Review][Patch] Misleading `data_points` Javadoc comment claims "total history including current" but `historicalValues.size()` (excludes current day) is passed [InferredSlaCheck.java:159] — Fixed: corrected comment to accurately describe the value.
- [x] [Review][Patch] Negative `inferredSlaHours` collapses WARN zone for high-variance low-mean data [InferredSlaCheck.java:153] — Fixed: added `inferredSlaHours > 0.0` guard in WARN condition; PASS emitted when inferred SLA is non-positive.
- [x] [Review][Patch] `computeStdDev` has no internal guard against empty list causing divide-by-zero [InferredSlaCheck.java:184] — Fixed: added `if (values.isEmpty()) return 0.0` defensive guard.
- [x] [Review][Defer] LIKE wildcard (`%`, `_`) injection in `JdbcCorrelationStatsProvider.getStats` likePattern construction [CorrelationCheck.java:316] — deferred, pre-existing pattern from `SourceSystemHealthCheck` (Story 7.1); consistent with codebase convention.

## Change Log

- 2026-04-04: Story created by bmad-create-story workflow. Status: ready-for-dev.
- 2026-04-04: Implemented by dev agent (claude-sonnet-4-6). Status: review. Created CorrelationCheck.java and InferredSlaCheck.java. Registered both checks in DqsJob. Fixed mathematically incorrect test data in InferredSlaCheckTest. All 264 tests pass (19 new).
- 2026-04-04: Code reviewed by bmad-code-review (claude-sonnet-4-6). Status: done. 4 patch findings fixed (unused constant, misleading comment, negative inferredSlaHours WARN zone collapse, computeStdDev empty-list guard). 1 deferred (LIKE wildcard, pre-existing pattern). 264 tests pass.
