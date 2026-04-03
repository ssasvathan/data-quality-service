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
storyId: 2-7-schema-check-implementation
tddPhase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/2-7-schema-check-implementation.md
  - _bmad/tea/config.yaml
  - _bmad-output/project-context.md
  - dqs-spark/pom.xml
  - dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/FreshnessCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java
---

# ATDD Checklist: Story 2-7 — Schema Check Implementation

## Step 1: Preflight & Context

- **Stack detected:** `backend` (story scope is `dqs-spark`, Java/Maven/JUnit5/Spark local tests)
- **Story file:** `_bmad-output/implementation-artifacts/2-7-schema-check-implementation.md`
- **Status at start:** `ready-for-dev`
- **Prerequisites verified:**
  - [x] Story has clear testable acceptance criteria (AC1-AC5)
  - [x] Backend test framework exists (`dqs-spark/pom.xml`, JUnit5 in `src/test/java`)
  - [x] Existing check/factory contracts exist (`DqCheck`, `CheckFactory`)
  - [x] Existing Spark local check-test patterns exist (`FreshnessCheckTest`, `VolumeCheckTest`)
  - [x] Development environment can run targeted `mvn test` commands

### Knowledge Fragments Loaded

- Core: `data-factories.md`, `component-tdd.md`, `test-quality.md`, `test-healing-patterns.md`
- Backend: `test-levels-framework.md`, `test-priorities-matrix.md`, `ci-burn-in.md`
- API-only Playwright utilities profile from config reviewed for RED/GREEN discipline guidance (no browser tests generated for this backend story)

## Step 2: Generation Mode

- **Mode selected:** AI generation
- **Rationale:** backend Java story with deterministic unit/integration acceptance tests, no browser recording needed
- **Execution mode resolved:** sequential

## Step 3: Test Strategy

### Acceptance Criteria to Scenario Mapping

| AC | Scenario | Level | Priority | Test Target |
|---|---|---|---|---|
| AC1, AC4 | No prior schema baseline: stores current schema hash payload and passes | Integration-style unit (Spark local) | P0 | `SchemaCheckTest.executePassesAndStoresBaselineWhenNoPreviousHash` |
| AC1, AC3 | Current schema hash equals baseline hash: no drift classification | Integration-style unit (Spark local) | P0 | `SchemaCheckTest.executePassesWhenSchemaHashMatchesBaseline` |
| AC2, AC3, AC5 | Add-only schema drift: emits diff and warns | Integration-style unit (Spark local) | P0 | `SchemaCheckTest.executeWarnsAndListsAddedFieldsWhenDriftIsAddOnly` |
| AC2, AC3 | Removed/changed fields: emits diff and fails | Integration-style unit (Spark local) | P0 | `SchemaCheckTest.executeFailsWhenFieldsRemovedOrChanged` |
| AC1 edge | Missing context/dataframe produces NOT_RUN diagnostics | Unit | P1 | `SchemaCheckTest.executeReturnsNotRunForMissingContextOrDataFrame` |
| AC2 edge | Exception path sanitizes detail payload (no raw message leakage) | Unit | P1 | `SchemaCheckTest.executeSanitizesExecutionErrorPayload` |
| Contract | Canonical check type string is `SCHEMA` | Unit | P1 | `SchemaCheckTest.getCheckTypeReturnsSchema` |
| Factory wiring | Real `SchemaCheck` registers and resolves through factory | Unit | P0 | `CheckFactoryTest.registerSchemaCheckImplementationAddsRealCheck`, `CheckFactoryTest.getEnabledChecksReturnsRegisteredSchemaCheckImplementation` |

### Test Levels Selected

- **Primary:** backend unit/integration tests under `dqs-spark/src/test/java`
- **No E2E/browser tests:** story scope is Spark check logic and factory wiring only

### RED Phase Requirement

- Tests intentionally reference `SchemaCheck` and nested baseline abstractions before implementation exists.
- Expected RED result: `testCompile` fails until `SchemaCheck` is implemented to satisfy the acceptance contract.

## Step 4: Failing Test Generation (RED)

### Worker A (Backend acceptance tests) — COMPLETE

Generated/updated test files:

1. `dqs-spark/src/test/java/com/bank/dqs/checks/SchemaCheckTest.java` (new, RED acceptance contract tests)
2. `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java` (updated, +2 `SchemaCheck` registration/enablement tests)

### Worker B (E2E tests) — N/A for this backend story

- No frontend flows in scope.
- No Playwright/Cypress tests generated.

## Step 4C: Aggregation & RED Compliance

### RED Compliance Checks

- [x] No placeholder assertions were introduced
- [x] Tests assert expected production behavior (schema drift classification, diff details, and factory registration)
- [x] Backend-only scope honored; no out-of-scope UI/API/E2E artifacts created
- [x] All new tests are intentionally RED by requiring non-existent `SchemaCheck` symbols pre-implementation

### Fixture Needs

- Backend story: no additional fixture files required beyond helpers embedded in `SchemaCheckTest`.

### Generated ATDD Artifact

- `_bmad-output/test-artifacts/atdd-checklist-2-7-schema-check-implementation.md`

## Step 5: Validate & Complete

### Validation Against Checklist

- [x] Story ACs mapped to concrete failing acceptance tests
- [x] Test files created in existing `dqs-spark` backend test structure
- [x] No out-of-scope component/UI tests introduced
- [x] RED phase mechanism is explicit (compile-time failure before implementation)
- [x] Checklist artifact saved under `_bmad-output/test-artifacts`
- [x] Story status updated to `in-progress`

### RED Verification Command

```bash
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest="SchemaCheckTest,CheckFactoryTest"
```

Observed RED result (pre-implementation): build fails in `testCompile` on missing `SchemaCheck` symbols.

```text
[ERROR] COMPILATION ERROR :
[ERROR] .../SchemaCheckTest.java:[63,13] cannot find symbol class SchemaCheck
[ERROR] .../CheckFactoryTest.java:[125,55] cannot find symbol class SchemaCheck
[INFO] BUILD FAILURE
```

### Completion Summary

- **Test files created/updated:**
  - `dqs-spark/src/test/java/com/bank/dqs/checks/SchemaCheckTest.java`
  - `dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java`
- **ATDD checklist output:**
  - `_bmad-output/test-artifacts/atdd-checklist-2-7-schema-check-implementation.md`
- **Key assumption:** implementation will add `SchemaCheck` with baseline retrieval and diff contracts required by these tests.
- **Next workflow:** `bmad-dev-story` for story 2.7 implementation, then code-review workflow.
