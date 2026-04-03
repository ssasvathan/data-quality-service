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
storyId: 2-4-legacy-path-resolution-via-dataset-enrichment
tddPhase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/2-4-legacy-path-resolution-via-dataset-enrichment.md
  - _bmad-output/project-context.md
  - dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java
  - dqs-spark/src/main/java/com/bank/dqs/DqsJob.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java
  - dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java
---

# ATDD Checklist: Story 2-4 — Legacy Path Resolution via Dataset Enrichment

## Step 1: Preflight & Context

- **Stack detected:** `backend` (Java 17 source / Java 21 JVM, Maven, dqs-spark, JUnit5 + H2 + Hadoop LocalFileSystem)
- **Story file:** `_bmad-output/implementation-artifacts/2-4-legacy-path-resolution-via-dataset-enrichment.md`
- **Status at start:** `ready-for-dev`
- **Prerequisites verified:**
  - [x] Story has clear acceptance criteria (AC1–AC3)
  - [x] `dqs-spark/pom.xml` exists with JUnit5 (5.10.2), H2 in test scope, SLF4J — no new dependencies needed
  - [x] `ConsumerZoneScanner.java` exists from story 2-2 with single-arg constructor
  - [x] `DatasetContext.java` exists with nullable `lookupCode` field
  - [x] `DqsJob.java` exists from story 2-3 using `ConsumerZoneScanner(fs)` (single-arg)
  - [x] `DqsConstants.EXPIRY_SENTINEL` exists — NOT needed for this story (view filters already)
  - [x] H2 test DB pattern established in `CheckFactoryTest` (uses `checkfactory_testdb`)
  - [x] Test framework: JUnit5 (Maven Surefire, Java 21 forked JVM with `--add-opens` flags)
  - [x] Existing 19 `ConsumerZoneScannerTest` tests confirmed passing — zero regressions expected
  - [x] Story specifies distinct H2 DB name: `enrichment_testdb`

---

## Step 2: Generation Mode

**Mode selected:** AI generation (backend Java — no browser recording needed)
**Resolved execution mode:** Sequential (no subagent/agent-team runtime available)

---

## Step 3: Test Strategy

### Acceptance Criteria to Test Scenario Mapping

| AC | Scenario | Level | Priority | Test Class | Test Method |
|---|---|---|---|---|---|
| AC1 | EnrichmentResolver queries v_dataset_enrichment_active for matching pattern | Unit (H2) | P0 | `EnrichmentResolverTest` | `resolveReturnsEnrichedLookupCodeWhenPatternMatches` |
| AC2 | No matching enrichment record → raw code returned, WARN logged | Unit (H2) | P0 | `EnrichmentResolverTest` | `resolveReturnsRawCodeWhenNoEnrichmentRecord` |
| AC3 | row maps omni → UE90 → DatasetContext.lookupCode = UE90 | Integration (H2+FS) | P0 | `ConsumerZoneScannerEnrichmentTest` | `scanUsesEnrichedLookupCodeWhenResolverProvided` |
| AC1, AC3 | Wildcard pattern src_sys_nm=omni% matches omni_ext variant | Unit (H2) | P1 | `EnrichmentResolverTest` | `resolveMatchesWildcardPatternForPrefixVariants` |
| AC2 | Scanner without resolver returns raw code (backward compat) | Integration (FS) | P0 | `ConsumerZoneScannerEnrichmentTest` | `scanUsesRawLookupCodeWhenNoResolverProvided` |
| — | Multiple patterns match → LIMIT 1, no crash | Unit (H2) | P1 | `EnrichmentResolverTest` | `resolveUsesFirstMatchWhenMultiplePatternsMatch` |
| — | Row exists but lookup_code IS NULL → raw code returned | Unit (H2) | P1 | `EnrichmentResolverTest` | `resolveReturnsRawCodeWhenLookupCodeIsNull` |
| — | Expired enrichment row not returned via view | Unit (H2) | P1 | `EnrichmentResolverTest` | `resolveReturnsRawCodeWhenMatchingRowIsExpired` |
| — | EnrichmentResolver constructor throws on null Connection | Unit | P0 | `EnrichmentResolverTest` | `constructorThrowsOnNullConnection` |
| — | resolve() throws on null rawLookupCode | Unit | P0 | `EnrichmentResolverTest` | `resolveThrowsOnNullRawLookupCode` |
| — | Per-dataset isolation: SQLException from enrichment does not crash scanner | Integration (H2+FS) | P0 | `ConsumerZoneScannerEnrichmentTest` | `scanContinuesAfterEnrichmentSqlException` |
| AC2 | No enrichment match → resolver returns raw, scanner uses it | Integration (H2+FS) | P0 | `ConsumerZoneScannerEnrichmentTest` | `scanUsesRawCodeWhenResolverFindsNoMatch` |
| AC3 | Multiple datasets — each resolved independently | Integration (H2+FS) | P0 | `ConsumerZoneScannerEnrichmentTest` | `scanResolvesEachDatasetIndependently` |
| — | Two-arg constructor with null resolver = enrichment disabled | Integration (FS) | P0 | `ConsumerZoneScannerEnrichmentTest` | `scanWithNullResolverUsesRawLookupCode` |
| — | toString() updated to indicate enrichment status | Unit | P2 | `ConsumerZoneScannerEnrichmentTest` | `toStringIndicatesEnrichmentEnabled` |

### Test Levels Selected

- **Unit tests (H2):** `EnrichmentResolverTest` — exercises SQL logic against an in-memory H2 database with `v_dataset_enrichment_active` VIEW. No Spark, no Hadoop.
- **Integration tests (H2 + Hadoop LocalFileSystem):** `ConsumerZoneScannerEnrichmentTest` — combines a real `EnrichmentResolver` (backed by H2) with a real `ConsumerZoneScanner` scanning local filesystem via `@TempDir`. No Mockito. Tests the end-to-end path: HDFS scan → enrichment resolution → `DatasetContext.lookupCode`.
- **No E2E tests:** This story is purely within the `dqs-spark` component; no API endpoints or browser interactions.

### TDD Red Phase Mechanism

This is a Java project. The TDD red phase is a **compilation failure** — tests import `com.bank.dqs.scanner.EnrichmentResolver` (a class that does not yet exist) and reference the two-arg `ConsumerZoneScanner(FileSystem, EnrichmentResolver)` constructor (which does not yet exist). Maven `test-compile` will fail until the implementation is complete. No `@Disabled` annotation is needed.

---

## Step 4: Test Generation (Sequential Mode)

### Test Files

| File | Tests | Framework | Red Phase |
|---|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/scanner/EnrichmentResolverTest.java` | 8 | JUnit5, H2 | Compile failure (`EnrichmentResolver` class missing) |
| `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerEnrichmentTest.java` | 7 | JUnit5, H2, Hadoop LocalFileSystem | Compile failure (`EnrichmentResolver` class missing; two-arg `ConsumerZoneScanner` constructor missing) |

**Total new test methods: 15**
**Existing tests preserved: 19 (ConsumerZoneScannerTest) + 14 (CheckFactoryTest) + other tests = 72 total**
**Expected total after story: 87 tests**

---

### Test File 1: EnrichmentResolverTest.java

**Location:** `dqs-spark/src/test/java/com/bank/dqs/scanner/EnrichmentResolverTest.java`

**H2 DDL used (in @BeforeAll):**
```java
stmt.execute("""
    CREATE TABLE IF NOT EXISTS dataset_enrichment (
        id              INTEGER PRIMARY KEY AUTO_INCREMENT,
        dataset_pattern TEXT NOT NULL,
        lookup_code     TEXT,
        custom_weights  VARCHAR(4096),    -- H2 uses VARCHAR; prod uses JSONB
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

**Note on `custom_weights`:** H2 does not support `JSONB`. Test DDL uses `VARCHAR(4096)` instead — test-only divergence, no effect on production code.

**Test methods:**

| Test Method | AC | Priority | What It Verifies |
|---|---|---|---|
| `resolveReturnsEnrichedLookupCodeWhenPatternMatches` | AC1, AC3 | P0 | `src_sys_nm=omni` pattern → resolve("omni") = "UE90" |
| `resolveReturnsRawCodeWhenNoEnrichmentRecord` | AC2 | P0 | Empty view → resolve returns raw code unchanged |
| `resolveUsesFirstMatchWhenMultiplePatternsMatch` | — | P1 | Two patterns match → LIMIT 1, no crash, valid code returned |
| `resolveReturnsRawCodeWhenLookupCodeIsNull` | — | P1 | Row exists but `lookup_code IS NULL` → AND filter excludes it → raw returned |
| `constructorThrowsOnNullConnection` | — | P0 | `new EnrichmentResolver(null)` throws `IllegalArgumentException` |
| `resolveThrowsOnNullRawLookupCode` | — | P0 | `resolve(null)` throws `IllegalArgumentException` |
| `resolveMatchesWildcardPatternForPrefixVariants` | AC1 | P1 | `src_sys_nm=omni%` LIKE pattern matches `src_sys_nm=omni_ext` via LIKE reversal |
| `resolveReturnsRawCodeWhenMatchingRowIsExpired` | — | P1 | Expired row invisible through view → raw code returned |

---

### Test File 2: ConsumerZoneScannerEnrichmentTest.java

**Location:** `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerEnrichmentTest.java`

**Setup:** Two resources initialized in `@BeforeAll`:
1. Hadoop `LocalFileSystem` (same pattern as `ConsumerZoneScannerTest`)
2. H2 connection (`scanner_enrichment_testdb`) with `dataset_enrichment` table and view

Both are torn down in `@AfterAll`. Enrichment table data deleted in `@BeforeEach`.

**Test methods:**

| Test Method | AC | Priority | What It Verifies |
|---|---|---|---|
| `scanUsesEnrichedLookupCodeWhenResolverProvided` | AC1, AC3 | P0 | Scanner with resolver: `omni` → `UE90` in `DatasetContext.lookupCode`; `datasetName` still uses raw HDFS path |
| `scanUsesRawLookupCodeWhenNoResolverProvided` | AC2 | P0 | Single-arg constructor: raw `omni` used as `lookupCode` (backward compat) |
| `scanContinuesAfterEnrichmentSqlException` | — | P0 | Closed connection causes `SQLException`; scanner catches it, logs ERROR, returns both datasets with raw codes |
| `scanUsesRawCodeWhenResolverFindsNoMatch` | AC2 | P0 | Resolver returns raw code when no match; scanner uses it |
| `scanResolvesEachDatasetIndependently` | AC3 | P0 | Two datasets: `omni` → `UE90`; `cb10` → `cb10` (no enrichment row) |
| `scanWithNullResolverUsesRawLookupCode` | — | P0 | Two-arg constructor `ConsumerZoneScanner(fs, null)` = enrichment disabled |
| `toStringIndicatesEnrichmentEnabled` | — | P2 | `toString()` reflects enrichment enabled/disabled state |

---

## Step 4C: Aggregation

### TDD Red Phase Validation

**EnrichmentResolverTest:**
- [x] Imports `com.bank.dqs.scanner.EnrichmentResolver` (class does not exist yet) — compilation fails in red phase
- [x] All 8 tests assert EXPECTED behavior per AC (not placeholder assertions)
- [x] H2 in-memory DB with distinct name `enrichment_testdb` (no collision with `checkfactory_testdb`)
- [x] `@BeforeAll` for DB/table setup, `@BeforeEach` for data cleanup, `@AfterAll` for connection close
- [x] `v_dataset_enrichment_active` VIEW created as specified in story Dev Notes
- [x] H2-compatible DDL: `VARCHAR(4096)` for `custom_weights` instead of `JSONB`
- [x] Test naming: `<action><expectation>` pattern throughout
- [x] No Mockito — real H2 database used

**ConsumerZoneScannerEnrichmentTest:**
- [x] Imports `com.bank.dqs.scanner.EnrichmentResolver` (class does not exist yet)
- [x] References two-arg `new ConsumerZoneScanner(fs, resolver)` constructor (does not exist yet) — compilation fails in red phase
- [x] Uses distinct H2 DB name `scanner_enrichment_testdb`
- [x] Both Hadoop LocalFileSystem and H2 connection managed with proper lifecycle
- [x] Per-dataset isolation test uses a closed JDBC connection to simulate real `SQLException`
- [x] `@TempDir` provides automatic HDFS-like filesystem cleanup
- [x] No Mockito, no MiniDFSCluster, no running HDFS or Postgres required

### Summary Statistics

| Metric | Value |
|---|---|
| New test classes | 2 |
| New test methods | 15 |
| EnrichmentResolverTest methods | 8 |
| ConsumerZoneScannerEnrichmentTest methods | 7 |
| Existing tests preserved | 72 |
| Expected total after story | 87 |
| Test framework | JUnit5 / Maven Surefire |
| Java version (test JVM) | Java 21 (configured in pom.xml) |
| New dependencies needed | None (H2, JDBC, SLF4J, Hadoop already present) |
| Execution mode | Sequential |

---

## Step 5: Validation & Completion

### Checklist Validation

- [x] Prerequisites satisfied (H2 in test scope, JUnit5, SLF4J — all in `pom.xml`)
- [x] 2 new test files created under `dqs-spark/src/test/java/com/bank/dqs/scanner/`
- [x] All 3 ACs covered by at least one test method
- [x] No `@Disabled` (correct — compilation failure is the red phase in Java)
- [x] `@BeforeAll`/`@BeforeEach`/`@AfterAll` lifecycle used correctly in both files
- [x] H2 DB names are distinct from each other and from existing `checkfactory_testdb` and `testdb`
- [x] H2 VIEW `v_dataset_enrichment_active` created correctly with sentinel filter
- [x] LIKE reversal SQL pattern matches story Dev Notes specification
- [x] `custom_weights` uses `VARCHAR(4096)` not `JSONB` (H2 compatibility)
- [x] Per-dataset isolation tested via closed JDBC connection (real SQLException, not mock)
- [x] `DatasetContext.java` not modified (lookupCode field already nullable)
- [x] `PathScanner.java` not modified
- [x] `DqsConstants.java` not modified (EXPIRY_SENTINEL not needed in EnrichmentResolver)
- [x] `pom.xml` not modified
- [x] Zero files created outside `dqs-spark/`
- [x] No shared config files created
- [x] All 19 existing `ConsumerZoneScannerTest` tests expected to continue passing
- [x] `datasetName` field in assertions still uses raw HDFS path (not enriched code)
- [x] No temp artifacts in random locations — test output in `_bmad-output/test-artifacts/`

### Generated Files

| File | Type | Description |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/scanner/EnrichmentResolverTest.java` | NEW | 8 unit tests for EnrichmentResolver JDBC logic against H2 |
| `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerEnrichmentTest.java` | NEW | 7 integration tests for ConsumerZoneScanner enrichment path |
| `_bmad-output/test-artifacts/atdd-checklist-2-4-legacy-path-resolution-via-dataset-enrichment.md` | NEW | This checklist |

### Files NOT Modified

| File | Reason |
|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/scanner/ConsumerZoneScannerTest.java` | Existing 19 tests preserved as-is; enrichment tests go in separate class |
| `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` | Not in scope; H2 DB name is distinct |
| `dqs-spark/pom.xml` | Already correct — H2, JDBC, SLF4J all present |
| `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java` | Not modified — `lookupCode` already nullable |
| `dqs-spark/src/main/java/com/bank/dqs/scanner/PathScanner.java` | Not modified — interface unchanged |
| `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java` | Not modified — EXPIRY_SENTINEL not needed in EnrichmentResolver (view handles it) |

### Acceptance Criteria Coverage

| AC | Description | Covered By | Status |
|---|---|---|---|
| AC1 | Path with `src_sys_nm=omni` queries `v_dataset_enrichment_active` for matching pattern and resolves lookup code | `resolveReturnsEnrichedLookupCodeWhenPatternMatches`, `resolveMatchesWildcardPatternForPrefixVariants`, `scanUsesEnrichedLookupCodeWhenResolverProvided` | COVERED |
| AC2 | No matching enrichment record → raw path segment used as lookup code, WARN logged | `resolveReturnsRawCodeWhenNoEnrichmentRecord`, `scanUsesRawLookupCodeWhenNoResolverProvided`, `scanUsesRawCodeWhenResolverFindsNoMatch` | COVERED |
| AC3 | `dataset_enrichment` row maps `omni` → `UE90` → `DatasetContext.lookupCode = "UE90"` | `scanUsesEnrichedLookupCodeWhenResolverProvided`, `scanResolvesEachDatasetIndependently` | COVERED |

### Test Scenarios by Category

**Happy Path (P0):**
1. `resolveReturnsEnrichedLookupCodeWhenPatternMatches` — exact pattern match, returns UE90
2. `scanUsesEnrichedLookupCodeWhenResolverProvided` — scanner calls resolver, DatasetContext gets UE90
3. `scanResolvesEachDatasetIndependently` — mixed: omni→UE90, cb10→cb10

**No-Match / Raw Code Fallback (P0):**
4. `resolveReturnsRawCodeWhenNoEnrichmentRecord` — empty view, raw code returned
5. `scanUsesRawLookupCodeWhenNoResolverProvided` — single-arg constructor, no resolver, raw code
6. `scanUsesRawCodeWhenResolverFindsNoMatch` — resolver finds nothing, raw code flows through

**Per-Dataset Isolation / Resilience (P0):**
7. `scanContinuesAfterEnrichmentSqlException` — closed connection causes SQLException, scanner continues with raw codes for all datasets

**Edge Cases (P1):**
8. `resolveUsesFirstMatchWhenMultiplePatternsMatch` — LIMIT 1 prevents crash, returns one valid code
9. `resolveReturnsRawCodeWhenLookupCodeIsNull` — NULL lookup_code filtered by AND clause, raw returned
10. `resolveMatchesWildcardPatternForPrefixVariants` — wildcard LIKE pattern works via LIKE reversal
11. `resolveReturnsRawCodeWhenMatchingRowIsExpired` — expired row invisible through active view

**Validation (P0):**
12. `constructorThrowsOnNullConnection` — IllegalArgumentException
13. `resolveThrowsOnNullRawLookupCode` — IllegalArgumentException

**Backward Compatibility / Design (P0–P2):**
14. `scanWithNullResolverUsesRawLookupCode` — two-arg ctor with null resolver = same as single-arg
15. `toStringIndicatesEnrichmentEnabled` — toString reflects enrichment state

---

## Running Tests

```bash
# Run just the new story tests
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="EnrichmentResolverTest,ConsumerZoneScannerEnrichmentTest"

# Run all scanner tests (verify no regressions)
mvn test -Dtest="EnrichmentResolverTest,ConsumerZoneScannerTest,ConsumerZoneScannerEnrichmentTest"

# Full regression (all 87 tests must pass after implementation)
mvn test

# Check compilation only (verify RED phase before implementation)
mvn test-compile
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

**TEA Agent Responsibilities:**

- [x] All 15 new tests written and documented
- [x] H2 DDL specified (dataset_enrichment table + v_dataset_enrichment_active view)
- [x] Lifecycle patterns specified (BeforeAll/BeforeEach/AfterAll)
- [x] Per-dataset isolation test using real closed-connection SQLException
- [x] All AC coverage mapped and verified
- [x] Implementation checklist created below
- [x] No temp artifacts in random locations

**Verification:**

- Tests fail to compile because `EnrichmentResolver` class does not exist
- Tests fail to compile because two-arg `ConsumerZoneScanner(FileSystem, EnrichmentResolver)` constructor does not exist
- Failure messages are clear: `cannot find symbol: class EnrichmentResolver`
- Tests fail due to missing implementation, not test bugs

---

### GREEN Phase (DEV Agent — Next Steps)

#### Task 1: Create EnrichmentResolver.java

**File to create:** `dqs-spark/src/main/java/com/bank/dqs/scanner/EnrichmentResolver.java`

**Tasks to make these tests pass:**
- [ ] Create class in `com.bank.dqs.scanner` package
- [ ] `private final Connection conn` field
- [ ] Constructor `EnrichmentResolver(Connection conn)` — throw `IllegalArgumentException` if null
- [ ] Static `SQL` constant: `"SELECT lookup_code FROM v_dataset_enrichment_active WHERE ? LIKE dataset_pattern AND lookup_code IS NOT NULL LIMIT 1"`
- [ ] Method `String resolve(String rawLookupCode) throws SQLException`
  - [ ] Null guard on `rawLookupCode` → throw `IllegalArgumentException`
  - [ ] Build candidate: `"src_sys_nm=" + rawLookupCode`
  - [ ] Use `PreparedStatement` with `?` placeholder (never string concat)
  - [ ] Use try-with-resources for `PreparedStatement` and `ResultSet`
  - [ ] If `rs.next()` → return `rs.getString("lookup_code")`
  - [ ] Else → `LOG.warn("No enrichment record found for raw lookup code: '{}' — using raw value", rawLookupCode)` → return `rawLookupCode`
- [ ] `@Override toString()` returning useful debug string
- [ ] Run: `mvn test -Dtest="EnrichmentResolverTest"`
- [ ] ✅ All 8 EnrichmentResolverTest tests pass

**Estimated Effort:** 0.5 hours

---

#### Task 2: Modify ConsumerZoneScanner.java

**File to modify:** `dqs-spark/src/main/java/com/bank/dqs/scanner/ConsumerZoneScanner.java`

**Tasks to make these tests pass:**
- [ ] Add `private final EnrichmentResolver enrichmentResolver;` field (nullable)
- [ ] Modify existing single-arg constructor to delegate: `this(fs, null)`
- [ ] Add two-arg constructor `ConsumerZoneScanner(FileSystem fs, EnrichmentResolver enrichmentResolver)`:
  - [ ] Guard `fs == null` → `IllegalArgumentException`
  - [ ] Assign both fields
- [ ] In `scan()` method, after `String lookupCode = extractLookupCode(dirName);` (currently named differently), add enrichment block:
  ```java
  String lookupCode = rawLookupCode;
  if (enrichmentResolver != null) {
      try {
          lookupCode = enrichmentResolver.resolve(rawLookupCode);
      } catch (SQLException e) {
          LOG.error("Enrichment resolution failed for '{}', using raw value: {}",
                    rawLookupCode, e.getMessage());
      }
  }
  ```
- [ ] Update `toString()` to include enrichment resolver status
- [ ] Ensure `datasetName` still uses `rawLookupCode` (NOT the enriched `lookupCode`)
- [ ] Run: `mvn test -Dtest="ConsumerZoneScannerTest,ConsumerZoneScannerEnrichmentTest"`
- [ ] ✅ All 19 existing ConsumerZoneScannerTest tests pass (zero regressions)
- [ ] ✅ All 7 ConsumerZoneScannerEnrichmentTest tests pass

**Estimated Effort:** 0.5 hours

---

#### Task 3: Modify DqsJob.java

**File to modify:** `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`

**Tasks:**
- [ ] Load `application.properties` from classpath using `DqsJob.class.getResourceAsStream("/application.properties")`
- [ ] Read `db.url`, `db.user`, `db.password` properties with sensible defaults
- [ ] Wrap the scan + reader block in `try (Connection conn = DriverManager.getConnection(...))`:
  - [ ] Create `EnrichmentResolver resolver = new EnrichmentResolver(conn)`
  - [ ] `LOG.info("Dataset enrichment resolver enabled")`
  - [ ] Create `ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs, resolver)`
  - [ ] Rest of existing scan/load logic unchanged
- [ ] Run: `mvn test` (full regression — no DqsJob unit tests for this change, but 87 tests must pass)
- [ ] ✅ All 87 tests pass

**Estimated Effort:** 0.5 hours

---

### REFACTOR Phase (After All Tests Pass)

After all 87 tests pass:
1. Review `EnrichmentResolver.resolve()` for any edge cases not yet covered
2. Confirm `toString()` output format matches logging requirements
3. Check that `datasetName` vs `lookupCode` distinction is clear in comments
4. Ensure no hardcoded `"9999-12-31 23:59:59"` strings in main code (use view; or `DqsConstants.EXPIRY_SENTINEL` if raw table access is ever added)
5. Run `mvn test` after each refactor to confirm tests stay green
6. Update `sprint-status.yaml` to mark story 2-4 as `done`

---

## Key Design Decisions

- **No Mockito for EnrichmentResolver:** Uses a real H2 database — same pattern as `CheckFactoryTest`. This validates the actual JDBC SQL, LIKE reversal, and NULL filtering logic.
- **Distinct H2 DB names:** `enrichment_testdb` for `EnrichmentResolverTest`, `scanner_enrichment_testdb` for `ConsumerZoneScannerEnrichmentTest`. Both are distinct from `checkfactory_testdb` used in story 2-1.
- **Separate enrichment test class:** Story 2-4 tests go in `ConsumerZoneScannerEnrichmentTest.java`, not appended to `ConsumerZoneScannerTest.java`. This keeps the existing test file clean and avoids H2 + Hadoop `@BeforeAll` lifecycle conflicts.
- **Closed connection for SQLException test:** A real closed JDBC connection is used to force `SQLException` in `scanContinuesAfterEnrichmentSqlException`. This tests real exception flow without Mockito.
- **`datasetName` vs `lookupCode` distinction explicitly tested:** `scanUsesEnrichedLookupCodeWhenResolverProvided` asserts both that `lookupCode = "UE90"` AND that `datasetName` still contains `src_sys_nm=omni`. This prevents a common implementation mistake.
- **LIKE reversal:** The SQL `? LIKE dataset_pattern` tests whether the candidate (`src_sys_nm=omni`) matches a stored SQL LIKE pattern (`src_sys_nm=omni` or `src_sys_nm=omni%`). This is the same pattern established in story 2-1's `CheckFactory`.
- **No EXPIRY_SENTINEL in EnrichmentResolver:** The story Dev Notes explicitly state the view already filters on the sentinel. `DqsConstants.EXPIRY_SENTINEL` is intentionally not imported in `EnrichmentResolver`.

---

## Cross-Story Notes

- **Story 2-1 (DONE):** Established LIKE reversal pattern in `CheckFactory`. `EnrichmentResolver` uses the same pattern.
- **Story 2-2 (DONE):** `ConsumerZoneScanner` currently sets `lookupCode = raw src_sys_nm`. Story 2-4 enriches it.
- **Story 2-3 (DONE):** `DqsJob` uses `ConsumerZoneScanner(fs)`. Story 2-4 changes it to the two-arg constructor.
- **Stories 2-5 to 2-9 (NEXT):** Check implementations receive `DatasetContext` and will see the enriched `lookupCode`.
- **Story 2-10:** `BatchWriter` uses `ctx.getLookupCode()` — must be the enriched value from this story.

---

## Next Steps

1. Run `mvn test-compile` to confirm RED phase (compilation failure expected)
2. Implement `EnrichmentResolver.java` (Task 1)
3. Modify `ConsumerZoneScanner.java` (Task 2)
4. Run `mvn test -Dtest="EnrichmentResolverTest,ConsumerZoneScannerTest,ConsumerZoneScannerEnrichmentTest"` — verify GREEN
5. Modify `DqsJob.java` (Task 3)
6. Run `mvn test` — verify all 87 tests pass
7. Refactor if needed; confirm tests still pass
8. Mark story 2-4 as `done` in `sprint-status.yaml`
9. Proceed to story 2-5 (first DQ check implementation)

---

**Generated by BMad TEA Agent** — 2026-04-03
