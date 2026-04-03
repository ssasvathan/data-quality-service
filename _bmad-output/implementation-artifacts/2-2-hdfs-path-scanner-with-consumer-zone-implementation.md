# Story 2.2: HDFS Path Scanner with Consumer Zone Implementation

Status: done

## Story

As a **platform operator**,
I want an extensible HDFS path scanner that discovers dataset subdirectories and extracts dataset name, lookup code, and partition date,
so that DQS automatically finds all datasets under a configured parent path without manual registration.

## Acceptance Criteria

1. **Given** a configured parent HDFS path (e.g., `/prod/abc/data/consumerzone/`) **When** the `ConsumerZoneScanner` scans the path **Then** it discovers all dataset subdirectories (e.g., `src_sys_nm=cb10/partition_date=20260330`)
2. **And** it extracts the dataset name including lookup code from the path structure
3. **And** it extracts the partition date from the path
4. **And** `PathScanner` is an interface that `ConsumerZoneScanner` implements, allowing future scanner implementations for different path structures
5. **And** the scanner returns a list of `DatasetContext` objects ready for check execution

## Tasks / Subtasks

- [x] Task 1: Create `PathScanner` interface (AC: #4)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/scanner/PathScanner.java`
  - [x] Define method: `List<DatasetContext> scan(String parentPath, LocalDate partitionDate)` throws `IOException`
  - [x] Javadoc noting extensibility — future scanners for different HDFS path structures implement this

- [x] Task 2: Create `ConsumerZoneScanner` (AC: #1, #2, #3, #4, #5)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java` implementing `PathScanner`
  - [x] Accept Hadoop `FileSystem` via constructor (injection for testability — mock in tests, real HDFS in prod)
  - [x] Scan logic:
    1. Build the scan path: `{parentPath}/partition_date={yyyyMMdd}` using the provided `partitionDate`
    2. Use `FileSystem.listStatus()` to list subdirectories under the partition_date directory
    3. Each subdirectory represents a dataset (e.g., `src_sys_nm=cb10`)
  - [x] For each discovered subdirectory:
    1. Extract dataset name: construct path segment as `src_sys_nm={value}/partition_date={date}` relative to parent
    2. Extract the `src_sys_nm` value as the raw lookup code (e.g., `cb10`)
    3. Parse partition date from the path segment (format `yyyyMMdd`)
    4. Detect format by checking file extensions in the directory (`.avro` -> AVRO, `.parquet`/`.snappy.parquet` -> PARQUET, else UNKNOWN)
    5. Create `DatasetContext` with: datasetName (full relative path), lookupCode (src_sys_nm value), partitionDate, parentPath, `null` df (loaded later by DqsJob in story 2.3), and detected format
  - [x] Return `List<DatasetContext>` for all discovered datasets
  - [x] Handle edge cases:
    - If the partition_date directory does not exist, return empty list (log INFO — no data for this date)
    - If a subdirectory has no files, skip it (log WARN — empty dataset directory)
    - If FileSystem operations throw IOException, let it propagate (caller handles)

- [x] Task 3: Write `PathScanner` / `ConsumerZoneScanner` tests (AC: all)
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java`
  - [x] Use Hadoop `LocalFileSystem` with `@TempDir` for lightweight HDFS API testing (no MiniDFSCluster needed)
  - [x] Test cases:
    - `scanDiscoversAllDatasetSubdirectories` — multiple src_sys_nm dirs under partition_date
    - `scanExtractsDatasetNameFromPath` — verify datasetName includes full relative path
    - `scanExtractsLookupCodeFromSrcSysNm` — verify lookupCode = src_sys_nm value
    - `scanExtractsPartitionDateFromPath` — verify partitionDate is correctly parsed
    - `scanDetectsAvroFormat` — directory with .avro files
    - `scanDetectsParquetFormat` — directory with .parquet/.snappy.parquet files
    - `scanReturnsUnknownFormatWhenNoRecognizedFiles` — mixed or unknown files
    - `scanReturnsEmptyListWhenPartitionDateDirMissing` — no data for date
    - `scanSkipsEmptySubdirectories` — subdirectory with no files
    - `scanReturnsDatasetContextWithNullDf` — df is null (loaded later by story 2.3)
    - `scanSetsParentPathOnContext` — verify parentPath passthrough
    - `constructorThrowsOnNullFileSystem` — constructor validation
    - `scanThrowsOnNullParentPath` — argument validation
    - `scanThrowsOnNullPartitionDate` — argument validation
    - `consumerZoneScannerImplementsPathScanner` — interface conformance

## Dev Notes

### Existing Code to Build On

The following files already exist from story 2-1 -- do NOT modify them:
- `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java` -- holds dataset_name, lookup_code, partition_date, parent_path, df, format. This is what `ConsumerZoneScanner` produces.
- `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java` -- has `EXPIRY_SENTINEL`. Not needed for this story but do NOT touch.
- `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java` -- strategy interface. Not needed for this story.
- `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java` -- factory for checks. Not needed for this story.
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` -- placeholder. Do NOT modify (story 2.10).
- `dqs-spark/pom.xml` -- Maven config. Already has Spark (provided scope), JUnit 5, H2, Jackson, JDBC. Spark includes `hadoop-client` transitively, so Hadoop `FileSystem` API is available without adding new dependencies.
- `dqs-spark/config/application.properties` -- DB config. Not needed for this story.

### Package Structure (Strict)

Place files exactly in these locations per architecture spec:

```
dqs-spark/src/main/java/com/bank/dqs/
  scanner/
    PathScanner.java           <- NEW (interface)
    ConsumerZoneScanner.java   <- NEW (implementation)

dqs-spark/src/test/java/com/bank/dqs/
  scanner/
    ConsumerZoneScannerTest.java  <- NEW
```

The `scanner/` package is new. Maven picks up new packages under `src/main/java/` automatically.

### Consumer Zone HDFS Path Structure

The consumer zone uses a specific directory layout. This is the known pattern:

```
/prod/abc/data/consumerzone/           <- parent path (configured per orchestrator run)
  partition_date=20260330/             <- date-partitioned directory
    src_sys_nm=cb10/                   <- dataset subdirectory
      part-00000.avro
      part-00001.avro
    src_sys_nm=alpha/
      part-00000.snappy.parquet
    src_sys_nm=omni/                   <- legacy naming (resolved in story 2.4)
      part-00000.avro
```

Key extraction rules:
- **parentPath**: The configured root (e.g., `/prod/abc/data/consumerzone/`)
- **partitionDate**: Parsed from the `partition_date=yyyyMMdd` directory name
- **datasetName**: Relative path from parentPath, e.g., `src_sys_nm=cb10/partition_date=20260330` (this is used for `check_config.dataset_pattern` LIKE matching)
- **lookupCode**: The `src_sys_nm` value (e.g., `cb10`). Legacy codes like `omni` are resolved to proper lookup codes in story 2.4 via `dataset_enrichment` table -- for this story, just pass the raw value.
- **format**: Determined by file extensions in the directory: `.avro` = AVRO, `.parquet`/`.snappy.parquet` = PARQUET, else UNKNOWN. Use `DatasetContext.FORMAT_AVRO`, `FORMAT_PARQUET`, `FORMAT_UNKNOWN` constants.
- **df**: Set to `null`. The DataFrame is loaded later by `DqsJob` (story 2.3) after scanner returns the list.

### PathScanner Interface Design

```java
package com.bank.dqs.scanner;

import com.bank.dqs.model.DatasetContext;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Strategy interface for HDFS path scanning.
 * Different HDFS path structures require different scanner implementations.
 * See FR3: "System can support different HDFS path structures via an extensible interface."
 */
public interface PathScanner {
    /**
     * Scan the given parent path for datasets at the specified partition date.
     *
     * @param parentPath    HDFS parent path (e.g., "/prod/abc/data/consumerzone/")
     * @param partitionDate the partition date to scan for
     * @return list of DatasetContext objects discovered (df will be null — loaded separately)
     * @throws IOException if HDFS operations fail
     */
    List<DatasetContext> scan(String parentPath, LocalDate partitionDate) throws IOException;
}
```

### ConsumerZoneScanner Implementation Guidance

```java
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

public class ConsumerZoneScanner implements PathScanner {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerZoneScanner.class);
    private static final DateTimeFormatter PARTITION_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FileSystem fs;

    public ConsumerZoneScanner(FileSystem fs) {
        if (fs == null) {
            throw new IllegalArgumentException("FileSystem must not be null");
        }
        this.fs = fs;
    }

    @Override
    public List<DatasetContext> scan(String parentPath, LocalDate partitionDate) throws IOException {
        // 1. Build partition path: {parentPath}/partition_date={yyyyMMdd}
        // 2. Check if path exists — return empty list if not
        // 3. List subdirectories (each is a dataset, e.g., src_sys_nm=cb10)
        // 4. For each subdirectory:
        //    a. Extract src_sys_nm value as lookupCode
        //    b. Detect format from file extensions
        //    c. Build datasetName as relative path
        //    d. Create DatasetContext (df = null)
        // 5. Return list
    }
}
```

Key implementation details:
- Use `FileSystem.listStatus(Path)` to list subdirectories
- Check `FileStatus.isDirectory()` to filter only directories
- Use `FileSystem.listStatus(subdirPath)` on each dataset directory to check file extensions for format detection
- For format detection, iterate files and check names: `.avro` suffix = AVRO, `.parquet` or `.snappy.parquet` suffix = PARQUET. If mixed, prefer the first recognized format. If none recognized, use UNKNOWN.
- The `src_sys_nm` value is extracted from the directory name by parsing after `src_sys_nm=`. Use `dirName.substring(dirName.indexOf('=') + 1)` or similar.
- Validate `parentPath` and `partitionDate` are not null (throw `IllegalArgumentException`)

### Hadoop FileSystem API Notes

The `FileSystem` class is from `org.apache.hadoop.fs` package, available transitively through the Spark dependency (Spark depends on `hadoop-client`). No new Maven dependencies needed.

Key API methods:
- `FileSystem.exists(Path)` -- check if a path exists
- `FileSystem.listStatus(Path)` -- list files/directories at a path, returns `FileStatus[]`
- `FileStatus.getPath()` -- get the Path object
- `FileStatus.isDirectory()` -- check if entry is a directory
- `Path.getName()` -- get the last component of the path (e.g., `src_sys_nm=cb10`)

For Spark-managed FileSystem access in production:
```java
// In DqsJob (story 2.10), FileSystem is obtained from SparkSession:
FileSystem fs = FileSystem.get(spark.sparkContext().hadoopConfiguration());
ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs);
```

### Testing Strategy -- Mocking FileSystem

Use a **mock FileSystem** approach for unit tests. Do NOT use `MiniDFSCluster` (heavyweight, slow, requires native Hadoop libs). Instead, use Hadoop's built-in local filesystem or Mockito-style mocking.

Recommended approach: Use **Hadoop `LocalFileSystem`** with temp directories. This is the lightest-weight way to test `FileSystem` API calls without mocking every method.

```java
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

// In @BeforeAll or @BeforeEach:
Configuration conf = new Configuration();
conf.set("fs.defaultFS", "file:///");
FileSystem fs = FileSystem.get(conf);
// Create temp directory structure matching consumer zone pattern
// fs.mkdirs(new Path(tempDir, "partition_date=20260330/src_sys_nm=cb10"));
// Create dummy files to signal format:
// fs.create(new Path(..., "part-00000.avro")).close();
```

This uses real FileSystem API calls against the local filesystem, no mocking framework needed. JUnit 5 `@TempDir` provides automatic cleanup.

Test lifecycle:
- `@BeforeAll`: Create `FileSystem` instance with local config
- `@BeforeEach`: Create fresh temp directory with HDFS-like structure, populate with dummy files
- `@AfterEach`: Clean up temp directory
- `@AfterAll`: Close FileSystem

### What NOT to Do in This Story

- **Do NOT modify `DatasetContext.java`** -- it already has all needed fields (datasetName, lookupCode, partitionDate, parentPath, df, format)
- **Do NOT implement dataset_enrichment lookup** -- that is story 2.4. Pass the raw `src_sys_nm` value as `lookupCode`.
- **Do NOT load DataFrames** -- that is story 2.3. Set `df = null` in all DatasetContext objects returned by the scanner.
- **Do NOT modify `DqsJob.java`** -- it's a placeholder until story 2.10
- **Do NOT modify `pom.xml`** -- Hadoop FileSystem API is already available transitively through Spark dependency
- **Do NOT create integration tests that require a running HDFS cluster** -- use local FileSystem for tests
- **Do NOT add Mockito or other mocking frameworks** -- use Hadoop LocalFileSystem with temp dirs
- **Do NOT use `MiniDFSCluster`** -- too heavyweight, requires native Hadoop libs that may not be installed
- **Do NOT create any files outside `dqs-spark/`** -- this story is entirely within the Spark component
- **Do NOT hardcode any sentinel timestamps** -- not needed for this story, but rule reminder
- **Do NOT implement `--date` parameter handling** -- that is story 2.3 (DqsJob parses CLI args). The scanner accepts `partitionDate` as a `LocalDate` parameter.

### Java Rules Enforced in This Story

- **Constructor validation** with `IllegalArgumentException` for null FileSystem, null parentPath, null partitionDate
- **Full generic types**: `List<DatasetContext>`, `FileStatus[]` -- never raw types
- **Logging**: Use SLF4J (`LoggerFactory.getLogger(...)`) -- Spark provides SLF4J transitively
- **`throws IOException`** declared on scan method -- checked exception propagation for HDFS failures
- **Static imports** for format constants: `import static com.bank.dqs.model.DatasetContext.FORMAT_AVRO` etc. (optional but clean)
- **No raw types, no `any` style generics**
- **Test naming**: `<action><expectation>` pattern (e.g., `scanDiscoversAllDatasetSubdirectories`)

### Testing With Maven

```bash
cd dqs-spark
mvn test
```

Run just the new test:
```bash
cd dqs-spark
mvn test -Dtest="ConsumerZoneScannerTest"
```

The `pom.xml` is already configured with:
- Java 21 JVM for test fork
- All required `--add-opens` flags for Spark/Hadoop reflection
- JUnit Jupiter (JUnit 5)

### Cross-Story Context

- **Story 2.1 (DONE)**: Created `DatasetContext`, `DqCheck`, `CheckFactory`, `DqMetric`, `MetricNumeric`, `MetricDetail`. This story produces `DatasetContext` objects using the scanner.
- **Story 2.3 (NEXT)**: Will load DataFrames from the discovered paths. Uses the `DatasetContext` list from the scanner and populates the `df` field.
- **Story 2.4**: Will add `dataset_enrichment` lookup to resolve legacy `src_sys_nm` values to proper lookup codes. Will modify or wrap scanner output.
- **Story 2.10**: `DqsJob` will call `ConsumerZoneScanner.scan()`, then load DataFrames (2.3), then run checks, then batch-write results.

### Previous Story Intelligence (from 2-1)

Key patterns established in story 2-1 that apply here:
- Constructor validation with `IllegalArgumentException` for mandatory parameters -- apply same to `ConsumerZoneScanner` and argument validation in `scan()`
- `toString()` on models for debugging -- consider adding `toString()` if helpful for scanner logging
- Instance-based design (not static/singleton) for `CheckFactory` -- same approach for `ConsumerZoneScanner` (takes `FileSystem` in constructor)
- H2 in-memory DB for tests that need DB -- not needed here, use local filesystem instead
- Test naming: `<action><expectation>` -- follow same pattern
- Test helpers: helper methods to reduce test boilerplate (e.g., `ctx()` helper in CheckFactoryTest) -- create similar helpers for scanner tests

### Review Findings from Story 2-1

Patterns to apply proactively (learned from code review):
- Add null guard on constructor parameters (`FileSystem fs` must not be null)
- Add `toString()` on any new classes for debugging
- Use descriptive test DB/filesystem names to avoid collision

### Project Structure Notes

- All files are in `dqs-spark/` -- this is the Spark component (Java/Maven)
- Zero files created outside `dqs-spark/`
- The `scanner/` package is new -- Maven will pick it up automatically since it's under `src/main/java/`
- Test directory `src/test/java/com/bank/dqs/scanner/` is also new -- Maven Surefire discovers tests automatically

### References

- Story 2.2 AC: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.2)
- Package structure: `_bmad-output/planning-artifacts/architecture.md` -- Structure Patterns -- dqs-spark (scanner/ package)
- Java rules: `_bmad-output/project-context.md` -- Language-Specific Rules -- Java
- Testing rules: `_bmad-output/project-context.md` -- Testing Rules -- dqs-spark
- `DatasetContext` model: `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java` (existing, from story 2-1)
- FR1-FR3: `_bmad-output/planning-artifacts/prd.md` -- Data Discovery & Ingestion
- Architecture data flow: `_bmad-output/planning-artifacts/architecture.md` -- Data Flow (step 4: Spark scans HDFS, discovers datasets)
- Existing pom.xml: `dqs-spark/pom.xml` (Spark provided scope includes hadoop-client transitively)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (claude-opus-4-6)

### Debug Log References

- RED phase: Test compilation failed as expected (PathScanner, ConsumerZoneScanner classes missing)
- GREEN phase: All 15 tests pass after implementation
- Full regression: 57/57 tests pass (15 new + 42 existing), zero failures

### Completion Notes List

- Created `PathScanner` interface with `scan(String parentPath, LocalDate partitionDate)` method, Javadoc documenting extensibility for future scanner implementations (AC4)
- Implemented `ConsumerZoneScanner` with constructor injection of Hadoop `FileSystem` for testability (AC1-AC5)
- Scan logic: builds partition path `{parentPath}/partition_date={yyyyMMdd}`, lists subdirectories via `FileSystem.listStatus()`, extracts `src_sys_nm` value as lookupCode, detects format from file extensions (.avro -> AVRO, .parquet/.snappy.parquet -> PARQUET, else UNKNOWN)
- Edge cases handled: missing partition_date dir returns empty list (INFO log), empty subdirectories skipped (WARN log), IOExceptions propagated to caller
- Constructor and argument validation with `IllegalArgumentException` for null parameters
- Added `toString()` on `ConsumerZoneScanner` for debugging
- Tests use Hadoop `LocalFileSystem` with JUnit 5 `@TempDir` -- no MiniDFSCluster, no Mockito, no running HDFS
- All DatasetContext objects returned with `df = null` (loaded later by DqsJob in story 2.3)

### File List

- `dqs-spark/src/main/java/com/bank/dqs/scanner/PathScanner.java` — NEW: Strategy interface for HDFS path scanning
- `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java` — NEW: Consumer zone scanner implementation
- `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java` — NEW: 15 unit tests for PathScanner/ConsumerZoneScanner

### Review Findings

- [x] [Review][Patch] Filter non-src_sys_nm directories under partition_date to prevent processing of _temporary, staging, or other non-dataset directories [ConsumerZoneScanner.java:79-83] -- FIXED
- [x] [Review][Patch] Skip hidden files and Hadoop metadata files (_SUCCESS, .crc) in format detection to avoid false FORMAT_UNKNOWN results [ConsumerZoneScanner.java:146-148] -- FIXED
- [x] [Review][Patch] Add WARN log in extractLookupCode fallback path when directory name has no '=' separator [ConsumerZoneScanner.java:123-124] -- FIXED
- [x] [Review][Patch] Add isDirectory() guard on partition_date path to handle edge case where path exists as file not directory [ConsumerZoneScanner.java:63] -- FIXED
- [x] [Review][Patch] Add test for non-src_sys_nm directory filtering (scanSkipsNonSrcSysNmDirectories) -- FIXED
- [x] [Review][Patch] Add test for Hadoop metadata file filtering (scanIgnoresHadoopMetadataFilesInFormatDetection, scanSkipsDirWithOnlyMetadataFiles) -- FIXED
- [x] [Review][Patch] Add test for mixed-format directory behavior (scanHandlesMixedFormatDirectory) -- FIXED
- [x] [Review][Defer] extractLookupCode WARN log branch unreachable after src_sys_nm= prefix filter -- deferred, defensive code for future scanner changes

## Change Log

- 2026-04-03: Story created. Status set to ready-for-dev.
- 2026-04-03: Implementation complete. PathScanner interface + ConsumerZoneScanner + 15 tests. All 57 tests pass (zero regressions). Status set to review.
- 2026-04-03: Code review complete. 7 patch findings fixed, 1 deferred, 6 dismissed. Added 4 new tests (19 total). All 61 tests pass. Status set to done.
