package com.dqs.checks;

import com.dqs.db.CheckResult;
import com.dqs.db.MetricSnapshot;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.List;
import static org.apache.spark.sql.functions.*;

public class CheckExecutor {
    
    private double computeSchemaHash(Dataset<Row> df, String format) {
        if ("avro".equals(format) && java.util.Arrays.asList(df.columns()).contains("eventAttribute")) {
            Dataset<Row> jsonStrings = df.select(explode(map_values(col("eventAttribute"))));
            StructType inferredSchema = df.sparkSession().read().json(jsonStrings.as(Encoders.STRING())).schema();
            return (double) Math.abs(inferredSchema.json().hashCode());
        } else {
            return (double) Math.abs(df.schema().json().hashCode());
        }
    }

    public Results execute(Dataset<Row> df, String format, List<Double> historicalRowCounts, Double latestSchemaHash, List<Double> historicalFreshnessMins) {
        List<CheckResult> checkResults = new ArrayList<>();
        List<MetricSnapshot> metrics = new ArrayList<>();
        
        long count = df.count();
        double currentHash = computeSchemaHash(df, format);
        
        metrics.add(new MetricSnapshot("rowCount", null, (double) count));
        metrics.add(new MetricSnapshot("schemaHash", null, currentHash));

        // 1. Volume #3: Statistical Baseline + Variance
        boolean isVolumePassed = true;
        if (historicalRowCounts == null || historicalRowCounts.isEmpty()) {
            checkResults.add(new CheckResult("volume", null, "PASSED", "First run: established baseline"));
        } else {
            double mean = historicalRowCounts.stream().mapToDouble(d -> d).average().orElse(0.0);
            double stddev = Math.sqrt(historicalRowCounts.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0.0));
            double margin = stddev == 0.0 ? Math.max(mean * 0.05, 1.0) : 2 * stddev;
            isVolumePassed = Math.abs(count - mean) <= margin;
            checkResults.add(new CheckResult("volume", null, isVolumePassed ? "PASSED" : "FAILED", 
                isVolumePassed ? null : String.format("Count %d outside 2 stddev (Mean: %.1f)", count, mean)));
        }

        // 2. Schema #5: Column Fingerprint Drift
        boolean isSchemaPassed = true;
        if (latestSchemaHash == null) {
            checkResults.add(new CheckResult("schema", null, "PASSED", "First run: established fingerprint"));
        } else {
            isSchemaPassed = Double.compare(currentHash, latestSchemaHash) == 0;
            checkResults.add(new CheckResult("schema", null, isSchemaPassed ? "PASSED" : "FAILED", 
                isSchemaPassed ? null : "Schema fingerprint shifted"));
        }
        
        // 3. Freshness #1: Heartbeat Monitor (Intra-day Data Completeness)
        boolean isFresh = true;
        if (java.util.Arrays.asList(df.columns()).contains("source_event_timestamp")) {
            Row maxTimeRow = df.selectExpr("max(hour(cast(source_event_timestamp as timestamp)) * 60 + minute(cast(source_event_timestamp as timestamp)))").first();
            if (!maxTimeRow.isNullAt(0)) {
                double currentFreshnessMins = maxTimeRow.getInt(0);
                metrics.add(new MetricSnapshot("maxEventTimeMins", null, currentFreshnessMins));
                
                if (historicalFreshnessMins != null && !historicalFreshnessMins.isEmpty()) {
                    double meanMins = historicalFreshnessMins.stream().mapToDouble(d -> d).average().orElse(0.0);
                    double stddevMins = Math.sqrt(historicalFreshnessMins.stream().mapToDouble(d -> Math.pow(d - meanMins, 2)).average().orElse(0.0));
                    double margin = stddevMins == 0.0 ? 60.0 : 2 * stddevMins; 
                    isFresh = currentFreshnessMins >= (meanMins - margin);
                    checkResults.add(new CheckResult("freshness", null, isFresh ? "PASSED" : "FAILED", 
                        isFresh ? null : String.format("Data stopped at minute %.0f, earlier than historical mean %.0f", currentFreshnessMins, meanMins)));
                } else {
                    checkResults.add(new CheckResult("freshness", null, "PASSED", "First run: established freshness baseline"));
                }
            } else {
                checkResults.add(new CheckResult("freshness", null, "PASSED", "No timestamps available to perform check"));
            }
        } else {
            checkResults.add(new CheckResult("freshness", null, "PASSED", "Format does not support source_event_timestamp check"));
        }
            
        // 4. Operations #24: Source System Heartbeat
        checkResults.add(new CheckResult("operations_heartbeat", null, count > 0 ? "PASSED" : "FAILED", 
            count > 0 ? "Source system delivered data today" : "Source system silence detected (0 rows)"));

        // 5. Executive #25: Data Quality Score (DQS)
        int score = 100;
        if (!isVolumePassed) score -= 30;
        if (!isSchemaPassed) score -= 30;
        if (!isFresh) score -= 20;
        if (count == 0) score -= 20;
        score = Math.max(0, score);
        checkResults.add(new CheckResult("dqs_score", null, score >= 70 ? "PASSED" : "FAILED", "Score: " + score));
        metrics.add(new MetricSnapshot("dqsScore", null, (double) score));

        return new Results(checkResults, metrics);
    }
    
    // Results
    public static class Results {
        private final List<CheckResult> checkResults;
        private final List<MetricSnapshot> metrics;

        public Results(List<CheckResult> checkResults, List<MetricSnapshot> metrics) {
            this.checkResults = checkResults;
            this.metrics = metrics;
        }

        public List<CheckResult> getCheckResults() { return checkResults; }
        public List<MetricSnapshot> getMetrics() { return metrics; }

        public boolean hasFailed() {
            return checkResults.stream().anyMatch(r -> "FAILED".equals(r.getStatus()));
        }
    }
}
