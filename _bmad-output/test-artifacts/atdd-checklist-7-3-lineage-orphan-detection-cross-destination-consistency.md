---
stepsCompleted:
  - step-01-preflight-and-context
  - step-02-generation-mode
  - step-03-test-strategy
  - step-04-generate-tests
  - step-04c-aggregate
  - step-05-validate-and-complete
lastStep: step-05-validate-and-complete
lastSaved: '2026-04-04'
workflowType: testarch-atdd
inputDocuments:
  - _bmad-output/implementation-artifacts/7-3-lineage-orphan-detection-cross-destination-consistency.md
  - _bmad-output/project-context.md
  - dqs-spark/src/test/java/com/bank/dqs/checks/SourceSystemHealthCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/InferredSlaCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/CorrelationCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/ClassificationWeightedCheckTest.java
---

# ATDD Checklist — Epic 7, Story 7.3: Lineage, Orphan Detection & Cross-Destination Consistency

**Date:** 2026-04-04
**Author:** Sas
**Primary Test Level:** Unit (JUnit 5, backend only)
**TDD Phase:** RED — tests will not compile until production classes are created

---

## Story Summary

As a platform operator, I want lineage checks, orphan detection, and cross-destination
consistency validation so that data flow integrity is verified beyond individual dataset quality.

**As a** platform operator
**I want** lineage checks, orphan detection, and cross-destination consistency validation
**So that** data flow integrity is verified beyond individual dataset quality

---

## Stack Detection

- Detected stack: `backend` (Java 17 + Maven, `pom.xml` found in `dqs-spark/`)
- Mode: AI generation (backend — no browser recording needed)
- Execution: sequential

---

## Acceptance Criteria

1. Lineage check identifies upstream datasets with failing health for datasets containing `src_sys_nm=` segment
2. Lineage check gracefully skips datasets without `src_sys_nm=` segment (PASS + reason)
3. Lineage check returns NOT_RUN on null context without propagating exceptions
4. Orphan Detection check fails datasets with DQS run history but no enabled check_config
5. Orphan Detection check returns NOT_RUN on null context without propagating exceptions
6. Cross-Destination check detects inconsistent DQS scores across multi-run datasets
7. Cross-Destination check returns PASS for single-destination datasets
8. Cross-Destination check returns NOT_RUN on null context without propagating exceptions
9. All three checks implement DqCheck, registered in DqsJob after InferredSlaCheck and before DqsScoreCheck

---

## TDD Red Phase (Current) — Complete

All 25 tests generated and confirmed failing (compilation error — production classes do not yet exist).

- LineageCheck Tests: 10 tests (all RED)
- OrphanDetectionCheck Tests: 8 tests (all RED)
- CrossDestinationCheck Tests: 8 tests (all RED)
- **Total: 26 failing tests**

---

## Failing Tests Created (RED Phase)

### LineageCheckTest (10 tests)

**File:** `dqs-spark/src/test/java/com/bank/dqs/checks/LineageCheckTest.java`

- **[P0]** `executeWritesWarnMetricWhenUpstreamDatasetsFailing`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: AC1 — upstreamFailedCount=2 → MetricNumeric upstream_failed_count=2.0, MetricDetail status=WARN

- **[P0]** `executeWritesPassMetricWhenAllUpstreamsHealthy`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: AC1 — upstreamFailedCount=0 → MetricDetail status=PASS

- **[P0]** `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: AC2 — no `src_sys_nm=` → MetricDetail status=PASS, reason=no_source_system_segment, no MetricNumeric

- **[P0]** `executeReturnsPassWhenNoUpstreamDatasetsFound`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: AC1 — provider returns empty → MetricDetail status=PASS, reason=no_upstream_datasets

- **[P0]** `executeReturnsNotRunWhenContextIsNull`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: AC3 — null context → NOT_RUN detail, no exception

- **[P1]** `executeHandlesExceptionGracefully`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: provider throws RuntimeException → errorDetail, metrics.clear(), no propagation

- **[P1]** `getCheckTypeReturnsLineage`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: AC9 — getCheckType() == "LINEAGE" == CHECK_TYPE constant

- **[P1]** `executeWarnDetailContainsAllRequiredContextFields`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: AC1 — WARN payload contains src_sys_nm, upstream_dataset_count, upstream_failed_count, upstream_fail_threshold

- **[P0]** `executeEmitsCorrectMetricNumericsForUpstreamCounts`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: AC1 — both MetricNumeric(upstream_dataset_count=5) and MetricNumeric(upstream_failed_count=2) emitted

- **[P1]** `constructorThrowsWhenStatsProviderIsNull`
  - Status: RED — `LineageCheck` class does not exist
  - Verifies: IllegalArgumentException on null statsProvider (project pattern)

### OrphanDetectionCheckTest (8 tests)

**File:** `dqs-spark/src/test/java/com/bank/dqs/checks/OrphanDetectionCheckTest.java`

- **[P0]** `executeWritesFailMetricWhenNoCheckConfigExists`
  - Status: RED — `OrphanDetectionCheck` class does not exist
  - Verifies: AC4 — provider returns false → MetricDetail status=FAIL, no MetricNumeric

- **[P0]** `executeWritesPassMetricWhenCheckConfigExists`
  - Status: RED — `OrphanDetectionCheck` class does not exist
  - Verifies: AC4 — provider returns true → MetricDetail status=PASS, no MetricNumeric

- **[P0]** `executeReturnsNotRunWhenContextIsNull`
  - Status: RED — `OrphanDetectionCheck` class does not exist
  - Verifies: AC5 — null context → NOT_RUN detail, no exception

- **[P1]** `executeHandlesExceptionGracefully`
  - Status: RED — `OrphanDetectionCheck` class does not exist
  - Verifies: provider throws RuntimeException → errorDetail, metrics.clear(), no propagation

- **[P1]** `getCheckTypeReturnsOrphanDetection`
  - Status: RED — `OrphanDetectionCheck` class does not exist
  - Verifies: AC9 — getCheckType() == "ORPHAN_DETECTION" == CHECK_TYPE constant

- **[P0]** `executeFailDetailContainsReasonField`
  - Status: RED — `OrphanDetectionCheck` class does not exist
  - Verifies: AC4 — FAIL payload contains reason=no_check_config

- **[P0]** `executePassDetailContainsReasonField`
  - Status: RED — `OrphanDetectionCheck` class does not exist
  - Verifies: AC4 — PASS payload contains reason=check_config_present

- **[P1]** `constructorThrowsWhenConfigProviderIsNull`
  - Status: RED — `OrphanDetectionCheck` class does not exist
  - Verifies: IllegalArgumentException on null configProvider (project pattern)

### CrossDestinationCheckTest (8 tests)

**File:** `dqs-spark/src/test/java/com/bank/dqs/checks/CrossDestinationCheckTest.java`

- **[P0]** `executeWritesFailMetricWhenDestinationsAreInconsistent`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: AC6 — destinationCount=3, inconsistentCount=1 → MetricNumeric×2, MetricDetail status=FAIL

- **[P0]** `executeWritesPassMetricWhenAllDestinationsConsistent`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: AC6 — inconsistentCount=0 → MetricDetail status=PASS

- **[P0]** `executeReturnsPassWhenSingleDestination`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: AC7 — provider returns empty → MetricDetail status=PASS, reason=single_destination

- **[P0]** `executeReturnsNotRunWhenContextIsNull`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: AC8 — null context → NOT_RUN detail, no exception

- **[P1]** `executeHandlesExceptionGracefully`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: provider throws RuntimeException → errorDetail, metrics.clear(), no propagation

- **[P1]** `getCheckTypeReturnsCrossDestination`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: AC9 — getCheckType() == "CROSS_DESTINATION" == CHECK_TYPE constant

- **[P1]** `executeFailDetailContainsAllRequiredContextFields`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: AC6 — FAIL payload contains destination_count, inconsistent_destination_count, mean_score, max_deviation, threshold

- **[P0]** `executeEmitsCorrectMetricNumericsForDestinationCounts`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: AC6 — both MetricNumeric(destination_count=3) and MetricNumeric(inconsistent_destination_count=1) emitted

- **[P1]** `constructorThrowsWhenStatsProviderIsNull`
  - Status: RED — `CrossDestinationCheck` class does not exist
  - Verifies: IllegalArgumentException on null statsProvider (project pattern)

---

## Test Design Decisions

### No SparkSession Required

All three checks are purely JDBC-based — none calls `context.getDf()`. Test classes follow the
same pattern as `SourceSystemHealthCheckTest` and `InferredSlaCheckTest`:
- No `@BeforeAll`/`@AfterAll` SparkSession lifecycle
- `DatasetContext` created with `df=null` (safe per DatasetContext Javadoc)
- Mock providers injected via `@FunctionalInterface` lambdas (no Mockito)

### Provider Lambda Injection Pattern

```java
// LineageCheck — upstreamDatasetCount=5, upstreamFailedCount=2 (WARN)
LineageCheck check = new LineageCheck(
        (srcSysNm, date) -> Optional.of(new LineageCheck.LineageStats(5, 2))
);

// OrphanDetectionCheck — no check_config (FAIL)
OrphanDetectionCheck check = new OrphanDetectionCheck(
        datasetName -> false
);

// CrossDestinationCheck — 3 destinations, 1 inconsistent (FAIL)
CrossDestinationCheck check = new CrossDestinationCheck(
        (datasetName, date) -> Optional.of(
                new CrossDestinationCheck.DestinationStats(3, 1, 80.0, 18.0))
);
```

### PARTITION_DATE

All tests use `LocalDate.of(2026, 4, 4)` (today per project context).

---

## Acceptance Criteria Coverage

| AC | Description | Test Method(s) |
|----|-------------|---------------|
| AC1 | Lineage WARN when upstream failing | `executeWritesWarnMetricWhenUpstreamDatasetsFailing`, `executeEmitsCorrectMetricNumericsForUpstreamCounts`, `executeWarnDetailContainsAllRequiredContextFields` |
| AC1 | Lineage PASS when all upstreams healthy | `executeWritesPassMetricWhenAllUpstreamsHealthy` |
| AC1 | Lineage PASS when no upstream datasets | `executeReturnsPassWhenNoUpstreamDatasetsFound` |
| AC2 | Lineage graceful skip without src_sys_nm | `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName` |
| AC3 | Lineage NOT_RUN on null context | `executeReturnsNotRunWhenContextIsNull` |
| AC4 | Orphan FAIL when no check_config | `executeWritesFailMetricWhenNoCheckConfigExists`, `executeFailDetailContainsReasonField` |
| AC4 | Orphan PASS when check_config present | `executeWritesPassMetricWhenCheckConfigExists`, `executePassDetailContainsReasonField` |
| AC5 | Orphan NOT_RUN on null context | `executeReturnsNotRunWhenContextIsNull` |
| AC6 | Cross-Dest FAIL when inconsistent | `executeWritesFailMetricWhenDestinationsAreInconsistent`, `executeEmitsCorrectMetricNumericsForDestinationCounts`, `executeFailDetailContainsAllRequiredContextFields` |
| AC6 | Cross-Dest PASS when all consistent | `executeWritesPassMetricWhenAllDestinationsConsistent` |
| AC7 | Cross-Dest PASS for single destination | `executeReturnsPassWhenSingleDestination` |
| AC8 | Cross-Dest NOT_RUN on null context | `executeReturnsNotRunWhenContextIsNull` |
| AC9 | DqCheck contract + check type constants | All `getCheckType*` tests |

---

## Implementation Checklist

### To pass LineageCheckTest (10 tests)

- [ ] Create `dqs-spark/src/main/java/com/bank/dqs/checks/LineageCheck.java`
  - `public static final String CHECK_TYPE = "LINEAGE"`
  - `public static final String METRIC_UPSTREAM_DATASET_COUNT = "upstream_dataset_count"`
  - `public static final String METRIC_UPSTREAM_FAILED_COUNT = "upstream_failed_count"`
  - `public static final String DETAIL_TYPE_STATUS = "lineage_status"`
  - `@FunctionalInterface LineageStatsProvider` with `getStats(String, LocalDate) throws Exception`
  - `public record LineageStats(int upstreamDatasetCount, int upstreamFailedCount)`
  - `JdbcLineageStatsProvider implements LineageStatsProvider` — two JDBC queries on same connection
  - `NoOpLineageStatsProvider` (returns `Optional.empty()`)
  - No-arg constructor delegating to testable constructor
  - Testable constructor with null guard (`IllegalArgumentException`)
  - `execute()`: null guard, src_sys_nm extraction, provider call, emit MetricNumeric×2 + MetricDetail
  - WARN if upstreamFailedCount > 0, PASS otherwise
  - Entire body wrapped in try/catch → `metrics.clear()` + errorDetail

### To pass OrphanDetectionCheckTest (8 tests)

- [ ] Create `dqs-spark/src/main/java/com/bank/dqs/checks/OrphanDetectionCheck.java`
  - `public static final String CHECK_TYPE = "ORPHAN_DETECTION"`
  - `public static final String DETAIL_TYPE_STATUS = "orphan_detection_status"`
  - `@FunctionalInterface CheckConfigProvider` with `hasEnabledCheckConfig(String) throws Exception`
  - `JdbcCheckConfigProvider` — LIKE reversal query on `v_check_config_active`
  - `NoOpCheckConfigProvider` (returns `false`)
  - No-arg constructor delegating to testable constructor
  - Testable constructor with null guard
  - `execute()`: null guard, provider call, FAIL (no_check_config) or PASS (check_config_present)
  - No MetricNumeric emitted — binary check only
  - Entire body wrapped in try/catch → `metrics.clear()` + errorDetail

### To pass CrossDestinationCheckTest (8 tests)

- [ ] Create `dqs-spark/src/main/java/com/bank/dqs/checks/CrossDestinationCheck.java`
  - `public static final String CHECK_TYPE = "CROSS_DESTINATION"`
  - `public static final String METRIC_DESTINATION_COUNT = "destination_count"`
  - `public static final String METRIC_INCONSISTENT_DESTINATION_COUNT = "inconsistent_destination_count"`
  - `public static final String DETAIL_TYPE_STATUS = "cross_destination_status"`
  - `public static final double SCORE_DEVIATION_THRESHOLD = 15.0`
  - `@FunctionalInterface DestinationStatsProvider` with `getStats(String, LocalDate) throws Exception`
  - `public record DestinationStats(int destinationCount, int inconsistentDestinationCount, double meanScore, double maxDeviation)`
  - `JdbcDestinationStatsProvider` — collect DQS scores, compute mean, count deviations
  - `NoOpDestinationStatsProvider` (returns `Optional.empty()`)
  - No-arg constructor delegating to testable constructor
  - Testable constructor with null guard
  - `execute()`: null guard, provider call, if empty → PASS single_destination, else emit MetricNumeric×2 + MetricDetail
  - FAIL if inconsistentCount > 0, PASS otherwise
  - Entire body wrapped in try/catch → `metrics.clear()` + errorDetail

### To pass DqsJob integration (AC9)

- [ ] Add imports for LineageCheck, OrphanDetectionCheck, CrossDestinationCheck to `DqsJob.java`
- [ ] Register all three checks after InferredSlaCheck and before DqsScoreCheck in `buildCheckFactory()`
- [ ] Update Javadoc for `buildCheckFactory()` — add Tier 3 Story 7.3 mention

---

## Running Tests

```bash
# Verify RED phase (will show compile error — expected)
cd dqs-spark
mvn test-compile

# After implementation — run all three test classes
mvn test -Dtest="LineageCheckTest,OrphanDetectionCheckTest,CrossDestinationCheckTest" \
    -pl dqs-spark

# Run full suite (regression check — must not drop below 264 tests)
cd dqs-spark
mvn test
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

- ✅ 26 failing tests generated across 3 test files
- ✅ Compilation error confirmed: `cannot find symbol: class LineageCheck/OrphanDetectionCheck/CrossDestinationCheck`
- ✅ All tests assert expected behavior (not placeholders)
- ✅ Mock providers via `@FunctionalInterface` lambdas — no Mockito needed
- ✅ No SparkSession in any test file
- ✅ PARTITION_DATE = `LocalDate.of(2026, 4, 4)` (today)
- ✅ All fixtures implicit (lambda-injected providers)

### GREEN Phase (DEV Team — Next Steps)

1. Create `LineageCheck.java` — make LineageCheckTest pass
2. Create `OrphanDetectionCheck.java` — make OrphanDetectionCheckTest pass
3. Create `CrossDestinationCheck.java` — make CrossDestinationCheckTest pass
4. Update `DqsJob.java` — register all three checks in correct order
5. Run `mvn test` → verify 264 + 26 = ~290 tests all pass, 0 failures

### REFACTOR Phase (After All Tests Pass)

1. Review constant declarations — remove any declared but unused constants
2. Verify `src_sys_nm` extraction helper duplicated locally in LineageCheck (per project anti-patterns)
3. Confirm no shared utility class created across check files
4. Run `mvn test` after each refactor to maintain green state

---

## Key Risks & Assumptions

- **Regression risk**: Existing 264 tests must not break. All three new checks are self-contained.
- **LIKE reversal pattern**: OrphanDetectionCheck uses `WHERE ? LIKE dataset_pattern` — same as SlaCountdownCheck and InferredSlaCheck. Tests verify binary PASS/FAIL only (no JDBC SQL tested here).
- **Cross-destination design**: `CrossDestinationCheck` queries multiple rows by `dataset_name + partition_date`. Test uses lambda mock — no H2 required.
- **No shared utility**: `extractSrcSysNm` must be duplicated locally in `LineageCheck` — project mandates each check is self-contained.

---

## Knowledge Base References Applied

- **test-levels-framework.md** — backend stack: unit tests only (no E2E), JUnit 5
- **data-factories.md** — lambda-injected providers as lightweight test factories
- **test-quality.md** — Given-When-Then structure, one assertion focus per test, determinism
- **ci-burn-in.md** — 264 existing tests as regression baseline; new tests extend not replace
- **component-tdd.md** — TDD red phase: compile failure IS the red signal for Java

---

## Test Execution Evidence

**Command:** `cd dqs-spark && mvn test-compile`

**Result:**
```
ERROR: cannot find symbol: class LineageCheck
ERROR: cannot find symbol: class OrphanDetectionCheck
ERROR: cannot find symbol: class CrossDestinationCheck
```

**Summary:**
- Total new tests: 26 (all RED — compile failure)
- Passing: 0 (expected)
- Failing: 26 (expected — TDD red phase)
- Status: ✅ RED phase verified

---

**Generated by BMad TEA Agent (bmad-testarch-atdd)** — 2026-04-04
