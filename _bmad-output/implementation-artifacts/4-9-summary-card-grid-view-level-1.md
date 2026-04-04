# Story 4.9: Summary Card Grid View (Level 1)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want a Summary view showing a stats bar and LOB card grid,
so that I can assess overall data quality health at a glance and identify LOBs needing attention.

## Acceptance Criteria

1. **Given** the dashboard loads on `/summary` **When** the Summary page renders **Then** a stats bar shows total datasets, healthy count, degraded count, and critical count
2. **And** a 3-column grid displays DatasetCard components for each LOB
3. **Given** LOB cards are displayed **When** I scan the grid **Then** each card shows LOB name, dataset count, DQS score (color-coded), trend sparkline, and status chip summary (pass/warn/fail counts)
4. **Given** an LOB with a critical DQS score (< 60) **When** the card renders **Then** the score displays in red, providing immediate visual signal
5. **Given** I click an LOB card **When** navigation occurs **Then** I am taken to `/lob/{lob_id}` (LOB Detail view)
6. **Given** the data is loading **When** the page first renders **Then** skeleton cards matching the target layout dimensions are displayed (no layout shift when data arrives)

## Tasks / Subtasks

- [x] Task 1: Update `api/types.ts` to match actual API response shapes (AC: all)
  - [x] Replace `LobSummary` interface with `LobSummaryItem` interface matching `/api/summary` LOB items: `lob_id: string`, `dataset_count: number`, `aggregate_score: number | null`, `healthy_count: number`, `degraded_count: number`, `critical_count: number`, `trend: number[]`
  - [x] Add `SummaryResponse` interface: `total_datasets: number`, `healthy_count: number`, `degraded_count: number`, `critical_count: number`, `lobs: LobSummaryItem[]`
  - [x] Preserve existing `DatasetSummary` and `HealthResponse` interfaces — do not remove them
  - [x] Preserve `export type { TimeRange } from '../context/TimeRangeContext'` re-export

- [x] Task 2: Add `useSummary` query hook to `api/queries.ts` (AC: all)
  - [x] Add `import type { SummaryResponse } from './types'` to imports
  - [x] Implement `export function useSummary()` that calls `GET /api/summary` with no params (summary endpoint has no `time_range` parameter)
  - [x] Use query key `['summary']` — this is intentionally not time-range parameterized (per API design: fixed 7-day trend window)
  - [x] Return type: `useQuery<SummaryResponse>`
  - [x] Do NOT modify existing `useLobs()` or `useDatasets()` — they remain for future LOB detail use

- [x] Task 3: Implement `SummaryPage.tsx` replacing the placeholder (AC: 1, 2, 3, 4, 5, 6)
  - [x] Replace `dqs-dashboard/src/pages/SummaryPage.tsx` entirely — current file is a placeholder
  - [x] Import `useSummary` from `'../api/queries'`
  - [x] Import `DatasetCard` from `'../components'`
  - [x] Import `useNavigate` from `'react-router'` for LOB navigation
  - [x] Import MUI: `Box`, `Grid`, `Typography`, `Skeleton`, `Chip` from `'@mui/material'`
  - [x] Stats bar section: horizontal row showing 4 stat tiles (total, healthy, degraded, critical) using MUI `Box` with flexbox layout
  - [x] Stats bar stat tile structure: label (Typography caption, neutral-500) + value (Typography h2 or h1, bold) + optional Chip for status-colored counts
  - [x] Grid section: MUI `Grid` container with `spacing={3}`, each LOB as `Grid item xs={12} sm={6} md={4}` (3 columns at 1280px+)
  - [x] For each LOB in `data.lobs`: render `DatasetCard` with mapped props (see prop mapping table below)
  - [x] `onClick` handler for each card: `useNavigate()` to `/lobs/${lob.lob_id}` — note the route is `/lobs/:lobId` (plural, per App.tsx)
  - [x] Skeleton loading state: when `isLoading` is true, render 6 MUI `Skeleton` cards matching DatasetCard dimensions (use `variant="rectangular"` with fixed height ~220px)
  - [x] No spinners — skeletons only, per project-context.md anti-patterns
  - [x] Handle `isError` state: show component-level error message with retry (not full-page crash); see Anti-Patterns section

- [x] Task 4: Stats bar visual design (AC: 1)
  - [x] Stats bar uses `bgcolor: 'background.paper'`, `border: 1px solid neutral-200`, `borderRadius: '8px'`, `p: 2`, `mb: 3`
  - [x] Four stat tiles in a horizontal row, evenly spaced using `display: 'flex'`, `gap: 2` or `justifyContent: 'space-around'`
  - [x] "Total Datasets" label: neutral text (no color), value: Typography variant with `neutral-900` color
  - [x] "Healthy" (healthy_count): value colored `success.main` (#2E7D32)
  - [x] "Degraded" (degraded_count): value colored `warning.main` (#ED6C02)
  - [x] "Critical" (critical_count): value colored `error.main` (#D32F2F)
  - [x] No hardcoded hex — use theme palette tokens

- [x] Task 5: Write Vitest tests in `dqs-dashboard/tests/pages/SummaryPage.test.tsx` (AC: 1, 2, 3, 5, 6)
  - [x] Create `dqs-dashboard/tests/pages/` directory (new)
  - [x] Create `dqs-dashboard/tests/pages/SummaryPage.test.tsx`
  - [x] Mock `useSummary` with `vi.mock('../../src/api/queries', ...)` — avoid real API calls in tests
  - [x] Mock `DatasetCard` with `vi.mock('../../src/components', ...)` — avoid rendering Recharts canvas in page tests
  - [x] Use `renderWithProviders` helper: wrap with `QueryClientProvider` + `ThemeProvider` + `MemoryRouter` + `TimeRangeProvider`
  - [x] Test: loading state renders Skeleton elements (not DatasetCard)
  - [x] Test: renders stats bar with correct total_datasets, healthy_count, degraded_count, critical_count
  - [x] Test: renders one DatasetCard mock per LOB in `data.lobs`
  - [x] Test: clicking a DatasetCard mock triggers navigation to `/lobs/{lob_id}`
  - [x] Test: error state renders error message (not DatasetCard or stats)
  - [x] Test: page renders without crashing when `data.lobs` is empty

## Dev Notes

### CRITICAL: `types.ts` Has Wrong `LobSummary` Interface — Must Fix First

The **current `LobSummary` interface in `api/types.ts` does NOT match the actual API response** from `GET /api/summary`. This is the most important bug to fix before writing the page component:

**Current (wrong):**
```typescript
export interface LobSummary {
  lob_id: number      // ← WRONG: API returns string (e.g., "LOB_RETAIL")
  lob_name: string    // ← WRONG: API does NOT return lob_name in summary
  dqs_score: number   // ← WRONG: API uses aggregate_score
  dataset_count: number
  last_run_date: string  // ← WRONG: API does NOT return this field
}
```

**Required (matching actual API):**
```typescript
export interface LobSummaryItem {
  lob_id: string              // ← string, e.g. "LOB_RETAIL"
  dataset_count: number
  aggregate_score: number | null  // can be null if no scores exist
  healthy_count: number
  degraded_count: number
  critical_count: number
  trend: number[]             // array of avg daily DQS scores, oldest-first
}

export interface SummaryResponse {
  total_datasets: number
  healthy_count: number
  degraded_count: number
  critical_count: number
  lobs: LobSummaryItem[]
}
```

The actual API implementation is at `dqs-serve/src/serve/routes/summary.py` — `LobSummaryItem` and `SummaryResponse` Pydantic models are the source of truth.

**Check impact on existing code:** `useLobs()` in `queries.ts` uses `LobSummary` — after renaming to `LobSummaryItem`, ensure `useLobs()` still works (it calls `/api/lobs`, not `/api/summary`). The types for `/api/lobs` are actually `LobDetail` in Python (`lob_id`, `dataset_count`, `aggregate_score`, `healthy_count`, `degraded_count`, `critical_count` — no `trend`). So `useLobs()` can be updated to use `LobSummaryItem` omitting `trend`, or create a separate `LobDetail` type. **Safest approach:** rename old `LobSummary` to `LobDetail` (for `/api/lobs` usage) and add new `LobSummaryItem` (for `/api/summary` usage). Keep both.

### DatasetCard Prop Mapping

`DatasetCard` requires these props (from Story 4.7 implementation):
```typescript
interface DatasetCardProps {
  lobName: string           // ← map from lob.lob_id (display name is the ID for now)
  datasetCount: number      // ← lob.dataset_count
  dqsScore: number          // ← lob.aggregate_score ?? 0
  previousScore?: number    // ← not available from /api/summary; omit
  trendData: number[]       // ← lob.trend
  statusCounts: { pass: number; warn: number; fail: number }
                            // ← { pass: lob.healthy_count, warn: lob.degraded_count, fail: lob.critical_count }
  onClick: () => void       // ← () => navigate(`/lobs/${lob.lob_id}`)
}
```

**Status counts mapping:** The API uses `healthy_count` / `degraded_count` / `critical_count` but `DatasetCard` expects `pass` / `warn` / `fail`. Map explicitly:
- `pass: lob.healthy_count`
- `warn: lob.degraded_count`
- `fail: lob.critical_count`

**LOB name display:** The API returns `lob_id` as a lookup code string (e.g., `"LOB_RETAIL"`). Reference data resolution (human-readable name) is Story 4.5 — for this story, pass `lob.lob_id` as `lobName`. The DatasetCard renders it as-is.

**Null aggregate_score:** Handle `lob.aggregate_score === null` by falling back to `0`. DatasetCard expects `dqsScore: number`.

### Route Path — Critical Detail

**The navigation on card click MUST use `/lobs/${lob.lob_id}` (plural)**:
- App.tsx route: `<Route path="/lobs/:lobId" element={<LobDetailPage />} />`
- Epic 4.9 AC says: "taken to `/lob/{lob_id}` (LOB Detail view)" — note singular `/lob/` in the AC description
- BUT the actual App.tsx route is `/lobs/:lobId` (plural) — **use `/lobs/` to match the existing route**
- Never create a new route or modify App.tsx in this story

### Skeleton Loading Pattern

Per UX spec and project-context.md: no spinners, skeletons only. The skeleton cards must match DatasetCard dimensions to prevent layout shift:

```typescript
// Loading state — 6 skeleton cards matching DatasetCard height
{isLoading && (
  <Grid container spacing={3}>
    {Array.from({ length: 6 }).map((_, i) => (
      <Grid item xs={12} sm={6} md={4} key={i}>
        <Skeleton variant="rectangular" height={220} sx={{ borderRadius: '8px' }} />
      </Grid>
    ))}
  </Grid>
)}
```

Use 6 skeletons as a reasonable default (2 rows of 3 cards). The height 220px must approximate the actual DatasetCard rendered height.

### Error Handling Pattern

Per UX spec (ux-consistency-patterns.md): component-level failures don't crash the page. For the summary API error:

```typescript
{isError && (
  <Box sx={{ textAlign: 'center', py: 4 }}>
    <Typography color="error.main">Failed to load summary data.</Typography>
    <Typography
      component="button"
      onClick={() => refetch()}
      sx={{ mt: 1, color: 'primary.main', cursor: 'pointer', textDecoration: 'underline', background: 'none', border: 'none' }}
    >
      Retry
    </Typography>
  </Box>
)}
```

Use `refetch` from `useSummary()` return value for the retry action.

### Empty State

Per UX spec (ux-consistency-patterns.md): if `data.lobs` is empty (system has no results yet):
```typescript
{data && data.lobs.length === 0 && (
  <Box sx={{ textAlign: 'center', py: 8 }}>
    <Typography color="text.secondary">
      No data quality results yet. Results will appear after the first DQS orchestration run completes.
    </Typography>
  </Box>
)}
```

### File Structure — Exact Paths

```
dqs-dashboard/
  src/
    api/
      types.ts        ← MODIFY: fix LobSummary → LobSummaryItem + add SummaryResponse
      queries.ts      ← MODIFY: add useSummary() hook
    pages/
      SummaryPage.tsx ← REPLACE: full implementation (was placeholder)
    components/       ← do NOT touch (DatasetCard, DqsScoreChip, TrendSparkline)
    context/          ← do NOT touch (TimeRangeContext)
    layouts/          ← do NOT touch (AppLayout)
    App.tsx           ← do NOT touch
    theme.ts          ← do NOT touch
  tests/
    pages/            ← NEW directory
      SummaryPage.test.tsx ← NEW: page tests
    components/       ← do NOT touch existing tests
    layouts/          ← do NOT touch existing tests
    context/          ← do NOT touch existing tests
    setup.ts          ← do NOT touch
vite.config.ts        ← do NOT touch
package.json          ← do NOT touch (all deps already installed)
```

### TypeScript — Strict Mode Requirements

- No `any` types — strict mode enforced
- `useSummary` return type explicitly: `useQuery<SummaryResponse>`
- `SummaryPage` is a `default export function SummaryPage(): JSX.Element`
- When calling `navigate`, type the lob_id as `string` (matches `LobSummaryItem.lob_id: string`)
- `null` coalescing for `aggregate_score`: `lob.aggregate_score ?? 0`
- Map type in status counts: use explicit object literal `{ pass: lob.healthy_count, warn: lob.degraded_count, fail: lob.critical_count }`

### MUI Grid v2 — Already Installed

MUI 7 uses Grid v2 by default. The import is `import { Grid } from '@mui/material'` and Grid v2 uses `size` prop for responsive columns:

```typescript
// MUI 7 Grid v2 syntax (not the old xs/sm/md props):
<Grid container spacing={3}>
  <Grid size={{ xs: 12, sm: 6, md: 4 }}>
    <DatasetCard ... />
  </Grid>
</Grid>
```

**CRITICAL:** MUI 7 Grid v2 uses `size={{ xs: 12, sm: 6, md: 4 }}` NOT the legacy `item xs={12} sm={6} md={4}` syntax. The `item` prop is deprecated in Grid v2. Check `package.json` for `@mui/material` version — it is `^7.3.9`.

### Test Setup — Established Patterns

Follow the exact render helper pattern from Story 4.8:

```typescript
// tests/pages/SummaryPage.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router'
import React from 'react'
import theme from '../../src/theme'
import SummaryPage from '../../src/pages/SummaryPage'
import { TimeRangeProvider } from '../../src/context/TimeRangeContext'

// Mock useSummary to avoid real API calls
vi.mock('../../src/api/queries', () => ({
  useSummary: vi.fn(),
}))

// Mock DatasetCard to avoid Recharts canvas issues
vi.mock('../../src/components', () => ({
  DatasetCard: ({ lobName, onClick }: { lobName: string; onClick: () => void }) => (
    <div data-testid="dataset-card" onClick={onClick}>{lobName}</div>
  ),
}))

const renderSummaryPage = (route = '/summary') => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[route]}>
          <TimeRangeProvider>
            <Routes>
              <Route path="/*" element={<SummaryPage />} />
            </Routes>
          </TimeRangeProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
```

**Note on `useNavigate` in tests:** Wrap with `MemoryRouter` so `useNavigate` works in the test environment.

### Naming Conventions

Per project-context.md for TypeScript/React:
- Component file: `SummaryPage.tsx` (PascalCase)
- Test file: `SummaryPage.test.tsx`
- Local variables: camelCase (`lobsData`, `statsBar`, `handleCardClick`)
- No `any` types

### Anti-Patterns to Avoid

From `_bmad-output/project-context.md` and previous story learnings:
- **NEVER use `useEffect + fetch`** — `useSummary()` via TanStack Query only
- **NEVER use spinning loaders** — `Skeleton` components only per UX spec
- **NEVER hardcode hex colors** — use `theme.palette.success.main`, `theme.palette.error.main`, etc.
- **NEVER use `any` TypeScript type** — strict mode
- **NEVER modify App.tsx** — route is already `/lobs/:lobId`, no changes needed
- **NEVER modify AppLayout.tsx** — layout is already done (Story 4.8)
- **NEVER use MUI Grid legacy `item` prop** — MUI 7 Grid v2 uses `size={{ xs, sm, md }}` syntax
- **NEVER navigate to `/lob/` (singular)** — route is `/lobs/` (plural) per App.tsx
- **NEVER create a full-page crash for API errors** — component-level error with retry link
- **NEVER modify existing test files** — 169 tests currently passing, zero tolerance for regressions

### Architecture Compliance Checklist

- [x] `SummaryPage.tsx` lives in `src/pages/` — per architecture structure spec
- [x] Test lives in `tests/pages/SummaryPage.test.tsx` — per project-context.md testing rules (top-level `tests/` by type)
- [x] No direct API `fetch` calls — `useSummary()` via TanStack Query
- [x] Skeleton loading state — no spinners, per project-context.md and UX spec
- [x] Navigation to `/lobs/${lob_id}` using `useNavigate` — not `window.location.href`
- [x] TypeScript strict mode — no `any`, all props typed
- [x] Uses existing `DatasetCard`, `DqsScoreChip`, `TrendSparkline` via components barrel — no wheel reinvention
- [x] MUI theme tokens for all colors — no hardcoded hex values

### Previous Story Intelligence

**From Story 4.8 (completed):**
- 169 tests currently pass — do NOT break any
- `useNavigate` from `'react-router'` (not `'react-router-dom'`) — package is `react-router@^7.14.0`
- MUI `AppBar` at `position="fixed"` means page content already has correct top offset via `<Toolbar aria-hidden="true" />` spacer in `AppLayout.tsx`. `SummaryPage` renders into `<Box component="main">` inside `AppLayout` — just fill the content area normally.
- `useTimeRange()` hook available from `'../context/TimeRangeContext'` — import if needed for future (not required for this story since `/api/summary` has no time_range param)

**From Story 4.7 (completed):**
- `DatasetCard` is exported from `'../components'` barrel
- `DatasetCard` expects `onClick: () => void` — do NOT pass `(e: MouseEvent) => void`, just `() => void`
- All 4 story cards (DqsScoreChip, TrendSparkline, DatasetCard, AppLayout) are done; `SummaryPage` assembles them

**From Story 4.2 (completed):**
- `GET /api/summary` endpoint exists and works — `lob_id` is a `string` (lookup_code from DB, e.g., `"LOB_RETAIL"`)
- Trend is `list[float]` — mapped to `trendData: number[]` for `DatasetCard`
- `aggregate_score` can be `null` — handle with `?? 0` nullish coalescing
- `GET /api/lobs` endpoint also exists but has no `trend` field — use `/api/summary` for the summary page (it returns the per-LOB trend data needed for sparklines)

### UX Spec Summary for This Story

From `ux-design-specification/component-strategy.md` and `visual-design-foundation.md`:
- **3-column LOB card grid** at standard desktop (1280px+): `md={4}` in MUI Grid 12-column system
- **Stats bar above the grid**: 4 tiles (total, healthy, degraded, critical) with semantic colors
- **No page title** (the fixed AppLayout header has breadcrumbs showing "Summary")
- **Generous spacing**: `spacing={3}` (24px) between cards per visual design foundation
- **White card background**: `bgcolor: 'background.paper'`, no box-shadow
- **Skeleton height**: ~220px to match DatasetCard (LOB name + count + score chip + progress bar + sparkline + status chips)

From `ux-design-specification/user-journey-flows.md`:
- **Journey 1 (Priya):** "If all LOB cards are green, she is done in <30 seconds. No clicks needed." — the stats bar enables this instant assessment
- **Journey 3 (Marcus):** "90-day time range: all sparklines and trends update globally" — `SummaryPage` uses `useSummary()` which has a fixed 7-day trend; the time range toggle does NOT affect this page's API call (the summary endpoint has no time_range param per AC spec)

### References

- Epic 4 Story 4.9 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md#Story 4.9`
- UX Component Strategy (DatasetCard anatomy, Grid): `_bmad-output/planning-artifacts/ux-design-specification/component-strategy.md`
- UX Visual Design Foundation (colors, spacing, 3-col grid): `_bmad-output/planning-artifacts/ux-design-specification/visual-design-foundation.md`
- UX Consistency Patterns (loading skeletons, empty states, error handling): `_bmad-output/planning-artifacts/ux-design-specification/ux-consistency-patterns.md`
- UX User Journey Flows (Journey 1 — Priya's 30-second triage): `_bmad-output/planning-artifacts/ux-design-specification/user-journey-flows.md`
- Architecture — dqs-dashboard structure: `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`
- Architecture — Data flow (summary consumption): `_bmad-output/planning-artifacts/architecture.md#Data Flow`
- Project Context rules: `_bmad-output/project-context.md`
- Actual API response model (source of truth for types): `dqs-serve/src/serve/routes/summary.py` (LobSummaryItem, SummaryResponse)
- Existing types.ts (to update): `dqs-dashboard/src/api/types.ts`
- Existing queries.ts (to update): `dqs-dashboard/src/api/queries.ts`
- Existing SummaryPage.tsx (to replace): `dqs-dashboard/src/pages/SummaryPage.tsx`
- DatasetCard component (import only, do NOT modify): `dqs-dashboard/src/components/DatasetCard.tsx`
- Components barrel (import from here): `dqs-dashboard/src/components/index.ts`
- App.tsx (routes — do NOT modify): `dqs-dashboard/src/App.tsx`
- Theme file (getDqsColor, palette tokens): `dqs-dashboard/src/theme.ts`
- TimeRangeContext (useTimeRange hook): `dqs-dashboard/src/context/TimeRangeContext.tsx`
- Test setup file: `dqs-dashboard/tests/setup.ts`
- Story 4.8 completion notes (169 tests, useNavigate pattern): `_bmad-output/implementation-artifacts/4-8-app-layout-with-fixed-header-routing-react-query.md`
- Story 4.7 completion notes (DatasetCard props, export pattern): `_bmad-output/implementation-artifacts/4-7-datasetcard-lob-card-component.md`
- Story 4.2 completion notes (API response structure): `_bmad-output/implementation-artifacts/4-2-fastapi-summary-lob-api-endpoints.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Encountered ATDD test data conflict: `makeSummaryResponse` default `degraded_count: 4` conflicted with `critical_count: 4` override, causing `getByText('4')` to find two elements. Fixed by changing default `degraded_count` from 4 to 3 in the test factory function.
- MUI 7 Grid v2 `size={{ xs, sm, md }}` syntax used throughout (not legacy `item` prop).
- `LobSummary` renamed to `LobDetail` for `/api/lobs` usage; new `LobSummaryItem` added for `/api/summary`; both preserved.

### Completion Notes List

- Task 1: Updated `api/types.ts` — replaced wrong `LobSummary` interface with correct `LobDetail` (for /api/lobs) and new `LobSummaryItem` (for /api/summary) + `SummaryResponse`. Preserved `DatasetSummary`, `HealthResponse`, and `TimeRange` re-export.
- Task 2: Added `useSummary()` hook to `api/queries.ts` — calls `GET /api/summary`, query key `['summary']` (no time range param), return type `useQuery<SummaryResponse>`. Updated `useLobs()` to use `LobDetail[]` type.
- Task 3+4: Replaced `SummaryPage.tsx` placeholder with full implementation — stats bar (4 tiles, MUI theme tokens, flexbox), skeleton loading (6 cards at 220px height), error state with retry, empty state message, 3-column LOB card grid with DatasetCard prop mapping. Uses `useNavigate` to `/lobs/${lob.lob_id}` (plural route). MUI Grid v2 `size` prop syntax.
- Task 5: Activated all 34 tests in `tests/pages/SummaryPage.test.tsx` (removed all `it.skip`). Fixed `makeSummaryResponse` default `degraded_count` (4→3) to resolve value collision in critical_count test. All 203 tests pass (169 pre-existing + 34 new).

### File List

- `dqs-dashboard/src/api/types.ts` — modified: replaced `LobSummary` with `LobDetail` + `LobSummaryItem` + `SummaryResponse`
- `dqs-dashboard/src/api/queries.ts` — modified: updated `useLobs` type to `LobDetail[]`, added `useSummary()` hook
- `dqs-dashboard/src/pages/SummaryPage.tsx` — replaced: full implementation replacing placeholder
- `dqs-dashboard/tests/pages/SummaryPage.test.tsx` — modified: removed all `it.skip`, fixed test data default conflict

### Review Findings

- [x] [Review][Patch] Dead code: `navigatedTo` and `NavigationCapture` declared but never used in navigation test [tests/pages/SummaryPage.test.tsx:482] — fixed
- [x] [Review][Patch] Stale `eslint-disable` directive for `no-explicit-any` at line 151 with no matching issue [tests/pages/SummaryPage.test.tsx:151] — fixed
- [x] [Review][Patch] Stale RED-phase "THIS TEST WILL FAIL" comments throughout test file — fixed
- [x] [Review][Patch] Stats bar container missing accessible `aria-label` and `role="region"` [src/pages/SummaryPage.tsx:76] — fixed
- [x] [Review][Patch] Retry `<button>` missing `type="button"` attribute [src/pages/SummaryPage.tsx:52] — fixed
- [x] [Review][Defer] `LobDetail` / `LobSummaryItem` structural duplication (could use `extends`) [src/api/types.ts] — deferred, pre-existing design choice
- [x] [Review][Defer] `DatasetSummary.lob_id` typed as `number` inconsistent with string lob_id convention [src/api/types.ts] — deferred, pre-existing, not in story scope

### Change Log

| Date | Change |
|------|--------|
| 2026-04-03 | Story 4.9 created — Summary Card Grid View (Level 1) story ready for dev. |
| 2026-04-03 | Story 4.9 implemented — all 5 tasks complete; 203 tests pass (0 skipped, 0 regressions). |
| 2026-04-03 | Story 4.9 code review complete — 5 patches applied, 2 deferred; 203 tests pass. Status: done. |
