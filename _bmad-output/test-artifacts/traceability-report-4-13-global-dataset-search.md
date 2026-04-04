---
stepsCompleted:
  - step-01-load-context
  - step-02-discover-tests
  - step-03-map-criteria
  - step-04-analyze-gaps
  - step-05-gate-decision
lastStep: step-05-gate-decision
lastSaved: '2026-04-03'
story: 4-13-global-dataset-search
gateDecision: CONCERNS
---

# Traceability Report: Story 4.13 — Global Dataset Search

**Date:** 2026-04-03  
**Story:** `4-13-global-dataset-search`  
**Story Status:** done  
**Workflow:** bmad-testarch-trace (Create mode, sequential execution)

---

## Gate Decision: CONCERNS

**Rationale:** P0 coverage is 100%, overall coverage is 95% (minimum: 80%), but P1 coverage is 83.3% (target: 90%). One P1 criterion (ERR-1: isError error-state display) has partial coverage — the implementation correctly handles `isError` (patched in code review finding F4), but no test asserts the "Search unavailable — please try again" noOptionsText. All 6 story acceptance criteria are verified implemented and passing (344 tests pass, 0 skipped, 0 failed).

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| Total Requirements Tracked | 20 |
| Fully Covered | 19 (95%) |
| Partially Covered | 1 |
| Uncovered (NONE) | 0 |
| P0 Coverage | 100% (8/8) |
| P1 Coverage | 83.3% (5/6 fully; 1 partial) |
| P2 Coverage | 100% (4/4) |
| P3 Coverage | N/A |
| Overall Coverage | 95% |

### Gate Criteria

| Criterion | Required | Actual | Status |
|-----------|----------|--------|--------|
| P0 Coverage | 100% | 100% | MET |
| P1 Coverage (PASS target) | ≥90% | 83.3% | PARTIAL |
| P1 Coverage (FAIL threshold) | ≥80% | 83.3% | MET |
| Overall Coverage | ≥80% | 95% | MET |

---

## Test Inventory

### Test Files Discovered

| File | Level | Test Count | AC Coverage |
|------|-------|-----------|-------------|
| `dqs-dashboard/tests/layouts/GlobalSearch.test.tsx` | Component (RTL/jsdom) | 19 | AC1, AC2, AC3, AC4, AC5, AC6 |
| `dqs-dashboard/tests/api/useSearch.test.ts` | Unit/Hook (Vitest renderHook) | 10 | AC1, AC3, AC6 |
| `dqs-dashboard/tests/layouts/AppLayout.test.tsx` | Component (RTL/jsdom) | 25 | Backward compat, search placeholder |
| **Total** | — | **54 relevant** | All ACs |

**Total test suite:** 344 tests passing, 0 skipped, 0 failed (315 pre-existing + 29 new).

### Test Categorization

| Level | Count | Notes |
|-------|-------|-------|
| E2E | 0 | No Playwright configured; project uses Vitest/RTL only |
| Component | 44 | GlobalSearch.test.tsx (19) + AppLayout.test.tsx (25) |
| Unit/Hook | 10 | useSearch.test.ts (10 renderHook tests) |

---

## Traceability Matrix

| ID | Acceptance Criterion | Priority | Test(s) | Level | Coverage |
|----|---------------------|----------|---------|-------|----------|
| AC1-a | 2+ char triggers autocomplete dropdown (300ms debounce) | P0 | GS-03, GS-04, US-03, US-04 | Component + Hook | FULL |
| AC1-b | 1 char does NOT open dropdown (enabled guard) | P0 | GS-02, US-02 | Component + Hook | FULL |
| AC1-c | Placeholder "Search datasets... (Ctrl+K)" rendered | P0 | GS-01, AppLayout existing | Component | FULL |
| AC1-d | Results show DqsScoreChip + dataset name + LOB name | P0 | GS-04, GS-05, GS-06 | Component | FULL |
| AC2 | Escape closes dropdown without navigation | P1 | GS-07 | Component | FULL |
| AC3-a | Click result navigates to `/datasets/{dataset_id}` | P0 | GS-08 | Component | FULL |
| AC3-b | Input cleared after selection | P0 | GS-09 | Component | FULL |
| AC4-a | Ctrl+K focuses search input from any view | P0 | GS-10 | Component | FULL |
| AC4-b | Cmd+K (Mac) also focuses search input | P1 | GS-11 | Component | FULL |
| AC4-c | Ctrl+K calls e.preventDefault() | P1 | GS-12 | Component | FULL |
| AC5-a | No results shows "No datasets matching '{query}'" | P1 | GS-13, GS-14 | Component | FULL |
| AC5-b | Single char does not show no-results message | P2 | GS-15 | Component | FULL |
| AC6-a | Max 10 results displayed in dropdown | P2 | GS-16 | Component | FULL |
| AC6-b | useSearch called with typed query | P2 | GS-17, US-05, US-06 | Component + Hook | FULL |
| AC6-c | Global across all LOBs (prefix first, then substring) | P2 | US-05, US-06, GS-16 | Hook + Component | FULL |
| BW-1 | AppLayout renders without error with GlobalSearch | P0 | GS-18, AppLayout (25) | Component | FULL |
| BW-2 | Breadcrumbs still present alongside GlobalSearch | P0 | GS-19 | Component | FULL |
| ERR-1 | isError state shows "Search unavailable — please try again" | P1 | No dedicated test | — | PARTIAL |
| NULL-1 | null dqs_score / check_status handled gracefully | P1 | US-08 | Hook | FULL |
| CACHE-1 | staleTime 30s — no re-fetch within window | P2 | US-10 | Hook | FULL |

**Test ID Reference:**

- GS-01 to GS-19: `tests/layouts/GlobalSearch.test.tsx` tests (in file order by describe block)
- US-01 to US-10: `tests/api/useSearch.test.ts` tests (in file order)

---

## Gap Analysis

### Critical Gaps (P0) — 0

No P0 gaps. All critical acceptance criteria have full test coverage.

### High-Priority Gaps (P1) — 0 uncovered, 1 partial

| ID | Gap | Severity | Recommendation |
|----|-----|----------|----------------|
| ERR-1 | `isError` branch in GlobalSearch shows "Search unavailable — please try again" via `noOptionsText` — implemented (code review F4) but no test asserts it | PARTIAL | Add a component test that mocks `useSearch` returning `{ isError: true, data: undefined }` and asserts the error noOptionsText appears |

### Coverage Heuristics

| Heuristic | Findings |
|-----------|---------|
| API endpoint coverage | `GET /api/search?q=...` fully exercised: URL construction, `encodeURIComponent`, 2-char guard, 3+-char enabled, queryKey shape |
| Auth/authz coverage | N/A — search endpoint requires no auth in this story |
| Error-path coverage | Implementation handles `isError`; 1 happy-path-only gap (ERR-1 noOptionsText not asserted in tests) |

---

## Recommendations

1. **MEDIUM — Add error state component test** (ERR-1):
   In `GlobalSearch.test.tsx`, add a test in the AC5 describe block:
   ```typescript
   it('[P1] shows "Search unavailable" when useSearch returns an error', async () => {
     getUseSearchMock().mockReturnValue({ data: undefined, isLoading: false, isError: true })
     renderAppLayout()
     const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
     fireEvent.change(input, { target: { value: 'sales' } })
     await waitFor(() => {
       expect(screen.getByText('Search unavailable — please try again')).toBeInTheDocument()
     })
   })
   ```
   This would close the PARTIAL gap and raise P1 coverage to 100%, qualifying for a PASS gate.

2. **LOW — Run bmad-testarch-test-review** to assess overall test quality (isolation, execution time, selector resilience).

---

## Code Review Cross-Reference

| Finding | Status | Impact on Coverage |
|---------|--------|--------------------|
| F1 — Duplicate SearchResult type in tests | Fixed | Clean type imports; no coverage impact |
| F2 — Stale TDD red-phase comments | Fixed | Tests now accurately reflect GREEN PHASE |
| F3 — Ctrl+K stale setOpen closure | Fixed | AC4 tests pass correctly |
| F4 — Missing isError handling | Fixed (impl) | ERR-1 partial: implementation done, test missing |
| F5 — getUseSearchMock indirection | Deferred | No coverage impact |
| F6 — Stale results during query transition | Deferred | Acceptable UX; no AC violation |
| F7 — Escape UX spec vs AC2 discrepancy | Deferred | No AC violation; AC2 only requires close |

---

## Gate Decision Summary

```
GATE DECISION: CONCERNS

Coverage Analysis:
  P0 Coverage:      100% (Required: 100%)  → MET
  P1 Coverage:      83.3% (PASS target: 90%, minimum: 80%)  → PARTIAL
  Overall Coverage: 95% (Minimum: 80%)  → MET

Decision Rationale:
  P0 coverage is 100% and overall coverage is 95% (minimum: 80%), but P1 coverage
  is 83.3% (target: 90%). One P1 criterion (ERR-1: error state noOptionsText) has
  partial coverage — the implementation is correct (code review F4 patched), but
  no test asserts the "Search unavailable — please try again" message.

  All 6 story ACs are verified PASS in the code review. 344 tests pass, 0 skipped.

Critical Gaps: 0
Partial Gaps: 1 (ERR-1, low effort to close)

GATE: CONCERNS — Proceed with caution. Address ERR-1 test gap in next sprint.
To reach PASS: add 1 error-state test (closes ERR-1), raising P1 to 100%.
```

---

## Next Actions

1. **(Recommended — MEDIUM priority)** Add ERR-1 error-state test to `GlobalSearch.test.tsx` to close the P1 partial gap and achieve a PASS gate.
2. Story 4.13 is `done`; all 6 ACs verified implemented and passing. CONCERNS gate does not block story completion.
3. Reference for next story: 315 → 344 tests (29 new). All tests green.
