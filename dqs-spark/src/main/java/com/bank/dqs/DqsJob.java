package com.bank.dqs;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.reader.DatasetReader;
import com.bank.dqs.scanner.ConsumerZoneScanner;
import org.apache.hadoop.fs.FileSystem;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * DqsJob — Spark job entry point for the Data Quality Service.
 *
 * <p>Parses CLI arguments, scans HDFS for datasets at the given partition date,
 * loads each dataset into a Spark DataFrame, and (in future stories) runs configured
 * DQ checks and writes results to Postgres.
 *
 * <p>Usage:
 * <pre>
 * spark-submit dqs-spark.jar --parent-path /prod/abc/data/consumerzone [--date 20260325]
 * </pre>
 *
 * <p>If {@code --date} is omitted, today's date is used as the partition date.
 */
public class DqsJob {

    private static final Logger LOG = LoggerFactory.getLogger(DqsJob.class);

    // ---------------------------------------------------------------------------
    // Inner record: parsed CLI arguments
    // ---------------------------------------------------------------------------

    /**
     * Value holder for parsed CLI arguments.
     * Package-private to allow unit testing via DqsJobArgParserTest.
     */
    record DqsJobArgs(String parentPath, LocalDate partitionDate) {}

    // ---------------------------------------------------------------------------
    // Argument parsing
    // ---------------------------------------------------------------------------

    /** Formatter for the {@code --date} CLI argument (yyyyMMdd). */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Parse the CLI argument array into a {@link DqsJobArgs} record.
     *
     * <p>Supported arguments:
     * <ul>
     *   <li>{@code --parent-path <path>} (required)
     *   <li>{@code --date <yyyyMMdd>} (optional; defaults to today)
     * </ul>
     *
     * @param args raw CLI arguments
     * @return parsed DqsJobArgs
     * @throws IllegalArgumentException if args is null/empty, --parent-path is missing,
     *                                  a flag is dangling (has no following value),
     *                                  or --date has wrong format
     */
    static DqsJobArgs parseArgs(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("--parent-path is required");
        }

        String parentPath = null;
        LocalDate partitionDate = LocalDate.now();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--parent-path" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException(
                                "--parent-path flag requires a value but none was provided");
                    }
                    parentPath = args[++i];
                }
                case "--date" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException(
                                "--date flag requires a value but none was provided");
                    }
                    String dateStr = args[++i];
                    try {
                        partitionDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException(
                                "Invalid date format. Expected yyyyMMdd, got: " + dateStr, e);
                    }
                }
            }
        }

        if (parentPath == null || parentPath.isBlank()) {
            throw new IllegalArgumentException("--parent-path is required");
        }

        return new DqsJobArgs(parentPath, partitionDate);
    }

    // ---------------------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        DqsJobArgs jobArgs = parseArgs(args);
        String parentPath = jobArgs.parentPath();
        LocalDate partitionDate = jobArgs.partitionDate();

        // TODO (epic-3): include run_id in log messages once run_id is assigned by the orchestrator
        LOG.info("DqsJob started: parentPath={}, date={}", parentPath, partitionDate);

        SparkSession spark = SparkSession.builder()
                .appName("dqs-spark")
                .getOrCreate();

        FileSystem fs = FileSystem.get(spark.sparkContext().hadoopConfiguration());
        ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs);
        DatasetReader reader = new DatasetReader(spark);

        List<DatasetContext> datasets = scanner.scan(parentPath, partitionDate);
        List<DatasetContext> loaded = new ArrayList<>();
        int errorCount = 0;

        for (DatasetContext ctx : datasets) {
            try {
                Dataset<Row> df = reader.read(ctx);
                loaded.add(new DatasetContext(
                        ctx.getDatasetName(),
                        ctx.getLookupCode(),
                        ctx.getPartitionDate(),
                        ctx.getParentPath(),
                        df,
                        ctx.getFormat()
                ));
                LOG.debug("Loaded dataset: {}", ctx.getDatasetName());
            } catch (UnsupportedOperationException e) {
                LOG.error("Unsupported format for dataset {}: {}", ctx.getDatasetName(), e.getMessage());
                errorCount++;
            } catch (Exception e) {
                LOG.error("Failed to load dataset {}: {}", ctx.getDatasetName(), e.getMessage(), e);
                errorCount++;
            }
        }

        LOG.info("DqsJob completed: {} datasets loaded successfully, {} skipped/errors",
                loaded.size(), errorCount);

        // TODO (story 2.5-2.9): run DQ checks on each loaded DatasetContext
        // TODO (story 2.10): write check results to Postgres via BatchWriter
    }
}
