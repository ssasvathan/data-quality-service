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
  - _bmad-output/implementation-artifacts/1-3-implement-metric-storage-schema.md
  - _bmad-output/project-context.md
  - dqs-serve/tests/conftest.py
  - dqs-serve/tests/test_schema/test_dq_run_schema.py
  - dqs-serve/src/serve/db/models.py
  - dqs-serve/src/serve/schema/ddl.sql
  - dqs-serve/pyproject.toml
---

# ATDD Checklist: Story 1-3 — Implement Metric Storage Schema

## TDD Red Phase (Current)

All 34 tests are FAILING by design — `ddl.sql` does not yet contain `dq_metric_numeric`
or `dq_metric_detail`.  Once Story 1-3 DDL is appended, they will all pass.

## Summary

- **Stack**: Backend (Python 3.13 / pytest)
- **Generation Mode**: AI generation (no browser recording needed)
- **Total Tests**: 34 (all `@pytest.mark.integration`)
- **Structural Tests (no DB)**: 0 — all ACs require real Postgres
- **Integration Tests (real DB)**: 34
- **Test File**: `dqs-serve/tests/test_schema/test_metric_schema.py`
- **TDD Phase**: RED (tests will fail until DDL is implemented)

## Test Inventory

### TestDqMetricNumericTableExists (AC1) — 10 tests

| Test | Priority | AC |
|------|----------|----|
| `test_dq_metric_numeric_table_exists` | P0 | AC1 |
| `test_dq_metric_numeric_id_is_serial_pk` | P0 | AC1 |
| `test_dq_metric_numeric_column_exists_with_correct_type[dq_run_id-integer-NO]` | P0 | AC1 |
| `test_dq_metric_numeric_column_exists_with_correct_type[check_type-text-NO]` | P0 | AC1 |
| `test_dq_metric_numeric_column_exists_with_correct_type[metric_name-text-NO]` | P0 | AC1 |
| `test_dq_metric_numeric_column_exists_with_correct_type[metric_value-numeric-YES]` | P0 | AC1 |
| `test_dq_metric_numeric_column_exists_with_correct_type[create_date-timestamp without time zone-NO]` | P0 | AC1 |
| `test_dq_metric_numeric_column_exists_with_correct_type[expiry_date-timestamp without time zone-NO]` | P0 | AC1 |
| `test_dq_metric_numeric_expiry_date_default_is_sentinel` | P0 | AC1 |
| `test_dq_metric_numeric_create_date_default_is_now` | P0 | AC1 |

### TestDqMetricDetailTableExists (AC2) — 10 tests

| Test | Priority | AC |
|------|----------|----|
| `test_dq_metric_detail_table_exists` | P0 | AC2 |
| `test_dq_metric_detail_id_is_serial_pk` | P0 | AC2 |
| `test_dq_metric_detail_column_exists_with_correct_type[dq_run_id-integer-NO]` | P0 | AC2 |
| `test_dq_metric_detail_column_exists_with_correct_type[check_type-text-NO]` | P0 | AC2 |
| `test_dq_metric_detail_column_exists_with_correct_type[detail_type-text-NO]` | P0 | AC2 |
| `test_dq_metric_detail_column_exists_with_correct_type[detail_value-jsonb-YES]` | P0 | AC2 |
| `test_dq_metric_detail_column_exists_with_correct_type[create_date-timestamp without time zone-NO]` | P0 | AC2 |
| `test_dq_metric_detail_column_exists_with_correct_type[expiry_date-timestamp without time zone-NO]` | P0 | AC2 |
| `test_dq_metric_detail_expiry_date_default_is_sentinel` | P0 | AC2 |
| `test_dq_metric_detail_create_date_default_is_now` | P0 | AC2 |

### TestDqMetricNumericUniqueConstraint (AC3) — 4 tests

| Test | Priority | AC |
|------|----------|----|
| `test_unique_constraint_exists_with_correct_name` | P0 | AC3 |
| `test_unique_constraint_covers_correct_columns` | P0 | AC3 |
| `test_two_active_numeric_rows_same_natural_key_rejected` | P1 | AC3 |
| `test_active_and_expired_numeric_rows_same_natural_key_allowed` | P1 | AC3 |

### TestDqMetricDetailUniqueConstraint (AC4) — 4 tests

| Test | Priority | AC |
|------|----------|----|
| `test_unique_constraint_exists_with_correct_name` | P0 | AC4 |
| `test_unique_constraint_covers_correct_columns` | P0 | AC4 |
| `test_two_active_detail_rows_same_natural_key_rejected` | P1 | AC4 |
| `test_active_and_expired_detail_rows_same_natural_key_allowed` | P1 | AC4 |

### TestDqMetricNumericForeignKey (AC5) — 3 tests

| Test | Priority | AC |
|------|----------|----|
| `test_fk_constraint_exists_with_correct_name` | P0 | AC5 |
| `test_fk_references_dq_run_id` | P0 | AC5 |
| `test_insert_with_invalid_dq_run_id_raises_fk_violation` | P1 | AC5 |

### TestDqMetricDetailForeignKey (AC5) — 3 tests

| Test | Priority | AC |
|------|----------|----|
| `test_fk_constraint_exists_with_correct_name` | P0 | AC5 |
| `test_fk_references_dq_run_id` | P0 | AC5 |
| `test_insert_with_invalid_dq_run_id_raises_fk_violation` | P1 | AC5 |

## Acceptance Criteria Coverage

| AC | Description | Covered By |
|----|-------------|------------|
| AC1 | `dq_metric_numeric` table + columns + defaults | TestDqMetricNumericTableExists (10 tests) |
| AC2 | `dq_metric_detail` table + columns + defaults | TestDqMetricDetailTableExists (10 tests) |
| AC3 | `dq_metric_numeric` unique constraint name + columns + behavioral | TestDqMetricNumericUniqueConstraint (4 tests) |
| AC4 | `dq_metric_detail` unique constraint name + columns + behavioral | TestDqMetricDetailUniqueConstraint (4 tests) |
| AC5 | Both tables have FK constraints referencing `dq_run.id` | TestDqMetricNumericForeignKey + TestDqMetricDetailForeignKey (6 tests) |

## Design Decisions

- No structural/unit-level tests for this story — all ACs require real DB validation.
  Story 1-2 had `EXPIRY_SENTINEL` constant checks; those already exist and are not
  re-tested here.
- `EXPIRY_SENTINEL` imported from `serve.db.models` in behavioral tests — never hardcoded.
- `from __future__ import annotations` + `TYPE_CHECKING` guard used for psycopg2 type hints
  (pattern learned from story 1-2 ruff fix).
- `db_conn` fixture from `conftest.py` used as-is — no modifications to conftest.
- `tests/test_schema/__init__.py` already exists — not recreated.
- Behavioral tests for unique constraint violation insert a `dq_run` row first to satisfy
  the FK constraint before testing `dq_metric_*` insertion.

## Next Steps (TDD Green Phase)

After Story 1-3 implementation appends `dq_metric_numeric` and `dq_metric_detail` DDL
to `dqs-serve/src/serve/schema/ddl.sql`:

1. Run integration tests: `cd dqs-serve && uv run pytest -m integration`
2. Verify all 34 tests PASS (green phase)
3. Also run default suite to confirm no regressions: `uv run pytest`
4. Story status: `in-progress` → `complete`
