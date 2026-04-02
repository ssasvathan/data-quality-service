package com.dqs;

import com.dqs.checks.CheckExecutor;
import com.dqs.db.MetadataRepository;
import com.dqs.db.PostgresWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

public class DqsJob {

    static void validateArgs(String[] args) {
        if (args.length != 8) {
            throw new IllegalArgumentException(
                    "Usage: DqsJob <input-path> <format> <dataset-name> " +
                    "<db-host> <db-port> <db-name> <db-user> <db-pass>");
        }
    }

    public static void main(String[] args) throws Exception {
        validateArgs(args);

        String inputPath = args[0];
        String format    = args[1].toLowerCase();
        String dataset   = args[2];
        String dbHost    = args[3];
        int    dbPort    = Integer.parseInt(args[4]);
        String dbName    = args[5];
        String dbUser    = args[6];
        String dbPass    = args[7];

        PostgresWriter writer = new PostgresWriter(dbHost, dbPort, dbName, dbUser, dbPass);

        try (Connection conn = DriverManager.getConnection(writer.getJdbcUrl(), writer.connectionProperties());
             MetadataRepository repo = new MetadataRepository(conn)) {
            
            int datasetId = repo.upsertDataset(dataset);
            long startMs = System.currentTimeMillis();
            int runId = repo.openRun(datasetId);

            SparkSession spark = SparkSession.builder().appName("DqsJob").getOrCreate();
            String runStatus = "ERROR";
            try {
                Dataset<Row> df;
                if ("avro".equals(format)) {
                    df = spark.read().format("avro").load(inputPath);
                } else {
                    df = spark.read().parquet(inputPath);
                }

                List<Double> rowCountHistory = repo.getHistoricalMetricValues(datasetId, "rowCount", 30);
                Double lastSchemaHash = repo.getLatestMetricValue(datasetId, "schemaHash");
                List<Double> historicalFreshnessMins = repo.getHistoricalMetricValues(datasetId, "maxEventTimeMins", 30);

                CheckExecutor executor = new CheckExecutor();
                CheckExecutor.Results results = executor.execute(df, format, rowCountHistory, lastSchemaHash, historicalFreshnessMins);

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
