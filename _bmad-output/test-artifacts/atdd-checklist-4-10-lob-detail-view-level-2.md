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
story_id: 4-10-lob-detail-view-level-2
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/4-10-lob-detail-view-level-2.md
  - _bmad-output/project-context.md
  - dqs-dashboard/tests/pages/SummaryPage.test.tsx
  - dqs-dashboard/tests/setup.ts
  - dqs-dashboard/src/api/types.ts
  - dqs-dashboard/src/api/queries.ts
  - dqs-dashboard/src/pages/LobDetailPage.tsx
  - dqs-dashboard/src/components/index.ts
  - dqs-dashboard/vite.config.ts
  - _bmad/tea/config.yaml
---

# ATDD Checklist: Story 4.10 — LOB Detail View (Level 2)

## Step 1: Preflight & Context

### Stack Detection

- **Detected Stack**: `frontend`
- **Detection Evidence**: `dqs-dashboard/package.json` with React/TypeScript/Vite/Vitest dependencies, no backend manifests (pyproject.toml / pom.xml)
- **Test Framework**: Vitest + React Testing Library (jsdom)
- **Note**: No E2E tests — deferred for MVP per `project-context.md`: "No E2E tests for MVP (deferred)"

### Prerequisites Check

- [x] Story `4-10-lob-detail-view-level-2.md` exists with status `ready-for-dev`
- [x] Clear acceptance criteria (6 ACs defined)
- [x] Vitest configured in `vite.config.ts` with jsdom environment
- [x] Test directory `dqs-dashboard/tests/` exists with established patterns (`SummaryPage.test.tsx`)
- [x] Development environment: React/TypeScript/MUI

### Story Summary

> As a data steward, I want a LOB Detail view with stats header and sortable dataset table, so that I can scan all datasets in a LOB and identify which need attention.

### Current State

- `LobDetailPage.tsx`: Placeholder (renders `<Typography>LOB Detail: {lobId}</Typography>` only)
- `useLobDatasets`: Does NOT exist in `queries.ts`
- `DatasetInLob` / `LobDatasetsResponse`: NOT in `types.ts`
- `@mui/x-data-grid`: NOT installed

### Loaded Knowledge Fragments

- `component-tdd.md` — Red-Green-Refactor cycle, provider isolation
- `data-factories.md` — Factory functions for test data
- `test-quality.md` — Test quality standards
- `test-healing-patterns.md` — Resilient test patterns
- `selector-resilience.md` — Frontend selector best practices
- `timing-debugging.md` — Async timing patterns

---

## Step 2: Generation Mode

**Mode Selected**: AI Generation (no browser recording)

**Rationale**: UI is a placeholder — browser recording would show the placeholder, not the target UI. Acceptance criteria are clear and standard (CRUD-like component, navigation, loading/error/empty states). AI generation from documentation is the correct approach.

**Browser automation**: Skipped (`tea_browser_automation: auto` → no running dev server for TDD red phase)

---

## Step 3: Test Strategy

### Acceptance Criteria → Test Scenarios Mapping

| AC | Description | Test Level | Priority | Scenario Count |
|---|---|---|---|---|
| AC1 | Stats header: LOB name, dataset count, avg score, passing rate, last run | Component | P0/P1 | 6 |
| AC2 | Sortable dataset table with all required columns and status chips | Component | P0/P1 | 10 |
| AC3 | Click column header sorts (default: DQS Score ascending — worst first) | Component | P0/P1 | 2 |
| AC4 | Row click navigates to `/datasets/{dataset_id}` | Component | P0 | 2 |
| AC5 | Breadcrumb `Summary > {LOB Name}` (handled by AppLayout, not in scope here) | Excluded | — | 0 |
| AC6 | Loading state: skeleton rows, no DataGrid | Component | P0/P1 | 4 |
| Error | Component-level error with retry | Component | P0/P1 | 4 |
| Empty | Empty state message | Component | P1 | 2 |
| Hook | `useLobDatasets` called with correct lobId + timeRange | Component | P0 | 2 |
| Smoke | Rendering stability in all states | Component | P0 | 3 |

**AC5 exclusion rationale**: Dev Notes explicitly state "Breadcrumb is handled by `AppLayout.tsx` automatically — LOB detail shows `Summary > {lobId}` based on pathname. Do NOT re-implement breadcrumbs in `LobDetailPage.tsx`". AppLayout is tested in `tests/layouts/AppLayout.test.tsx`.

### Test Level Rationale

All tests are **Component tests** (Vitest + React Testing Library):
- No E2E: explicitly deferred for MVP
- No pure API tests: `useLobDatasets` is mocked; API contract tested in `dqs-serve` pytest suite
- Component tests cover all 6 ACs for the React component behavior

### Priority Distribution

| Priority | Count |
|---|---|
| P0 | 21 |
| P1 | 14 |
| P2 | 0 |
| P3 | 0 |
| **Total** | **35** |

### Red Phase Confirmation

All tests use `it.skip()`. Tests will fail because:
1. `useLobDatasets` does not exist in `queries.ts` → mock import error
2. `LobDetailPage` is a placeholder → no stats header, no DataGrid, no skeleton, no error state
3. `@mui/x-data-grid` is not installed → DataGrid import would fail

---

## Step 4: Test Generation

### Subagent A: API Tests

**Decision**: No separate API test file generated.

**Rationale**:
- This is a frontend-only story
- `GET /api/lobs/{lob_id}/datasets` API exists and is tested in `dqs-serve` pytest suite (Story 4.2)
- Frontend component mocks `useLobDatasets` → API contract is validated through types (`DatasetInLob`, `LobDatasetsResponse`)
- Generating Playwright API tests would require E2E setup which is explicitly deferred for MVP

### Subagent B: E2E Tests

**Decision**: Deferred. No E2E test file generated.

**Rationale**: Per `project-context.md` testing rules for dqs-dashboard: "No E2E tests for MVP (deferred)"

### Generated Test Files

| File | Type | Tests | Phase |
|---|---|---|---|
| `dqs-dashboard/tests/pages/LobDetailPage.test.tsx` | Component (Vitest) | 35 | RED (all `it.skip`) |

### TDD Compliance Verification

- [x] All 35 tests use `it.skip()` (not `it()`)
- [x] All tests assert EXPECTED behavior (not placeholder `expect(true).toBe(true)`)
- [x] All tests include priority tags [P0] / [P1]
- [x] All tests include `// THIS TEST WILL FAIL —` comment explaining failure reason
- [x] No tests are expected to pass before implementation

---

## Step 4C: Aggregation

### TDD Red Phase Validation

- [x] All tests use `it.skip()` ✅
- [x] No placeholder assertions ✅
- [x] Tests assert expected behavior ✅
- [x] `expected_to_fail: true` for all test groups ✅

### Files Written to Disk

- [x] `dqs-dashboard/tests/pages/LobDetailPage.test.tsx` — 35 component tests (all `it.skip`)

### Fixture Infrastructure

No new fixtures needed. The existing patterns from `SummaryPage.test.tsx` are followed:
- `makeDatasetInLob()` — factory for `DatasetInLob` test objects
- `makeLobDatasetsResponse()` — factory for `LobDatasetsResponse` test objects
- `renderLobDetailPage(lobId)` — render helper with all providers

### Summary Statistics

```
TDD Phase: RED
Total Tests: 35 (all skipped)
  Component Tests: 35
  API Tests: 0 (frontend only, API tested in dqs-serve)
  E2E Tests: 0 (deferred for MVP)
Execution Mode: SEQUENTIAL
Performance Gain: baseline (no parallel speedup — single agent)
```

---

## Step 5: Validation & Completion

### Checklist Validation

- [x] Prerequisites satisfied (story ready-for-dev, Vitest configured)
- [x] Test file created at correct path per project-context.md: `tests/pages/LobDetailPage.test.tsx`
- [x] Checklist matches all acceptance criteria
- [x] Tests designed to fail before implementation (TDD red phase)
- [x] No orphaned browser sessions (no browser automation used)
- [x] Temp artifacts stored in `_bmad-output/test-artifacts/` not random locations
- [x] 203 existing tests NOT touched (zero regression risk)

### Test Counts by AC

| AC | Tests | Priority |
|---|---|---|
| AC1 (stats header) | 6 | P0(3) + P1(3) |
| AC2 (dataset table) | 10 | P0(5) + P1(5) |
| AC3 (sorting) | 2 | P0(1) + P1(1) |
| AC4 (row click navigation) | 2 | P0(2) |
| AC5 (breadcrumb) | 0 | excluded — AppLayout's responsibility |
| AC6 (loading skeleton) | 4 | P0(3) + P1(1) |
| Error state | 4 | P0(2) + P1(2) |
| Empty state | 2 | P1(2) |
| Hook integration | 2 | P0(2) |
| Smoke tests | 3 | P0(3) |
| **Total** | **35** | |

### Key Risks / Assumptions

1. **`@mui/x-data-grid` must be installed** — `npm install @mui/x-data-grid` required before DataGrid tests can pass (Task 1 in story). Tests with `role="grid"` assertions will fail until installed.
2. **`ResizeObserver` polyfill** — Added to test file. Required for MUI X DataGrid in jsdom. If issues arise, move to `tests/setup.ts`.
3. **AC3 sort assertion** — The `data-rowindex` attribute approach for verifying sort order depends on MUI DataGrid internals. If MUI X DataGrid renders differently in jsdom, the sort assertion (P1) may need adjustment to check text order via `querySelectorAll('[role="row"]')`.
4. **Row click** — `fireEvent.click(row!)` on the nearest `[role="row"]` element. MUI DataGrid's `onRowClick` event may bubble from a different DOM element. If the click test fails during green phase, use `userEvent.click` from `@testing-library/user-event` instead.
5. **Mocked `useTimeRange`** — The mock uses `vi.fn()` from importOriginal pattern to preserve `TimeRangeProvider`. If the `TimeRangeProvider` wraps children correctly, `useTimeRange` mock returns `'7d'` as expected.

### Next Steps (TDD Green Phase)

After implementing the feature:

1. **Run tests to confirm RED phase**: `cd dqs-dashboard && npm test -- --reporter=verbose tests/pages/LobDetailPage.test.tsx`
2. **Implement story** following Tasks 1–8 in `4-10-lob-detail-view-level-2.md`
3. **Remove `it.skip`** from all 35 tests (convert to `it`)
4. **Run tests**: `cd dqs-dashboard && npm test`
5. **Verify 35 new tests PASS + 203 existing tests still pass** (total: 238 tests green)
6. If any tests fail → either fix implementation (bug) or fix test (incorrect assertion)
7. Commit passing tests

### Implementation Guidance

**Files to modify (in order per Dev Notes):**

1. `dqs-dashboard/package.json` — `npm install @mui/x-data-grid` (community tier only)
2. `dqs-dashboard/src/api/types.ts` — Add `DatasetInLob` and `LobDatasetsResponse` interfaces
3. `dqs-dashboard/src/api/queries.ts` — Add `useLobDatasets(lobId, timeRange)` hook
4. `dqs-dashboard/src/pages/LobDetailPage.tsx` — Full implementation (replace placeholder)

**Do NOT modify:** `App.tsx`, `AppLayout.tsx`, `SummaryPage.tsx`, `components/`, `context/`, `theme.ts`, existing test files.

---

## Output Summary

```
ATDD Test Generation Complete (TDD RED PHASE)

TDD Red Phase: Failing Tests Generated

Summary:
- Total Tests: 35 (all with it.skip())
  - Component Tests: 35 (RED)
  - E2E Tests: 0 (deferred for MVP)
- All tests will FAIL until feature implemented

Acceptance Criteria Coverage: AC1, AC2, AC3, AC4, AC6 (AC5 excluded — AppLayout scope)

Performance: SEQUENTIAL (baseline)

Generated Files:
- dqs-dashboard/tests/pages/LobDetailPage.test.tsx (35 tests with it.skip())
- _bmad-output/test-artifacts/atdd-checklist-4-10-lob-detail-view-level-2.md

Next Steps:
1. Implement the feature (Tasks 1-8 in story file)
2. Remove it.skip() from all 35 tests
3. Run tests → verify PASS (green phase)
4. Commit passing tests (total target: 238 tests green)
```
