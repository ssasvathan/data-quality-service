# DQS Spark Job: Statistical Tier 1 Checks Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pivot the Spark job from a static JSON rule configuration to an AI-brainstormed **domain-agnostic statistical baseline**. We will implement Tier 1 Foundation checks: Volume (30-day statistical variance) and Schema (Column fingerprint drift) by fetching historical metrics from the Postgres `dq_metric_snapshot` table.

**Architecture:** 
1. `MetadataRepository` gets new methods to fetch the last 30 days of metrics for a dataset.
2. `CheckExecutor` drops `RuleConfig` and instead receives previous metrics.
3. Compute `rowCount`, compare against 30-day average ±2 stddev.
4. Compute Schema Fingerprint (MD5 of DataFrame Schema JSON), compare against yesterday's fingerprint.
5. Compute overall Data Quality Score (0-100).

---

### Task 1: Update MetadataRepository to Fetch Historical Metrics

**Files:**
- Modify: `spark-job/src/main/java/com/dqs/db/MetadataRepository.java`
- Modify: `spark-job/src/test/java/com/dqs/db/MetadataRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
// Append to spark-job/src/test/java/com/dqs/db/MetadataRepositoryTest.java
    @Test
    void fetchesHistoricalMetricsCorrectly() throws SQLException {
        int datasetId = repo.upsertDataset("MY_SYS");
        int runId1 = repo.openRun(datasetId);
        repo.insertMetrics(runId1, datasetId, java.util.List.of(
            new MetricSnapshot("rowCount", null, 150.0),
            new MetricSnapshot("schemaHash", null, 123456.0)
        ));
        
        java.util.List<Double> rowCounts = repo.getHistoricalMetricValues(datasetId, "rowCount", 30);
        assertEquals(1, rowCounts.size());
        assertEquals(150.0, rowCounts.get(0), 0.001);
        
        Double lastHash = repo.getLatestMetricValue(datasetId, "schemaHash");
        assertNotNull(lastHash);
        assertEquals(123456.0, lastHash, 0.001);
    }
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd spark-job && mvn test -Dtest=MetadataRepositoryTest`

- [ ] **Step 3: Write minimal implementation**

```java
// Append to spark-job/src/main/java/com/dqs/db/MetadataRepository.java
    /** Fetches a list of historical values for a specific metric for the last N runs of a dataset. */
    public List<Double> getHistoricalMetricValues(int datasetId, String metricName, int limit) throws SQLException {
        String sql = "SELECT m.metric_value FROM dq_metric_snapshot m " +
                     "JOIN dq_run r ON m.run_id = r.run_id " +
                     "WHERE m.dataset_id = ? AND m.metric_name = ? " +
                     "ORDER BY r.run_timestamp DESC LIMIT ?";
        List<Double> values = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.setString(2, metricName);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getDouble(1));
                }
            }
        }
        return values;
    }

    /** Fetches the single most recent value for a metric (e.g., last known schema hash). */
    public Double getLatestMetricValue(int datasetId, String metricName) throws SQLException {
        List<Double> vals = getHistoricalMetricValues(datasetId, metricName, 1);
        return vals.isEmpty() ? null : vals.get(0);
    }
```

- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**
`git commit -m "feat: add historical metric retrieval to metadata repository"`

### Task 2: Implement Domain-Agnostic Statistical Checks

**Files:**
- Modify: `spark-job/src/main/java/com/dqs/checks/CheckExecutor.java`
- Modify: `spark-job/src/test/java/com/dqs/checks/CheckExecutorTest.java`

- [ ] **Step 1: Write the failing test**

```java
// spark-job/src/test/java/com/dqs/checks/CheckExecutorTest.java
// Replace existing tests to verify standard statistical execution without RuleConfig
    @Test
    void volumeCheckFlagsOutliers() {
        // Provide history: 100, 105, 95 (mean 100, stddev ~5)
        // Current dataset has 50 rows -> anomaly!
        java.util.List<Double> history = java.util.List.of(100.0, 105.0, 95.0);
        CheckExecutor.Results res = new CheckExecutor().execute(dfWithRows(50), history, null);
        
        CheckResult volCheck = res.getCheckResults().stream().filter(r -> "volume".equals(r.getCheckType())).findFirst().get();
        assertEquals("FAILED", volCheck.getStatus());
        assertTrue(volCheck.getFailureReason().contains("outside 2 standard deviations"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Write minimal implementation**

```java
// Replace spark-job/src/main/java/com/dqs/checks/CheckExecutor.java
package com.dqs.checks;

import com.dqs.db.CheckResult;
import com.dqs.db.MetricSnapshot;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import java.util.ArrayList;
import java.util.List;

public class CheckExecutor {
    // Computes MD5 hash from schema JSON natively stringified
    private double computeSchemaHash(Dataset<Row> df) {
        return (double) Math.abs(df.schema().json().hashCode());
    }

    public Results execute(Dataset<Row> df, List<Double> historicalRowCounts, Double latestSchemaHash) {
        List<CheckResult> checkResults = new ArrayList<>();
        List<MetricSnapshot> metrics = new ArrayList<>();
        
        long count = df.count();
        metrics.add(new MetricSnapshot("rowCount", null, (double) count));

        // 1. Volume: Statistical Baseline +/- 2 stddev
        if (historicalRowCounts == null || historicalRowCounts.isEmpty()) {
            checkResults.add(new CheckResult("volume", null, "PASSED", "First run: establishing baseline"));
        } else {
            double mean = historicalRowCounts.stream().mapToDouble(d -> d).average().orElse(0.0);
            double variance = historicalRowCounts.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0.0);
            double stddev = Math.sqrt(variance);
            
            // If stddev is 0, allow 5% drift, otherwise 2 stddev
            double margin = stddev == 0.0 ? Math.max(mean * 0.05, 1.0) : 2 * stddev;
            boolean passed = Math.abs(count - mean) <= margin;
            String reason = passed ? null : String.format("Row count %d is outside 2 standard deviations (Mean: %.1f, StdDev: %.1f)", count, mean, stddev);
            checkResults.add(new CheckResult("volume", null, passed ? "PASSED" : "FAILED", reason));
        }

        // 2. Schema: Column Fingerprint Drift
        double currentHash = computeSchemaHash(df);
        metrics.add(new MetricSnapshot("schemaHash", null, currentHash));
        
        if (latestSchemaHash == null) {
            checkResults.add(new CheckResult("schema", null, "PASSED", "First run: establishing schema fingerprint"));
        } else {
            boolean passed = Double.compare(currentHash, latestSchemaHash) == 0;
            String reason = passed ? null : "Schema fingerprint changed since last run (Drift Detected)";
            checkResults.add(new CheckResult("schema", null, passed ? "PASSED" : "FAILED", reason));
        }

        // 3. Executive DQS Score (Tier 1 quick win)
        int score = 100;
        if (checkResults.stream().anyMatch(r -> "volume".equals(r.getCheckType()) && "FAILED".equals(r.getStatus()))) score -= 40;
        if (checkResults.stream().anyMatch(r -> "schema".equals(r.getCheckType()) && "FAILED".equals(r.getStatus()))) score -= 40;
        metrics.add(new MetricSnapshot("dqsScore", null, (double) score));

        return new Results(checkResults, metrics);
    }
... // Results inner class remains identical
}
```

- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**
`git commit -m "feat: implement statistical volume baseline and schema fingerprint checks"`

### Task 3: Refactor DqsJob to Eliminate Config and Use DB History

**Files:**
- Modify: `spark-job/src/main/java/com/dqs/DqsJob.java`
- Delete: `spark-job/src/main/java/com/dqs/config/*`

- [ ] **Step 1: Delete config files and update `validateArgs`**
Change arguments to `<input-parquet-path> <dataset-name> <db-host> <db-port> <db-name> <db-user> <db-pass>`. Note that the JSON file is gone, replaced entirely by `dataset-name`.

- [ ] **Step 2-5: Follow TDD loop to update `DqsJob.main`**
Update `DqsJob` to:
1. Fetch `List<Double> history = repo.getHistoricalMetricValues(datasetId, "rowCount", 30);`
2. Fetch `Double lastSchema = repo.getLatestMetricValue(datasetId, "schemaHash");`
3. Pass both to `executor.execute(df, history, lastSchema);`
4. Commit: `git commit -m "refactor: transition DqsJob to statistical db history over static configs"`
