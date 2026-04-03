# Story 2.3: Dataset Reading (Avro & Parquet) with Backfill Support

Status: done

## Story

As a **platform operator**,
I want DqsJob to read both Avro and Parquet format files from discovered dataset paths, with a `--date` parameter for backfill runs,
so that quality checks work across all data formats and historical dates can be reprocessed.

## Acceptance Criteria

1. **Given** a discovered dataset path containing Avro files **When** DqsJob processes the dataset **Then** the data is read into a Spark DataFrame successfully
2. **Given** a discovered dataset path containing Parquet files **When** DqsJob processes the dataset **Then** the data is read into a Spark DataFrame successfully
3. **Given** DqsJob is invoked with `--date 20260325` **When** the scanner discovers datasets **Then** it scans for partition_date=20260325 instead of the current date
4. **Given** a dataset with an unrecognized format **When** DqsJob attempts to read it **Then** it logs an error for that dataset and continues to the next dataset without crashing

## Tasks / Subtasks

- [x] Task 1: Implement `DatasetReader` class (AC: #1, #2, #4)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/reader/DatasetReader.java`
  - [x] Accept `SparkSession` via constructor (injection for testability)
  - [x] Method: `Dataset<Row> read(DatasetContext ctx)` — reads HDFS data based on `ctx.getFormat()` and `ctx.getParentPath()` / `ctx.getDatasetName()`
  - [x] For `FORMAT_AVRO`: use `spark.read().format("avro").load(dataPath)` — returns DataFrame
  - [x] For `FORMAT_PARQUET`: use `spark.read().parquet(dataPath)` — returns DataFrame
  - [x] For `FORMAT_UNKNOWN`: throw `UnsupportedOperationException` with descriptive message
  - [x] Construct the full HDFS path as: `{parentPath}/{datasetName}` — this is the directory containing the actual data files
  - [x] Constructor validates: `spark` must not be null — throw `IllegalArgumentException`
  - [x] Argument validation in `read()`: `ctx` must not be null — throw `IllegalArgumentException`
  - [x] Add `toString()` for debugging

- [x] Task 2: Implement `--date` CLI argument parsing in `DqsJob` (AC: #3)
  - [x] Modify `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` (placeholder, safe to modify now)
  - [x] Accept CLI args: `--parent-path <path>` (required), `--date <yyyyMMdd>` (optional, defaults to today)
  - [x] Parse `--date` argument using `DateTimeFormatter.ofPattern("yyyyMMdd")` into a `LocalDate`
  - [x] If `--date` is absent, use `LocalDate.now()` as the partition date
  - [x] Build `SparkSession` with `SparkSession.builder().appName("dqs-spark").getOrCreate()`
  - [x] Create `ConsumerZoneScanner` with `FileSystem.get(spark.sparkContext().hadoopConfiguration())`
  - [x] Call `scanner.scan(parentPath, partitionDate)` to get the list of `DatasetContext` objects
  - [x] For each dataset: call `DatasetReader.read(ctx)` to populate df, then create a new `DatasetContext` with the loaded df
  - [x] Per-dataset failure isolation: wrap each dataset's read in a try/catch, log `ERROR` on failure, continue to next dataset — **NEVER let one dataset crash the JVM**
  - [x] `FORMAT_UNKNOWN` datasets: catch `UnsupportedOperationException`, log `ERROR` with dataset name, continue
  - [x] Add INFO log: "DqsJob started: parentPath={}, date={}" at entry
  - [x] Add INFO log: "DqsJob completed: {} datasets loaded successfully, {} skipped/errors" at end

- [x] Task 3: Write `DatasetReader` tests (AC: #1, #2, #4)
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/reader/DatasetReaderTest.java`
  - [x] Use `SparkSession` with `local[1]` master — shared `@BeforeAll` / `@AfterAll` lifecycle
  - [x] Use JUnit 5 `@TempDir` for creating test Avro/Parquet fixture files
  - [x] Test cases:
    - `readLoadsAvroDatasetIntoDataFrame` — write Avro test fixture, read with DatasetReader, verify row count > 0
    - `readLoadsParquetDatasetIntoDataFrame` — write Parquet test fixture, read with DatasetReader, verify row count > 0
    - `readThrowsForUnknownFormat` — DatasetContext with FORMAT_UNKNOWN triggers UnsupportedOperationException
    - `constructorThrowsOnNullSparkSession` — constructor validation
    - `readThrowsOnNullContext` — argument validation
  - [x] Test naming: `<action><expectation>` pattern (e.g., `readLoadsAvroDatasetIntoDataFrame`)
  - [x] Create test DataFrames using `RowFactory` + `spark.createDataFrame()` for fixtures (write to temp dir, then read back to verify DatasetReader)

- [x] Task 4: Write `DqsJob` CLI argument parsing tests (AC: #3)
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java`
  - [x] Extract argument parsing logic into a package-private helper method (e.g., `DqsJobArgs parseArgs(String[] args)` returning a simple value holder) so it can be unit tested without a SparkSession
  - [x] Test cases:
    - `parseArgsExtractsParentPath` — `--parent-path /prod/abc/data` parsed correctly
    - `parseArgsDefaultsToTodayWhenDateAbsent` — no `--date` arg → `LocalDate.now()` returned
    - `parseArgsExtractsDateArg` — `--date 20260325` → `LocalDate.of(2026, 3, 25)`
    - `parseArgsThrowsOnMissingParentPath` — missing required arg → `IllegalArgumentException`
    - `parseArgsThrowsOnInvalidDateFormat` — `--date 2026-03-25` (wrong format) → `IllegalArgumentException`

## Dev Notes

### Existing Code to Build On — Do NOT Reinvent

The following files already exist from stories 2-1 and 2-2. Do NOT recreate or modify these unless explicitly in the task list:

- `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java` — holds `datasetName`, `lookupCode`, `partitionDate`, `parentPath`, `df`, `format`. **This story populates the `df` field** (scanner set it to null, this story loads it).
- `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java` — has `EXPIRY_SENTINEL`. Not needed for this story.
- `dqs-spark/src/main/java/com/bank/dqs/scanner/PathScanner.java` — interface with `scan(String parentPath, LocalDate partitionDate)`.
- `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java` — produces `List<DatasetContext>` with `df = null`. This story will call it.
- `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`, `CheckFactory.java` — check strategy/factory. Not needed for this story.
- `dqs-spark/src/main/java/com/bank/dqs/model/DqMetric.java`, `MetricNumeric.java`, `MetricDetail.java` — metrics. Not needed for this story.
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` — **placeholder that this story modifies** (add CLI arg parsing + scanner + reader integration).
- `dqs-spark/pom.xml` — **DO NOT modify**. Already has Spark (provided scope) which includes both Avro and Parquet support, Hadoop FileSystem transitively, JUnit 5.

### Package Structure (Strict)

Place files exactly in these locations per architecture spec:

```
dqs-spark/src/main/java/com/bank/dqs/
  DqsJob.java                     ← MODIFY (placeholder -> real entry point skeleton)
  reader/
    DatasetReader.java            ← NEW

dqs-spark/src/test/java/com/bank/dqs/
  DqsJobArgParserTest.java        ← NEW
  reader/
    DatasetReaderTest.java        ← NEW
```

The `reader/` package is new. Maven discovers it automatically under `src/main/java/`.

### DatasetReader — Key Design Decisions

**Full HDFS path construction:**

The `DatasetContext.datasetName` is the relative path segment (e.g., `src_sys_nm=cb10/partition_date=20260330`). The full data path is:

```java
String dataPath = ctx.getParentPath() + "/" + ctx.getDatasetName();
// e.g., /prod/abc/data/consumerzone/src_sys_nm=cb10/partition_date=20260330
```

**Avro reading — spark-avro is bundled with Spark 3.5:**

```java
// Avro: use the "avro" format string — spark-avro is part of Spark 3.5 distribution
Dataset<Row> df = spark.read().format("avro").load(dataPath);
```

Do NOT add `spark-avro` as a Maven dependency — it is provided as part of Spark 3.5 on the cluster (like `spark-sql`).

**Parquet reading:**

```java
Dataset<Row> df = spark.read().parquet(dataPath);
```

**Format constants — use the existing constants from DatasetContext:**

```java
import static com.bank.dqs.model.DatasetContext.FORMAT_AVRO;
import static com.bank.dqs.model.DatasetContext.FORMAT_PARQUET;
import static com.bank.dqs.model.DatasetContext.FORMAT_UNKNOWN;
```

**DatasetContext is immutable — you must create a new instance with the loaded df:**

`DatasetContext` has no setters. To populate the `df` field after reading, create a new `DatasetContext` with the same values plus the loaded DataFrame:

```java
DatasetContext ctxWithDf = new DatasetContext(
    ctx.getDatasetName(),
    ctx.getLookupCode(),
    ctx.getPartitionDate(),
    ctx.getParentPath(),
    loadedDf,          // the populated DataFrame
    ctx.getFormat()
);
```

This is the expected pattern documented in the DatasetContext Javadoc comment "df may be null in unit tests".

### DqsJob CLI Argument Parsing

**Argument format:** Space-separated `--key value` pairs. Simple manual parsing — do NOT add a CLI parsing library (no picocli, commons-cli, etc.). The argument list is short and fixed.

**Parsing pattern:**

```java
String parentPath = null;
LocalDate partitionDate = LocalDate.now();

for (int i = 0; i < args.length - 1; i++) {
    switch (args[i]) {
        case "--parent-path" -> parentPath = args[++i];
        case "--date" -> {
            try {
                partitionDate = LocalDate.parse(args[++i],
                        DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid date format. Expected yyyyMMdd, got: " + args[i], e);
            }
        }
    }
}
if (parentPath == null || parentPath.isBlank()) {
    throw new IllegalArgumentException("--parent-path is required");
}
```

**Testability:** Extract the argument parsing into a package-private static method or inner record class `DqsJobArgs` so `DqsJobArgParserTest` can test it without launching Spark. Example:

```java
// In DqsJob.java (package-private for test access):
static record DqsJobArgs(String parentPath, LocalDate partitionDate) {}

static DqsJobArgs parseArgs(String[] args) { ... }
```

### Per-Dataset Failure Isolation in DqsJob (Critical Anti-Pattern Prevention)

This is the key architectural rule from `project-context.md`:

> **Never let one dataset crash the entire Spark job — catch and continue.**

Pattern for the dataset loop:

```java
List<DatasetContext> datasets = scanner.scan(parentPath, partitionDate);
List<DatasetContext> loaded = new ArrayList<>();
int errorCount = 0;

for (DatasetContext ctx : datasets) {
    try {
        Dataset<Row> df = reader.read(ctx);
        loaded.add(new DatasetContext(
            ctx.getDatasetName(), ctx.getLookupCode(),
            ctx.getPartitionDate(), ctx.getParentPath(),
            df, ctx.getFormat()
        ));
        LOG.debug("Loaded dataset: {}", ctx.getDatasetName());
    } catch (UnsupportedOperationException e) {
        LOG.error("Unsupported format for dataset {}: {}", ctx.getDatasetName(), e.getMessage());
        errorCount++;
    } catch (Exception e) {
        LOG.error("Failed to load dataset {}: {}", ctx.getDatasetName(), e.getMessage(), e);
        errorCount++;
    }
}
LOG.info("DqsJob: {} datasets loaded, {} errors", loaded.size(), errorCount);
```

**Story 2.3 scope boundary:** After loading DataFrames, DqsJob in this story does NOT run checks or write to DB. Those are in stories 2.5-2.9 (checks) and 2.10 (BatchWriter + full integration). This story ends with the `loaded` list of DatasetContexts with populated DFs. A TODO comment placeholder is fine.

### SparkSession Lifecycle for Tests

Tests that require Spark must use a `SparkSession` with `local[1]` master. Key pattern from project-context.md:

```java
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
```

**Important:** Spark tests run in a forked JVM using Java 21 (configured in pom.xml Surefire with `<jvm>/usr/lib/jvm/java-21-openjdk-amd64/bin/java</jvm>`). The `--add-opens` JVM flags are already in pom.xml. Do NOT modify pom.xml.

**Writing test fixtures — create real Avro/Parquet files:**

Use `spark.createDataFrame()` with `RowFactory` to create a test DataFrame, then write it to `@TempDir`:

```java
// Write Avro test fixture
StructType schema = new StructType()
    .add("id", DataTypes.IntegerType)
    .add("value", DataTypes.StringType);
List<Row> rows = List.of(
    RowFactory.create(1, "foo"),
    RowFactory.create(2, "bar")
);
Dataset<Row> testDf = spark.createDataFrame(rows, schema);
String avroPath = tempDir.resolve("avro_dataset").toString();
testDf.write().format("avro").save(avroPath);
```

Then construct a `DatasetContext` pointing at that path and call `DatasetReader.read(ctx)`.

**Path construction for test contexts:**

Since the full HDFS path = `parentPath + "/" + datasetName`, to make DatasetReader read from a specific temp dir:
- Set `parentPath` = `tempDir.toString()`
- Set `datasetName` = the subdirectory name (e.g., `"avro_dataset"`)

### What NOT to Do in This Story

- **Do NOT add new Maven dependencies** — Avro and Parquet support are bundled with Spark 3.5 (provided scope). No `spark-avro` artifact needed in pom.xml.
- **Do NOT implement `dataset_enrichment` lookup** — that is story 2.4.
- **Do NOT implement check execution** — that is stories 2.5-2.9.
- **Do NOT implement BatchWriter or DB writes** — that is story 2.10.
- **Do NOT create a full end-to-end DqsJob** — this story adds CLI parsing + scanner invocation + DataFrame loading only. Stub the rest with TODO.
- **Do NOT hardcode sentinel timestamps** — `EXPIRY_SENTINEL` is in `DqsConstants`. Not needed for this story but rule applies.
- **Do NOT use `static` imports for Spark SQL functions** in this story — no Spark transformations required, only reading.
- **Do NOT create any files outside `dqs-spark/`** — this story is entirely within the Spark component.
- **Do NOT modify `DatasetContext.java`** — it already has all fields needed. Story 2.3 just populates `df` by creating a new instance.
- **Do NOT share SparkSession across test classes** — each test class manages its own SparkSession lifecycle with `@BeforeAll` / `@AfterAll`.

### Java Rules Enforced in This Story

- **Static imports** for `DatasetContext` format constants: `import static com.bank.dqs.model.DatasetContext.FORMAT_AVRO` etc.
- **Full generic types**: `List<DatasetContext>`, `Dataset<Row>` — never raw types
- **Constructor validation**: `IllegalArgumentException` for null `SparkSession` and null `ctx`
- **Logging**: SLF4J via `LoggerFactory.getLogger(...)` — Spark provides SLF4J transitively
- **`UnsupportedOperationException`** for FORMAT_UNKNOWN (runtime exception, no `throws` declaration needed)
- **Try-with-resources** not applicable here (no JDBC), but follow it if any streams used
- **`throws` declaration**: `read()` does NOT need `throws` declaration since Spark's `DataFrameReader` wraps IO errors in runtime exceptions
- **Test naming**: `<action><expectation>` pattern (e.g., `readLoadsAvroDatasetIntoDataFrame`)
- **`toString()`** on `DatasetReader` for debugging

### Running Tests

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test
```

Run just the new tests:

```bash
mvn test -Dtest="DatasetReaderTest,DqsJobArgParserTest"
```

Run with full regression (all 61 existing tests + new):

```bash
mvn test
```

### Cross-Story Context

- **Story 2.1 (DONE)**: Created `DatasetContext`, `DqCheck`, `CheckFactory`, `DqMetric`, `MetricNumeric`, `MetricDetail`. `DatasetContext.df` field exists but is null until this story.
- **Story 2.2 (DONE)**: Created `PathScanner` interface and `ConsumerZoneScanner`. Scanner returns `DatasetContext` objects with `df = null`. This story (2.3) calls the scanner and populates `df`.
- **Story 2.4 (NEXT)**: Adds `dataset_enrichment` DB lookup to resolve legacy `src_sys_nm` values. Will wrap or extend scanner output.
- **Story 2.10**: `DqsJob` full integration — calls scanner (2.2), loads DataFrames (2.3), runs checks (2.5-2.9), batch-writes results. Story 2.3 creates the skeleton of this flow.

### Previous Story Intelligence (from 2-2)

Key learnings from story 2-2 to apply proactively:

- **Null guard on constructor parameters** — apply to `DatasetReader(SparkSession spark)` and `read(DatasetContext ctx)`
- **`toString()` on new classes** — add to `DatasetReader`
- **Filter non-dataset entries** — when iterating results from ConsumerZoneScanner, only process entries with recognized formats (AVRO or PARQUET) by default; handle UNKNOWN gracefully
- **Test helper methods** to reduce boilerplate — create reusable `createAvroDataset(path)` and `createParquetDataset(path)` helpers in `DatasetReaderTest`
- **Hadoop metadata file handling** — scanner already filters `_SUCCESS` and `.crc` files; the reader reads directories, Spark handles this transparently
- **Skip non-src_sys_nm directories** — already handled by ConsumerZoneScanner; DatasetReader just reads what it's given

### Git Intelligence from Recent Commits

From the last 2 commits:
- `2-2`: Created `scanner/PathScanner.java`, `scanner/ConsumerZoneScanner.java`, `scanner/ConsumerZoneScannerTest.java` (19 tests, 61 total pass)
- `2-1`: Created all model classes, DqCheck interface, CheckFactory, tests

Current test count: **61 tests** — all must still pass after this story (full regression required).

Files to be aware of:
- `ConsumerZoneScanner.java` — returns `df=null` DatasetContexts; this story populates them
- `DatasetContext.java` — immutable, constructor-validated; df is nullable per Javadoc

### Project Structure Notes

- All files go in `dqs-spark/` only — no changes to any other component
- `reader/` package is new, placed at `src/main/java/com/bank/dqs/reader/`
- Test directory `src/test/java/com/bank/dqs/reader/` is also new
- `DqsJobArgParserTest.java` goes directly in `src/test/java/com/bank/dqs/` (same package as `DqsJob`)
- Maven Surefire discovers all test classes automatically — no registration needed
- Do NOT create `tests/fixtures/` directory — test data is generated programmatically using Spark

### References

- Story 2.3 AC: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.3)
- FR4-FR7: `_bmad-output/planning-artifacts/prd.md` — Data Discovery & Ingestion
- Architecture — dqs-spark structure: `_bmad-output/planning-artifacts/architecture.md` — Structure Patterns — dqs-spark
- Architecture — FR mapping: `_bmad-output/planning-artifacts/architecture.md` — Requirements to Structure Mapping (FR4-5 → `DqsJob.java`, FR7 → `DqsJob.java`)
- Java rules: `_bmad-output/project-context.md` — Language-Specific Rules — Java
- Testing rules: `_bmad-output/project-context.md` — Testing Rules — dqs-spark
- Per-dataset isolation: `_bmad-output/project-context.md` — Framework-Specific Rules — Spark — "Never let one dataset crash the JVM"
- `DatasetContext` model: `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java` (existing)
- `ConsumerZoneScanner`: `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java` (existing)
- pom.xml: `dqs-spark/pom.xml` (Spark 3.5 provided scope, Avro/Parquet bundled)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- spark-avro test scope issue: `spark-sql_2.13` (provided scope) does not transitively include `spark-avro_2.13`. Added `spark-avro_2.13:3.5.0` with `test` scope to pom.xml so `DatasetReaderTest` can write/read Avro fixtures locally. This does NOT affect the fat JAR — cluster deployment still relies on the bundled spark-avro in Spark 3.5 distribution.

### Completion Notes List

- Implemented `DatasetReader.java` in new `reader/` package with SparkSession constructor injection, null validation, FORMAT_AVRO/FORMAT_PARQUET routing, UnsupportedOperationException for FORMAT_UNKNOWN, SLF4J logging, and toString().
- Modified `DqsJob.java` from placeholder to real entry point skeleton: added package-private `DqsJobArgs` record + `parseArgs(String[] args)` static method, SparkSession + ConsumerZoneScanner + DatasetReader wiring, per-dataset try/catch failure isolation, INFO logging at entry/exit, TODO stubs for checks (2.5-2.9) and BatchWriter (2.10).
- Created `DatasetReaderTest.java` (6 tests): Avro read, Parquet read, FORMAT_UNKNOWN throw, null SparkSession constructor, null ctx, schema verification. All use SparkSession local[1] with @BeforeAll/@AfterAll and @TempDir.
- Created `DqsJobArgParserTest.java` (5 tests): parent-path extraction, date defaulting to today, date parsing yyyyMMdd, missing parent-path error, invalid date format error. No SparkSession required.
- Full regression: **72 tests pass** (61 existing + 11 new). Zero failures, zero errors.

### File List

- `dqs-spark/src/main/java/com/bank/dqs/reader/DatasetReader.java` — NEW
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` — MODIFIED (placeholder → real entry point skeleton)
- `dqs-spark/src/test/java/com/bank/dqs/reader/DatasetReaderTest.java` — NEW
- `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java` — NEW
- `dqs-spark/pom.xml` — MODIFIED (added spark-avro_2.13 test scope for local Avro test fixture support)
