---
stepsCompleted:
  [
    'step-01-preflight-and-context',
    'step-02-generation-mode',
    'step-03-test-strategy',
    'step-04-generate-tests',
    'step-04c-aggregate',
    'step-05-validate-and-complete',
  ]
lastStep: 'step-05-validate-and-complete'
lastSaved: '2026-04-03'
story: '4-14-loading-empty-error-states'
tdd_phase: 'RED'
inputDocuments:
  - _bmad-output/implementation-artifacts/4-14-loading-empty-error-states.md
  - _bmad-output/project-context.md
  - _bmad/tea/config.yaml
  - dqs-dashboard/tests/pages/SummaryPage.test.tsx
  - dqs-dashboard/tests/pages/LobDetailPage.test.tsx
  - dqs-dashboard/tests/pages/DatasetDetailPage.test.tsx
  - dqs-dashboard/tests/layouts/AppLayout.test.tsx
  - dqs-dashboard/src/pages/SummaryPage.tsx
  - dqs-dashboard/src/layouts/AppLayout.tsx
  - dqs-dashboard/src/api/types.ts
---

# ATDD Checklist: Story 4.14 — Loading, Empty & Error States

## TDD Red Phase (Current)

29 failing acceptance tests generated — all marked with `it.skip()` (TDD red phase).

## Test Counts

| File | New Tests (skipped) | AC Coverage |
|---|---|---|
| `tests/pages/SummaryPage.test.tsx` | 6 tests | AC2 (isFetching opacity), AC8 (network error) |
| `tests/pages/LobDetailPage.test.tsx` | 4 tests | AC2 (isFetching opacity on sparklines) |
| `tests/pages/DatasetDetailPage.test.tsx` | 16 tests | AC7 (trendError), AC7 (metricsError), AC2 (isFetching) |
| `tests/layouts/AppLayout.test.tsx` | 9 tests | AC5 (LastUpdatedIndicator), AC6 (RunFailedBanner) |
| **Total** | **35 tests** | AC2, AC5, AC6, AC7, AC8 |

> Note: AC1 (loading skeletons), AC3 (empty LOB), AC4 (empty summary) are already covered by existing passing tests in the respective files. Only the NEW behaviors from Story 4.14 require new tests.

## Acceptance Criteria Coverage

| AC | Description | Test Status | Test File |
|---|---|---|---|
| AC1 | Skeleton screens on load — no spinners | ALREADY PASSING | SummaryPage, LobDetailPage, DatasetDetailPage |
| AC2 | isFetching opacity 0.5 on sparklines | RED (6 skipped) | SummaryPage, LobDetailPage, DatasetDetailPage |
| AC3 | Empty LOB: "No datasets monitored in {LOB Name}" | ALREADY PASSING | LobDetailPage |
| AC4 | Empty summary: full-page empty state | ALREADY PASSING | SummaryPage |
| AC5 | Last updated indicator: amber when >24h, gray when <24h | RED (4 skipped) | AppLayout |
| AC6 | Run failed banner: shows on run_failed=true, dismissible | RED (5 skipped) | AppLayout |
| AC7 | trendError: "Failed to load trend data" + retry isolated | RED (9 skipped) | DatasetDetailPage |
| AC7 | metricsError: "Failed to load check results" + retry isolated | RED (7 skipped) | DatasetDetailPage |
| AC8 | API unreachable: full-page "Unable to connect to DQS" | RED (3 skipped) | SummaryPage |

## Pre-existing Test Count

- Before Story 4.14: **344 tests passing** (after Story 4.13)
- After red phase additions: **345 passing + 29 skipped = 374 total** (1 additional test became passing from DatasetDetailPage prior story work)

## Implementation Requirements (for green phase)

### SummaryPage.tsx — 2 changes needed
1. Destructure `isFetching` from `useSummary()`, wrap each DatasetCard Grid item in `<Box sx={{ opacity: isFetching ? 0.5 : 1, transition: 'opacity 0.2s' }}>` (AC2)
2. In error state: check `error instanceof TypeError && error.message.includes('fetch')` — if true, render full-page "Unable to connect to DQS..." message with retry button (AC8)

### LobDetailPage.tsx — 1 change needed
1. Destructure `isFetching` from `useLobDatasets()`, apply opacity Box wrapper in TrendSparkline renderCell (AC2)

### DatasetDetailPage.tsx — 3 changes needed
1. Destructure `isFetching` from `useDatasetTrend()`, wrap TrendSparkline in opacity Box (AC2)
2. Destructure `isError: trendError, refetch: trendRefetch` from `useDatasetTrend()`, render "Failed to load trend data." + Retry link when `trendError` is true — replacing/alongside the sparkline area (AC7)
3. Destructure `isError: metricsError, refetch: metricsRefetch` from `useDatasetMetrics()`, render "Failed to load check results." + Retry link when `metricsError` is true — replacing/alongside the check results area (AC7)

### AppLayout.tsx — 2 new internal components needed
1. Add `LastUpdatedIndicator` function component — reads `useSummary()` data for `last_run_at`, renders "Last updated: {relative time}" with `color: 'warning.main'` when >24h, `color: 'text.secondary'` when <24h; skip render if `last_run_at` is null/absent (AC5)
2. Add `RunFailedBanner` function component — reads `useSummary()` data for `run_failed`, renders yellow Box below AppBar when `run_failed === true`; text "Latest run failed at {time}. Showing results from {previous run date}."; dismissible via local `useState<boolean>` (AC6)

### api/types.ts — SummaryResponse extension needed
Add to `SummaryResponse` interface:
```typescript
last_run_at: string | null   // ISO 8601 timestamp of most recent run
run_failed: boolean          // true if most recent run failed entirely
```
(Required before AppLayout tests can pass)

## New Test Files Modified

- `dqs-dashboard/tests/pages/SummaryPage.test.tsx` — 6 new `it.skip()` tests appended
- `dqs-dashboard/tests/pages/LobDetailPage.test.tsx` — 4 new `it.skip()` tests appended
- `dqs-dashboard/tests/pages/DatasetDetailPage.test.tsx` — 16 new `it.skip()` tests appended (+ 1 new `it.skip()` for AC2)
- `dqs-dashboard/tests/layouts/AppLayout.test.tsx` — 9 new `it.skip()` tests appended (+ import of `useSummary`)

## Next Steps (TDD Green Phase)

After implementing the feature (per bmad-dev-story):

1. Remove `it.skip` (change to `it`) from all new test cases in all 4 files
2. Run tests: `cd dqs-dashboard && npx vitest run`
3. Verify 374 tests PASS (green phase)
4. If any tests fail: fix the implementation (not the tests)
5. Commit passing tests

## TDD Red Phase Validation

- All 29 new tests use `it.skip()` — CI will not fail
- All tests assert EXPECTED behavior (no placeholder assertions)
- Tests are placed in the correct existing files per story Dev Notes
- Existing 345 tests continue to pass
- Test patterns follow established project conventions (vi.mocked, ThemeProvider wrap, etc.)
