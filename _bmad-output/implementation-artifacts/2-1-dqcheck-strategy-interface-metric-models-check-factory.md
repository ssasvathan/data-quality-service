# Story 2.1: DqCheck Strategy Interface, Metric Models & Check Factory

Status: done

## Story

As a **developer**,
I want a `DqCheck` strategy interface with `DqMetric` models (`MetricNumeric`, `MetricDetail`) and a `CheckFactory` for registration,
so that all current and future checks share a consistent contract and can be discovered via factory lookup.

## Acceptance Criteria

1. **Given** the dqs-spark Maven project exists **When** the interface and models are implemented **Then** `DqCheck` interface defines a method that accepts a `DatasetContext` and returns a list of `DqMetric` results
2. **And** `MetricNumeric` model maps to `dq_metric_numeric` columns (check_type, metric_name, metric_value)
3. **And** `MetricDetail` model maps to `dq_metric_detail` columns (check_type, detail_type, detail_value)
4. **And** `CheckFactory` registers check implementations by check_type string and returns enabled checks for a given dataset
5. **And** `CheckFactory` reads `check_config` from Postgres (or H2 in tests) to determine which checks are enabled per dataset pattern
6. **And** `DatasetContext` holds dataset_name, lookup_code, partition_date, parent_path, DataFrame reference, and format type

## Tasks / Subtasks

- [x] Task 1: Create `DqMetric` interface (AC: #1, #2, #3)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/model/DqMetric.java` — marker interface (no methods), type root for MetricNumeric and MetricDetail

- [x] Task 2: Create `MetricNumeric` model (AC: #2)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java` — implements `DqMetric`
  - [x] Fields: `String checkType`, `String metricName`, `double metricValue`
  - [x] All-args constructor, getters (no setters — immutable)

- [x] Task 3: Create `MetricDetail` model (AC: #3)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java` — implements `DqMetric`
  - [x] Fields: `String checkType`, `String detailType`, `String detailValue` (JSON string for JSONB column)
  - [x] All-args constructor, getters (no setters — immutable)

- [x] Task 4: Create `DatasetContext` model (AC: #6)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
  - [x] Fields: `String datasetName`, `String lookupCode`, `LocalDate partitionDate`, `String parentPath`, `Dataset<Row> df`, `String format`
  - [x] Constructor validates: `datasetName` non-null/non-blank, `partitionDate` non-null, `parentPath` non-null/non-blank, `format` non-null — throw `IllegalArgumentException` on violation
  - [x] `df` may be null (tests don't need SparkSession for factory/model tests)
  - [x] Format constants as `public static final String`: `FORMAT_AVRO = "AVRO"`, `FORMAT_PARQUET = "PARQUET"`, `FORMAT_UNKNOWN = "UNKNOWN"`

- [x] Task 5: Create `DqCheck` interface (AC: #1)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java` — strategy interface
  - [x] Method: `List<DqMetric> execute(DatasetContext context)`
  - [x] Method: `String getCheckType()` — returns the canonical check_type string used in `check_config`

- [x] Task 6: Create `CheckFactory` (AC: #4, #5)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
  - [x] Instance-based (not static singleton) — fresh factory per `DqsJob` run
  - [x] `private final Map<String, DqCheck> registry = new LinkedHashMap<>()` (insertion-order-preserved for deterministic check order)
  - [x] `void register(DqCheck check)` — adds to registry using `check.getCheckType()` as key
  - [x] `List<DqCheck> getEnabledChecks(DatasetContext ctx, Connection conn) throws SQLException` — queries `check_config` and returns enabled registered checks
  - [x] SQL query (see Dev Notes for exact SQL)
  - [x] Use `PreparedStatement` with `?` placeholders — never string concatenation
  - [x] Use `DqsConstants.EXPIRY_SENTINEL` for the expiry filter — never hardcode `9999-12-31 23:59:59`

- [x] Task 7: Write tests (AC: all)
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java`
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/model/DatasetContextTest.java`
  - [x] All JUnit 5 (`@Test`, `@BeforeEach`, etc.)

## Dev Notes

### Existing Code to Build On

The following files already exist — do NOT modify them:
- `dqs-spark/pom.xml` — Maven config already has JUnit 5, H2, Jackson, JDBC. All dependencies are present. No new deps needed.
- `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java` — has `EXPIRY_SENTINEL = "9999-12-31 23:59:59"`. **Always use this constant**, never hardcode the sentinel string.
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` — placeholder, do NOT change.
- `dqs-spark/config/application.properties` — DB config.

### Package Structure (Strict)

Place files exactly in these locations per architecture spec:

```
dqs-spark/src/main/java/com/bank/dqs/
  checks/
    DqCheck.java           ← NEW
    CheckFactory.java      ← NEW
  model/
    DqsConstants.java      ← EXISTING (do not touch)
    DqMetric.java          ← NEW
    MetricNumeric.java     ← NEW
    MetricDetail.java      ← NEW
    DatasetContext.java    ← NEW

dqs-spark/src/test/java/com/bank/dqs/
  checks/
    CheckFactoryTest.java  ← NEW
  model/
    DatasetContextTest.java ← NEW
```

### DqCheck Interface

```java
package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import java.util.List;

public interface DqCheck {
    /**
     * Execute this check against the given dataset context.
     * Never let exceptions escape — catch, log, and return a failure metric.
     */
    List<DqMetric> execute(DatasetContext context);

    /**
     * Returns the canonical check_type string used in the check_config table.
     * Example: "FRESHNESS", "VOLUME", "SCHEMA", "OPS", "DQS_SCORE"
     */
    String getCheckType();
}
```

### DqMetric Interface

```java
package com.bank.dqs.model;

/** Marker interface — root type for all DQ metric results. */
public interface DqMetric {}
```

### MetricNumeric Model

```java
package com.bank.dqs.model;

/**
 * Maps to dq_metric_numeric(dq_run_id, check_type, metric_name, metric_value).
 * dq_run_id is resolved by BatchWriter when writing — not stored here.
 */
public final class MetricNumeric implements DqMetric {
    private final String checkType;
    private final String metricName;
    private final double metricValue;

    public MetricNumeric(String checkType, String metricName, double metricValue) {
        this.checkType = checkType;
        this.metricName = metricName;
        this.metricValue = metricValue;
    }

    public String getCheckType() { return checkType; }
    public String getMetricName() { return metricName; }
    public double getMetricValue() { return metricValue; }
}
```

### MetricDetail Model

```java
package com.bank.dqs.model;

/**
 * Maps to dq_metric_detail(dq_run_id, check_type, detail_type, detail_value).
 * detailValue is a JSON-formatted string stored as JSONB in Postgres.
 * Never store raw PII/PCI — only field names, schema structures, aggregate metrics.
 */
public final class MetricDetail implements DqMetric {
    private final String checkType;
    private final String detailType;
    private final String detailValue;  // JSON string → stored as JSONB

    public MetricDetail(String checkType, String detailType, String detailValue) {
        this.checkType = checkType;
        this.detailType = detailType;
        this.detailValue = detailValue;
    }

    public String getCheckType() { return checkType; }
    public String getDetailType() { return detailType; }
    public String getDetailValue() { return detailValue; }
}
```

### DatasetContext Model

```java
package com.bank.dqs.model;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import java.time.LocalDate;

/**
 * Immutable context for a single dataset being quality-checked.
 * df may be null in unit tests (factory/model tests don't need SparkSession).
 */
public final class DatasetContext {
    public static final String FORMAT_AVRO    = "AVRO";
    public static final String FORMAT_PARQUET = "PARQUET";
    public static final String FORMAT_UNKNOWN = "UNKNOWN";

    private final String datasetName;
    private final String lookupCode;
    private final LocalDate partitionDate;
    private final String parentPath;
    private final Dataset<Row> df;
    private final String format;

    public DatasetContext(String datasetName, String lookupCode, LocalDate partitionDate,
                         String parentPath, Dataset<Row> df, String format) {
        if (datasetName == null || datasetName.isBlank())
            throw new IllegalArgumentException("datasetName must not be null or blank");
        if (partitionDate == null)
            throw new IllegalArgumentException("partitionDate must not be null");
        if (parentPath == null || parentPath.isBlank())
            throw new IllegalArgumentException("parentPath must not be null or blank");
        if (format == null)
            throw new IllegalArgumentException("format must not be null");
        this.datasetName   = datasetName;
        this.lookupCode    = lookupCode;
        this.partitionDate = partitionDate;
        this.parentPath    = parentPath;
        this.df            = df;
        this.format        = format;
    }

    public String getDatasetName()    { return datasetName; }
    public String getLookupCode()     { return lookupCode; }
    public LocalDate getPartitionDate() { return partitionDate; }
    public String getParentPath()     { return parentPath; }
    public Dataset<Row> getDf()       { return df; }
    public String getFormat()         { return format; }
}
```

### CheckFactory — CRITICAL SQL Query

The core of `CheckFactory` is this query pattern:

```sql
SELECT DISTINCT check_type
FROM check_config
WHERE ? LIKE dataset_pattern
  AND enabled = TRUE
  AND expiry_date = ?
```

- First `?` = `ctx.getDatasetName()` (the actual dataset path, e.g., `lob=retail/src_sys_nm=alpha/dataset=sales_daily`)
- Second `?` = `DqsConstants.EXPIRY_SENTINEL` (active records only)
- The LIKE reversal (`value LIKE pattern`) is intentional: `dataset_pattern` stores SQL LIKE patterns (e.g., `lob=retail/%`), and we test whether the dataset name matches any of them.
- This SQL works identically in H2 (tests) and Postgres (production).

```java
package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqsConstants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CheckFactory {
    private final Map<String, DqCheck> registry = new LinkedHashMap<>();

    public void register(DqCheck check) {
        registry.put(check.getCheckType(), check);
    }

    public List<DqCheck> getEnabledChecks(DatasetContext ctx, Connection conn) throws SQLException {
        List<DqCheck> enabled = new ArrayList<>();
        String sql = "SELECT DISTINCT check_type FROM check_config "
                   + "WHERE ? LIKE dataset_pattern AND enabled = TRUE AND expiry_date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ctx.getDatasetName());
            ps.setString(2, DqsConstants.EXPIRY_SENTINEL);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String checkType = rs.getString("check_type");
                    DqCheck check = registry.get(checkType);
                    if (check != null) {
                        enabled.add(check);
                    }
                }
            }
        }
        return enabled;
    }
}
```

### CheckFactoryTest — H2 Test Setup

Use H2 in-memory DB to test `CheckFactory`. The `check_config` DDL is Postgres-compatible — works in H2 with minor adjustment (use `AUTO_INCREMENT` instead of `SERIAL`):

```java
package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CheckFactoryTest {

    private static Connection conn;
    private CheckFactory factory;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        conn = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS check_config (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    dataset_pattern TEXT NOT NULL,
                    check_type      TEXT NOT NULL,
                    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
                    explosion_level INTEGER NOT NULL DEFAULT 0,
                    create_date     TIMESTAMP NOT NULL DEFAULT NOW(),
                    expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
                )
            """);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        factory = new CheckFactory();
        // Clean up between tests
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM check_config");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        conn.close();
    }

    /** Helper: stub DqCheck for testing registration only. */
    private DqCheck stubCheck(String type) {
        return new DqCheck() {
            @Override public String getCheckType() { return type; }
            @Override public List<DqMetric> execute(DatasetContext ctx) { return List.of(); }
        };
    }

    /** Helper: DatasetContext with null df (no SparkSession needed for factory tests). */
    private DatasetContext ctx(String datasetName) {
        return new DatasetContext(datasetName, "LOB_TEST",
                LocalDate.of(2026, 4, 3), "/prod/data", null, DatasetContext.FORMAT_PARQUET);
    }

    @Test
    void getEnabledChecksReturnsRegisteredChecksMatchingPattern() throws Exception {
        factory.register(stubCheck("FRESHNESS"));
        factory.register(stubCheck("VOLUME"));
        insertConfig("lob=retail/%", "FRESHNESS", true);
        insertConfig("lob=retail/%", "VOLUME", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(2, result.size());
    }

    @Test
    void getEnabledChecksExcludesDisabledRows() throws Exception {
        factory.register(stubCheck("FRESHNESS"));
        factory.register(stubCheck("VOLUME"));
        insertConfig("lob=retail/%", "FRESHNESS", true);
        insertConfig("lob=retail/%", "VOLUME", false);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(1, result.size());
        assertEquals("FRESHNESS", result.get(0).getCheckType());
    }

    @Test
    void getEnabledChecksReturnsEmptyListWhenNoPatternMatches() throws Exception {
        factory.register(stubCheck("FRESHNESS"));
        insertConfig("lob=commercial/%", "FRESHNESS", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertTrue(result.isEmpty());
    }

    @Test
    void getEnabledChecksIgnoresUnregisteredCheckTypes() throws Exception {
        // DB has SCHEMA config but no SCHEMA check registered in factory
        insertConfig("lob=retail/%", "SCHEMA", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertTrue(result.isEmpty());
    }

    @Test
    void registerAddsCheckByCheckType() {
        DqCheck check = stubCheck("VOLUME");
        factory.register(check);
        // Verify indirectly — no direct registry access, tested via getEnabledChecks
        assertDoesNotThrow(() -> factory.register(stubCheck("FRESHNESS")));
    }

    private void insertConfig(String pattern, String checkType, boolean enabled) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO check_config(dataset_pattern, check_type, enabled) VALUES (?, ?, ?)")) {
            ps.setString(1, pattern);
            ps.setString(2, checkType);
            ps.setBoolean(3, enabled);
            ps.executeUpdate();
        }
    }
}
```

### DatasetContextTest

```java
package com.bank.dqs.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class DatasetContextTest {

    @Test
    void constructorThrowsOnNullDatasetName() {
        assertThrows(IllegalArgumentException.class, () ->
            new DatasetContext(null, "LOB", LocalDate.now(), "/path", null, DatasetContext.FORMAT_AVRO));
    }

    @Test
    void constructorThrowsOnBlankDatasetName() {
        assertThrows(IllegalArgumentException.class, () ->
            new DatasetContext("   ", "LOB", LocalDate.now(), "/path", null, DatasetContext.FORMAT_AVRO));
    }

    @Test
    void constructorThrowsOnNullPartitionDate() {
        assertThrows(IllegalArgumentException.class, () ->
            new DatasetContext("ds", "LOB", null, "/path", null, DatasetContext.FORMAT_PARQUET));
    }

    @Test
    void constructorThrowsOnNullParentPath() {
        assertThrows(IllegalArgumentException.class, () ->
            new DatasetContext("ds", "LOB", LocalDate.now(), null, null, DatasetContext.FORMAT_PARQUET));
    }

    @Test
    void constructorThrowsOnNullFormat() {
        assertThrows(IllegalArgumentException.class, () ->
            new DatasetContext("ds", "LOB", LocalDate.now(), "/path", null, null));
    }

    @Test
    void constructorAllowsNullDf() {
        assertDoesNotThrow(() ->
            new DatasetContext("ds", "LOB", LocalDate.now(), "/path", null, DatasetContext.FORMAT_PARQUET));
    }

    @Test
    void gettersReturnConstructedValues() {
        LocalDate date = LocalDate.of(2026, 4, 3);
        DatasetContext ctx = new DatasetContext("lob=retail/src_sys_nm=alpha/dataset=sales_daily",
                "LOB001", date, "/prod/data", null, DatasetContext.FORMAT_PARQUET);

        assertEquals("lob=retail/src_sys_nm=alpha/dataset=sales_daily", ctx.getDatasetName());
        assertEquals("LOB001", ctx.getLookupCode());
        assertEquals(date, ctx.getPartitionDate());
        assertEquals("/prod/data", ctx.getParentPath());
        assertNull(ctx.getDf());
        assertEquals(DatasetContext.FORMAT_PARQUET, ctx.getFormat());
    }
}
```

### Java Rules Enforced in This Story

- **try-with-resources** for all JDBC: `PreparedStatement` and `ResultSet` are closed automatically (shown in `CheckFactory`)
- **PreparedStatement** with `?` — zero string concatenation in SQL
- **No raw types** — use `List<DqMetric>`, `Map<String, DqCheck>`
- **Full generic types** — `List<DqMetric>` not `List`
- **EXPIRY_SENTINEL** from `DqsConstants` — never hardcode
- **Constructor validation** with `IllegalArgumentException`
- **Static imports** for Spark SQL functions when used in check implementations (not in this story yet)
- **`throws SQLException`** declared on `getEnabledChecks` — checked exception propagation, not swallow

### Testing With Maven

```bash
cd dqs-spark
mvn test
```

The `pom.xml` is already configured with:
- Java 21 JVM for test fork (`/usr/lib/jvm/java-21-openjdk-amd64/bin/java`)
- All required `--add-opens` flags for Spark reflection
- H2 dependency for in-memory DB testing
- JUnit Jupiter (JUnit 5)

Run just the new tests:
```bash
cd dqs-spark
mvn test -Dtest="CheckFactoryTest,DatasetContextTest"
```

### What NOT to Do in This Story

- **Do NOT create a static registry** in `CheckFactory` — it would persist across tests and be hard to reset
- **Do NOT add Spark dependency** beyond what's already in `pom.xml` (Spark is `provided` scope — available at compile time, not bundled)
- **Do NOT add `getCheckType()` to `DqMetric` interface** — it's on `DqCheck` (the strategy), not the result model. `MetricNumeric` and `MetricDetail` each have their own `getCheckType()` getter (non-interface method).
- **Do NOT implement actual check logic** (FreshnessCheck, VolumeCheck, etc.) — those are stories 2.5-2.9
- **Do NOT modify `DqsJob.java`** — it's a placeholder until story 2.10
- **Do NOT modify `DqsConstants.java`** — already defines `EXPIRY_SENTINEL` correctly
- **Do NOT add ORM or Spring** — this is plain JDBC as designed
- **Do NOT create tests that need SparkSession** — for this story, `df` is null in all test contexts
- **Do NOT query `v_check_config_active` view** — dqs-spark reads raw tables directly (it writes via JDBC, not through the serve-layer views; views are for serve-layer only)

### Cross-Story Design Note

This story creates the backbone for epic-2. All check implementations (stories 2.5–2.9) MUST implement `DqCheck` and be registered in `CheckFactory`. Design decisions here propagate downstream:
- `DqCheck.getCheckType()` strings become the canonical `check_type` values in `check_config` and in DB columns
- `MetricNumeric` and `MetricDetail` are what `BatchWriter` (story 2.10) will write to Postgres
- `DatasetContext` is what `ConsumerZoneScanner` (story 2.2) will produce and `DqsJob` (story 2.10) will pass to checks

### Project Structure Notes

- All files are in `dqs-spark/` — this is the Spark component (Java/Maven)
- Zero files created outside `dqs-spark/`
- The `checks/` package is new — Maven will pick it up automatically since it's under `src/main/java/`
- Test directory `src/test/java/com/bank/dqs/checks/` is also new — Maven Surefire will discover tests automatically

### References

- Story 2.1 AC: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.1)
- Package structure: `_bmad-output/planning-artifacts/architecture.md` § Structure Patterns → dqs-spark
- Java rules: `_bmad-output/project-context.md` § Language-Specific Rules → Java
- Temporal pattern: `_bmad-output/project-context.md` § Temporal Data Pattern
- Testing rules: `_bmad-output/project-context.md` § Testing Rules → dqs-spark
- `DqsConstants.EXPIRY_SENTINEL`: `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`
- `check_config` table DDL: `dqs-serve/src/serve/schema/ddl.sql` (lines 72-82)
- Existing pom.xml: `dqs-spark/pom.xml` (JUnit 5, H2, JDBC already present)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_none_

### Completion Notes List

- Created 6 new source files in dqs-spark: DqMetric, MetricNumeric, MetricDetail, DatasetContext, DqCheck, CheckFactory
- Created 3 test files: CheckFactoryTest (10 tests), DatasetContextTest (12 tests), MetricModelTest (8 tests)
- All 30 JUnit5 tests pass: 0 failures, 0 errors, 0 skipped
- CheckFactory uses PreparedStatement with ? placeholders and EXPIRY_SENTINEL from DqsConstants — no hardcoded values
- LIKE reversal in SQL (? LIKE dataset_pattern) enables wildcard pattern matching from check_config
- DatasetContext allows null df for test-friendly usage without SparkSession
- LinkedHashMap registry preserves insertion order for deterministic check execution
- All ACs satisfied: DqCheck interface (AC1), MetricNumeric (AC2), MetricDetail (AC3), CheckFactory registration+query (AC4+AC5), DatasetContext fields (AC6)

### File List

- dqs-spark/src/main/java/com/bank/dqs/model/DqMetric.java (NEW)
- dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java (NEW)
- dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java (NEW)
- dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java (NEW)
- dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java (NEW)
- dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java (NEW)
- dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java (NEW)
- dqs-spark/src/test/java/com/bank/dqs/model/DatasetContextTest.java (NEW)
- dqs-spark/src/test/java/com/bank/dqs/model/MetricModelTest.java (NEW)

### Review Findings

- [x] [Review][Patch] MetricNumeric missing constructor validation for checkType and metricName [MetricNumeric.java:17] -- FIXED: Added null/blank checks with IllegalArgumentException, consistent with DatasetContext pattern
- [x] [Review][Patch] MetricDetail missing constructor validation for checkType and detailType [MetricDetail.java:21] -- FIXED: Added null/blank checks with IllegalArgumentException, consistent with DatasetContext pattern
- [x] [Review][Patch] No toString() on model classes (MetricNumeric, MetricDetail, DatasetContext) -- FIXED: Added toString() overrides for debugging visibility
- [x] [Review][Patch] CheckFactory.register() accepts null without guard [CheckFactory.java:48] -- FIXED: Added null check with IllegalArgumentException
- [x] [Review][Patch] H2 test DB name collision risk in CheckFactoryTest [CheckFactoryTest.java:28] -- FIXED: Changed from generic `testdb` to `checkfactory_testdb` for test isolation
- [x] [Review][Dismiss] CheckFactory.register() silent overwrite -- By design per spec: "Registering a second implementation with the same check_type overwrites the first"
- [x] [Review][Dismiss] String comparison for TIMESTAMP column -- By design per spec: setString() for EXPIRY_SENTINEL works in both H2 and Postgres
- [x] [Review][Dismiss] No equals/hashCode on models -- Models are value carriers for DB writes, not used in collections as keys

Review agent: claude-opus-4-6 | Review date: 2026-04-03 | Tests: 42 pass (12 new tests added for review fixes)

## Change Log

- 2026-04-03: Story created. Status set to ready-for-dev.
- 2026-04-03: Implementation complete. 6 source files + 3 test files created. 30 tests pass. Status set to review.
- 2026-04-03: Code review complete. 5 patches applied, 3 dismissed. 12 new tests added. 42 total tests pass. Status set to done.
