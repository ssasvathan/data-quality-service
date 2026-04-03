package com.bank.dqs.scanner;

import com.bank.dqs.model.DatasetContext;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Strategy interface for HDFS path scanning.
 *
 * <p>Different HDFS path structures require different scanner implementations.
 * Each implementation discovers dataset subdirectories under a given parent path
 * and returns {@link DatasetContext} objects ready for quality check execution.
 *
 * <p>See FR3: "System can support different HDFS path structures via an extensible interface."
 */
public interface PathScanner {

    /**
     * Scan the given parent path for datasets at the specified partition date.
     *
     * @param parentPath    HDFS parent path (e.g., "/prod/abc/data/consumerzone/")
     * @param partitionDate the partition date to scan for
     * @return list of DatasetContext objects discovered (df will be null -- loaded separately)
     * @throws IOException              if HDFS operations fail
     * @throws IllegalArgumentException if parentPath or partitionDate is null
     */
    List<DatasetContext> scan(String parentPath, LocalDate partitionDate) throws IOException;
}
