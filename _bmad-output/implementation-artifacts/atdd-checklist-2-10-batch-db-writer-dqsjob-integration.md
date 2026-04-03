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
  - _bmad-output/implementation-artifacts/2-10-batch-db-writer-dqsjob-integration.md
  - _bmad-output/project-context.md
  - _bmad/tea/config.yaml
  - dqs-spark/src/test/java/com/bank/dqs/checks/CheckFactoryTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/DqsScoreCheckTest.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/DqsScoreCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/DqsJob.java
  - _bmad-output/implementation-artifacts/atdd-checklist-2-9-dqs-score-computation.md
---

# ATDD Checklist: Story 2-10 — Batch DB Writer & DqsJob Integration

## TDD Red Phase (Current)

All 11 tests FAIL TO COMPILE — `BatchWriter` does not exist yet. Compilation failure
is the correct TDD red phase mechanism for Java projects (not `@Disabled`, per story dev notes).

```
[ERROR] COMPILATION ERROR :
[ERROR] BatchWriterTest.java: cannot find symbol: class BatchWriter
[ERROR] ...11 occurrences...
[INFO] BUILD FAILURE
```

This is INTENTIONAL — the class does not exist until Story 2-10 implementation.

## Summary

- **Stack**: Backend (Java 17 / JUnit 5 / Maven / H2)
- **Detected stack type**: `backend`
- **Generation mode**: AI generation (no browser/recording needed)
- **Execution mode**: Sequential
- **TDD Phase**: RED (compilation failure)
- **No SparkSession required**: BatchWriter is a pure JDBC writer

## Test Files Generated

| File | Type | Tests | Status |
|---|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java` | New | 11 | RED (compile failure) |

**Total tests added: 11**

## Acceptance Criteria Coverage

| AC | Description | Test(s) Covering | Priority |
|---|---|---|---|
| AC1 | Single JDBC transaction creates dq_run + all metrics | `writeCreatesDqRunRecordAndReturnsGeneratedId`, `writeInsertsAllMetricNumericEntries`, `writeInsertsAllMetricDetailEntries` | P0 |
| AC2 | Transaction rollback on failure, no partial records | `writeRollsBackOnFailure` | P0 |
| AC3 | Per-dataset error isolation (DqsJob level) | Covered in DqsJob wiring — not tested in BatchWriterTest directly | N/A |
| AC4 | End-to-end: 3 datasets → 3 dq_run records with metrics | Covered by AC1 tests + integration; full E2E is DqsJob scope | N/A |
| AC5 | dq_run has valid dqs_score, check_status, lookup_code | `writeSetsDqRunCheckStatusFromDqsScoreBreakdown`, `writeSetsDqRunDqsScoreFromCompositeScoreMetric`, `writeSetsDqRunCheckStatusUnknownWhenDqsScoreAbsent`, `writePreservesLookupCodeInDqRun`, `writePreservesPartitionDateInDqRun` | P0–P2 |

## Complete Test Inventory

| Test Method | AC | Priority | Level | Description |
|---|---|---|---|---|
| `writeCreatesDqRunRecordAndReturnsGeneratedId` | AC1, AC5 | P0 | Integration (H2) | BatchWriter.write() inserts 1 dq_run row and returns generated id > 0 |
| `writeInsertsAllMetricNumericEntries` | AC1, AC3 | P0 | Integration (H2) | 3 MetricNumeric entries → 3 dq_metric_numeric rows with correct values |
| `writeInsertsAllMetricDetailEntries` | AC1, AC4 | P0 | Integration (H2) | 2 MetricDetail entries → 2 dq_metric_detail rows with correct values |
| `writeSetsDqRunCheckStatusFromDqsScoreBreakdown` | AC1, AC5 | P0 | Integration (H2) | check_status extracted from DQS_SCORE breakdown detail status field |
| `writeSetsDqRunDqsScoreFromCompositeScoreMetric` | AC1, AC5 | P0 | Integration (H2) | dqs_score set from DQS_SCORE composite_score MetricNumeric (value=87.5) |
| `writeRollsBackOnFailure` | AC2 | P0 | Integration (H2) | Closed connection forces failure → 0 dq_run rows remain (rollback) |
| `writeSetsDqRunCheckStatusUnknownWhenDqsScoreAbsent` | AC5 | P1 | Integration (H2) | No DQS_SCORE metric → check_status = UNKNOWN |
| `writeHandlesEmptyMetricsList` | AC1 | P1 | Integration (H2) | Empty list → 1 dq_run row (check_status=UNKNOWN, dqs_score=NULL, 0 metric rows) |
| `constructorThrowsOnNullConnection` | Constructor | P1 | Unit | null Connection → IllegalArgumentException |
| `writePreservesLookupCodeInDqRun` | AC1, AC5 | P2 | Integration (H2) | lookup_code stored from DatasetContext.getLookupCode() |
| `writePreservesPartitionDateInDqRun` | AC1, AC5 | P2 | Integration (H2) | partition_date stored from DatasetContext.getPartitionDate() |

## Test Strategy Details

### Level: Integration (H2) — 10 tests
BatchWriter writes to 3 JDBC tables in a single transaction. Integration testing with H2
in-memory DB validates the full write path including autocommit=false, commit/rollback,
PreparedStatement parameter binding, executeBatch(), and generated key retrieval.

### Level: Unit — 1 test
Constructor null-guard: no DB required, pure object instantiation.

### No E2E Tests
Pure backend story — no UI, no browser automation needed.

### H2 Compatibility Notes
- DDL uses `INTEGER AUTO_INCREMENT` (not `SERIAL`), no JSONB, no `CAST(? AS JSONB)`
- `detail_value` stored as `VARCHAR` in H2 (correct for testing; Postgres uses JSONB at runtime)
- `BatchWriter` must detect DB type and use plain `?` for detail_value in H2 (vs `CAST(? AS JSONB)` for Postgres)

### Test Isolation Design
- `@BeforeAll`/`@AfterAll` for H2 connection lifecycle (matches `CheckFactoryTest` pattern)
- `@BeforeEach` truncates all 3 tables (not drop/recreate) for isolation
- Each test opens its own `freshConn()` for BatchWriter to avoid shared-state issues
- Shared `conn` (class-level) used only for verification queries

### Priority Mapping
- **P0**: `writeCreatesDqRunRecordAndReturnsGeneratedId`, `writeInsertsAllMetricNumericEntries`, `writeInsertsAllMetricDetailEntries`, `writeSetsDqRunCheckStatusFromDqsScoreBreakdown`, `writeSetsDqRunDqsScoreFromCompositeScoreMetric`, `writeRollsBackOnFailure`
- **P1**: `writeSetsDqRunCheckStatusUnknownWhenDqsScoreAbsent`, `writeHandlesEmptyMetricsList`, `constructorThrowsOnNullConnection`
- **P2**: `writePreservesLookupCodeInDqRun`, `writePreservesPartitionDateInDqRun`

### Key Patterns from Prior Checks Followed
- `@BeforeAll`/`@AfterAll` for connection lifecycle (exact match with `CheckFactoryTest`)
- `@BeforeEach` truncation (not drop/recreate) for test isolation
- Constructor null-guard pattern (IllegalArgumentException)
- Test naming convention: `<action><expectation>` (camelCase)
- No `@Disabled` (per story dev notes — "tests must be green from the start after implementation")
- `freshConn()` helper to open a new H2 connection per test (BatchWriter does not close conn)
- Shared `conn` (class-level) for verification queries only

## Compilation Failure Confirmation (Red Phase Evidence)

```
[ERROR] COMPILATION ERROR :
[ERROR] /.../.../BatchWriterTest.java:[130,27] cannot find symbol
[ERROR]   symbol:   class BatchWriter
[ERROR]   location: class com.bank.dqs.writer.BatchWriterTest
[ERROR] ...11 occurrences total...
[INFO] BUILD FAILURE
```

This is INTENTIONAL — `BatchWriter` class does not exist until Story 2-10 is implemented.

## Implementation Classes to Create (Story 2-10)

```
dqs-spark/src/main/java/com/bank/dqs/
  writer/
    BatchWriter.java                ← NEW (pure JDBC writer, no Spark)

dqs-spark/src/main/java/com/bank/dqs/
  DqsJob.java                      ← MODIFY (wire all 5 checks + BatchWriter)
```

## TDD Green Phase Instructions

After implementing Story 2-10:

1. Run targeted test suite:
   ```bash
   cd /home/sas/workspace/data-quality-service/dqs-spark
   mvn test -Dtest="BatchWriterTest"
   ```
2. Verify all 11 tests PASS (green phase)
3. Run full regression:
   ```bash
   mvn test
   ```
4. Confirm no regressions — baseline: 156 tests, 0 failures (from Story 2-9 completion)
5. Total after Story 2-10: 167 tests expected (156 + 11)

## Key Risks and Assumptions

1. **H2 vs Postgres SQL dialect**: `BatchWriter` must use plain `?` for `detail_value` in H2 (H2 does not support `CAST(? AS JSONB)`). Detect via `conn.getMetaData().getDatabaseProductName()`.
2. **`writeRollsBackOnFailure` implementation detail**: Test uses a pre-closed connection to force failure. `BatchWriter` must call `conn.rollback()` before rethrowing. If the connection is already closed, rollback may throw — implementation should handle this gracefully (catch and rethrow original exception).
3. **`freshConn()` pattern**: Each test opens its own connection for `BatchWriter`. The shared `conn` (class-level) is used for verification queries only. This ensures no autocommit state leaks between tests.
4. **`writeHandlesEmptyMetricsList`**: BatchWriter must skip `executeBatch()` for empty metric lists (do not execute empty batch against H2).
5. **DqsJob wiring**: The 3 per-dataset ACs (AC3, AC4) require DqsJob integration. These are verified at the DqsJob level and are not covered by `BatchWriterTest` directly. Consider adding a `DqsJobIntegrationTest` if regression coverage is needed.
6. **`statement.RETURN_GENERATED_KEYS`**: H2 supports this — test `writeCreatesDqRunRecordAndReturnsGeneratedId` will validate the generated key retrieval pattern works in H2 before Postgres deployment.
