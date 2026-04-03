package com.bank.dqs.scanner;

import com.bank.dqs.model.DatasetContext;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerZoneScannerTest {

    private static FileSystem fs;

    @TempDir
    java.nio.file.Path tempDir;

    private ConsumerZoneScanner scanner;
    private String parentPath;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 3, 30);

    @BeforeAll
    static void initFileSystem() throws IOException {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        fs = FileSystem.get(conf);
    }

    @BeforeEach
    void setUp() {
        scanner = new ConsumerZoneScanner(fs);
        parentPath = tempDir.toAbsolutePath().toString();
    }

    @AfterAll
    static void closeFileSystem() throws IOException {
        if (fs != null) {
            fs.close();
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Create an HDFS-like directory structure under the temp directory. */
    private void createDatasetDir(String srcSysNm) throws IOException {
        Path dir = new Path(parentPath, "partition_date=20260330/src_sys_nm=" + srcSysNm);
        fs.mkdirs(dir);
    }

    /** Create a dummy file in a dataset directory to signal the format. */
    private void createFile(String srcSysNm, String fileName) throws IOException {
        Path filePath = new Path(parentPath,
                "partition_date=20260330/src_sys_nm=" + srcSysNm + "/" + fileName);
        fs.create(filePath).close();
    }

    // ---------------------------------------------------------------------------
    // AC4: PathScanner interface conformance
    // ---------------------------------------------------------------------------

    @Test
    void consumerZoneScannerImplementsPathScanner() {
        assertTrue(scanner instanceof PathScanner,
                "ConsumerZoneScanner must implement PathScanner interface");
    }

    // ---------------------------------------------------------------------------
    // Constructor validation
    // ---------------------------------------------------------------------------

    @Test
    void constructorThrowsOnNullFileSystem() {
        assertThrows(IllegalArgumentException.class, () -> new ConsumerZoneScanner(null));
    }

    // ---------------------------------------------------------------------------
    // AC1: Discover all dataset subdirectories
    // ---------------------------------------------------------------------------

    @Test
    void scanDiscoversAllDatasetSubdirectories() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");
        createDatasetDir("alpha");
        createFile("alpha", "part-00000.snappy.parquet");
        createDatasetDir("omni");
        createFile("omni", "part-00000.avro");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(3, results.size(), "Should discover all 3 dataset subdirectories");
    }

    // ---------------------------------------------------------------------------
    // AC2: Extract dataset name from path
    // ---------------------------------------------------------------------------

    @Test
    void scanExtractsDatasetNameFromPath() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        String datasetName = results.get(0).getDatasetName();
        assertTrue(datasetName.contains("src_sys_nm=cb10"),
                "datasetName should contain src_sys_nm=cb10, got: " + datasetName);
        assertTrue(datasetName.contains("partition_date=20260330"),
                "datasetName should contain partition_date=20260330, got: " + datasetName);
    }

    // ---------------------------------------------------------------------------
    // AC2: Extract lookup code from src_sys_nm
    // ---------------------------------------------------------------------------

    @Test
    void scanExtractsLookupCodeFromSrcSysNm() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");
        createDatasetDir("alpha");
        createFile("alpha", "part-00000.parquet");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(2, results.size());
        List<String> codes = results.stream()
                .map(DatasetContext::getLookupCode)
                .sorted()
                .toList();
        assertEquals(List.of("alpha", "cb10"), codes,
                "lookupCode should be the raw src_sys_nm value");
    }

    // ---------------------------------------------------------------------------
    // AC3: Extract partition date from path
    // ---------------------------------------------------------------------------

    @Test
    void scanExtractsPartitionDateFromPath() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals(PARTITION_DATE, results.get(0).getPartitionDate(),
                "partitionDate should match the provided LocalDate");
    }

    // ---------------------------------------------------------------------------
    // AC5: Format detection - Avro
    // ---------------------------------------------------------------------------

    @Test
    void scanDetectsAvroFormat() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");
        createFile("cb10", "part-00001.avro");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals(DatasetContext.FORMAT_AVRO, results.get(0).getFormat(),
                "Should detect AVRO format from .avro file extensions");
    }

    // ---------------------------------------------------------------------------
    // AC5: Format detection - Parquet
    // ---------------------------------------------------------------------------

    @Test
    void scanDetectsParquetFormat() throws IOException {
        createDatasetDir("alpha");
        createFile("alpha", "part-00000.snappy.parquet");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals(DatasetContext.FORMAT_PARQUET, results.get(0).getFormat(),
                "Should detect PARQUET format from .snappy.parquet extension");
    }

    // ---------------------------------------------------------------------------
    // AC5: Format detection - Unknown
    // ---------------------------------------------------------------------------

    @Test
    void scanReturnsUnknownFormatWhenNoRecognizedFiles() throws IOException {
        createDatasetDir("legacy");
        createFile("legacy", "data.csv");
        createFile("legacy", "metadata.json");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals(DatasetContext.FORMAT_UNKNOWN, results.get(0).getFormat(),
                "Should return UNKNOWN when no .avro or .parquet files found");
    }

    // ---------------------------------------------------------------------------
    // AC1, AC5: Empty partition_date directory - return empty list
    // ---------------------------------------------------------------------------

    @Test
    void scanReturnsEmptyListWhenPartitionDateDirMissing() throws IOException {
        // Do not create any directory structure - partition_date dir does not exist
        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertTrue(results.isEmpty(),
                "Should return empty list when partition_date directory does not exist");
    }

    // ---------------------------------------------------------------------------
    // AC1: Skip empty subdirectories
    // ---------------------------------------------------------------------------

    @Test
    void scanSkipsEmptySubdirectories() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");
        // Create empty dataset directory (has src_sys_nm= prefix but no files inside)
        createDatasetDir("empty_ds");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size(),
                "Should skip empty subdirectory and only return cb10");
        assertEquals("cb10", results.get(0).getLookupCode());
    }

    // ---------------------------------------------------------------------------
    // AC5: df is null on returned DatasetContext
    // ---------------------------------------------------------------------------

    @Test
    void scanReturnsDatasetContextWithNullDf() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertNull(results.get(0).getDf(),
                "df should be null - loaded later by DqsJob in story 2.3");
    }

    // ---------------------------------------------------------------------------
    // AC5: parentPath passthrough
    // ---------------------------------------------------------------------------

    @Test
    void scanSetsParentPathOnContext() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals(parentPath, results.get(0).getParentPath(),
                "parentPath should be passed through to DatasetContext");
    }

    // ---------------------------------------------------------------------------
    // Review finding: non-src_sys_nm directories are skipped
    // ---------------------------------------------------------------------------

    @Test
    void scanSkipsNonSrcSysNmDirectories() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");
        // Create a non-dataset directory (e.g., _temporary or staging) under partition_date
        Path nonDatasetDir = new Path(parentPath, "partition_date=20260330/_temporary");
        fs.mkdirs(nonDatasetDir);
        fs.create(new Path(nonDatasetDir, "some-file.tmp")).close();

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size(),
                "Should skip directories not matching src_sys_nm= pattern");
        assertEquals("cb10", results.get(0).getLookupCode());
    }

    // ---------------------------------------------------------------------------
    // Review finding: hidden files and _SUCCESS ignored in format detection
    // ---------------------------------------------------------------------------

    @Test
    void scanIgnoresHadoopMetadataFilesInFormatDetection() throws IOException {
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");
        // Add Hadoop metadata files that should be ignored
        createFile("cb10", "_SUCCESS");
        createFile("cb10", ".part-00000.avro.crc");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals(DatasetContext.FORMAT_AVRO, results.get(0).getFormat(),
                "Should detect AVRO format, ignoring _SUCCESS and .crc metadata files");
    }

    @Test
    void scanSkipsDirWithOnlyMetadataFiles() throws IOException {
        createDatasetDir("meta_only");
        createFile("meta_only", "_SUCCESS");
        createFile("meta_only", ".hidden_file");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertTrue(results.isEmpty(),
                "Should skip directory containing only metadata files (no data files)");
    }

    // ---------------------------------------------------------------------------
    // Review finding: mixed-format directory behavior
    // ---------------------------------------------------------------------------

    @Test
    void scanHandlesMixedFormatDirectory() throws IOException {
        createDatasetDir("mixed");
        createFile("mixed", "part-00000.avro");
        createFile("mixed", "part-00001.parquet");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        String format = results.get(0).getFormat();
        // First recognized format wins -- either AVRO or PARQUET is acceptable
        assertTrue(DatasetContext.FORMAT_AVRO.equals(format) || DatasetContext.FORMAT_PARQUET.equals(format),
                "Mixed-format directory should return first recognized format, got: " + format);
    }

    // ---------------------------------------------------------------------------
    // Argument validation
    // ---------------------------------------------------------------------------

    @Test
    void scanThrowsOnNullParentPath() {
        assertThrows(IllegalArgumentException.class,
                () -> scanner.scan(null, PARTITION_DATE));
    }

    @Test
    void scanThrowsOnNullPartitionDate() {
        assertThrows(IllegalArgumentException.class,
                () -> scanner.scan(parentPath, null));
    }
}
