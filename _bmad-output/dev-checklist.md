# Developer Pre-Review Checklist

> Created: 2026-04-04 (Epic 4 Retrospective — third-epic miss, finally created)
> Purpose: Run through this checklist before submitting a story for SM/code review.

---

## Universal (All Languages)

- [ ] **No stale ATDD RED-phase comments** — remove all `// THIS TEST WILL FAIL`, `// RED PHASE:`, `# RED PHASE:`, and `# THIS TEST WILL FAIL` comments from test files before review
- [ ] **No hardcoded EXPIRY_SENTINEL** — use `DqsConstants.EXPIRY_SENTINEL` (Java) or `EXPIRY_SENTINEL` (Python); never `'9999-12-31 23:59:59'`
- [ ] **All constructor/init parameters validated** — null/blank checks throw `IllegalArgumentException` (Java) or `ValueError` (Python) at object construction time
- [ ] **Per-item failure isolation** — dataset/path loop operations wrapped in try/catch; one failure never halts the pipeline
- [ ] **Active-record views only** — queries target `v_*_active` views; no direct `dq_run`, `dq_metric_numeric`, or `dq_metric_detail` table reads

---

## Java (dqs-spark)

- [ ] **JDBC safety**: save/restore `autoCommit`; add rollback exceptions as suppressed; use `TypeReference` for JSON Map parsing
- [ ] **H2 DDL decimal columns**: use `DECIMAL(20,5)` for numeric columns with fractional values in H2 test DDL
- [ ] **Constructor null guards**: `Objects.requireNonNull()` or explicit `if (x == null) throw new IllegalArgumentException(...)` for every constructor parameter
- [ ] **No bare `NumberFormatException`**: wrap `Long.parseLong()` / `Integer.parseInt()` with user-friendly error messages
- [ ] **Test coverage for all new CLI args**: happy path, invalid value, dangling/missing flag

---

## Python (dqs-orchestrator, dqs-serve)

- [ ] **psycopg2 resource management**: every `get_connection()` call in a try block has a corresponding `finally: conn.close()`; never rely on except-only cleanup; prefer `with`-style context managers
- [ ] **Type annotation completeness**: all new functions have type annotations on parameters and return values; no implicit `Any`
- [ ] **Import ordering**: run `ruff check --select I --fix` or `isort` before review; no import ordering violations
- [ ] **No `ruff` violations**: run `ruff check .` and resolve all findings before review
- [ ] **pytest pythonpath configured**: `pyproject.toml` has `[tool.pytest.ini_options] pythonpath = ["src"]` (or equivalent) before first test run

---

## TypeScript / React (dqs-dashboard)

- [ ] **No floating Promises**: all async calls in event handlers prefixed with `void` (e.g., `void navigator.clipboard.writeText(...)`) or properly awaited
- [ ] **No `any` types**: no `as any` or `: any` in implementation code; use `unknown` + type guards if needed
- [ ] **Import order clean**: no duplicate imports, no unused imports, no relative-vs-absolute inconsistencies
- [ ] **Stale ATDD RED-phase comments removed**: `// THIS TEST WILL FAIL` and `// RED PHASE:` comments cleaned from all `.test.tsx` / `.test.ts` files
- [ ] **axe-core violations**: zero violations reported by `@axe-core/react` during development build for any new component
- [ ] **Aria attributes present**: all interactive components have `aria-label`, `role`, and keyboard handlers (Tab, Enter/Space)
- [ ] **No layout-shift skeletons**: loading states use skeleton components matching target layout dimensions, not spinners

---

## Before Marking Story Done

1. All ATDD tests pass: `npm test -- --watchAll=false` / `uv run pytest -v` / `mvn test`
2. This checklist reviewed — no unchecked items that weren't explicitly deferred with a documented reason
3. Review Findings section in the story file is filled out (even if "0 findings")
4. Stale ATDD RED-phase comments removed from test files
