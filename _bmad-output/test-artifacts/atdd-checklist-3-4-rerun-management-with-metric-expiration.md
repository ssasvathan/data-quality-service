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
story_id: 3-4-rerun-management-with-metric-expiration
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/3-4-rerun-management-with-metric-expiration.md
  - _bmad/tea/config.yaml
  - dqs-orchestrator/tests/test_db.py
  - dqs-orchestrator/tests/test_cli.py
  - dqs-orchestrator/tests/test_runner.py
  - dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java
  - dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java
---

# ATDD Checklist: Story 3-4 — Rerun Management with Metric Expiration

## Step 1: Preflight & Context

### Stack Detection

- **Detected stack:** `backend`
  - Indicators: `pom.xml` (Java/Maven), `pyproject.toml` (Python), `src/test/` (Java)
  - No `package.json`, no `playwright.config.ts`, no `vite.config.ts`
  - Result: pure backend project, AI generation mode, no browser recording

### Prerequisites

- Story 3-4 accepted with clear acceptance criteria: 3 ACs documented
- Backend test config present: `pyproject.toml` (pytest), `pom.xml` (JUnit 5), H2 in-memory DB
- Development environment available: Python 3.13 + uv, Java + Maven

### Story Context Loaded

- **AC1:** Rerun triggers `--datasets` filter + incremented `rerun_number` in new `dq_run` record
- **AC2:** Previous `dq_run.expiry_date` set to current timestamp; metrics cascaded; views show only new run
- **AC3:** Raw table retains all 3 historical rows with respective `expiry_dates` (full audit trail)

### Framework & Patterns

- Python tests: pytest with `unittest.mock`; match pattern in existing `test_db.py` / `test_cli.py` / `test_runner.py`
- Java tests: JUnit 5 with H2 in-memory DB; match pattern in `BatchWriterTest.java` / `DqsJobArgParserTest.java`
- TEA config: `tea_use_playwright_utils: true` — API-only profile (backend stack)

---

## Step 2: Generation Mode

**Mode selected:** AI Generation (backend project — no browser recording)

- Detected stack is `backend`
- Acceptance criteria are clear and standard (DB functions, CLI wiring, arg parsing)
- All scenarios are unit-testable without live browser

---

## Step 3: Test Strategy

### Acceptance Criteria to Test Scenarios

| AC | Scenario | Level | Priority |
|----|----------|-------|---------|
| AC1 | `get_next_rerun_number()` returns 0 on no history | Unit (Python) | P0 |
| AC1 | `get_next_rerun_number()` returns 1 when first run exists | Unit (Python) | P0 |
| AC1 | `get_next_rerun_number()` queries ALL history (no expiry filter) | Unit (Python) | P0 |
| AC1 | `run_spark_job()` appends `--rerun-number` when provided | Unit (Python) | P0 |
| AC1 | `run_spark_job()` omits `--rerun-number` when None | Unit (Python) | P1 |
| AC1 | `run_all_paths()` threads `rerun_numbers` dict to spark jobs | Unit (Python) | P0 |
| AC1 | `run_all_paths()` passes `None` rerun when no rerun_numbers | Unit (Python) | P1 |
| AC1 | `main()` calls `expire_previous_run()` for each dataset when `--rerun` | Unit (Python) | P0 |
| AC1 | `main()` does NOT call `expire_previous_run()` on normal runs | Unit (Python) | P0 |
| AC1 | `main()` passes `rerun_numbers` to `run_all_paths()` | Unit (Python) | P0 |
| AC1 | `DqsJob` parses `--rerun-number 2` → `rerunNumber == 2` | Unit (Java) | P0 |
| AC1 | `DqsJob` defaults `rerunNumber = 0` when absent | Unit (Java) | P1 |
| AC1 | `DqsJob` throws on invalid `--rerun-number not-a-number` | Unit (Java) | P1 |
| AC1 | `DqsJob` throws on dangling `--rerun-number` (no value) | Unit (Java) | P1 |
| AC1 | `BatchWriter.write()` stores provided `rerunNumber` in `dq_run` | Unit (Java) | P0 |
| AC1 | `BatchWriter.write()` stores `0` for first-time run | Unit (Java) | P1 |
| AC2 | `expire_previous_run()` updates `dq_run.expiry_date` with correct params | Unit (Python) | P0 |
| AC2 | `expire_previous_run()` cascades to `dq_metric_numeric` | Unit (Python) | P0 |
| AC2 | `expire_previous_run()` cascades to `dq_metric_detail` | Unit (Python) | P0 |
| AC2 | `expire_previous_run()` commits once (single transaction) | Unit (Python) | P0 |
| AC2 | `expire_previous_run()` returns `None` when no active run (first-time) | Unit (Python) | P1 |
| AC2 | `expire_previous_run()` uses `EXPIRY_SENTINEL` in WHERE clause | Unit (Python) | P0 |
| Safety | `expire_previous_run()` raising does NOT stop spark-submit | Unit (Python) | P0 |

### Test Level Selection

- **Unit tests only** — pure backend project, all logic is mockable
- No integration tests (no live DB required — mock psycopg2 cursor)
- No E2E tests (no browser/UI involved)

---

## Step 4: TDD Red Phase — Failing Tests Generated

### Python Test Files

#### `dqs-orchestrator/tests/test_db.py` — Story 3-4 additions

9 new failing tests (TDD RED):

| Test | Failure Reason |
|------|---------------|
| `test_expire_previous_run_updates_expiry_date_on_dq_run` | `ImportError: cannot import 'expire_previous_run'` |
| `test_expire_previous_run_cascades_to_metric_numeric` | `ImportError: cannot import 'expire_previous_run'` |
| `test_expire_previous_run_cascades_to_metric_detail` | `ImportError: cannot import 'expire_previous_run'` |
| `test_expire_previous_run_commits_all_three_updates_in_one_transaction` | `ImportError: cannot import 'expire_previous_run'` |
| `test_expire_previous_run_returns_none_when_no_active_run` | `ImportError: cannot import 'expire_previous_run'` |
| `test_expire_previous_run_uses_expiry_sentinel_constant_in_where_clause` | `ImportError: cannot import 'expire_previous_run'` |
| `test_get_next_rerun_number_returns_zero_when_no_history` | `ImportError: cannot import 'get_next_rerun_number'` |
| `test_get_next_rerun_number_returns_one_when_first_run_exists` | `ImportError: cannot import 'get_next_rerun_number'` |
| `test_get_next_rerun_number_queries_all_history_including_expired` | `ImportError: cannot import 'get_next_rerun_number'` |

#### `dqs-orchestrator/tests/test_cli.py` — Story 3-4 additions

4 new failing tests (TDD RED):

| Test | Failure Reason |
|------|---------------|
| `test_main_calls_expire_previous_run_when_rerun_flag_set` | `AttributeError: orchestrator.cli has no attribute 'expire_previous_run'` |
| `test_main_does_not_call_expire_previous_run_when_rerun_not_set` | `AttributeError: orchestrator.cli has no attribute 'expire_previous_run'` |
| `test_main_expire_error_is_non_fatal_spark_submit_still_proceeds` | `AttributeError: orchestrator.cli has no attribute 'expire_previous_run'` |
| `test_main_passes_rerun_numbers_to_run_all_paths` | `AttributeError: orchestrator.cli has no attribute 'expire_previous_run'` |

#### `dqs-orchestrator/tests/test_runner.py` — Story 3-4 additions

4 new failing tests (TDD RED):

| Test | Failure Reason |
|------|---------------|
| `test_run_spark_job_appends_rerun_number_to_command` | `TypeError: run_spark_job() got unexpected keyword argument 'rerun_number'` |
| `test_run_spark_job_omits_rerun_number_when_none` | `TypeError: run_spark_job() got unexpected keyword argument 'rerun_number'` |
| `test_run_all_paths_threads_rerun_numbers_to_spark_job` | `TypeError: run_all_paths() got unexpected keyword argument 'rerun_numbers'` |
| `test_run_all_paths_passes_none_rerun_number_when_rerun_numbers_is_none` | `TypeError: run_all_paths() got unexpected keyword argument 'rerun_numbers'` |

### Java Test Files

#### `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java` — Story 3-4 additions

4 new failing tests (TDD RED — compile error):

| Test | Failure Reason |
|------|---------------|
| `parseArgsExtractsRerunNumber` | `cannot find symbol: method rerunNumber()` |
| `parseArgsDefaultsRerunNumberToZeroWhenAbsent` | `cannot find symbol: method rerunNumber()` |
| `parseArgsThrowsOnInvalidRerunNumberValue` | `cannot find symbol: method rerunNumber()` |
| `parseArgsThrowsOnDanglingRerunNumberFlag` | `cannot find symbol: method rerunNumber()` |

#### `dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java` — Story 3-4 additions

2 new failing tests + all existing calls updated (TDD RED — compile error):

| Test | Failure Reason |
|------|---------------|
| `writeStoresRerunNumberInDqRun` | `method write(...) cannot be applied with 4 args — wrong arity` |
| `writeStoresZeroRerunNumberForFirstRun` | `method write(...) cannot be applied with 4 args — wrong arity` |

All existing `BatchWriterTest` call sites updated from `writer.write(ctx, metrics, null)` to `writer.write(ctx, metrics, null, 0)`. These will also compile-fail until `BatchWriter.write()` gains the `int rerunNumber` 4th parameter.

---

## Step 5: Validation Summary

### TDD Red Phase: PASS

- All 17 Python tests FAIL (ImportError / TypeError — implementation missing)
- All 6 Java tests FAIL (compile error — implementation missing)
- All 49 existing Python tests still PASS (no regression)
- Existing Java tests: compile-fail until `BatchWriter.write()` gains 4th parameter — this is intentional and expected

### Acceptance Criteria Coverage

| AC | Python Coverage | Java Coverage |
|----|----------------|--------------|
| AC1 (rerun_number increments) | `test_get_next_rerun_number_*` (×3), `test_run_spark_job_appends_rerun_number_*` (×2), `test_run_all_paths_threads_rerun_numbers_*` (×2), `test_main_calls_expire_previous_run_*`, `test_main_passes_rerun_numbers_*` | `parseArgsExtractsRerunNumber`, `parseArgsDefaultsRerunNumberToZeroWhenAbsent`, `writeStoresRerunNumberInDqRun`, `writeStoresZeroRerunNumberForFirstRun` |
| AC2 (expiry cascade) | `test_expire_previous_run_*` (×6) | `writeStoresRerunNumberInDqRun` (verifies column set) |
| AC3 (audit trail) | `test_get_next_rerun_number_queries_all_history_including_expired` (verifies no expiry filter in query) | N/A (covered by Python unit tests) |
| Safety | `test_main_expire_error_is_non_fatal_*`, `test_main_does_not_call_expire_previous_run_when_rerun_not_set` | N/A |

### Key Risks / Assumptions

1. **Ordering constraint:** `expire_previous_run()` MUST be called BEFORE `run_all_paths()` — tested indirectly via `test_main_passes_rerun_numbers_to_run_all_paths` which verifies the rerun_numbers kwarg reflects the expired state
2. **UNIQUE constraint awareness:** Tests use mocked DB — actual constraint enforcement validated during integration
3. **Single rerun_number for spark-submit:** For multi-dataset reruns, `max(rerun_numbers.values())` is used — this is per spec

### Files Created / Modified

| File | Action | Tests Added |
|------|--------|------------|
| `dqs-orchestrator/tests/test_db.py` | Extended | 9 failing |
| `dqs-orchestrator/tests/test_cli.py` | Extended | 4 failing |
| `dqs-orchestrator/tests/test_runner.py` | Extended | 4 failing |
| `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java` | Extended | 4 failing |
| `dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java` | Updated + Extended | 2 new failing + 11 call sites updated |

**Total: 23 new failing tests** (17 Python + 6 Java)

### Next Steps (TDD Green Phase)

After implementation:

1. Add `expire_previous_run()` and `get_next_rerun_number()` to `dqs-orchestrator/src/orchestrator/db.py`
2. Wire rerun management block into `dqs-orchestrator/src/orchestrator/cli.py`
3. Add `rerun_number` parameter to `run_spark_job()` and `run_all_paths()` in `dqs-orchestrator/src/orchestrator/runner.py`
4. Add `int rerunNumber` to `DqsJobArgs` record in `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`
5. Update `BatchWriter.write()` signature + replace `ps.setInt(6, 0)` TODO in `dqs-spark/src/main/java/com/bank/dqs/writer/BatchWriter.java`
6. Run `uv run pytest -v` → verify all 23 new tests pass (+ 49 existing still pass)
7. Run `mvn test` → verify all new Java tests pass (+ 171 existing still pass)
8. Commit passing tests
