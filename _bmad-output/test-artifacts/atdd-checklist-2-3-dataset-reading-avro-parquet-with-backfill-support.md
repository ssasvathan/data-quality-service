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
storyId: 2-3-dataset-reading-avro-parquet-with-backfill-support
tddPhase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/2-3-dataset-reading-avro-parquet-with-backfill-support.md
  - _bmad-output/project-context.md
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/DqsJob.java
  - dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java
---

# ATDD Checklist: Story 2-3 — Dataset Reading (Avro & Parquet) with Backfill Support

## Step 1: Preflight & Context

- **Stack detected:** `backend` (Java/Maven — dqs-spark component, JUnit5 + SparkSession local[1])
- **Story file:** `_bmad-output/implementation-artifacts/2-3-dataset-reading-avro-parquet-with-backfill-support.md`
- **Status at start:** `ready-for-dev`
- **Prerequisites verified:**
  - [x] Story has clear acceptance criteria (AC1–AC4)
  - [x] `dqs-spark/pom.xml` exists with JUnit5, Spark 3.5 (provided scope) — Avro + Parquet bundled
  - [x] `DatasetContext.java` exists from story 2-1 with FORMAT_AVRO, FORMAT_PARQUET, FORMAT_UNKNOWN, all 6-arg constructor
  - [x] `ConsumerZoneScanner.java` exists from story 2-2; produces DatasetContexts with `df = null`
  - [x] Test framework: JUnit5 (Maven Surefire, Java 21 forked JVM with `--add-opens` flags)
  - [x] `DqsJob.java` placeholder exists — safe to modify per story 2-3 task list
  - [x] Existing test patterns: `@BeforeAll`/`@AfterAll`/`@BeforeEach`, `@TempDir`, helper methods, `<action><expectation>` naming
  - [x] 61 existing tests must remain passing (full regression required)

---

## Step 2: Generation Mode

**Mode selected:** AI generation (backend Java — no browser recording needed)
**Resolved execution mode:** Sequential (no subagent/agent-team runtime available)

---

## Step 3: Test Strategy

### Acceptance Criteria to Test Scenario Mapping

| AC | Scenario | Level | Priority | Test Method | Test File |
|---|---|---|---|---|---|
| AC1 | DatasetReader reads Avro files into a non-empty DataFrame | Unit | P0 | `readLoadsAvroDatasetIntoDataFrame` | DatasetReaderTest |
| AC2 | DatasetReader reads Parquet files into a non-empty DataFrame | Unit | P0 | `readLoadsParquetDatasetIntoDataFrame` | DatasetReaderTest |
| AC4 | DatasetReader throws UnsupportedOperationException for FORMAT_UNKNOWN | Unit | P0 | `readThrowsForUnknownFormat` | DatasetReaderTest |
| — | Constructor rejects null SparkSession | Unit | P0 | `constructorThrowsOnNullSparkSession` | DatasetReaderTest |
| — | read() rejects null DatasetContext | Unit | P0 | `readThrowsOnNullContext` | DatasetReaderTest |
| — | DatasetReader populates df field; returned DataFrame has expected schema | Unit | P1 | `readAvroDataFrameHasExpectedSchema` | DatasetReaderTest |
| AC3 | `--parent-path` is parsed correctly from CLI args | Unit | P0 | `parseArgsExtractsParentPath` | DqsJobArgParserTest |
| AC3 | No `--date` arg → defaults to `LocalDate.now()` | Unit | P0 | `parseArgsDefaultsToTodayWhenDateAbsent` | DqsJobArgParserTest |
| AC3 | `--date 20260325` → `LocalDate.of(2026, 3, 25)` | Unit | P0 | `parseArgsExtractsDateArg` | DqsJobArgParserTest |
| AC3 | Missing `--parent-path` throws IllegalArgumentException | Unit | P0 | `parseArgsThrowsOnMissingParentPath` | DqsJobArgParserTest |
| AC3 | Wrong date format (`2026-03-25`) throws IllegalArgumentException | Unit | P0 | `parseArgsThrowsOnInvalidDateFormat` | DqsJobArgParserTest |

### Test Levels Selected

- **Unit tests only** — no integration or E2E tests needed.
  - `DatasetReaderTest`: uses SparkSession `local[1]` with `@TempDir` for real Avro/Parquet fixture files. Exercises actual `DataFrameReader` API without a running HDFS cluster.
  - `DqsJobArgParserTest`: pure Java unit tests for the `parseArgs` static method on `DqsJobArgs` record extracted from `DqsJob`. No SparkSession required.

### TDD Red Phase Confirmation

- `DatasetReaderTest` imports `com.bank.dqs.reader.DatasetReader` — a class that does not exist yet. Maven compilation fails until implementation is complete. This is the natural Java red phase; no `@Disabled` annotation needed.
- `DqsJobArgParserTest` imports `DqsJob.DqsJobArgs` and calls `DqsJob.parseArgs(String[])` — the inner record and static method that do not exist yet in the placeholder `DqsJob.java`. Maven compilation fails until implementation is complete.

---

## Step 4: Test Generation (Sequential Mode)

### Worker A (Unit Tests) — COMPLETE

**Test files to create:**

| File | Tests | Description |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/reader/DatasetReaderTest.java` | 6 | AC1, AC2, AC4: DatasetReader Avro/Parquet reading + validation |
| `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java` | 5 | AC3: DqsJob CLI argument parsing |

**Total test methods: 11** (all JUnit5 `@Test` methods, no `@Disabled`)

**Red phase mechanism:** Tests fail to compile because `com.bank.dqs.reader.DatasetReader` and `DqsJob.DqsJobArgs` / `DqsJob.parseArgs()` do not exist yet.

### Worker B (E2E Tests) — N/A

Not applicable. This story has no browser interactions, no API endpoints, and no frontend components. Pure backend Java: file format reading + CLI arg parsing.

---

### Test File 1: DatasetReaderTest.java

**Location:** `dqs-spark/src/test/java/com/bank/dqs/reader/DatasetReaderTest.java`

```java
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
}
```

---

### Test File 2: DqsJobArgParserTest.java

**Location:** `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java`

```java
package com.bank.dqs;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the CLI argument parsing logic extracted from DqsJob.
 *
 * <p>Tests target the package-private {@code DqsJob.parseArgs(String[])} static method
 * and the {@code DqsJob.DqsJobArgs} record. No SparkSession required.
 */
class DqsJobArgParserTest {

    // ---------------------------------------------------------------------------
    // AC3: --parent-path is parsed correctly
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsExtractsParentPath() {
        String[] args = {"--parent-path", "/prod/abc/data"};

        DqsJob.DqsJobArgs result = DqsJob.parseArgs(args);

        assertEquals("/prod/abc/data", result.parentPath(),
                "parentPath must equal the value passed after --parent-path");
    }

    // ---------------------------------------------------------------------------
    // AC3: --date absent → defaults to LocalDate.now()
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsDefaultsToTodayWhenDateAbsent() {
        String[] args = {"--parent-path", "/prod/abc/data"};
        LocalDate before = LocalDate.now();

        DqsJob.DqsJobArgs result = DqsJob.parseArgs(args);

        LocalDate after = LocalDate.now();
        assertTrue(
                !result.partitionDate().isBefore(before) && !result.partitionDate().isAfter(after),
                "partitionDate should default to today's date when --date is absent"
        );
    }

    // ---------------------------------------------------------------------------
    // AC3: --date 20260325 → LocalDate.of(2026, 3, 25)
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsExtractsDateArg() {
        String[] args = {"--parent-path", "/prod/abc/data", "--date", "20260325"};

        DqsJob.DqsJobArgs result = DqsJob.parseArgs(args);

        assertEquals(LocalDate.of(2026, 3, 25), result.partitionDate(),
                "partitionDate must parse yyyyMMdd format correctly");
    }

    // ---------------------------------------------------------------------------
    // AC3: Missing --parent-path → IllegalArgumentException
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsThrowsOnMissingParentPath() {
        String[] args = {"--date", "20260325"};

        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.parseArgs(args),
                "parseArgs must throw IllegalArgumentException when --parent-path is absent");
    }

    // ---------------------------------------------------------------------------
    // AC3: Wrong date format → IllegalArgumentException
    // ---------------------------------------------------------------------------

    @Test
    void parseArgsThrowsOnInvalidDateFormat() {
        String[] args = {"--parent-path", "/prod/abc/data", "--date", "2026-03-25"};

        assertThrows(IllegalArgumentException.class,
                () -> DqsJob.parseArgs(args),
                "parseArgs must throw IllegalArgumentException for date format other than yyyyMMdd");
    }
}
```

---

## Step 4C: Aggregation

### TDD Red Phase Validation

All tests:
- [x] Import `com.bank.dqs.reader.DatasetReader` (non-existent class) → `DatasetReaderTest` fails to compile until `DatasetReader.java` is created
- [x] Import `DqsJob.DqsJobArgs` and call `DqsJob.parseArgs()` (non-existent inner record + method) → `DqsJobArgParserTest` fails to compile until `DqsJob.java` is modified
- [x] Assert EXPECTED behavior per AC (not placeholder assertions — real `assertEquals`, `assertTrue`, `assertThrows`)
- [x] `DatasetReaderTest` uses `SparkSession.builder().master("local[1]")` with `@BeforeAll` / `@AfterAll` lifecycle (per project testing rules)
- [x] `DatasetReaderTest` uses `@TempDir` for automatic cleanup between tests (test isolation)
- [x] Helper methods (`createAvroDataset`, `createParquetDataset`, `ctx`) reduce test boilerplate (pattern from stories 2-1 and 2-2)
- [x] No `@Disabled` (intentional — tests MUST fail at compile time until impl exists)
- [x] No Mockito or other mocking frameworks (uses real SparkSession local[1])
- [x] No running HDFS cluster or MiniDFSCluster (uses @TempDir on local filesystem)
- [x] Test naming follows `<action><expectation>` pattern
- [x] `DqsJobArgParserTest` requires NO SparkSession — pure arg parsing logic testability via extracted static method + record

### Summary Statistics

| Metric | Value |
|---|---|
| Total test methods | 11 |
| DatasetReaderTest | 6 |
| DqsJobArgParserTest | 5 |
| Unit tests (SparkSession local[1]) | 6 |
| Unit tests (no Spark — pure Java) | 5 |
| Integration tests | 0 |
| E2E tests | 0 |
| Execution mode | Sequential |
| Test framework | JUnit5 / Maven Surefire |
| Java version (test JVM) | Java 21 (configured in pom.xml) |
| New dependencies needed | None (Avro/Parquet bundled with Spark 3.5 provided scope) |

---

## Step 5: Validation & Completion

### Checklist Validation

- [x] Prerequisites satisfied (`pom.xml` has JUnit5, Spark 3.5 provides Avro + Parquet; Java 21 JVM configured)
- [x] 2 test files to be created under `dqs-spark/src/test/java/com/bank/dqs/`
- [x] All 4 ACs covered by at least one test method
- [x] No `@Disabled` (correct — compilation failure is the red phase for Java)
- [x] `@BeforeAll`/`@AfterAll` lifecycle used correctly in `DatasetReaderTest` for SparkSession
- [x] `@TempDir` provides automatic cleanup between tests in `DatasetReaderTest`
- [x] Helper methods `createAvroDataset`, `createParquetDataset`, `ctx()` follow story 2-1/2-2 patterns
- [x] `DqsJobArgParserTest` tests static `parseArgs` method — no SparkSession in test class
- [x] `DatasetContext.java` NOT modified
- [x] `DatasetContext` immutable pattern respected — tests use existing constructor (4 fields + null df + format)
- [x] `pom.xml` NOT modified
- [x] No Mockito, no MiniDFSCluster, no running HDFS cluster required
- [x] Test package mirrors planned main package (`com.bank.dqs.reader`)
- [x] No files created outside `dqs-spark/`
- [x] No temp artifacts in random locations — all output in `_bmad-output/test-artifacts/`

### Generated Files

| File | Type | Description |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/reader/DatasetReaderTest.java` | NEW (to create) | 6 unit tests for DatasetReader (Avro, Parquet, FORMAT_UNKNOWN, null checks, schema) |
| `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java` | NEW (to create) | 5 unit tests for DqsJob CLI argument parsing |

### Files NOT Modified

| File | Reason |
|---|---|
| `dqs-spark/pom.xml` | Already correct — Avro/Parquet via Spark transitive dep; Java 21 Surefire already configured |
| `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java` | Existing from story 2-1 — do NOT modify |
| `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java` | Existing from story 2-2 — do NOT modify |
| `dqs-spark/src/main/java/com/bank/dqs/scanner/PathScanner.java` | Existing from story 2-2 — do NOT modify |

### Acceptance Criteria Coverage

| AC | Covered By | Status |
|---|---|---|
| AC1: Avro files read into Spark DataFrame successfully | `readLoadsAvroDatasetIntoDataFrame`, `readAvroDataFrameHasExpectedSchema` | COVERED |
| AC2: Parquet files read into Spark DataFrame successfully | `readLoadsParquetDatasetIntoDataFrame` | COVERED |
| AC3: `--date` arg changes partition date; absent defaults to today | `parseArgsExtractsDateArg`, `parseArgsDefaultsToTodayWhenDateAbsent`, `parseArgsExtractsParentPath`, `parseArgsThrowsOnMissingParentPath`, `parseArgsThrowsOnInvalidDateFormat` | COVERED |
| AC4: Unrecognized format logs error and continues (DatasetReader throws `UnsupportedOperationException`) | `readThrowsForUnknownFormat` | COVERED |

### Test Scenarios by Category

**Happy Path (P0):**
1. `readLoadsAvroDatasetIntoDataFrame` — Avro files under @TempDir read into non-empty DataFrame
2. `readLoadsParquetDatasetIntoDataFrame` — Parquet files under @TempDir read into non-empty DataFrame
3. `parseArgsExtractsParentPath` — `--parent-path /prod/abc/data` parsed correctly
4. `parseArgsDefaultsToTodayWhenDateAbsent` — no `--date` → `LocalDate.now()` returned
5. `parseArgsExtractsDateArg` — `--date 20260325` → `LocalDate.of(2026, 3, 25)`

**Edge Cases / Error Paths (P0):**
6. `readThrowsForUnknownFormat` — FORMAT_UNKNOWN triggers `UnsupportedOperationException`
7. `constructorThrowsOnNullSparkSession` — `IllegalArgumentException` on null spark
8. `readThrowsOnNullContext` — `IllegalArgumentException` on null ctx
9. `parseArgsThrowsOnMissingParentPath` — `IllegalArgumentException` when `--parent-path` absent
10. `parseArgsThrowsOnInvalidDateFormat` — `IllegalArgumentException` for `2026-03-25` (wrong format)

**Schema / Data Quality (P1):**
11. `readAvroDataFrameHasExpectedSchema` — Avro DataFrame has expected column names and row count

### Implementation Checklist (TDD Green Phase Guide)

#### Phase A: Implement DatasetReader.java

**File to create:** `dqs-spark/src/main/java/com/bank/dqs/reader/DatasetReader.java`

- [ ] Create `reader/` package directory under `src/main/java/com/bank/dqs/`
- [ ] Implement `DatasetReader` class with `SparkSession spark` constructor field
- [ ] Validate `spark != null` in constructor — throw `IllegalArgumentException`
- [ ] Implement `Dataset<Row> read(DatasetContext ctx)` method
- [ ] Validate `ctx != null` in `read()` — throw `IllegalArgumentException`
- [ ] Construct full HDFS path: `ctx.getParentPath() + "/" + ctx.getDatasetName()`
- [ ] Branch on `ctx.getFormat()`:
  - `FORMAT_AVRO` → `spark.read().format("avro").load(dataPath)`
  - `FORMAT_PARQUET` → `spark.read().parquet(dataPath)`
  - `FORMAT_UNKNOWN` (or default) → `throw new UnsupportedOperationException(...)`
- [ ] Use `import static com.bank.dqs.model.DatasetContext.FORMAT_AVRO` etc. (per project rules)
- [ ] Use SLF4J logger: `LoggerFactory.getLogger(DatasetReader.class)`
- [ ] Add `toString()` method
- [ ] Create test directory: `src/test/java/com/bank/dqs/reader/`
- [ ] Create `DatasetReaderTest.java` (copy from this checklist)
- [ ] Verify compilation fails first: `cd dqs-spark && mvn test-compile` (DatasetReader class missing)
- [ ] Run `mvn test-compile` after creating class — should succeed
- [ ] Run `mvn test -Dtest="DatasetReaderTest"` — all 6 tests must PASS

#### Phase B: Modify DqsJob.java

**File to modify:** `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`

- [ ] Add package-private `static record DqsJobArgs(String parentPath, LocalDate partitionDate) {}`
- [ ] Implement `static DqsJobArgs parseArgs(String[] args)` using the switch pattern from Dev Notes
- [ ] Parse `--parent-path` into `parentPath` field
- [ ] Parse `--date` using `DateTimeFormatter.ofPattern("yyyyMMdd")` — wrap `DateTimeParseException` in `IllegalArgumentException`
- [ ] Default `partitionDate` to `LocalDate.now()` when `--date` absent
- [ ] Throw `IllegalArgumentException("--parent-path is required")` when parentPath is null/blank
- [ ] Update `main(String[] args)` to call `parseArgs(args)`, build `SparkSession`, create `ConsumerZoneScanner`, call `scanner.scan()`, loop over datasets calling `DatasetReader.read()` with per-dataset try/catch
- [ ] Per-dataset failure isolation: `UnsupportedOperationException` caught + logged as ERROR; generic `Exception` caught + logged as ERROR; continue to next
- [ ] Add INFO log at entry and exit (per project context logging rules)
- [ ] Create `DqsJobArgParserTest.java` (copy from this checklist)
- [ ] Verify compilation fails first: `cd dqs-spark && mvn test-compile` (DqsJobArgs + parseArgs missing)
- [ ] Run `mvn test-compile` after implementing — should succeed
- [ ] Run `mvn test -Dtest="DqsJobArgParserTest"` — all 5 tests must PASS

#### Phase C: Full Regression

- [ ] Run `cd dqs-spark && mvn test` — all 72 tests must PASS (61 existing + 6 DatasetReaderTest + 5 DqsJobArgParserTest)

---

### Running Tests

```bash
# Run just the new tests for this story
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="DatasetReaderTest,DqsJobArgParserTest"

# Run DatasetReader tests only
mvn test -Dtest="DatasetReaderTest"

# Run arg parser tests only (no Spark needed)
mvn test -Dtest="DqsJobArgParserTest"

# Full regression (all existing + new — must pass)
mvn test
```

---

### Key Design Decisions

- **SparkSession local[1] over Mockito:** `DatasetReaderTest` exercises real `DataFrameReader` with actual Avro/Parquet files written by Spark itself. This validates the real format string `"avro"` and `parquet()` API calls without mocking framework dependencies.
- **`@TempDir` for test isolation:** Each `@Test` gets its own subdirectory inside the shared temp dir. Tests cannot interfere with each other's file state.
- **Static `SparkSession` with `@BeforeAll`/`@AfterAll`:** SparkSession creation is expensive (~3-5s). Sharing the session across all tests in `DatasetReaderTest` significantly reduces test suite execution time.
- **Do NOT share SparkSession across test classes:** `DatasetReaderTest` and any future Spark test classes each manage their own session lifecycle, per project rules.
- **Path construction tested implicitly:** The `ctx()` helper sets `parentPath = tempDir.toString()` and `datasetName = subDir` matching `DatasetReader.read()`'s path construction logic (`parentPath + "/" + datasetName`). This validates the path construction behavior without needing a separate explicit test.
- **No `@Disabled`:** Java's natural TDD red phase is compilation failure when imported classes or methods do not exist. No annotation needed.
- **`parseArgs` extracted for testability:** Argument parsing logic in `DqsJob` is extracted into a package-private `static DqsJobArgs parseArgs(String[] args)` method + `DqsJobArgs` record. This allows unit testing without launching a SparkSession — following the same pattern the story Dev Notes document.
- **No new Maven dependencies:** Avro support via `spark.read().format("avro")` and Parquet via `spark.read().parquet()` are both bundled with Spark 3.5 on the cluster. `pom.xml` is NOT modified.

---

### Cross-Story Context

- **Story 2.1 (DONE):** Created `DatasetContext`, `DqCheck`, `CheckFactory`, `DqMetric`, `MetricNumeric`, `MetricDetail`. `DatasetContext.df` exists as null — this story populates it.
- **Story 2.2 (DONE):** Created `PathScanner` interface and `ConsumerZoneScanner`. Scanner returns `List<DatasetContext>` with `df = null`. Story 2.3 calls the scanner and populates `df` via `DatasetReader`.
- **Story 2.4 (NEXT):** Adds `dataset_enrichment` DB lookup to resolve legacy `src_sys_nm` values.
- **Story 2.10 (FUTURE):** Full `DqsJob` integration — scanner + DatasetReader + checks + BatchWriter. Story 2.3 creates the skeleton of this flow.

---

### Next Steps (TDD Green Phase)

After implementing `DatasetReader.java` and modifying `DqsJob.java`:

1. Create `dqs-spark/src/test/java/com/bank/dqs/reader/DatasetReaderTest.java` (copy test code from this checklist)
2. Create `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java` (copy test code from this checklist)
3. Verify compilation fails (red phase): `cd dqs-spark && mvn test-compile` (should fail — `DatasetReader` and `DqsJobArgs`/`parseArgs` missing)
4. Implement `DatasetReader.java` under `src/main/java/com/bank/dqs/reader/`
5. Modify `DqsJob.java` — add `DqsJobArgs` record + `parseArgs()` method
6. Verify compilation succeeds: `mvn test-compile`
7. Run new tests: `mvn test -Dtest="DatasetReaderTest,DqsJobArgParserTest"`
8. Verify all 11 tests PASS (green phase)
9. Run full regression: `mvn test`
10. Verify all 72 tests PASS (61 existing + 6 DatasetReaderTest + 5 DqsJobArgParserTest)
11. Proceed to story 2-4 (dataset enrichment DB lookup)

---

## Knowledge Base References Applied

- **test-levels-framework.md** — Backend test level selection (unit only for pure functions and file I/O logic; no E2E or API layers involved)
- **test-priorities-matrix.md** — P0/P1 priority assignments (DataFrame loading is core pipeline data flow = P0; schema verification = P1)
- **test-quality.md** — Deterministic test design; isolated tests; explicit assertions in test bodies; no hard waits; `@TempDir` for auto-cleanup
- **data-factories.md** — Applied via `createAvroDataset()` and `createParquetDataset()` helper methods creating real Spark-written fixture data instead of static files
- **test-healing-patterns.md** — No conditionals in test flow; failure messages include actual vs. expected context strings; helper methods reduce duplication

---

**Generated by BMAD TEA Agent** — 2026-04-03
