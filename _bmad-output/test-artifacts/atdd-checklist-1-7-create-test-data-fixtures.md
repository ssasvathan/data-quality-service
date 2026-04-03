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
storyId: 1-7-create-test-data-fixtures
tddPhase: RED
---

# ATDD Checklist: Story 1-7 — Create Test Data Fixtures

## Step 1: Preflight & Context

- **Stack detected:** `backend` (Python/pyproject.toml, pytest/conftest.py)
- **Story file:** `_bmad-output/implementation-artifacts/1-7-create-test-data-fixtures.md`
- **Status at start:** ready-for-dev → updated to in-progress
- **Prerequisites verified:**
  - [x] Story has clear acceptance criteria (AC1–AC6)
  - [x] conftest.py exists at `dqs-serve/tests/conftest.py`
  - [x] ddl.sql and views.sql exist at `dqs-serve/src/serve/schema/`
  - [x] EXPIRY_SENTINEL at `serve.db.models.EXPIRY_SENTINEL`
  - [x] 139 existing tests — zero regressions required

---

## Step 2: Generation Mode

**Mode selected:** AI generation (backend stack — no browser recording)

---

## Step 3: Test Strategy

| AC | Scenario | Level | Priority | Test Class |
|---|---|---|---|---|
| AC1 | 3+ source systems + Avro/Parquet | Integration | P0 | TestFixturesSourceSystems |
| AC2 | Stale partition anomaly | Integration | P0 | TestFixturesAnomalies |
| AC2 | Zero-row anomaly | Integration | P0 | TestFixturesAnomalies |
| AC2 | Schema drift anomaly | Integration | P0 | TestFixturesAnomalies |
| AC2 | High null rate anomaly | Integration | P0 | TestFixturesAnomalies |
| AC3 | Legacy omni path in dq_run | Integration | P0 | TestFixturesLegacyPath |
| AC3 | Legacy omni path in dataset_enrichment | Integration | P0 | TestFixturesLegacyPath |
| AC4 | All 6 JSONB literal types present | Integration | P0 | TestFixturesEventAttributeTypes |
| AC5 | 7 distinct partition_dates for VOLUME | Integration | P0 | TestFixturesHistoricalBaseline |
| AC6 | check_config enabled + disabled rows | Integration | P0 | TestFixturesCheckConfig |
| AC6 | dataset_enrichment custom_weights JSONB | Integration | P1 | TestFixturesDatasetEnrichment |

**TDD Red Phase Confirmation:** All 29 tests are written to assert EXPECTED behavior that will only pass after `fixtures.sql` is loaded. The `db_with_fixtures` fixture is the gating condition — tests require a running Postgres instance.

---

## Step 4: Test Generation (Sequential Mode)

### Subagent A (API/Integration Tests) — COMPLETE

**File created:** `dqs-serve/tests/test_schema/test_fixtures.py`

**Test count:** 29 integration tests

**All tests marked:** `@pytest.mark.integration` (class-level, no test-level skip needed — tests are _genuinely_ failing in the red phase because `fixtures.sql` hasn't been created yet at test-write time)

**Fixture infrastructure created:**
- Local `db_with_fixtures` fixture in `test_fixtures.py` — loads DDL + views + fixtures in one rolled-back transaction
- Does NOT modify `tests/conftest.py`

**Files created:**
- `dqs-serve/tests/test_schema/test_fixtures.py` — 29 integration tests
- `dqs-serve/src/serve/schema/fixtures.sql` — test data fixture SQL

---

## Step 4C: Aggregation

### TDD Red Phase Validation

All tests:
- [x] Use `@pytest.mark.integration` (deselected from default suite per pyproject.toml)
- [x] Assert EXPECTED behavior against real Postgres
- [x] Will fail if `fixtures.sql` is not loaded (fixture gating enforces this)
- [x] No hardcoded `'9999-12-31 23:59:59'` in Python — uses `EXPIRY_SENTINEL` constant
- [x] Import order: stdlib (`pathlib`) → third-party (`pytest`) → local (`serve.db.models`)
- [x] `from __future__ import annotations` at top
- [x] `TYPE_CHECKING` guard for `psycopg2` import
- [x] `conftest.py` NOT modified

### Summary Statistics

| Metric | Value |
|---|---|
| Total tests | 29 |
| Test classes | 6 |
| All marked integration | Yes |
| conftest.py modified | NO |
| Hardcoded sentinel | NONE |
| EXPIRY_SENTINEL used | Yes (1 assertion) |
| Execution mode | Sequential |

---

## Step 5: Validation & Completion

### Checklist Validation

- [x] Prerequisites satisfied (conftest.py, ddl.sql, views.sql all present)
- [x] Test file created: `dqs-serve/tests/test_schema/test_fixtures.py`
- [x] fixtures.sql created: `dqs-serve/src/serve/schema/fixtures.sql`
- [x] All 6 ACs covered by at least one test
- [x] `@pytest.mark.integration` on all test classes
- [x] `db_with_fixtures` fixture is local — conftest.py untouched
- [x] No `test_schema/__init__.py` created (already exists)
- [x] No hardcoded serial IDs in SQL (subquery FK lookup pattern used)
- [x] Unique constraints respected (7 separate dq_run rows for sales_daily history)
- [x] Story status updated to in-progress

### Generated Files

| File | Type | Description |
|---|---|---|
| `dqs-serve/src/serve/schema/fixtures.sql` | NEW | Test data fixture SQL (AC1–AC6) |
| `dqs-serve/tests/test_schema/test_fixtures.py` | NEW | 29 integration tests (AC1–AC6) |

### Files NOT Modified

| File | Reason |
|---|---|
| `dqs-serve/src/serve/schema/ddl.sql` | Owned by stories 1-2 through 1-5 |
| `dqs-serve/src/serve/schema/views.sql` | Owned by story 1-6 |
| `dqs-serve/tests/conftest.py` | 139 existing tests depend on it |

### Acceptance Criteria Coverage

| AC | Covered By | Status |
|---|---|---|
| AC1: 3+ source systems, Avro+Parquet | TestFixturesSourceSystems (4 tests) | COVERED |
| AC2: Mixed anomalies | TestFixturesAnomalies (6 tests) | COVERED |
| AC3: Legacy omni path + enrichment | TestFixturesLegacyPath (3 tests) | COVERED |
| AC4: JSONB all 6 literal types | TestFixturesEventAttributeTypes (7 tests) | COVERED |
| AC5: 7-day historical baseline | TestFixturesHistoricalBaseline (2 tests) | COVERED |
| AC6: check_config + enrichment rows | TestFixturesCheckConfig (4) + TestFixturesDatasetEnrichment (3) | COVERED |

### Next Steps (TDD Green Phase)

After `fixtures.sql` is confirmed clean and tests pass:

1. Run integration tests: `cd dqs-serve && uv run pytest tests/test_schema/test_fixtures.py -m integration -v`
2. Verify all 29 tests PASS (green phase)
3. Update story status from `in-progress` → `done`
4. Confirm 139 + 29 = 168 total tests pass: `cd dqs-serve && uv run pytest -m integration -v`

### Key Assumptions

- Postgres available at `DATABASE_URL` (default: `postgresql://postgres:localdev@localhost:5432/postgres`)
- `uv` environment is configured with psycopg2-binary already installed (confirmed from story 1-6 notes)
- `generate_series` is available (standard Postgres function — no extension required)
