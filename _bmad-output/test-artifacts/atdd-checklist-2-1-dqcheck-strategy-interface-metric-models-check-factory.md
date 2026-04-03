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
storyId: 2-1-dqcheck-strategy-interface-metric-models-check-factory
tddPhase: RED
---

# ATDD Checklist: Story 2-1 — DqCheck Strategy Interface, Metric Models & Check Factory

## Step 1: Preflight & Context

- **Stack detected:** `fullstack` (dqs-spark `pom.xml` + dqs-dashboard present); this story is backend Java/JUnit5
- **Story file:** `_bmad-output/implementation-artifacts/2-1-dqcheck-strategy-interface-metric-models-check-factory.md`
- **Status at start:** `ready-for-dev` → updated to `in-progress`
- **Prerequisites verified:**
  - [x] Story has clear acceptance criteria (AC1–AC6)
  - [x] `dqs-spark/pom.xml` exists with JUnit5, H2, JDBC dependencies
  - [x] `DqsConstants.EXPIRY_SENTINEL` exists at `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`
  - [x] Test framework: JUnit5 (Maven Surefire, Java 21 forked JVM)
  - [x] `check_config` DDL confirmed: `(dataset_pattern, check_type, enabled, explosion_level, expiry_date)` — compatible with H2

---

## Step 2: Generation Mode

**Mode selected:** AI generation (backend Java — no browser recording needed)
**Resolved execution mode:** Sequential

---

## Step 3: Test Strategy

| AC | Scenario | Level | Priority | Test Class |
|---|---|---|---|---|
| AC1 | DqCheck.execute accepts DatasetContext, returns List<DqMetric> | Unit | P0 | CheckFactoryTest (stub) |
| AC2 | MetricNumeric implements DqMetric with checkType/metricName/metricValue | Unit | P0 | MetricModelTest |
| AC2 | MetricNumeric supports fractional and zero values | Unit | P0 | MetricModelTest |
| AC3 | MetricDetail implements DqMetric with checkType/detailType/detailValue | Unit | P0 | MetricModelTest |
| AC3 | MetricDetail accepts JSON string and null detailValue | Unit | P0 | MetricModelTest |
| AC4 | CheckFactory.register adds check by getCheckType() | Unit | P0 | CheckFactoryTest |
| AC4 | CheckFactory.register multiple checks without error | Unit | P0 | CheckFactoryTest |
| AC4 | getEnabledChecks returns single matching check | Integration (H2) | P0 | CheckFactoryTest |
| AC4 | getEnabledChecks returns multiple matching checks | Integration (H2) | P0 | CheckFactoryTest |
| AC5 | getEnabledChecks excludes disabled rows | Integration (H2) | P0 | CheckFactoryTest |
| AC5 | getEnabledChecks returns empty when no pattern matches | Integration (H2) | P0 | CheckFactoryTest |
| AC5 | getEnabledChecks uses wildcard % pattern | Integration (H2) | P0 | CheckFactoryTest |
| AC5 | getEnabledChecks uses specific prefix pattern | Integration (H2) | P1 | CheckFactoryTest |
| AC5 | getEnabledChecks ignores unregistered check types | Integration (H2) | P1 | CheckFactoryTest |
| AC5 | getEnabledChecks excludes expired config rows (EXPIRY_SENTINEL) | Integration (H2) | P0 | CheckFactoryTest |
| AC6 | DatasetContext constructor validates datasetName non-null | Unit | P0 | DatasetContextTest |
| AC6 | DatasetContext constructor validates datasetName non-blank | Unit | P0 | DatasetContextTest |
| AC6 | DatasetContext constructor validates partitionDate non-null | Unit | P0 | DatasetContextTest |
| AC6 | DatasetContext constructor validates parentPath non-null | Unit | P0 | DatasetContextTest |
| AC6 | DatasetContext constructor validates format non-null | Unit | P0 | DatasetContextTest |
| AC6 | DatasetContext allows null df (test-friendly) | Unit | P0 | DatasetContextTest |
| AC6 | DatasetContext allows null lookupCode | Unit | P0 | DatasetContextTest |
| AC6 | DatasetContext getters return all 6 fields | Unit | P0 | DatasetContextTest |
| AC6 | FORMAT_AVRO/PARQUET/UNKNOWN constants defined | Unit | P0 | DatasetContextTest |

**TDD Red Phase Confirmation:** All test files import classes that do not exist yet (`DqCheck`, `CheckFactory`, `DatasetContext`, `MetricNumeric`, `MetricDetail`, `DqMetric`). Maven compilation fails until implementation is complete. This is the natural Java red phase — no `@Disabled` annotation needed.

---

## Step 4: Test Generation (Sequential Mode)

### Worker A (Unit + Integration Tests) — COMPLETE

**Files created:**

| File | Tests | Description |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` | 11 | AC4+AC5: factory registration + H2 JDBC |
| `dqs-spark/src/test/java/com/bank/dqs/model/DatasetContextTest.java` | 12 | AC6: field validation and getters |
| `dqs-spark/src/test/java/com/bank/dqs/model/MetricModelTest.java` | 8 | AC2+AC3: MetricNumeric/MetricDetail |

**Total test methods: 31** (all JUnit5 @Test methods, no test.skip / no @Disabled)

**Red phase mechanism:** Tests fail to compile because imported classes are not yet implemented.

### Worker B (E2E Tests) — N/A

Not applicable. This story has no browser interactions, no API endpoints, and no frontend components. Pure backend Java interfaces and factory.

---

## Step 4C: Aggregation

### TDD Red Phase Validation

All tests:
- [x] Import non-existent classes → compilation fails until implementation complete
- [x] Assert EXPECTED behavior per AC (not placeholder assertions)
- [x] H2 JDBC tests use `DB_CLOSE_DELAY=-1` for single shared in-memory instance
- [x] `EXPIRY_SENTINEL` from `DqsConstants` tested via expired row exclusion test
- [x] `@BeforeAll` / `@AfterAll` lifecycle for H2 connection (per project testing rules)
- [x] `@BeforeEach` cleans check_config between tests (test isolation)
- [x] No `test.skip()` needed (Java red phase = compile failure)
- [x] No `@Disabled` (intentional — tests MUST fail at compile time until impl exists)

### Summary Statistics

| Metric | Value |
|---|---|
| Total test methods | 31 |
| CheckFactoryTest | 11 |
| DatasetContextTest | 12 |
| MetricModelTest | 8 |
| H2 Integration tests (factory) | 9 |
| Unit tests (model + validation) | 22 |
| Execution mode | Sequential |
| Test framework | JUnit5 / Maven Surefire |
| Java version (test JVM) | Java 21 (configured in pom.xml) |

---

## Step 5: Validation & Completion

### Checklist Validation

- [x] Prerequisites satisfied (`pom.xml` has JUnit5, H2, JDBC deps)
- [x] 3 test files created under `dqs-spark/src/test/java/com/bank/dqs/`
- [x] All 6 ACs covered by at least one test method
- [x] No `@Disabled` (correct — compilation failure is the red phase)
- [x] `@BeforeAll`/`@BeforeEach`/`@AfterAll` lifecycle used correctly
- [x] H2 connection is static (shared across `@Test` methods, safe for read-only in-memory)
- [x] `DELETE FROM check_config` in `@BeforeEach` ensures test isolation
- [x] No `DqsJob.java` or `DqsConstants.java` modified
- [x] No `pom.xml` modified
- [x] Test package mirrors main package (`com.bank.dqs.checks`, `com.bank.dqs.model`)
- [x] Story status updated: `ready-for-dev` → `in-progress`

### Generated Files

| File | Type | Description |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` | NEW | 11 unit+H2 integration tests for CheckFactory |
| `dqs-spark/src/test/java/com/bank/dqs/model/DatasetContextTest.java` | NEW | 12 unit tests for DatasetContext |
| `dqs-spark/src/test/java/com/bank/dqs/model/MetricModelTest.java` | NEW | 8 unit tests for MetricNumeric/MetricDetail |

### Files NOT Modified

| File | Reason |
|---|---|
| `dqs-spark/pom.xml` | Already correct — no new deps needed |
| `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` | Placeholder, untouched |
| `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java` | Existing — EXPIRY_SENTINEL already correct |

### Acceptance Criteria Coverage

| AC | Covered By | Status |
|---|---|---|
| AC1: DqCheck interface with execute(DatasetContext) → List<DqMetric> | CheckFactoryTest (stubCheck helper, 11 tests) | COVERED |
| AC2: MetricNumeric maps to dq_metric_numeric columns | MetricModelTest (4 tests) | COVERED |
| AC3: MetricDetail maps to dq_metric_detail columns | MetricModelTest (4 tests) | COVERED |
| AC4: CheckFactory registers by check_type, returns enabled checks | CheckFactoryTest (3 tests) | COVERED |
| AC5: CheckFactory reads check_config, pattern-matching, enabled filter | CheckFactoryTest (7 tests) | COVERED |
| AC6: DatasetContext holds 6 fields with constructor validation | DatasetContextTest (12 tests) | COVERED |

### Next Steps (TDD Green Phase)

After implementation of all 6 Java source files:

1. Verify compilation: `cd dqs-spark && mvn compile test-compile`
2. Run new tests: `cd dqs-spark && mvn test -Dtest="CheckFactoryTest,DatasetContextTest,MetricModelTest"`
3. Verify all 31 tests PASS
4. Run full regression: `cd dqs-spark && mvn test`
5. Proceed to `dev-story` implementation

### Key Design Assumptions

- H2 `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1` keeps the in-memory DB alive across all `@Test` methods in the `@BeforeAll` → `@AfterAll` lifecycle
- `? LIKE dataset_pattern` in `CheckFactory.getEnabledChecks` is valid SQL in both H2 and Postgres — H2 standard LIKE uses `%` wildcard
- `DqsConstants.EXPIRY_SENTINEL = "9999-12-31 23:59:59"` — H2 accepts this as a valid TIMESTAMP default
- `DatasetContext.df` is `Dataset<Row>` (Spark type) — passed as `null` in all test contexts; Spark not required for unit/factory tests
