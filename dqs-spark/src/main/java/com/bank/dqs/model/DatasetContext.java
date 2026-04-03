package com.bank.dqs.model;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.time.LocalDate;

/**
 * Immutable context for a single dataset being quality-checked.
 *
 * <p>Produced by {@code ConsumerZoneScanner} (story 2.2) and passed to every
 * {@code DqCheck.execute()} call. Also passed to {@code CheckFactory.getEnabledChecks()}
 * so the factory can match {@code dataset_name} against {@code check_config.dataset_pattern}.
 *
 * <p>{@code df} may be {@code null} in unit tests — factory and model tests do not
 * require a live SparkSession. Check implementations must guard against null df if they
 * use it outside of production execution paths.
 */
public final class DatasetContext {

    /** Format constant for Avro datasets. */
    public static final String FORMAT_AVRO    = "AVRO";

    /** Format constant for Parquet datasets. */
    public static final String FORMAT_PARQUET = "PARQUET";

    /** Format constant when the format cannot be determined. */
    public static final String FORMAT_UNKNOWN = "UNKNOWN";

    private final String        datasetName;
    private final String        lookupCode;
    private final LocalDate     partitionDate;
    private final String        parentPath;
    private final Dataset<Row>  df;
    private final String        format;

    /**
     * @param datasetName   HDFS path segment identifying the dataset (e.g.,
     *                      {@code "lob=retail/src_sys_nm=alpha/dataset=sales_daily"}).
     *                      Must not be null or blank.
     * @param lookupCode    LOB/owner lookup code resolved from {@code dataset_enrichment}.
     *                      May be {@code null} for datasets with no enrichment record.
     * @param partitionDate The partition date being processed. Must not be null.
     * @param parentPath    HDFS parent path passed to the Spark job. Must not be null or blank.
     * @param df            Spark DataFrame containing the dataset rows. May be {@code null}
     *                      in unit-test contexts.
     * @param format        Dataset file format — use {@link #FORMAT_AVRO},
     *                      {@link #FORMAT_PARQUET}, or {@link #FORMAT_UNKNOWN}.
     *                      Must not be null.
     * @throws IllegalArgumentException if any mandatory field is null or blank.
     */
    public DatasetContext(String datasetName, String lookupCode, LocalDate partitionDate,
                          String parentPath, Dataset<Row> df, String format) {
        if (datasetName == null || datasetName.isBlank()) {
            throw new IllegalArgumentException("datasetName must not be null or blank");
        }
        if (partitionDate == null) {
            throw new IllegalArgumentException("partitionDate must not be null");
        }
        if (parentPath == null || parentPath.isBlank()) {
            throw new IllegalArgumentException("parentPath must not be null or blank");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        this.datasetName   = datasetName;
        this.lookupCode    = lookupCode;
        this.partitionDate = partitionDate;
        this.parentPath    = parentPath;
        this.df            = df;
        this.format        = format;
    }

    /** Returns the dataset name / HDFS path segment (used for check_config LIKE matching). */
    public String getDatasetName()      { return datasetName; }

    /** Returns the LOB/owner lookup code, or {@code null} if unresolved. */
    public String getLookupCode()       { return lookupCode; }

    /** Returns the partition date being processed. */
    public LocalDate getPartitionDate() { return partitionDate; }

    /** Returns the HDFS parent path configured for this Spark job run. */
    public String getParentPath()       { return parentPath; }

    /**
     * Returns the Spark DataFrame containing dataset rows.
     * May be {@code null} in unit-test contexts where no SparkSession is available.
     */
    public Dataset<Row> getDf()         { return df; }

    /** Returns the dataset file format ({@code "AVRO"}, {@code "PARQUET"}, or {@code "UNKNOWN"}). */
    public String getFormat()           { return format; }

    @Override
    public String toString() {
        return "DatasetContext{datasetName='" + datasetName + "', lookupCode='" + lookupCode
                + "', partitionDate=" + partitionDate + ", parentPath='" + parentPath
                + "', format='" + format + "'}";
    }
}
