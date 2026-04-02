package com.dqs;

import com.dqs.checks.CheckExecutor;
import com.dqs.config.RuleConfig;
import com.dqs.db.MetadataRepository;
import com.dqs.db.PostgresWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

public class DqsJob {

    /**
     * Validates that exactly 7 positional args were supplied.
     * Args: config-json-path input-parquet-path db-host db-port db-name db-user db-pass
     */
    static void validateArgs(String[] args) {
        if (args.length != 7) {
            throw new IllegalArgumentException(
                    "Usage: DqsJob <config-json-path> <input-parquet-path> " +
                    "<db-host> <db-port> <db-name> <db-user> <db-pass>");
        }
    }

    public static void main(String[] args) throws Exception {
        validateArgs(args);

        String configPath  = args[0];
        String inputPath   = args[1];
        String dbHost      = args[2];
        int    dbPort      = Integer.parseInt(args[3]);
        String dbName      = args[4];
        String dbUser      = args[5];
        String dbPass      = args[6];

        // Parse rule config
        String configJson = new String(Files.readAllBytes(Paths.get(configPath)));
        RuleConfig config = RuleConfig.fromJson(configJson);

        // Set up DB connection
        PostgresWriter writer = new PostgresWriter(dbHost, dbPort, dbName, dbUser, dbPass);

        try (Connection conn = DriverManager.getConnection(writer.getJdbcUrl(), writer.connectionProperties());
             MetadataRepository repo = new MetadataRepository(conn)) {
            int datasetId = repo.upsertDataset(config.getDataset());
            long startMs = System.currentTimeMillis();
            int runId = repo.openRun(datasetId);

            SparkSession spark = SparkSession.builder().appName("DqsJob").getOrCreate();
            String runStatus = "ERROR"; // default if an exception is thrown
            try {
                Dataset<Row> df = spark.read().parquet(inputPath);

                CheckExecutor executor = new CheckExecutor();
                CheckExecutor.Results results = executor.execute(df, config);

                repo.insertCheckResults(runId, datasetId, results.getCheckResults());
                repo.insertMetrics(runId, datasetId, results.getMetrics());

                runStatus = results.hasFailed() ? "FAILED" : "PASSED";
            } finally {
                repo.closeRun(runId, runStatus, System.currentTimeMillis() - startMs);
                spark.stop();
            }
        }
    }
}
