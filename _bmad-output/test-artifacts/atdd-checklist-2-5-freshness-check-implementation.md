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
storyId: 2-5-freshness-check-implementation
tddPhase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/2-5-freshness-check-implementation.md
  - _bmad/tea/config.yaml
  - _bmad-output/project-context.md
  - dqs-spark/pom.xml
  - dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java
---

# ATDD Checklist: Story 2-5 — Freshness Check Implementation

## Step 1: Preflight & Context

- **Stack detected:** `backend` (story scope is `dqs-spark`, Java/Maven/JUnit5/Spark local tests)
- **Story file:** `_bmad-output/implementation-artifacts/2-5-freshness-check-implementation.md`
- **Status at start:** `ready-for-dev`
- **Prerequisites verified:**
  - [x] Story has clear acceptance criteria (AC1–AC4)
  - [x] Backend test framework exists (`dqs-spark/pom.xml`, JUnit5 setup under `src/test/java`)
  - [x] Existing factory/test patterns are available in `CheckFactoryTest`
  - [x] Existing metric model contracts available (`MetricNumeric`, `MetricDetail`)
  - [x] Spark local test lifecycle pattern already established in `dqs-spark` tests

### Knowledge Fragments Loaded

- Core: `data-factories.md`, `component-tdd.md`, `test-quality.md`, `test-healing-patterns.md`
- Backend: `test-levels-framework.md`, `test-priorities-matrix.md`, `ci-burn-in.md`
- API-only Playwright utils profile loaded per config for reusable red/green guidance (no browser tests generated for this backend story)

## Step 2: Generation Mode

- **Mode selected:** AI generation
- **Rationale:** backend Java story with deterministic unit/integration tests; no browser recording required
- **Execution mode resolved:** sequential

## Step 3: Test Strategy

### Acceptance Criteria to Scenario Mapping

| AC | Scenario | Level | Priority | Test Target |
|---|---|---|---|---|
| AC1 | Timestamp column present -> compute `hours_since_update` numeric metric | Integration-style unit (Spark local) | P0 | `FreshnessCheckTest.executeComputesHoursSinceUpdateMetricWhenTimestampColumnPresent` |
| AC2 | Baseline classification PASS/WARN/FAIL based on staleness vs baseline | Unit/integration hybrid | P0 | `executeClassifiesWarnWhenStalenessExceedsWarnThreshold`, `executeClassifiesFailWhenStalenessExceedsFailThreshold` |
| AC3 | Real `FreshnessCheck` implementation can be registered/resolved in `CheckFactory` | Unit | P0 | `CheckFactoryTest.registerFreshnessCheckImplementationAddsRealCheck`, `getEnabledChecksReturnsRegisteredFreshnessCheckImplementation` |
| AC4 | Missing `source_event_timestamp` -> check-not-run detail metric | Integration-style unit (Spark local) | P0 | `executeReturnsCheckNotRunDetailWhenTimestampColumnMissing` |
| AC2 edge | No baseline history -> baseline initialization mode | Unit/integration hybrid | P1 | `executeUsesBaselineInitializationModeWhenHistoryUnavailable` |
| AC1 edge | Future timestamp -> negative staleness clamped to 0.0 | Unit/integration hybrid | P1 | `executeClampsNegativeStalenessToZeroForFutureTimestamps` |
| AC3 contract | Canonical check type string is `FRESHNESS` | Unit | P1 | `getCheckTypeReturnsFreshness` |

### Test Levels Selected

- **Primary:** backend unit/integration tests in `dqs-spark/src/test/java`
- **No E2E/browser tests:** story scope is Spark check logic and check-factory wiring only

### Red Phase Requirement

- Tests intentionally target `FreshnessCheck` and nested baseline abstractions that are not yet implemented.
- Expected RED outcome: `testCompile` fails until `FreshnessCheck` is implemented to satisfy the acceptance contract.

## Step 4: Failing Test Generation (RED)

### Worker A (Backend acceptance tests) — COMPLETE

Generated/updated test files:

1. `dqs-spark/src/test/java/com/bank/dqs/checks/FreshnessCheckTest.java` (new, 7 tests)
2. `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` (updated, +2 tests)

### Worker B (E2E tests) — N/A for this backend story

- No frontend flows in scope.
- No Playwright/Cypress tests generated.

## Step 4C: Aggregation & RED Compliance

### RED Compliance Checks

- [x] No placeholder assertions like `expect(true).toBe(true)`
- [x] Tests assert expected production behavior (metric names, thresholds, status details, factory registration)
- [x] Deterministic clock usage is required by test API (`Clock.fixed(...)`)
- [x] Baseline-provider abstraction is required by test API for deterministic classification paths
- [x] Freshness contracts covered:
  - `hours_since_update` metric name
  - `FRESHNESS` check type
  - missing-column not-run behavior
  - PASS/WARN/FAIL classification and baseline initialization mode
  - clock-skew clamp to zero

### Fixture Needs

- Backend story: no extra fixture files required outside Spark DataFrame helpers embedded in test class.

### Generated ATDD Artifact

- `_bmad-output/test-artifacts/atdd-checklist-2-5-freshness-check-implementation.md`

## Step 5: Validation & Completion

### Validation Against Checklist

- [x] Story ACs mapped to concrete failing acceptance tests
- [x] Test files created in existing `dqs-spark` backend test structure
- [x] No out-of-scope component/UI tests introduced
- [x] RED phase mechanism is explicit (compile-time failure before implementation)
- [x] Checklist and artifacts saved under `_bmad-output/test-artifacts`

### RED Verification Command

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="FreshnessCheckTest,CheckFactoryTest"
```

Observed RED result (pre-implementation): build fails in `testCompile` on missing `FreshnessCheck` symbols.

```text
[ERROR] COMPILATION ERROR :
[ERROR] .../FreshnessCheckTest.java:[74,13] cannot find symbol class FreshnessCheck
[ERROR] .../CheckFactoryTest.java:[115,55] cannot find symbol class FreshnessCheck
[INFO] BUILD FAILURE
```

### Completion Summary

- **Test files created/updated:**
  - `dqs-spark/src/test/java/com/bank/dqs/checks/FreshnessCheckTest.java`
  - `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java`
- **ATDD checklist output:**
  - `_bmad-output/test-artifacts/atdd-checklist-2-5-freshness-check-implementation.md`
- **Key assumption:** story implementation will introduce `FreshnessCheck`, baseline abstractions, and deterministic constructor contract required by tests.
- **Next workflow:** `bmad-dev-story` for story 2.5 implementation, then code review workflow.
