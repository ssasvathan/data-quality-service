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
inputDocuments:
  - _bmad-output/implementation-artifacts/2-9-dqs-score-computation.md
  - _bmad-output/project-context.md
  - dqs-spark/src/test/java/com/bank/dqs/checks/OpsCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/OpsCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
---

# ATDD Checklist: Story 2-9 — DQS Score Computation

## TDD Red Phase (Current)

All 13 tests are FAILING by design — `DqsScoreCheck`, `ScoreInputProvider`, and `WeightProvider`
do not yet exist. The test files will not compile until Story 2-9 implementation is complete.

## Summary

- **Stack**: Backend (Java 17 / JUnit 5 / Maven)
- **Detected stack type**: `backend`
- **Generation mode**: AI generation (no browser/recording needed)
- **Execution mode**: Sequential
- **TDD Phase**: RED (all tests fail to compile)
- **No SparkSession required**: DqsScoreCheck operates on in-memory DqMetric objects only

## Test Files Generated

| File | Type | Tests | Status |
|---|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/checks/DqsScoreCheckTest.java` | New | 11 | RED (compile failure) |
| `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` | Modified (+2 tests) | 2 | RED (compile failure) |

**Total tests added: 13**

## Acceptance Criteria Coverage

| AC | Description | Test(s) Covering |
|---|---|---|
| AC1 | Reads per-check scores, applies configurable weights from config or defaults | `executeComputesCompositeScoreFromAllFourChecks`, `executeUsesDefaultWeightsWhenNoCustomWeightsConfigured` |
| AC2 | Computes composite score (0-100), emits MetricNumeric with check_type=DQS_SCORE | `executeComputesCompositeScoreFromAllFourChecks`, `executeComputesCompositeScoreWhenSomeChecksFail`, `executeClampsFinalScoreToZeroToHundredRange` |
| AC3 | Score breakdown written to MetricDetail for transparency | `executeComputesScoreBreakdownDetailWithPerCheckContributions` |
| AC4 | Custom weights from dataset_enrichment override defaults | `executeUsesCustomWeightsWhenProvided` |
| AC5 | Missing check weight redistributed proportionally among available checks | `executeRedistributesWeightForUnavailableCheck`, `executeReturnsNotRunWhenAllChecksUnavailable` |

### Additional Robustness Tests (Beyond ACs)

| Test | Coverage |
|---|---|
| `getCheckTypeReturnsDqsScore` | Check type constant contract |
| `executeReturnsNotRunForNullContext` | Null context guard (per-dataset isolation) |
| `executeSanitizesExecutionErrorPayload` | Exception isolation + payload sanitization |
| `registerDqsScoreCheckImplementationAddsRealCheck` (CheckFactoryTest) | Factory registration |
| `getEnabledChecksReturnsRegisteredDqsScoreCheckImplementation` (CheckFactoryTest) | Factory check type lookup |

## Test Strategy Details

### Level: Unit (all tests)
DqsScoreCheck is a pure in-memory computation class with no Spark DataFrame operations.
All tests use plain JUnit 5 with `@BeforeEach` for context setup. No `SparkSession` lifecycle.

### Priority Mapping
- **P0**: `executeComputesCompositeScoreFromAllFourChecks`, `executeReturnsNotRunWhenAllChecksUnavailable`, `executeReturnsNotRunForNullContext`, `executeSanitizesExecutionErrorPayload`, `getCheckTypeReturnsDqsScore`
- **P1**: `executeComputesCompositeScoreWhenSomeChecksFail`, `executeRedistributesWeightForUnavailableCheck`, `executeComputesScoreBreakdownDetailWithPerCheckContributions`
- **P2**: `executeUsesDefaultWeightsWhenNoCustomWeightsConfigured`, `executeUsesCustomWeightsWhenProvided`, `executeClampsFinalScoreToZeroToHundredRange`
- **P2**: CheckFactory registration tests

### Test Isolation Design
- Each test builds its own `List<DqMetric>` to simulate prior check outputs (using `MetricDetail` with JSON status payloads)
- `ScoreInputProvider` injected as a lambda returning the fixed metric list
- `WeightProvider` injected as a lambda returning the fixed weight map (two-arg constructor)
- No JDBC, no Spark, no H2 database required for DqsScoreCheckTest

### Key Patterns from Prior Checks Followed
- Constructor null-guard pattern (IllegalArgumentException)
- Per-dataset failure isolation: catch Exception, return NOT_RUN, never rethrow
- Sanitized error payloads: `error_type = exception.getClass().getSimpleName()` only
- `@FunctionalInterface` for ScoreInputProvider and WeightProvider
- `LinkedHashMap` for deterministic JSON field ordering (same as FreshnessCheck, VolumeCheck, SchemaCheck, OpsCheck)
- Static `ObjectMapper` reuse

## Compilation Failure Confirmation (Red Phase Evidence)

```
[ERROR] COMPILATION ERROR :
[ERROR] DqsScoreCheckTest.java: cannot find symbol: class DqsScoreCheck
[ERROR] CheckFactoryTest.java: cannot find symbol: class DqsScoreCheck
[INFO] BUILD FAILURE
```

This is INTENTIONAL — the class does not exist until Story 2-9 implementation.

## Implementation Classes to Create (Story 2-9)

```
dqs-spark/src/main/java/com/bank/dqs/checks/
  DqsScoreCheck.java                ← NEW (implements DqCheck)
    └─ ScoreInputProvider           ← @FunctionalInterface (inner)
    └─ WeightProvider               ← @FunctionalInterface (inner)
    └─ DefaultWeightProvider        ← private static final class
    └─ NoOpScoreInputProvider       ← private static final class (default)
```

`CheckFactory.java` — register `DqsScoreCheck` via `factory.register(new DqsScoreCheck())`

## TDD Green Phase Instructions

After implementing Story 2-9:

1. Remove `@Disabled` from `DqsScoreCheckTest` class annotation and from each individual `@Test`
2. Remove `@Disabled` from both new methods in `CheckFactoryTest`
3. Run targeted test suite:
   ```bash
   cd /home/sas/workspace/data-quality-service/dqs-spark
   mvn test -Dtest="DqsScoreCheckTest,CheckFactoryTest"
   ```
4. Verify all 13 new tests PASS (green phase)
5. Run full regression:
   ```bash
   mvn test
   ```
6. Confirm no regressions in FreshnessCheckTest, VolumeCheckTest, SchemaCheckTest, OpsCheckTest, CheckFactoryTest existing tests

## Key Risks and Assumptions

1. `ScoreInputProvider` and `WeightProvider` are defined as inner `@FunctionalInterface`s inside `DqsScoreCheck` — lambda injection in tests depends on this structure.
2. Two-arg constructor `DqsScoreCheck(ScoreInputProvider, WeightProvider)` is assumed; single-arg `DqsScoreCheck(ScoreInputProvider)` uses `DefaultWeightProvider`; no-arg uses `NoOpScoreInputProvider` + `DefaultWeightProvider`.
3. The `dqs_score_breakdown` detail type string must match exactly (used in assertions).
4. The `composite_score` metric name must match exactly (used in `findNumeric` assertions).
5. JSON field ordering must be deterministic (LinkedHashMap) for string-contains assertions to work.
6. `executeClampsFinalScoreToZeroToHundredRange` — with weights summing > 1.0, the normalized redistribution formula prevents values > 100 naturally; clamping is a safety net.
