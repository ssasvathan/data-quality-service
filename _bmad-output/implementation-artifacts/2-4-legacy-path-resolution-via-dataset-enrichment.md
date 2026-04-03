# Story 2.4: Legacy Path Resolution via Dataset Enrichment

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform operator**,
I want the scanner to resolve legacy dataset paths (e.g., `src_sys_nm=omni`) to proper lookup codes via the `dataset_enrichment` table,
so that datasets with non-standard naming are correctly identified for quality tracking.

## Acceptance Criteria

1. **Given** a dataset path with legacy format `src_sys_nm=omni` **When** the scanner processes this path **Then** it queries `v_dataset_enrichment_active` for a matching pattern and resolves to the correct lookup code
2. **Given** a dataset path with no matching enrichment record **When** the scanner processes this path **Then** it uses the raw path segment as the lookup code and logs a warning
3. **Given** a `dataset_enrichment` row maps `omni` to lookup code `UE90` **When** the scanner resolves the path **Then** the `DatasetContext` contains lookup_code = `UE90`

## Tasks / Subtasks

- [x] Task 1: Create `EnrichmentResolver` class (AC: #1, #2, #3)
  - [x] Create `dqs-spark/src/main/java/com/bank/dqs/scanner/EnrichmentResolver.java`
  - [x] Constructor accepts a JDBC `Connection` — throw `IllegalArgumentException` if null
  - [x] Method: `String resolve(String rawLookupCode) throws SQLException`
    - [x] Build `datasetPattern` candidate from raw value: `"src_sys_nm=" + rawLookupCode`
    - [x] Query `v_dataset_enrichment_active` WHERE `? LIKE dataset_pattern` AND `lookup_code IS NOT NULL`
    - [x] Use `PreparedStatement` with `?` placeholder — NEVER string concatenation
    - [x] Use `DqsConstants.EXPIRY_SENTINEL` is NOT needed here since the view already filters active records
    - [x] If a row is found and `lookup_code` is non-null → return the resolved `lookup_code`
    - [x] If no row found → log WARN ("No enrichment record for raw lookup code: {}") → return `rawLookupCode` unchanged
    - [x] Use try-with-resources for `PreparedStatement` and `ResultSet`
    - [x] Add `toString()` for debugging

- [x] Task 2: Modify `ConsumerZoneScanner` to accept and invoke `EnrichmentResolver` (AC: #1, #2, #3)
  - [x] Add optional `EnrichmentResolver` parameter to `ConsumerZoneScanner` constructor
  - [x] Design: use constructor overloading — existing `ConsumerZoneScanner(FileSystem fs)` stays as-is (enrichment disabled); add `ConsumerZoneScanner(FileSystem fs, EnrichmentResolver enrichmentResolver)` for enrichment-enabled use
  - [x] In the `scan()` method, after extracting `rawLookupCode` from `src_sys_nm=` dir name, apply resolution:
    - [x] If `enrichmentResolver` is non-null → call `enrichmentResolver.resolve(rawLookupCode)` → use result as `lookupCode`
    - [x] If `enrichmentResolver` is null → use `rawLookupCode` unchanged (backward-compatible behavior)
  - [x] Wrap `enrichmentResolver.resolve()` in a try/catch for `SQLException` — log ERROR and use raw code on failure (per-dataset isolation principle)
  - [x] Update `toString()` to indicate whether enrichment is enabled

- [x] Task 3: Update `DqsJob` to instantiate `EnrichmentResolver` and pass to scanner (AC: #1)
  - [x] Modify `DqsJob.main()` to open a JDBC connection from `config/application.properties`
  - [x] Read DB config using the existing config loading approach (see Dev Notes for exact properties keys)
  - [x] Create `EnrichmentResolver` with the JDBC connection
  - [x] Pass `EnrichmentResolver` to `ConsumerZoneScanner` constructor
  - [x] Close the JDBC connection after scanning completes (use try-with-resources around the scan block)
  - [x] Log INFO: "Dataset enrichment resolver enabled" when enrichment resolver is active

- [x] Task 4: Write `EnrichmentResolver` tests (AC: #1, #2, #3)
  - [x] Create `dqs-spark/src/test/java/com/bank/dqs/scanner/EnrichmentResolverTest.java`
  - [x] Use H2 in-memory DB (same approach as `CheckFactoryTest`) — distinct DB name `enrichment_testdb`
  - [x] Create `v_dataset_enrichment_active` as a VIEW in H2 (wrapping an inline table): create `dataset_enrichment` table first, then create view `v_dataset_enrichment_active` as `SELECT * FROM dataset_enrichment WHERE expiry_date = '9999-12-31 23:59:59'`
  - [x] Test cases:
    - `resolveReturnsEnrichedLookupCodeWhenPatternMatches` — row maps `src_sys_nm=omni` pattern → `UE90` returned
    - `resolveReturnsRawCodeWhenNoEnrichmentRecord` — no matching row → raw code returned, WARN logged
    - `resolveUsesFirstMatchWhenMultiplePatternsMatch` — verify only one result used (no crash)
    - `resolveReturnsRawCodeWhenLookupCodeIsNull` — row exists but `lookup_code` IS NULL → raw code returned
    - `constructorThrowsOnNullConnection` — `IllegalArgumentException` on null connection
    - `resolveThrowsOnNullRawLookupCode` — `IllegalArgumentException` for null input
  - [x] Test naming: `<action><expectation>` pattern
  - [x] All JUnit 5 (`@Test`, `@BeforeAll`, `@BeforeEach`, `@AfterAll`, lifecycle pattern same as CheckFactoryTest)

- [x] Task 5: Write `ConsumerZoneScanner` enrichment integration tests (AC: #1, #2, #3)
  - [x] Add new test methods to `ConsumerZoneScannerTest.java` in a new nested class or alongside existing tests
  - [x] Use Mockito-free approach: create a real `EnrichmentResolver` backed by H2 in-memory DB
  - [x] Alternatively: create a simple test stub implementing the resolution logic inline (avoids SparkSession + H2 simultaneously)
  - [x] Test cases:
    - `scanUsesEnrichedLookupCodeWhenResolverProvided` — scanner with enrichment resolves `omni` → `UE90` in returned `DatasetContext`
    - `scanUsesRawLookupCodeWhenNoResolverProvided` — scanner without resolver returns raw `src_sys_nm` value unchanged
    - `scanContinuesAfterEnrichmentSqlException` — enrichment failure does not crash scanner, raw code used
  - [x] Keep existing 19 ConsumerZoneScanner tests passing — zero regressions

## Dev Notes

### Critical Design: View vs Raw Table for dqs-spark JDBC Queries

The project-context anti-pattern "Never query raw tables in the serve layer" applies ONLY to the **serve layer** (FastAPI). dqs-spark is free to query either raw tables or views via JDBC.

Story 2-1's `CheckFactory` queries the raw `check_config` table. However, the AC for this story explicitly specifies `v_dataset_enrichment_active`. Use the view as specified in AC#1 — it is a simple SQL `SELECT * FROM dataset_enrichment WHERE expiry_date = '9999-12-31 23:59:59'` filter, works identically in JDBC.

**Key difference from CheckFactory pattern:** CheckFactory uses `EXPIRY_SENTINEL` in its WHERE clause because it queries the raw table. `EnrichmentResolver` queries the view `v_dataset_enrichment_active` — the view already filters on the sentinel. Do NOT add a redundant sentinel filter when querying the view.

### JDBC SQL for EnrichmentResolver

```sql
SELECT lookup_code
FROM v_dataset_enrichment_active
WHERE ? LIKE dataset_pattern
  AND lookup_code IS NOT NULL
LIMIT 1
```

- First `?` = constructed candidate: `"src_sys_nm=" + rawLookupCode` (e.g., `"src_sys_nm=omni"`)
- `LIKE` reversal is intentional: `dataset_pattern` stores SQL LIKE patterns (e.g., `src_sys_nm=omni`, `src_sys_nm=omni%`). We test whether the candidate matches any stored pattern — same LIKE reversal used in `CheckFactory`.
- `LIMIT 1` — take first match if multiple patterns match.
- `AND lookup_code IS NOT NULL` — skip enrichment rows that only carry `custom_weights` or `sla_hours` (lookup_code is nullable per schema).
- H2 supports `LIMIT` syntax — this SQL works in both H2 and Postgres.

### Existing Files — Do NOT Modify Without Explicit Task

| File | Status | Notes |
|---|---|---|
| `dqs-spark/src/main/java/com/bank/dqs/scanner/PathScanner.java` | EXISTING — do NOT modify | Interface unchanged |
| `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java` | MODIFY (Task 2) | Add overloaded constructor + enrichment in scan() |
| `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java` | EXISTING — do NOT modify | lookupCode field already exists, may be null |
| `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java` | EXISTING — do NOT modify | Has EXPIRY_SENTINEL; not needed for view query |
| `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` | MODIFY (Task 3) | Add JDBC + EnrichmentResolver wiring |
| `dqs-spark/pom.xml` | EXISTING — do NOT modify | All deps present; H2 already in test scope |

### Package Structure (Strict)

```
dqs-spark/src/main/java/com/bank/dqs/
  scanner/
    PathScanner.java            ← EXISTING (do not modify)
    ConsumerZoneScanner.java    ← MODIFY (Task 2)
    EnrichmentResolver.java     ← NEW (Task 1)

dqs-spark/src/test/java/com/bank/dqs/
  scanner/
    ConsumerZoneScannerTest.java ← MODIFY (add enrichment tests, Task 5)
    EnrichmentResolverTest.java  ← NEW (Task 4)
```

Zero files outside `dqs-spark/`.

### EnrichmentResolver — Full Design

```java
package com.bank.dqs.scanner;

import com.bank.dqs.model.DqsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Resolves raw HDFS src_sys_nm values to canonical lookup codes via the
 * dataset_enrichment table. Legacy path patterns like "omni" are mapped to
 * proper lookup codes (e.g., "UE90") to enable correct quality tracking.
 *
 * <p>Queries the active-record view {@code v_dataset_enrichment_active}.
 */
public final class EnrichmentResolver {
    private static final Logger LOG = LoggerFactory.getLogger(EnrichmentResolver.class);

    private static final String SQL =
        "SELECT lookup_code FROM v_dataset_enrichment_active "
        + "WHERE ? LIKE dataset_pattern AND lookup_code IS NOT NULL LIMIT 1";

    private final Connection conn;

    public EnrichmentResolver(Connection conn) {
        if (conn == null) throw new IllegalArgumentException("Connection must not be null");
        this.conn = conn;
    }

    /**
     * Resolve a raw src_sys_nm value to the canonical lookup code.
     * Queries v_dataset_enrichment_active with pattern "src_sys_nm={rawLookupCode}".
     *
     * @param rawLookupCode the src_sys_nm value extracted from HDFS path
     * @return enriched lookup code if found; rawLookupCode unchanged if no match
     * @throws IllegalArgumentException if rawLookupCode is null
     * @throws SQLException on JDBC errors
     */
    public String resolve(String rawLookupCode) throws SQLException {
        if (rawLookupCode == null) throw new IllegalArgumentException("rawLookupCode must not be null");
        String candidate = "src_sys_nm=" + rawLookupCode;
        try (PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, candidate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String resolved = rs.getString("lookup_code");
                    LOG.debug("Resolved '{}' to lookup_code='{}'", rawLookupCode, resolved);
                    return resolved;
                }
            }
        }
        LOG.warn("No enrichment record found for raw lookup code: '{}' — using raw value", rawLookupCode);
        return rawLookupCode;
    }

    @Override
    public String toString() {
        return "EnrichmentResolver{conn=" + conn + "}";
    }
}
```

### ConsumerZoneScanner Modification

Add a second constructor and a `final EnrichmentResolver enrichmentResolver` field (nullable):

```java
// Existing constructor — enrichment disabled
public ConsumerZoneScanner(FileSystem fs) {
    this(fs, null);
}

// New constructor — enrichment enabled
public ConsumerZoneScanner(FileSystem fs, EnrichmentResolver enrichmentResolver) {
    if (fs == null) throw new IllegalArgumentException("FileSystem must not be null");
    this.fs = fs;
    this.enrichmentResolver = enrichmentResolver;
}
```

In `scan()`, after extracting `rawLookupCode`, add:

```java
String lookupCode = rawLookupCode;
if (enrichmentResolver != null) {
    try {
        lookupCode = enrichmentResolver.resolve(rawLookupCode);
    } catch (SQLException e) {
        LOG.error("Enrichment resolution failed for '{}', using raw value: {}",
                  rawLookupCode, e.getMessage());
        // lookupCode remains rawLookupCode — per-dataset isolation
    }
}
```

The `datasetName` field is constructed from `rawLookupCode` (the HDFS directory name), NOT the enriched lookup code. `datasetName` is used for `check_config` LIKE matching and must reflect the actual path:
```java
String datasetName = "src_sys_nm=" + rawLookupCode + "/partition_date=" + dateStr;
```
Only the `lookupCode` field in `DatasetContext` gets the enriched value.

### DqsJob — DB Config Loading

Read the JDBC connection properties from `dqs-spark/config/application.properties`. The file already exists and contains the DB connection config. Use standard `java.util.Properties` to load it:

```java
// Load application.properties from classpath
Properties props = new Properties();
try (InputStream is = DqsJob.class.getResourceAsStream("/application.properties")) {
    if (is != null) props.load(is);
}
String jdbcUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/postgres");
String dbUser  = props.getProperty("db.user", "postgres");
String dbPass  = props.getProperty("db.password", "localdev");
```

Open the JDBC connection and wrap scanner+enrichment in try-with-resources:

```java
try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
    EnrichmentResolver resolver = new EnrichmentResolver(conn);
    LOG.info("Dataset enrichment resolver enabled");
    ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs, resolver);
    List<DatasetContext> datasets = scanner.scan(parentPath, partitionDate);
    // ... rest of dataset loading loop
}
```

Check `dqs-spark/config/application.properties` for the exact property keys before implementing.

### H2 View Creation in Tests

H2 supports `CREATE OR REPLACE VIEW`. Use this DDL in `EnrichmentResolverTest.@BeforeAll`:

```java
stmt.execute("""
    CREATE TABLE IF NOT EXISTS dataset_enrichment (
        id              INTEGER PRIMARY KEY AUTO_INCREMENT,
        dataset_pattern TEXT NOT NULL,
        lookup_code     TEXT,
        custom_weights  VARCHAR(4096),
        sla_hours       NUMERIC,
        explosion_level INTEGER NOT NULL DEFAULT 0,
        create_date     TIMESTAMP NOT NULL DEFAULT NOW(),
        expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
    )
""");
stmt.execute("""
    CREATE OR REPLACE VIEW v_dataset_enrichment_active AS
        SELECT * FROM dataset_enrichment
        WHERE expiry_date = '9999-12-31 23:59:59'
""");
```

Note: H2 does not support `JSONB` type — use `VARCHAR(4096)` for `custom_weights` in test DDL. This is test-only and has no effect on production code.

### dataset_enrichment Schema Summary (from story 1-4 DDL)

```sql
CREATE TABLE dataset_enrichment (
    id              SERIAL PRIMARY KEY,
    dataset_pattern TEXT NOT NULL,     -- e.g., "src_sys_nm=omni", "src_sys_nm=omni%"
    lookup_code     TEXT,              -- nullable: enrichment may only carry custom_weights
    custom_weights  JSONB,             -- nullable: weight overrides per check_type
    sla_hours       NUMERIC,           -- nullable
    explosion_level INTEGER NOT NULL DEFAULT 0,
    create_date     TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dataset_enrichment_dataset_pattern_expiry_date
        UNIQUE (dataset_pattern, expiry_date)
);
```

`dataset_pattern` stores literal values or SQL LIKE wildcards (e.g., `src_sys_nm=omni%`). The LIKE reversal in the query (`? LIKE dataset_pattern`) matches the candidate against stored patterns.

### Java Rules Enforced in This Story

- **try-with-resources** for all JDBC (`PreparedStatement`, `ResultSet`) — no manual close
- **PreparedStatement** with `?` placeholders — zero string concatenation in SQL
- **Constructor validation** with `IllegalArgumentException` for null parameters
- **Per-dataset isolation**: `SQLException` from enrichment is caught, logged at ERROR, raw code used — never propagates to crash the scanner
- **`throws SQLException`** declared on `resolve()` method — checked exception propagation (caller catches it in scanner)
- **`toString()`** on `EnrichmentResolver` and update on `ConsumerZoneScanner`
- **No hardcoded EXPIRY_SENTINEL** inline — view already handles the filter; if raw table is ever used, reference `DqsConstants.EXPIRY_SENTINEL`
- **Full generic types** — `List<DatasetContext>` — no raw types

### Testing With Maven

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test
```

Run just the new tests:
```bash
mvn test -Dtest="EnrichmentResolverTest,ConsumerZoneScannerTest"
```

Current test count before this story: **72 tests** (all passing). All 72 must continue passing after this story. New tests add to this total.

### What NOT To Do In This Story

- **Do NOT modify `DatasetContext.java`** — it already has `lookupCode` field (nullable)
- **Do NOT modify `PathScanner.java`** — interface unchanged
- **Do NOT modify the `datasetName` field** — it reflects the actual HDFS directory name, not the enriched lookup code
- **Do NOT add a sentinel filter in `EnrichmentResolver.SQL`** — `v_dataset_enrichment_active` view already filters on sentinel
- **Do NOT add `DqsConstants.EXPIRY_SENTINEL` to `EnrichmentResolver`** — only needed when querying raw tables
- **Do NOT add new Maven dependencies** — H2, JDBC, SLF4J all already present
- **Do NOT use the single-arg constructor `ConsumerZoneScanner(fs)` in DqsJob** — use the two-arg constructor to enable enrichment
- **Do NOT create files outside `dqs-spark/`** — this story is purely within the Spark component
- **Do NOT let enrichment `SQLException` propagate to the scanner's caller** — catch in scanner, log, use raw code

### Cross-Story Context

- **Story 2.1 (DONE)**: Created `DatasetContext` with `lookupCode` field (nullable). `CheckFactory` established LIKE-reversal pattern for querying config tables — same pattern used here.
- **Story 2.2 (DONE)**: `ConsumerZoneScanner` currently sets `lookupCode` = raw `src_sys_nm` value. This story enriches it. The `datasetName` field stays as raw HDFS path segment.
- **Story 2.3 (DONE)**: `DqsJob` currently creates `ConsumerZoneScanner(fs)`. This story modifies it to use the 2-arg constructor with `EnrichmentResolver`.
- **Story 2.5–2.9 (NEXT)**: Check implementations receive `DatasetContext` — they will see the enriched `lookupCode` when querying `dq_run` records. DQS Score check (2.9) uses custom_weights from `dataset_enrichment` (separate lookup, not in scope here).
- **Story 2.10**: `BatchWriter` uses `ctx.getLookupCode()` when writing `dq_run` records — must be the enriched value.

### Previous Story Intelligence (from 2-1, 2-2, 2-3)

Patterns to apply proactively:

- **H2 DB name uniqueness**: Use `enrichment_testdb` in `EnrichmentResolverTest` — not `testdb` (collision risk with CheckFactoryTest which uses `checkfactory_testdb`)
- **`toString()` on all new classes**: Applied to `EnrichmentResolver` (and update on `ConsumerZoneScanner`)
- **Null guard + `IllegalArgumentException`** on all constructor params and critical method args
- **Test lifecycle**: `@BeforeAll` for DB setup, `@BeforeEach` for data cleanup, `@AfterAll` for connection close — same as `CheckFactoryTest`
- **Constructor validation first**: Guard clauses at top of constructor, not scattered inline
- **Per-dataset failure isolation**: `SQLException` caught in scanner, logged at ERROR, raw value used — this pattern was established in story 2.3 for format errors
- **Review findings from 2-2**: Filter non-dataset entries defensively; review may ask for defensive guards in `resolve()` too (null result set, column missing) — pre-empt with null check on `rs.getString("lookup_code")`

### Git Commit Pattern

Recent commits follow: `bmad(epic-2/2-X-story-slug): complete workflow and quality gates`

### Project Structure Notes

- `EnrichmentResolver.java` goes in `scanner/` package — it's part of the scanning/discovery phase, alongside `PathScanner` and `ConsumerZoneScanner`
- `EnrichmentResolverTest.java` goes in `src/test/java/com/bank/dqs/scanner/` — mirrors main source structure
- No new top-level directories created
- No changes to any component outside `dqs-spark/`

### References

- Story 2.4 AC: `_bmad-output/planning-artifacts/epics/epic-2-dataset-discovery-tier-1-quality-checks.md` (Story 2.4)
- `dataset_enrichment` table DDL: `dqs-serve/src/serve/schema/ddl.sql` (lines 84-95)
- `v_dataset_enrichment_active` view DDL: `dqs-serve/src/serve/schema/views.sql` (line 22)
- `idx_dataset_enrichment_dataset_pattern` index: `dqs-serve/src/serve/schema/ddl.sql` (line 107)
- LIKE-reversal SQL pattern established in: `_bmad-output/implementation-artifacts/2-1-dqcheck-strategy-interface-metric-models-check-factory.md` (CheckFactory.java SQL section)
- H2 test DB approach: `_bmad-output/implementation-artifacts/2-1-dqcheck-strategy-interface-metric-models-check-factory.md` (CheckFactoryTest H2 Test Setup)
- Java rules: `_bmad-output/project-context.md` — Language-Specific Rules — Java
- Temporal pattern: `_bmad-output/project-context.md` — Temporal Data Pattern
- Per-dataset isolation: `_bmad-output/project-context.md` — Framework-Specific Rules — Spark
- `ConsumerZoneScanner.java`: `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java`
- `DqsJob.java`: `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`
- `DqsConstants.EXPIRY_SENTINEL`: `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`
- Application properties: `dqs-spark/config/application.properties`
- Architecture FR mapping (FR6 → legacy paths): `_bmad-output/planning-artifacts/architecture.md` — Project Structure & Boundaries

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_none_

### Completion Notes List

- Created `EnrichmentResolver.java` in `scanner/` package. Uses LIKE-reversal SQL (`? LIKE dataset_pattern`) against `v_dataset_enrichment_active` view. View handles sentinel filtering — no `EXPIRY_SENTINEL` needed. try-with-resources for `PreparedStatement` and `ResultSet`. Null guards on constructor and `resolve()` method via `IllegalArgumentException`.
- Modified `ConsumerZoneScanner.java`: added nullable `enrichmentResolver` field, refactored single-arg constructor to delegate via `this(fs, null)`, added two-arg constructor, applied enrichment in `scan()` after extracting `rawLookupCode`. Per-dataset isolation: `SQLException` caught, logged at ERROR, raw code used. `datasetName` still uses `rawLookupCode` (raw HDFS dir name); only `lookupCode` field gets enriched value. `toString()` updated to show enrichment resolver status.
- Modified `DqsJob.java`: loads `application.properties` from classpath, opens JDBC connection via `DriverManager`, creates `EnrichmentResolver` and passes to `ConsumerZoneScanner` two-arg constructor, wrapped in try-with-resources. Falls back gracefully to no-enrichment scanner on `SQLException` during DB connection.
- Tests were pre-created by ATDD step. All 93 tests pass: 8 `EnrichmentResolverTest`, 7 `ConsumerZoneScannerEnrichmentTest`, 19 `ConsumerZoneScannerTest`, plus 59 pre-existing tests (CheckFactory, DqsJobArgParser, MetricModel, DatasetContext, DatasetReader).
- All 3 ACs verified: AC1 (view queried via LIKE reversal), AC2 (raw code returned on no match with WARN log), AC3 (DatasetContext.lookupCode = "UE90" when omni → UE90 mapping exists).

### File List

- `dqs-spark/src/main/java/com/bank/dqs/scanner/EnrichmentResolver.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java` (MODIFIED)
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` (MODIFIED)
- `dqs-spark/src/test/java/com/bank/dqs/scanner/EnrichmentResolverTest.java` (pre-created by ATDD, unchanged)
- `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerEnrichmentTest.java` (pre-created by ATDD, unchanged)

## Change Log

- 2026-04-03: Story created. Status set to ready-for-dev.
- 2026-04-03: Implementation complete. Created EnrichmentResolver.java, modified ConsumerZoneScanner.java and DqsJob.java. All 93 tests pass (15 new + 78 pre-existing). Status set to review.
