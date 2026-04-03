package com.bank.dqs.reader;

import com.bank.dqs.model.DatasetContext;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bank.dqs.model.DatasetContext.FORMAT_AVRO;
import static com.bank.dqs.model.DatasetContext.FORMAT_PARQUET;
import static com.bank.dqs.model.DatasetContext.FORMAT_UNKNOWN;

/**
 * Reads dataset files from HDFS (Avro or Parquet) into Spark DataFrames.
 *
 * <p>Story 2.3: This class populates the {@code df} field of a {@link DatasetContext}
 * that was produced by {@code ConsumerZoneScanner} (story 2.2) with {@code df = null}.
 *
 * <p>Full HDFS path = {@code ctx.getParentPath() + "/" + ctx.getDatasetName()}.
 *
 * <p>Format routing:
 * <ul>
 *   <li>{@link DatasetContext#FORMAT_AVRO} → {@code spark.read().format("avro").load(path)}
 *   <li>{@link DatasetContext#FORMAT_PARQUET} → {@code spark.read().parquet(path)}
 *   <li>{@link DatasetContext#FORMAT_UNKNOWN} → throws {@link UnsupportedOperationException}
 * </ul>
 */
public class DatasetReader {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetReader.class);

    private final SparkSession spark;

    /**
     * @param spark active SparkSession to use for reading
     * @throws IllegalArgumentException if spark is null
     */
    public DatasetReader(SparkSession spark) {
        if (spark == null) {
            throw new IllegalArgumentException("SparkSession must not be null");
        }
        this.spark = spark;
    }

    /**
     * Read the dataset described by {@code ctx} into a Spark DataFrame.
     *
     * <p>The full data path is constructed as:
     * {@code ctx.getParentPath() + "/" + ctx.getDatasetName()}.
     *
     * @param ctx dataset context produced by the scanner (df may be null)
     * @return loaded DataFrame
     * @throws IllegalArgumentException      if ctx is null
     * @throws UnsupportedOperationException if ctx.getFormat() is {@link DatasetContext#FORMAT_UNKNOWN}
     */
    public Dataset<Row> read(DatasetContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("DatasetContext must not be null");
        }

        String dataPath = new Path(ctx.getParentPath(), ctx.getDatasetName()).toString();
        String format = ctx.getFormat();

        LOG.debug("Reading dataset: format={}, path={}", format, dataPath);

        switch (format) {
            case FORMAT_AVRO -> {
                Dataset<Row> df = spark.read().format("avro").load(dataPath);
                LOG.info("Loaded AVRO dataset: {}", ctx.getDatasetName());
                return df;
            }
            case FORMAT_PARQUET -> {
                Dataset<Row> df = spark.read().parquet(dataPath);
                LOG.info("Loaded PARQUET dataset: {}", ctx.getDatasetName());
                return df;
            }
            case FORMAT_UNKNOWN -> throw new UnsupportedOperationException(
                    "Dataset format could not be determined (FORMAT_UNKNOWN) for dataset: '"
                            + ctx.getDatasetName() + "' at path: " + dataPath
                            + ". Verify the HDFS path contains .avro or .parquet files "
                            + "and is accessible by the ConsumerZoneScanner.");
            default -> throw new UnsupportedOperationException(
                    "Unrecognized dataset format '" + format + "' for dataset: "
                            + ctx.getDatasetName());
        }
    }

    @Override
    public String toString() {
        return "DatasetReader{spark.appName=" + spark.conf().get("spark.app.name", "unknown") + "}";
    }
}
