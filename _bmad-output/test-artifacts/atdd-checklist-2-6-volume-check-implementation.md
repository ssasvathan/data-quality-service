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
storyId: 2-6-volume-check-implementation
tddPhase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/2-6-volume-check-implementation.md
  - _bmad/tea/config.yaml
  - _bmad-output/project-context.md
  - dqs-spark/pom.xml
  - dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/FreshnessCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java
---

# ATDD Checklist: Story 2-6 — Volume Check Implementation

## Step 1: Preflight & Context

- **Stack detected:** `backend` (story scope is `dqs-spark`, Java/Maven/JUnit5/Spark local tests)
- **Story file:** `_bmad-output/implementation-artifacts/2-6-volume-check-implementation.md`
- **Status at start:** `ready-for-dev`
- **Prerequisites verified:**
  - [x] Story has clear testable acceptance criteria (AC1-AC4)
  - [x] Backend test framework exists (`dqs-spark/pom.xml`, JUnit5 in `src/test/java`)
  - [x] Existing strategy and factory contracts exist (`DqCheck`, `CheckFactory`)
  - [x] Existing pattern reference exists (`FreshnessCheckTest`)
  - [x] Spark local test lifecycle pattern is already in place

### Knowledge Fragments Loaded

- Core: `data-factories.md`, `component-tdd.md`, `test-quality.md`, `test-healing-patterns.md`
- Backend: `test-levels-framework.md`, `test-priorities-matrix.md`, `ci-burn-in.md`
- API-only Playwright utils profile from config reviewed for consistency guidance (no browser tests generated for this backend story)

## Step 2: Generation Mode

- **Mode selected:** AI generation
- **Rationale:** backend Java story with deterministic unit/integration acceptance tests, no browser recording required
- **Execution mode resolved:** sequential

## Step 3: Test Strategy

### Acceptance Criteria to Scenario Mapping

| AC | Scenario | Level | Priority | Test Target |
|---|---|---|---|---|
| AC1 | Compute `row_count` and `pct_change` against historical mean | Integration-style unit (Spark local) | P0 | `VolumeCheckTest.executeComputesRowCountAndPctChangeMetricsWhenBaselinePresent` |
| AC2 | Emit `MetricNumeric` for `row_count`, `pct_change`, `row_count_stddev` and status detail payload | Integration-style unit (Spark local) | P0 | `executeComputesRowCountAndPctChangeMetricsWhenBaselinePresent` |
| AC3 | Classify PASS/WARN/FAIL from baseline deviation thresholds | Unit/integration hybrid | P0 | `executeClassifiesWarnWhenDeviationExceedsWarnThresholdButNotFailThreshold`, `executeClassifiesFailWhenDeviationExceedsTwoStdDev` |
| AC4 | First-run path: baseline unavailable returns PASS and persists metrics | Unit/integration hybrid | P0 | `executeUsesBaselineInitializationModeWhenHistoryUnavailable` |
| AC1 edge | Zero baseline mean avoids divide-by-zero and keeps deterministic metric output | Unit/integration hybrid | P1 | `executeHandlesZeroMeanBaselineWithoutDivisionByZero` |
| AC4 edge | Missing context/dataframe produces NOT_RUN diagnostic detail | Unit | P1 | `executeReturnsNotRunDetailWhenContextOrDataframeMissing` |
| Factory wiring | Real `VolumeCheck` registration and retrieval through `CheckFactory` | Unit | P0 | `CheckFactoryTest.registerVolumeCheckImplementationAddsRealCheck`, `getEnabledChecksReturnsRegisteredVolumeCheckImplementation` |
| Contract | Canonical check type string is `VOLUME` | Unit | P1 | `VolumeCheckTest.getCheckTypeReturnsVolume` |

### Test Levels Selected

- **Primary:** backend unit/integration tests under `dqs-spark/src/test/java`
- **No E2E/browser tests:** story scope is Spark check logic and factory wiring only

### RED Phase Requirement

- Tests intentionally reference `VolumeCheck` and its baseline abstractions before implementation exists.
- Expected RED result: `testCompile` fails until `VolumeCheck` is implemented to satisfy the acceptance contract.

## Step 4: Failing Test Generation (RED)

### Worker A (Backend acceptance tests) — COMPLETE

Generated/updated test files:

1. `dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java` (new, 179 lines, 7 test methods)
2. `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` (updated, +2 volume registration tests)

### Worker B (E2E tests) — N/A for this backend story

- No frontend flows in scope.
- No Playwright/Cypress tests generated.

## Step 4C: Aggregation & RED Compliance

### RED Compliance Checks

- [x] No placeholder assertions (`expect(true).toBe(true)` style) were introduced
- [x] Tests assert expected production behavior (metric names, baseline math outcomes, status detail classification)
- [x] Test contract requires `VolumeCheck` baseline abstraction (`BaselineProvider`, `BaselineStats`) for deterministic scenarios
- [x] Factory contract requires real class registration and lookup using canonical `VOLUME` check type
- [x] Backend-only scope honored; no out-of-scope UI/API/E2E artifacts created

### Fixture Needs

- Backend story: no additional fixture files required outside test helpers embedded in `VolumeCheckTest`.

### Generated ATDD Artifact

- `_bmad-output/test-artifacts/atdd-checklist-2-6-volume-check-implementation.md`

## Step 5: Validation & Completion

### Validation Against Checklist

- [x] Story ACs mapped to concrete failing acceptance tests
- [x] Test files created in existing `dqs-spark` backend test structure
- [x] No out-of-scope component/UI tests introduced
- [x] RED phase mechanism is explicit (compile-time failure before implementation)
- [x] Checklist artifact saved under `_bmad-output/test-artifacts`

### RED Verification Command

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="VolumeCheckTest,CheckFactoryTest"
```

Observed RED result (pre-implementation): build fails in `testCompile` on missing `VolumeCheck` symbols.

```text
[ERROR] COMPILATION ERROR :
[ERROR] .../VolumeCheckTest.java:[57,13] cannot find symbol class VolumeCheck
[ERROR] .../CheckFactoryTest.java:[120,55] cannot find symbol class VolumeCheck
[INFO] BUILD FAILURE
```

### Completion Summary

- **Test files created/updated:**
  - `dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java`
  - `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java`
- **ATDD checklist output:**
  - `_bmad-output/test-artifacts/atdd-checklist-2-6-volume-check-implementation.md`
- **Key assumption:** implementation will add `VolumeCheck` with baseline retrieval abstraction and classification policy required by these tests.
- **Next workflow:** `bmad-dev-story` for story 2.6 implementation, then code review workflow.
