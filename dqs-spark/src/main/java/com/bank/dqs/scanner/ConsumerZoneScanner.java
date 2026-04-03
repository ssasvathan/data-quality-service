package com.bank.dqs.scanner;

import com.bank.dqs.model.DatasetContext;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans consumer-zone HDFS paths to discover datasets.
 *
 * <p>Expects the following directory layout:
 * <pre>
 * {parentPath}/
 *   partition_date={yyyyMMdd}/
 *     src_sys_nm={value}/
 *       part-00000.avro
 *       ...
 * </pre>
 *
 * <p>Each {@code src_sys_nm} subdirectory under the partition date directory is treated
 * as a dataset. The scanner extracts the dataset name, lookup code, partition date,
 * and file format, producing a {@link DatasetContext} with {@code df = null} (the
 * DataFrame is loaded later by DqsJob in story 2.3).
 */
public class ConsumerZoneScanner implements PathScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumerZoneScanner.class);
    private static final DateTimeFormatter PARTITION_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FileSystem fs;

    /**
     * @param fs Hadoop FileSystem to use for path scanning
     * @throws IllegalArgumentException if fs is null
     */
    public ConsumerZoneScanner(FileSystem fs) {
        if (fs == null) {
            throw new IllegalArgumentException("FileSystem must not be null");
        }
        this.fs = fs;
    }

    @Override
    public List<DatasetContext> scan(String parentPath, LocalDate partitionDate) throws IOException {
        if (parentPath == null) {
            throw new IllegalArgumentException("parentPath must not be null");
        }
        if (partitionDate == null) {
            throw new IllegalArgumentException("partitionDate must not be null");
        }

        String dateStr = partitionDate.format(PARTITION_DATE_FORMAT);
        Path partitionDir = new Path(parentPath, "partition_date=" + dateStr);

        if (!fs.exists(partitionDir) || !fs.getFileStatus(partitionDir).isDirectory()) {
            LOG.info("No data for partition date {}: path does not exist or is not a directory: {}",
                    dateStr, partitionDir);
            return List.of();
        }

        FileStatus[] subdirs = fs.listStatus(partitionDir);
        List<DatasetContext> results = new ArrayList<>();

        for (FileStatus subdir : subdirs) {
            if (!subdir.isDirectory()) {
                continue;
            }

            String dirName = subdir.getPath().getName();

            // Skip directories that don't match the expected src_sys_nm= pattern
            if (!dirName.startsWith("src_sys_nm=")) {
                LOG.debug("Skipping non-dataset directory: {}", dirName);
                continue;
            }

            String lookupCode = extractLookupCode(dirName);
            String format = detectFormat(subdir.getPath());

            if (format == null) {
                // Empty directory -- no files found
                LOG.warn("Empty dataset directory, skipping: {}", subdir.getPath());
                continue;
            }

            // datasetName: relative path used for check_config LIKE matching
            String datasetName = "src_sys_nm=" + lookupCode + "/partition_date=" + dateStr;

            DatasetContext ctx = new DatasetContext(
                    datasetName,
                    lookupCode,
                    partitionDate,
                    parentPath,
                    null,   // df loaded later by DqsJob (story 2.3)
                    format
            );

            results.add(ctx);
            LOG.debug("Discovered dataset: {}", ctx);
        }

        LOG.info("Scanned {} -- found {} datasets for partition date {}",
                parentPath, results.size(), dateStr);
        return results;
    }

    /**
     * Extract the lookup code from a directory name like "src_sys_nm=cb10".
     */
    private String extractLookupCode(String dirName) {
        int eqIdx = dirName.indexOf('=');
        if (eqIdx >= 0 && eqIdx < dirName.length() - 1) {
            return dirName.substring(eqIdx + 1);
        }
        LOG.warn("Unexpected directory name format (no '=' separator), using full name as lookupCode: {}",
                dirName);
        return dirName;
    }

    /**
     * Detect the dataset format by inspecting file extensions in the directory.
     * Skips hidden files (starting with '.' or '_') as these are typically Hadoop
     * metadata files (_SUCCESS, .crc, etc.), not data files.
     *
     * @return FORMAT_AVRO, FORMAT_PARQUET, FORMAT_UNKNOWN, or null if directory has no data files
     */
    private String detectFormat(Path dirPath) throws IOException {
        FileStatus[] files = fs.listStatus(dirPath);

        // Filter to only data files (not subdirectories or hidden/metadata files)
        boolean hasDataFiles = false;
        for (FileStatus file : files) {
            if (file.isDirectory()) {
                continue;
            }
            String fileName = file.getPath().getName();
            // Skip hidden files and Hadoop metadata files (_SUCCESS, .crc, etc.)
            if (fileName.startsWith(".") || fileName.startsWith("_")) {
                continue;
            }
            hasDataFiles = true;
            if (fileName.endsWith(".avro")) {
                return DatasetContext.FORMAT_AVRO;
            }
            if (fileName.endsWith(".parquet")) {
                return DatasetContext.FORMAT_PARQUET;
            }
        }

        if (!hasDataFiles) {
            return null; // signals empty directory (no data files)
        }
        return DatasetContext.FORMAT_UNKNOWN;
    }

    @Override
    public String toString() {
        return "ConsumerZoneScanner{fs=" + fs.getUri() + "}";
    }
}
