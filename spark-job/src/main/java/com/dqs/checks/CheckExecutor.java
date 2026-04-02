package com.dqs.checks;

import com.dqs.config.CheckConfig;
import com.dqs.config.RuleConfig;
import com.dqs.db.CheckResult;
import com.dqs.db.MetricSnapshot;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.List;

public class CheckExecutor {

    /** Runs all checks defined in config against df and returns results + metrics. */
    public Results execute(Dataset<Row> df, RuleConfig config) {
        List<CheckResult> checkResults = new ArrayList<>();
        List<MetricSnapshot> metrics = new ArrayList<>();

        for (CheckConfig check : config.getChecks()) {
            if ("rowCount".equals(check.getCheckType())) {
                long count = df.count();
                metrics.add(new MetricSnapshot("rowCount", null, (double) count));

                boolean passed = check.getMin() == null || count >= check.getMin();
                String reason = passed ? null
                        : String.format("Row count %d is below minimum %d", count, check.getMin());
                checkResults.add(new CheckResult("rowCount", null, passed ? "PASSED" : "FAILED", reason));
            } else {
                checkResults.add(new CheckResult(
                        check.getCheckType(), null, "FAILED",
                        "Unknown check type: " + check.getCheckType()));
            }
        }

        return new Results(checkResults, metrics);
    }

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
