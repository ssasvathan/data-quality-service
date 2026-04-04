---
stepsCompleted: ['step-01-preflight-and-context', 'step-02-generation-mode', 'step-03-test-strategy', 'step-04-generate-tests', 'step-04c-aggregate', 'step-05-validate-and-complete']
lastStep: 'step-05-validate-and-complete'
lastSaved: '2026-04-04'
workflowType: 'testarch-atdd'
inputDocuments:
  - '_bmad-output/implementation-artifacts/6-1-sla-countdown-check.md'
  - 'dqs-spark/src/main/java/com/bank/dqs/checks/FreshnessCheck.java'
  - 'dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java'
  - 'dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java'
  - 'dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java'
  - 'dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java'
  - 'dqs-spark/pom.xml'
---

# ATDD Checklist - Epic 6, Story 6.1: SLA Countdown Check

**Date:** 2026-04-04
**Author:** ssasvathan
**Primary Test Level:** Unit (JUnit 5, no SparkSession)

---

## Story Summary

The SLA Countdown check measures the time remaining against a dataset's expected delivery
window. When a dataset has `sla_hours` configured in `dataset_enrichment`, the check computes
`hours_remaining = sla_hours - hours_elapsed_since_partition_date_midnight`, writes a
`MetricNumeric` with `check_type=SLA_COUNTDOWN`, and classifies as PASS/WARN/FAIL based on
the remaining window. Datasets with no SLA configuration are skipped gracefully.

**As a** data steward
**I want** the SLA Countdown check to measure time remaining against the expected delivery window
**So that** I can monitor whether datasets are arriving within their service level agreements.

---

## Acceptance Criteria

1. **Given** a dataset with `sla_hours` configured in `dataset_enrichment`
   **When** the SLA Countdown check executes
   **Then** it computes hours remaining until SLA breach (positive = time left, negative = overdue)
   and writes a `MetricNumeric` with `check_type=SLA_COUNTDOWN`, `metric_name=hours_remaining`
   **And** it determines PASS (within SLA), WARN (approaching — ≤20% of window remaining),
   or FAIL (breached — negative hours remaining)

2. **Given** a dataset with no SLA configuration in `dataset_enrichment`
   (no matching row or `sla_hours IS NULL`)
   **When** the SLA Countdown check is invoked
   **Then** it skips gracefully and returns an empty list (check not applicable, no metrics written)

3. **And** the check implements `DqCheck`, is registered in `CheckFactory` via
   `DqsJob.buildCheckFactory()`, and requires zero changes to serve/API/dashboard

---

## Failing Tests Created (RED Phase)

### Unit Tests (7 tests)

**File:** `dqs-spark/src/test/java/com/bank/dqs/checks/SlaCountdownCheckTest.java`

- **Test:** `executeReturnsHoursRemainingAndPassWhenWithinSla`
  - **Status:** RED — `SlaCountdownCheck` class does not exist; compilation fails
  - **Verifies:** AC1 — SLA=24h, 12h elapsed → hours_remaining=12.0, status=PASS, reason=within_sla
  - **Priority:** P0

- **Test:** `executeReturnsWarnWhenApproachingSlaThreshold`
  - **Status:** RED — `SlaCountdownCheck` class does not exist; compilation fails
  - **Verifies:** AC1 — SLA=10h, 8.5h elapsed → hours_remaining≤2.0, status=WARN, reason=approaching_sla
  - **Priority:** P0

- **Test:** `executeReturnsFailWhenSlaBreached`
  - **Status:** RED — `SlaCountdownCheck` class does not exist; compilation fails
  - **Verifies:** AC1 — SLA=12h, 13h elapsed → hours_remaining<0, status=FAIL, reason=sla_breached
  - **Priority:** P0

- **Test:** `executeReturnsEmptyListWhenNoSlaConfigured`
  - **Status:** RED — `SlaCountdownCheck` class does not exist; compilation fails
  - **Verifies:** AC2 — SlaProvider returns empty → returns empty list (no metrics written)
  - **Priority:** P0

- **Test:** `executeReturnsEmptyListWhenContextIsNull`
  - **Status:** RED — `SlaCountdownCheck` class does not exist; compilation fails
  - **Verifies:** AC2 — null context → returns empty list (guard clause)
  - **Priority:** P1

- **Test:** `executeHandlesExceptionFromSlaProviderGracefully`
  - **Status:** RED — `SlaCountdownCheck` class does not exist; compilation fails
  - **Verifies:** AC1 — SlaProvider throws → returns error detail metric, exception does NOT propagate
  - **Priority:** P1

- **Test:** `getCheckTypeReturnsSlaCountdown`
  - **Status:** RED — `SlaCountdownCheck` class does not exist; compilation fails
  - **Verifies:** AC3 — `getCheckType()` returns `"SLA_COUNTDOWN"`
  - **Priority:** P1

### E2E Tests

N/A — This story is a backend Spark check. No browser-based E2E tests required.
The check integrates into the existing DqsJob pipeline with zero UI changes.

### API Tests

N/A — The SLA Countdown check runs inside the Spark job, not via an HTTP endpoint.
Existing serve/API layer requires no changes (AC3).

---

## Mock Requirements

### SlaProvider (inner functional interface)

The test uses a lambda mock for `SlaCountdownCheck.SlaProvider`:

```java
SlaCountdownCheck.SlaProvider provider = ctx -> Optional.of(24.0);
// or for the "no SLA" case:
SlaCountdownCheck.SlaProvider provider = ctx -> Optional.empty();
// or for the "throws" case:
SlaCountdownCheck.SlaProvider failingProvider = ctx -> { throw new RuntimeException("..."); };
```

No external service mocking libraries are needed — the functional interface is mockable inline.

### Clock (java.time.Clock)

Fixed clock for deterministic "now":

```java
Instant fixedNow = PARTITION_DATE
    .atStartOfDay(ZoneId.systemDefault())
    .plusHours(12)
    .toInstant();
Clock clock = Clock.fixed(fixedNow, ZoneId.systemDefault());
```

---

## Implementation Checklist

### Test: `executeReturnsHoursRemainingAndPassWhenWithinSla`

**File:** `dqs-spark/src/test/java/com/bank/dqs/checks/SlaCountdownCheckTest.java`

**Tasks to make this test pass:**

- [ ] Create `SlaCountdownCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/`
- [ ] Implement `DqCheck` interface — `getCheckType()` returns `"SLA_COUNTDOWN"`
- [ ] Define `public static final String CHECK_TYPE = "SLA_COUNTDOWN"`
- [ ] Define `static final String METRIC_HOURS_REMAINING = "hours_remaining"`
- [ ] Define `static final String DETAIL_TYPE_STATUS = "sla_countdown_status"`
- [ ] Define inner `@FunctionalInterface SlaProvider` with `Optional<Double> getSlaHours(DatasetContext) throws Exception`
- [ ] Implement 2-arg constructor `SlaCountdownCheck(SlaProvider, Clock)` with null guards
- [ ] Compute `hoursRemaining = slaHours - Duration.between(partitionMidnight, clock.instant()).toHours()`
- [ ] Write `MetricNumeric(CHECK_TYPE, METRIC_HOURS_REMAINING, hoursRemaining)`
- [ ] Classify: `hoursRemaining > slaHours * 0.20` → PASS, reason `within_sla`
- [ ] Write `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, json)` with status/reason/hours_remaining/sla_hours/warn_threshold
- [ ] Run test: `mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#executeReturnsHoursRemainingAndPassWhenWithinSla`
- [ ] Test passes (green phase)

---

### Test: `executeReturnsWarnWhenApproachingSlaThreshold`

**Tasks to make this test pass:**

- [ ] `SlaCountdownCheck.java` exists (see above)
- [ ] Implement WARN classification: `0 <= hoursRemaining <= slaHours * 0.20` → WARN, reason `approaching_sla`
- [ ] Run test: `mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#executeReturnsWarnWhenApproachingSlaThreshold`
- [ ] Test passes (green phase)

---

### Test: `executeReturnsFailWhenSlaBreached`

**Tasks to make this test pass:**

- [ ] `SlaCountdownCheck.java` exists (see above)
- [ ] Implement FAIL classification: `hoursRemaining < 0` → FAIL, reason `sla_breached`
- [ ] Run test: `mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#executeReturnsFailWhenSlaBreached`
- [ ] Test passes (green phase)

---

### Test: `executeReturnsEmptyListWhenNoSlaConfigured`

**Tasks to make this test pass:**

- [ ] `SlaCountdownCheck.java` exists (see above)
- [ ] After querying SlaProvider, check `if (slaHours.isEmpty()) return emptyList()`
- [ ] Run test: `mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#executeReturnsEmptyListWhenNoSlaConfigured`
- [ ] Test passes (green phase)

---

### Test: `executeReturnsEmptyListWhenContextIsNull`

**Tasks to make this test pass:**

- [ ] `SlaCountdownCheck.java` exists (see above)
- [ ] Guard at top of `execute()`: `if (context == null) return emptyList()`
- [ ] Run test: `mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#executeReturnsEmptyListWhenContextIsNull`
- [ ] Test passes (green phase)

---

### Test: `executeHandlesExceptionFromSlaProviderGracefully`

**Tasks to make this test pass:**

- [ ] `SlaCountdownCheck.java` exists (see above)
- [ ] Wrap `execute()` body in `try { ... } catch (Exception e) { return List.of(errorDetailMetric(e)); }`
- [ ] Error detail: `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, json)` with `reason=execution_error`
- [ ] Run test: `mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#executeHandlesExceptionFromSlaProviderGracefully`
- [ ] Test passes (green phase)

---

### Test: `getCheckTypeReturnsSlaCountdown`

**Tasks to make this test pass:**

- [ ] `SlaCountdownCheck.java` exists with `getCheckType()` returning `"SLA_COUNTDOWN"`
- [ ] Run test: `mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#getCheckTypeReturnsSlaCountdown`
- [ ] Test passes (green phase)

---

## Running Tests

```bash
# Run all SlaCountdownCheck failing tests for this story
mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest

# Run specific test
mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#executeReturnsHoursRemainingAndPassWhenWithinSla

# Run all checks tests
mvn test -pl dqs-spark -Dtest="com.bank.dqs.checks.*"

# Run full dqs-spark test suite
mvn test -pl dqs-spark
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete)

- All 7 unit tests written in `SlaCountdownCheckTest.java`
- Tests reference `SlaCountdownCheck` (does not exist yet) → compilation fails
- Failure is intentional: tests define the contract before the class is built
- No SparkSession required — SLA check is time-based only
- Mock pattern: inline lambda for `SlaProvider`, fixed `Clock` for determinism

**Expected RED phase failure:**

```
[ERROR] COMPILATION ERROR
[ERROR] cannot find symbol: class SlaCountdownCheck
[ERROR] 7 errors
```

---

### GREEN Phase (DEV Team — Next Steps)

1. Pick one failing test from the implementation checklist (start with `getCheckTypeReturnsSlaCountdown` — simplest)
2. Read the test to understand expected behavior
3. Implement minimal `SlaCountdownCheck.java` to make that test pass
4. Run `mvn test -pl dqs-spark -Dtest=SlaCountdownCheckTest#getCheckTypeReturnsSlaCountdown`
5. Check off the task, move to next test (suggest: null guard → empty SLA → PASS → WARN → FAIL → error)
6. Register `new SlaCountdownCheck()` in `DqsJob.buildCheckFactory()` once all unit tests pass (Task 2)

---

### REFACTOR Phase (After All Tests Pass)

- Review `SlaCountdownCheck.java` against project Java rules (project-context.md)
- Ensure `JdbcSlaProvider` and `ConnectionProvider` inner classes follow `FreshnessCheck` pattern
- Verify no raw types, no manual `.close()`, no string-concatenated SQL
- Run full suite: `mvn test -pl dqs-spark`

---

## Notes

- **No `@Disabled` annotation used** — in Java TDD, the red phase is compilation failure (class doesn't exist). This is equivalent to `test.skip()` in TypeScript ATDD.
- **Sub-hour precision in WARN test** — the WARN boundary test uses `plusMinutes(510)` (8h 30m) since `Duration.toHours()` truncates. The assertion uses `<= 2.0` to tolerate truncation.
- **`METRIC_HOURS_REMAINING` and `DETAIL_TYPE_STATUS` are package-private** — accessible from the test since both classes share the `com.bank.dqs.checks` package.
- **`SlaProvider` inner interface must be `public`** — the test accesses it as `SlaCountdownCheck.SlaProvider`. Mark it `public` in the implementation.
- **No-arg constructor** uses `NoOpSlaProvider` (returns empty = check skipped). This is acceptable for Tier 2 MVP. The unit tests exercise the 2-arg constructor with a mock provider.

---

## Knowledge Base References Applied

- **test-quality.md** — deterministic test patterns, explicit assertions, isolation
- **data-factories.md** — factory helpers (`context()`, `checkWithSlaAndElapsed()`)
- **component-tdd.md** — Java unit test patterns (no external dependencies)
- **test-levels-framework.md** — unit tests selected (pure function, no I/O needed in tests)
- **test-healing-patterns.md** — package-private constant access pattern

---

**Generated by BMad TEA Agent (bmad-testarch-atdd)** - 2026-04-04
