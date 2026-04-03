---
stepsCompleted:
  - step-01-preflight-and-context
  - step-02-generation-mode
  - step-03-test-strategy
  - step-04-generate-tests
  - step-04c-aggregate
  - step-05-validate-and-complete
lastStep: step-05-validate-and-complete
lastSaved: '2026-04-03'
storyId: 2-2-hdfs-path-scanner-with-consumer-zone-implementation
tddPhase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/2-2-hdfs-path-scanner-with-consumer-zone-implementation.md
  - _bmad-output/project-context.md
  - _bmad-output/planning-artifacts/architecture.md
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/test/java/com/bank/dqs/model/DatasetContextTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java
---

# ATDD Checklist: Story 2-2 — HDFS Path Scanner with Consumer Zone Implementation

## Step 1: Preflight & Context

- **Stack detected:** `backend` (Java/Maven — dqs-spark component, JUnit5 + Hadoop LocalFileSystem)
- **Story file:** `_bmad-output/implementation-artifacts/2-2-hdfs-path-scanner-with-consumer-zone-implementation.md`
- **Status at start:** `ready-for-dev`
- **Prerequisites verified:**
  - [x] Story has clear acceptance criteria (AC1–AC5)
  - [x] `dqs-spark/pom.xml` exists with JUnit5, Spark (provided scope includes hadoop-client transitively)
  - [x] `DatasetContext.java` exists from story 2-1 with FORMAT_AVRO, FORMAT_PARQUET, FORMAT_UNKNOWN constants
  - [x] Test framework: JUnit5 (Maven Surefire, Java 21 forked JVM with `--add-opens` flags)
  - [x] Hadoop `FileSystem` API available transitively through Spark dependency — no new deps needed
  - [x] Existing test patterns established in story 2-1: `@BeforeAll`/`@BeforeEach`/`@AfterAll` lifecycle, helper methods, `<action><expectation>` naming

---

## Step 2: Generation Mode

**Mode selected:** AI generation (backend Java — no browser recording needed)
**Resolved execution mode:** Sequential (no subagent/agent-team runtime available)

---

## Step 3: Test Strategy

### Acceptance Criteria to Test Scenario Mapping

| AC | Scenario | Level | Priority | Test Method |
|---|---|---|---|---|
| AC1 | Scanner discovers all dataset subdirectories under partition_date | Unit | P0 | `scanDiscoversAllDatasetSubdirectories` |
| AC2 | Scanner extracts dataset name including lookup code from path structure | Unit | P0 | `scanExtractsDatasetNameFromPath` |
| AC3 | Scanner extracts partition date from path | Unit | P0 | `scanExtractsPartitionDateFromPath` |
| AC4 | PathScanner is an interface that ConsumerZoneScanner implements | Unit | P0 | `consumerZoneScannerImplementsPathScanner` |
| AC4 | PathScanner.scan method signature accepts parentPath and partitionDate | Unit | P0 | (covered by interface conformance — compile-time check) |
| AC5 | Scanner returns list of DatasetContext objects ready for check execution | Unit | P0 | `scanReturnsDatasetContextList` |
| AC1,5 | Scanner returns empty list when partition_date dir missing | Unit | P0 | `scanReturnsEmptyListWhenPartitionDateDirMissing` |
| AC2 | Scanner extracts lookupCode from src_sys_nm directory name | Unit | P0 | `scanExtractsLookupCodeFromSrcSysNm` |
| AC5 | Scanner detects Avro format from .avro file extensions | Unit | P0 | `scanDetectsAvroFormat` |
| AC5 | Scanner detects Parquet format from .parquet/.snappy.parquet extensions | Unit | P0 | `scanDetectsParquetFormat` |
| AC5 | Scanner returns UNKNOWN format when no recognized file extensions | Unit | P1 | `scanReturnsUnknownFormatWhenNoRecognizedFiles` |
| AC1 | Scanner skips empty subdirectories (no files) | Unit | P1 | `scanSkipsEmptySubdirectories` |
| AC5 | Scanner sets df to null on all returned DatasetContext objects | Unit | P0 | `scanReturnsDatasetContextWithNullDf` |
| AC5 | Scanner sets parentPath correctly on returned contexts | Unit | P0 | `scanSetsParentPathOnContext` |
| — | Constructor rejects null FileSystem | Unit | P0 | `constructorThrowsOnNullFileSystem` |
| — | scan() rejects null parentPath | Unit | P0 | `scanThrowsOnNullParentPath` |
| — | scan() rejects null partitionDate | Unit | P0 | `scanThrowsOnNullPartitionDate` |

### Test Levels Selected

- **Unit tests only** — no integration or E2E tests needed. All tests use Hadoop `LocalFileSystem` with `@TempDir` for lightweight HDFS API testing against the local filesystem. No mocking frameworks, no MiniDFSCluster, no running HDFS cluster.

### TDD Red Phase Confirmation

All test code imports `com.bank.dqs.scanner.PathScanner` and `com.bank.dqs.scanner.ConsumerZoneScanner` — classes that do not exist yet. Maven compilation fails until implementation is complete. This is the natural Java red phase; no `@Disabled` annotation needed.

---

## Step 4: Test Generation (Sequential Mode)

### Worker A (Unit Tests) — COMPLETE

**Test file to create:**

| File | Tests | Description |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java` | 14 | AC1–AC5: PathScanner interface, ConsumerZoneScanner scan behavior |

**Total test methods: 14** (all JUnit5 `@Test` methods, no `@Disabled`)

**Red phase mechanism:** Tests fail to compile because `PathScanner` and `ConsumerZoneScanner` classes do not exist yet.

### Worker B (E2E Tests) — N/A

Not applicable. This story has no browser interactions, no API endpoints, and no frontend components. Pure backend Java interface and implementation using Hadoop FileSystem API.

---

### Test File: ConsumerZoneScannerTest.java

**Location:** `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java`

```java
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
    // AC5: Format detection — Avro
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
    // AC5: Format detection — Parquet
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
    // AC5: Format detection — Unknown
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
    // AC1, AC5: Empty partition_date directory — return empty list
    // ---------------------------------------------------------------------------

    @Test
    void scanReturnsEmptyListWhenPartitionDateDirMissing() throws IOException {
        // Do not create any directory structure — partition_date dir does not exist
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
        // Create empty directory — no files inside
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
                "df should be null — loaded later by DqsJob in story 2.3");
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
```

---

## Step 4C: Aggregation

### TDD Red Phase Validation

All tests:
- [x] Import `com.bank.dqs.scanner.PathScanner` and `com.bank.dqs.scanner.ConsumerZoneScanner` (non-existent classes) -> compilation fails until implementation complete
- [x] Assert EXPECTED behavior per AC (not placeholder assertions)
- [x] Use Hadoop `LocalFileSystem` with `@TempDir` for lightweight HDFS API testing
- [x] `@BeforeAll` creates shared `FileSystem` instance (per project testing rules)
- [x] `@BeforeEach` creates fresh scanner and temp-dir-based parentPath (test isolation)
- [x] `@AfterAll` closes FileSystem
- [x] Helper methods (`createDatasetDir`, `createFile`) reduce test boilerplate (pattern from story 2-1)
- [x] No `@Disabled` (intentional — tests MUST fail at compile time until impl exists)
- [x] No Mockito or other mocking frameworks (uses real Hadoop LocalFileSystem)
- [x] No MiniDFSCluster (too heavyweight)
- [x] Test naming follows `<action><expectation>` pattern

### Summary Statistics

| Metric | Value |
|---|---|
| Total test methods | 14 |
| ConsumerZoneScannerTest | 14 |
| Unit tests (LocalFileSystem) | 14 |
| Integration tests | 0 |
| E2E tests | 0 |
| Execution mode | Sequential |
| Test framework | JUnit5 / Maven Surefire |
| Java version (test JVM) | Java 21 (configured in pom.xml) |
| New dependencies needed | None (Hadoop API via Spark transitive) |

---

## Step 5: Validation & Completion

### Checklist Validation

- [x] Prerequisites satisfied (`pom.xml` has JUnit5, Spark provides Hadoop FileSystem API transitively)
- [x] 1 test file to be created under `dqs-spark/src/test/java/com/bank/dqs/scanner/`
- [x] All 5 ACs covered by at least one test method
- [x] No `@Disabled` (correct — compilation failure is the red phase)
- [x] `@BeforeAll`/`@BeforeEach`/`@AfterAll` lifecycle used correctly
- [x] `@TempDir` provides automatic cleanup between tests
- [x] Helper methods follow story 2-1 patterns (concise, reduce boilerplate)
- [x] No `DatasetContext.java` modified
- [x] No `DqsJob.java` or `DqsConstants.java` modified
- [x] No `pom.xml` modified
- [x] No Mockito, no MiniDFSCluster, no running HDFS cluster required
- [x] Test package mirrors planned main package (`com.bank.dqs.scanner`)
- [x] No files created outside `dqs-spark/`
- [x] No temp artifacts in random locations — all output in `_bmad-output/test-artifacts/`

### Generated Files

| File | Type | Description |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java` | NEW (to create) | 14 unit tests for PathScanner/ConsumerZoneScanner |

### Files NOT Modified

| File | Reason |
|---|---|
| `dqs-spark/pom.xml` | Already correct — Hadoop API available via Spark transitive dep |
| `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java` | Existing from story 2-1 — do NOT modify |
| `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` | Placeholder, untouched until story 2.10 |
| `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java` | Existing — not needed for this story |

### Acceptance Criteria Coverage

| AC | Covered By | Status |
|---|---|---|
| AC1: ConsumerZoneScanner discovers all dataset subdirectories | `scanDiscoversAllDatasetSubdirectories`, `scanReturnsEmptyListWhenPartitionDateDirMissing`, `scanSkipsEmptySubdirectories` | COVERED |
| AC2: Extracts dataset name including lookup code from path structure | `scanExtractsDatasetNameFromPath`, `scanExtractsLookupCodeFromSrcSysNm` | COVERED |
| AC3: Extracts partition date from path | `scanExtractsPartitionDateFromPath` | COVERED |
| AC4: PathScanner is an interface, ConsumerZoneScanner implements it | `consumerZoneScannerImplementsPathScanner` (+ compile-time interface conformance) | COVERED |
| AC5: Returns list of DatasetContext objects ready for execution | `scanDetectsAvroFormat`, `scanDetectsParquetFormat`, `scanReturnsUnknownFormatWhenNoRecognizedFiles`, `scanReturnsDatasetContextWithNullDf`, `scanSetsParentPathOnContext` | COVERED |

### Test Scenarios by Category

**Happy Path (P0):**
1. `scanDiscoversAllDatasetSubdirectories` — 3 datasets under partition_date, all discovered
2. `scanExtractsDatasetNameFromPath` — datasetName contains src_sys_nm and partition_date segments
3. `scanExtractsLookupCodeFromSrcSysNm` — lookupCode is the raw src_sys_nm value (cb10, alpha)
4. `scanExtractsPartitionDateFromPath` — partitionDate matches the provided LocalDate
5. `scanDetectsAvroFormat` — .avro files yield FORMAT_AVRO
6. `scanDetectsParquetFormat` — .snappy.parquet files yield FORMAT_PARQUET
7. `scanReturnsDatasetContextWithNullDf` — df is null on all returned contexts
8. `scanSetsParentPathOnContext` — parentPath is passed through unchanged
9. `consumerZoneScannerImplementsPathScanner` — interface conformance check

**Edge Cases (P0–P1):**
10. `scanReturnsEmptyListWhenPartitionDateDirMissing` — no data for date, returns empty list
11. `scanSkipsEmptySubdirectories` — empty dataset dir is excluded
12. `scanReturnsUnknownFormatWhenNoRecognizedFiles` — .csv/.json yield FORMAT_UNKNOWN

**Validation (P0):**
13. `constructorThrowsOnNullFileSystem` — IllegalArgumentException
14. `scanThrowsOnNullParentPath` — IllegalArgumentException
15. `scanThrowsOnNullPartitionDate` — IllegalArgumentException

### Next Steps (TDD Green Phase)

After implementing `PathScanner.java` and `ConsumerZoneScanner.java`:

1. Create the test file: copy the test code from this checklist into `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java`
2. Verify compilation fails (red phase): `cd dqs-spark && mvn test-compile` (should fail — scanner classes missing)
3. Implement `PathScanner` interface and `ConsumerZoneScanner` class
4. Verify compilation succeeds: `cd dqs-spark && mvn test-compile`
5. Run scanner tests: `cd dqs-spark && mvn test -Dtest="ConsumerZoneScannerTest"`
6. Verify all 14 tests PASS (green phase)
7. Run full regression: `cd dqs-spark && mvn test`
8. Proceed to story 2-3 (DataFrame loading)

### Key Design Decisions

- **Hadoop LocalFileSystem over Mockito:** Tests exercise real `FileSystem.listStatus()`, `FileSystem.exists()`, `FileSystem.mkdirs()` calls against the local filesystem. This validates actual Hadoop API interactions without requiring mocking framework or running HDFS cluster.
- **`@TempDir` for test isolation:** Each `@Test` method gets a fresh temp directory, so tests cannot interfere with each other's filesystem state.
- **Static `FileSystem` instance:** Created once in `@BeforeAll`, shared across all tests (thread-safe for local filesystem). Closed in `@AfterAll`.
- **No `@Disabled`:** Java's natural TDD red phase is compilation failure when imported classes do not exist. No annotation needed.
- **Helper methods:** `createDatasetDir()` and `createFile()` reduce boilerplate, matching the helper method pattern from story 2-1 (`stubCheck()`, `ctx()`, `insertConfig()`).
