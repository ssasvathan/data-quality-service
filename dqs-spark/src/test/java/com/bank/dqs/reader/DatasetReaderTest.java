package com.bank.dqs.reader;

import com.bank.dqs.model.DatasetContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetReaderTest {

    private static SparkSession spark;

    /**
     * JUnit 5 injects a fresh temp directory per test instance (default per-method lifecycle),
     * so each test gets its own isolated directory. Subdirectory names within a single test's
     * tempDir must still be unique to avoid conflicts when a test writes both Avro and Parquet.
     */
    @TempDir
    Path tempDir;

    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 3, 30);

    @BeforeAll
    static void initSpark() {
        spark = SparkSession.builder()
                .appName("DatasetReaderTest")
                .master("local[1]")
                .getOrCreate();
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Writes a small two-row DataFrame as Avro files to {@code parentDir/subDir}
     * and returns the subdirectory name (datasetName segment).
     */
    private String createAvroDataset(String subDir) {
        String path = tempDir.resolve(subDir).toString();
        StructType schema = new StructType()
                .add("id", DataTypes.IntegerType)
                .add("value", DataTypes.StringType);
        List<Row> rows = List.of(
                RowFactory.create(1, "foo"),
                RowFactory.create(2, "bar")
        );
        Dataset<Row> df = spark.createDataFrame(rows, schema);
        df.write().format("avro").save(path);
        return subDir;
    }

    /**
     * Writes a small two-row DataFrame as Parquet files to {@code parentDir/subDir}
     * and returns the subdirectory name (datasetName segment).
     */
    private String createParquetDataset(String subDir) {
        String path = tempDir.resolve(subDir).toString();
        StructType schema = new StructType()
                .add("id", DataTypes.IntegerType)
                .add("value", DataTypes.StringType);
        List<Row> rows = List.of(
                RowFactory.create(10, "hello"),
                RowFactory.create(20, "world")
        );
        Dataset<Row> df = spark.createDataFrame(rows, schema);
        df.write().parquet(path);
        return subDir;
    }

    /** Builds a DatasetContext pointing at tempDir/datasetName. */
    private DatasetContext ctx(String datasetName, String format) {
        return new DatasetContext(
                datasetName,
                "cb10",
                PARTITION_DATE,
                tempDir.toAbsolutePath().toString(),
                null,
                format
        );
    }

    // ---------------------------------------------------------------------------
    // AC1: Read Avro files into DataFrame
    // ---------------------------------------------------------------------------

    @Test
    void readLoadsAvroDatasetIntoDataFrame() {
        String datasetName = createAvroDataset("avro_dataset");
        DatasetReader reader = new DatasetReader(spark);
        DatasetContext ctx = ctx(datasetName, DatasetContext.FORMAT_AVRO);

        Dataset<Row> result = reader.read(ctx);

        assertNotNull(result, "read() must return a non-null DataFrame for AVRO format");
        assertTrue(result.count() > 0, "Loaded Avro DataFrame must contain at least 1 row");
    }

    // ---------------------------------------------------------------------------
    // AC2: Read Parquet files into DataFrame
    // ---------------------------------------------------------------------------

    @Test
    void readLoadsParquetDatasetIntoDataFrame() {
        String datasetName = createParquetDataset("parquet_dataset");
        DatasetReader reader = new DatasetReader(spark);
        DatasetContext ctx = ctx(datasetName, DatasetContext.FORMAT_PARQUET);

        Dataset<Row> result = reader.read(ctx);

        assertNotNull(result, "read() must return a non-null DataFrame for PARQUET format");
        assertTrue(result.count() > 0, "Loaded Parquet DataFrame must contain at least 1 row");
    }

    // ---------------------------------------------------------------------------
    // AC4: Unrecognized format throws UnsupportedOperationException
    // ---------------------------------------------------------------------------

    @Test
    void readThrowsForUnknownFormat() {
        DatasetReader reader = new DatasetReader(spark);
        DatasetContext ctx = ctx("some_dataset", DatasetContext.FORMAT_UNKNOWN);

        assertThrows(UnsupportedOperationException.class,
                () -> reader.read(ctx),
                "read() must throw UnsupportedOperationException for FORMAT_UNKNOWN");
    }

    // ---------------------------------------------------------------------------
    // Constructor validation
    // ---------------------------------------------------------------------------

    @Test
    void constructorThrowsOnNullSparkSession() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatasetReader(null),
                "DatasetReader constructor must reject null SparkSession");
    }

    // ---------------------------------------------------------------------------
    // Argument validation in read()
    // ---------------------------------------------------------------------------

    @Test
    void readThrowsOnNullContext() {
        DatasetReader reader = new DatasetReader(spark);

        assertThrows(IllegalArgumentException.class,
                () -> reader.read(null),
                "read() must throw IllegalArgumentException when ctx is null");
    }

    // ---------------------------------------------------------------------------
    // P1: Loaded Avro DataFrame preserves expected schema columns
    // ---------------------------------------------------------------------------

    @Test
    void readAvroDataFrameHasExpectedSchema() {
        String datasetName = createAvroDataset("avro_schema_test");
        DatasetReader reader = new DatasetReader(spark);
        DatasetContext ctx = ctx(datasetName, DatasetContext.FORMAT_AVRO);

        Dataset<Row> result = reader.read(ctx);

        List<String> columns = List.of(result.columns());
        assertTrue(columns.contains("id"),
                "Schema must contain 'id' column; got: " + columns);
        assertTrue(columns.contains("value"),
                "Schema must contain 'value' column; got: " + columns);
        assertEquals(2, result.count(),
                "DataFrame must contain exactly 2 rows matching the Avro fixture");
    }

    // ---------------------------------------------------------------------------
    // P2: Loaded Parquet DataFrame preserves expected schema columns (symmetric to P1)
    // ---------------------------------------------------------------------------

    @Test
    void readParquetDataFrameHasExpectedSchema() {
        String datasetName = createParquetDataset("parquet_schema_test");
        DatasetReader reader = new DatasetReader(spark);
        DatasetContext ctx = ctx(datasetName, DatasetContext.FORMAT_PARQUET);

        Dataset<Row> result = reader.read(ctx);

        List<String> columns = List.of(result.columns());
        assertTrue(columns.contains("id"),
                "Schema must contain 'id' column; got: " + columns);
        assertTrue(columns.contains("value"),
                "Schema must contain 'value' column; got: " + columns);
        assertEquals(2, result.count(),
                "DataFrame must contain exactly 2 rows matching the Parquet fixture");
    }

    // ---------------------------------------------------------------------------
    // AC4: Unrecognized format (not AVRO/PARQUET/UNKNOWN) → UnsupportedOperationException
    // ---------------------------------------------------------------------------

    @Test
    void readThrowsForUnrecognizedFormat() {
        DatasetReader reader = new DatasetReader(spark);
        DatasetContext ctx = ctx("some_dataset", "CSV");

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> reader.read(ctx),
                "read() must throw UnsupportedOperationException for unrecognized format strings");
        assertTrue(ex.getMessage().contains("CSV"),
                "Exception message must include the unrecognized format value; got: " + ex.getMessage());
    }
}
