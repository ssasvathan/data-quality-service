package com.bank.dqs;

import com.bank.dqs.checks.CheckFactory;
import com.bank.dqs.checks.DqCheck;
import com.bank.dqs.checks.DqsScoreCheck;
import com.bank.dqs.checks.FreshnessCheck;
import com.bank.dqs.checks.OpsCheck;
import com.bank.dqs.checks.SchemaCheck;
import com.bank.dqs.checks.SlaCountdownCheck;
import com.bank.dqs.checks.VolumeCheck;
import com.bank.dqs.checks.ZeroRowCheck;
import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.reader.DatasetReader;
import com.bank.dqs.scanner.ConsumerZoneScanner;
import com.bank.dqs.scanner.EnrichmentResolver;
import com.bank.dqs.writer.BatchWriter;
import org.apache.hadoop.fs.FileSystem;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
    record DqsJobArgs(String parentPath, LocalDate partitionDate, Long orchestrationRunId, int rerunNumber) {}

    // ---------------------------------------------------------------------------
    // Argument parsing
    // ---------------------------------------------------------------------------

    /** Formatter for the {@code --date} CLI argument (yyyyMMdd). */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DB_URL_DEFAULT = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER_DEFAULT = "postgres";
    private static final String DB_PASSWORD_DEFAULT = "localdev";

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
        Long orchestrationRunId = null;
        int rerunNumber = 0;

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
                case "--orchestration-run-id" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException(
                                "--orchestration-run-id flag requires a value but none was provided");
                    }
                    String orchIdStr = args[++i];
                    try {
                        orchestrationRunId = Long.parseLong(orchIdStr);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Invalid --orchestration-run-id value. Expected a long integer, got: "
                                        + orchIdStr, e);
                    }
                }
                case "--rerun-number" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException(
                                "--rerun-number flag requires a value but none was provided");
                    }
                    String rerunStr = args[++i];
                    try {
                        rerunNumber = Integer.parseInt(rerunStr);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Invalid --rerun-number value. Expected an integer, got: " + rerunStr, e);
                    }
                }
            }
        }

        if (parentPath == null || parentPath.isBlank()) {
            throw new IllegalArgumentException("--parent-path is required");
        }

        return new DqsJobArgs(parentPath, partitionDate, orchestrationRunId, rerunNumber);
    }

    /**
     * Load DB config properties from classpath first, then from known filesystem paths.
     * This supports both packaged execution and local development layouts.
     */
    static Properties loadApplicationProperties(List<Path> configCandidates) throws IOException {
        Properties props = new Properties();

        // 1) Packaged/classpath mode
        try (InputStream is = DqsJob.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                props.load(is);
                LOG.debug("Loaded DB properties from classpath resource /application.properties");
                return props;
            }
        }

        // 2) Local filesystem mode (repo/component execution)
        for (Path candidate : configCandidates) {
            if (loadPropertiesFromFileIfPresent(props, candidate)) {
                return props;
            }
        }

        LOG.warn("DB properties file not found. Falling back to default DB connection values.");
        return props;
    }

    static boolean loadPropertiesFromFileIfPresent(Properties props, Path candidate) throws IOException {
        if (candidate == null || !Files.isRegularFile(candidate) || !Files.isReadable(candidate)) {
            return false;
        }
        try (InputStream is = Files.newInputStream(candidate)) {
            props.load(is);
            LOG.info("Loaded DB properties from {}", candidate.toAbsolutePath());
            return true;
        }
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

        Properties props = loadApplicationProperties(List.of(
                Path.of("config", "application.properties"),
                Path.of("dqs-spark", "config", "application.properties")
        ));
        String jdbcUrl = props.getProperty("db.url", DB_URL_DEFAULT);
        String dbUser  = props.getProperty("db.user", DB_USER_DEFAULT);
        String dbPass  = props.getProperty("db.password", DB_PASSWORD_DEFAULT);

        FileSystem fs = FileSystem.get(spark.sparkContext().hadoopConfiguration());
        DatasetReader reader = new DatasetReader(spark);

        List<DatasetContext> datasets;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
            EnrichmentResolver resolver = new EnrichmentResolver(conn);
            LOG.info("Dataset enrichment resolver enabled");
            ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs, resolver);
            datasets = scanner.scan(parentPath, partitionDate);
        } catch (SQLException e) {
            LOG.error("Failed to open DB connection for enrichment resolver, falling back to no enrichment: {}",
                      e.getMessage(), e);
            ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs);
            datasets = scanner.scan(parentPath, partitionDate);
        }

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

        LOG.info("Dataset loading complete: {} datasets loaded, {} skipped/errors",
                loaded.size(), errorCount);

        // Run DQ checks and write results for each loaded dataset
        for (DatasetContext ctx : loaded) {
            List<DqMetric> datasetMetrics = new ArrayList<>();
            LOG.info("Processing dataset: {}", ctx.getDatasetName());
            try {
                // CheckFactory created per-dataset so DqsScoreCheck lambda captures a fresh list
                CheckFactory datasetFactory = buildCheckFactory(datasetMetrics);

                List<DqCheck> checks;
                try (Connection checkConn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
                    checks = datasetFactory.getEnabledChecks(ctx, checkConn);
                }

                for (DqCheck check : checks) {
                    List<DqMetric> checkResult = check.execute(ctx);
                    datasetMetrics.addAll(checkResult);
                }

                try (Connection writeConn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
                    BatchWriter writer = new BatchWriter(writeConn);
                    long runId = writer.write(ctx, datasetMetrics, jobArgs.orchestrationRunId(), jobArgs.rerunNumber());
                    LOG.info("Dataset {} written: dq_run_id={}", ctx.getDatasetName(), runId);
                }
            } catch (Exception e) {
                LOG.error("Failed to process dataset {}: {}", ctx.getDatasetName(), e.getMessage(), e);
                errorCount++;
            }
        }

        LOG.info("DqsJob completed: {} datasets processed, {} total errors",
                loaded.size(), errorCount);
    }

    /**
     * Builds a per-dataset {@link CheckFactory} with all 5 Tier 1 checks registered.
     *
     * <p>{@link DqsScoreCheck} is registered LAST — it reads from the {@code accumulator}
     * list which is populated by the other four checks during the same dataset's run.
     *
     * @param accumulator the mutable list that will hold all metrics from prior checks
     * @return a freshly constructed {@link CheckFactory} with all checks registered
     */
    private static CheckFactory buildCheckFactory(List<DqMetric> accumulator) {
        CheckFactory f = new CheckFactory();
        f.register(new FreshnessCheck());
        f.register(new VolumeCheck());
        f.register(new SchemaCheck());
        f.register(new OpsCheck());
        f.register(new SlaCountdownCheck()); // Tier 2 — Epic 6, Story 6.1
        // TODO: wire JdbcSlaProvider via ConnectionProvider once JDBC connection threading is resolved
        f.register(new ZeroRowCheck());      // Tier 2 — Epic 6, Story 6.2
        // DqsScoreCheck is registered LAST — always runs after all other checks
        // Lambda captures the accumulator list: reads prior check results for score computation
        f.register(new DqsScoreCheck(ctx -> accumulator));
        return f;
    }
}
