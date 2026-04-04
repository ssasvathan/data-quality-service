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
  - _bmad-output/implementation-artifacts/4-12-dataset-detail-view-level-3.md
  - _bmad-output/project-context.md
  - dqs-dashboard/vite.config.ts
  - dqs-dashboard/src/api/types.ts
  - dqs-dashboard/src/api/queries.ts
  - dqs-dashboard/src/pages/DatasetDetailPage.tsx
  - dqs-dashboard/src/context/TimeRangeContext.tsx
  - dqs-dashboard/tests/pages/LobDetailPage.test.tsx
  - dqs-dashboard/tests/setup.ts
  - .claude/skills/bmad-testarch-atdd/resources/knowledge/component-tdd.md
---

# ATDD Checklist: Story 4.12 — Dataset Detail View (Level 3)

## TDD Red Phase (Current)

All 46 tests generated and confirmed SKIPPED (red phase).

- Component Tests: 46 tests (all skipped with `it.skip()`)
- E2E Tests: 0 (deferred per project-context.md — no E2E for MVP)

## Stack Detection

- **Detected Stack:** `frontend`
- **Framework:** Vitest + React Testing Library + MUI ThemeProvider
- **Generation Mode:** AI generation (standard component/navigation scenarios)

## Preflight Results

- Story status: `ready-for-dev`
- Acceptance criteria: 7 ACs — all clear and testable
- Test framework: Vitest configured in `vite.config.ts`
- Existing test patterns: loaded from `LobDetailPage.test.tsx`
- Prerequisites: PASS

## Test Strategy Summary

| AC | Test Level | Priority | Test Count |
|---|---|---|---|
| AC1: Left panel dataset list sorted by DQS asc | Component | P0 | 5 |
| AC2: Active dataset highlight | Component | P0 | 2 |
| AC3: Right panel header + 2-col grid | Component | P0 | 7 |
| AC4: Check results list | Component | P0 | 9 |
| AC5: Score breakdown card | Component | P1 | 3 |
| AC6: Left panel click -> URL update | Component | P0 | 2 |
| AC7: Breadcrumb 3-level | Component | P1 | 1 |
| Loading state | Component | P0 | 4 |
| Error state | Component | P0 | 3 |
| Hook integration | Component | P0 | 5 |
| Rendering stability | Component | P0 | 3 |

**Total: 44 acceptance-criteria tests + 3 stability tests = 46 tests**

## Acceptance Criteria Coverage

- [x] AC1: Left panel shows scrollable dataset list sorted by DQS Score ascending — **5 tests**
- [x] AC2: Active dataset highlighted with primary-light bg + left border — **2 tests**
- [x] AC3: Right panel shows dataset header + 2-column grid — **7 tests**
- [x] AC4: Check results list renders status chip, check name, score; FAIL/WARN emphasized — **9 tests**
- [x] AC5: Score breakdown card with LinearProgress per check category — **3 tests**
- [x] AC6: Left panel click updates URL + right panel (no full navigation) — **2 tests**
- [x] AC7: Breadcrumb shows Summary > {LOB} > {Dataset} (3-level) — **1 test**

## TDD Red Phase Compliance

- All tests use `it.skip()` (Vitest equivalent of `test.skip()`)
- All tests assert EXPECTED behavior (not placeholder assertions)
- All tests will fail when `it.skip()` is removed because `DatasetDetailPage` is a 10-line placeholder and `useDatasetMetrics`/`useDatasetTrend` do not exist in `queries.ts`

## Why Tests Will Fail (Red Phase)

1. `useDatasetMetrics` is mocked in the test file but does not exist in `src/api/queries.ts` — the mock factory pattern used in `beforeEach` will access `undefined` until the hook is added
2. `useDatasetTrend` has the same problem — not yet in `queries.ts`
3. `DatasetDetailPage` renders only a placeholder `<div>` with `Typography` — all assertions for left panel, right panel, check results, score breakdown, and navigation will fail
4. Types `CheckMetric`, `CheckDetailMetric`, `CheckResult`, `DatasetMetricsResponse`, `TrendPoint`, `DatasetTrendResponse` are not yet in `api/types.ts`

## Generated Test Files

| File | Status | Tests |
|---|---|---|
| `dqs-dashboard/tests/pages/DatasetDetailPage.test.tsx` | CREATED (RED) | 46 skipped |

## Fixture Infrastructure

All fixtures defined inline in the test file (no separate fixture files needed):

- `makeDatasetDetail()` — factory for `DatasetDetail` response
- `makeDatasetInLob()` — factory for left panel list items
- `makeLobDatasetsResponse()` — factory for `LobDatasetsResponse`
- `makeCheckResult()` — factory for individual `CheckResult`
- `makeMetricsResponse()` — factory for `DatasetMetricsResponse`
- `makeTrendResponse()` — factory for `DatasetTrendResponse`
- `renderDatasetDetail()` — render helper wrapping all providers

## Test Run Results (Confirmation)

```
Test Files  1 skipped (1)
     Tests  46 skipped (46)
  Start at  01:43:00
  Duration  1.70s
```

Existing test suite: **269 tests still passing** (no regressions introduced).

## Next Steps (TDD Green Phase)

After implementing the story:

1. Implement `CheckMetric`, `CheckDetailMetric`, `CheckResult`, `DatasetMetricsResponse`, `TrendPoint`, `DatasetTrendResponse` in `src/api/types.ts`
2. Implement `useDatasetMetrics` and `useDatasetTrend` in `src/api/queries.ts`
3. Replace `DatasetDetailPage.tsx` with full Master-Detail implementation
4. Update `AppLayout.tsx` breadcrumb to read `lobId` search param
5. Update `LobDetailPage.tsx` to pass `?lobId=...` on navigation
6. Remove `.skip` from all `it.skip()` in `DatasetDetailPage.test.tsx`
7. Run: `cd dqs-dashboard && npx vitest run tests/pages/DatasetDetailPage.test.tsx`
8. Verify all 46 tests PASS (green phase)
9. Run full suite: `npx vitest run` — verify 315 tests pass (269 existing + 46 new)

## Implementation Guidance

### API Hooks to Implement

- `useDatasetMetrics(datasetId: string | undefined)` — `GET /api/datasets/{id}/metrics`
- `useDatasetTrend(datasetId: string | undefined, timeRange: TimeRange)` — `GET /api/datasets/{id}/trend?time_range={tr}`

### UI Components to Implement

- Master-Detail split layout (`Box display="flex"`)
- Left panel: `List` with `ListItemButton` items sorted by DQS Score ascending
- Right panel: header (name + StatusChip + metadata) + 2-column Grid
- Check Results section: table/flex rows with status chip + formatted check type + score
- Score Breakdown card: `Card` with `LinearProgress` per check type
- Loading state: 8 left-panel skeleton rows + right-panel skeleton grid
- Error state: `Typography color="error.main"` + retry `Button`
