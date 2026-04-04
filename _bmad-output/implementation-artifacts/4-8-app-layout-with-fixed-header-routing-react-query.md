# Story 4.8: App Layout with Fixed Header, Routing & React Query

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user**,
I want a persistent fixed header with breadcrumbs, time range toggle, and search bar, with deep-linkable routes,
so that I always have navigation context and can bookmark or share any view.

## Acceptance Criteria

1. **Given** the dashboard is loaded **When** any view is displayed **Then** a fixed header is visible with breadcrumb navigation, time range toggle (7d/30d/90d), and search bar
2. **Given** React Router 7 is configured **When** I navigate to `/summary`, `/lob/:id`, or `/dataset/:id` **Then** the correct page component renders with breadcrumbs reflecting the current path
3. **Given** I change the time range from 7d to 90d **When** the toggle updates **Then** React Query invalidates all cached queries and components refetch with the new time range
4. **Given** I use browser back/forward buttons **When** navigating between views **Then** routing works correctly and breadcrumbs update to match
5. **And** `<header>`, `<main>`, and `<nav>` landmark regions are present for screen reader navigation
6. **And** a hidden "Skip to main content" link is visible on Tab focus

## Tasks / Subtasks

- [x] Task 1: Implement `TimeRangeContext` for global time range state (AC: 3)
  - [x] Create `dqs-dashboard/src/context/TimeRangeContext.tsx` exporting `TimeRangeProvider`, `useTimeRange` hook, and `TimeRange` type (`'7d' | '30d' | '90d'`)
  - [x] Default time range: `'7d'`
  - [x] `useTimeRange()` returns `{ timeRange: TimeRange; setTimeRange: (range: TimeRange) => void }`
  - [x] Export `TimeRangeContext` barrel from `dqs-dashboard/src/context/index.ts` (create file)

- [x] Task 2: Replace the skeleton `AppLayout.tsx` with full fixed header implementation (AC: 1, 2, 4, 5, 6)
  - [x] Replace `dqs-dashboard/src/layouts/AppLayout.tsx` entirely — the existing file is a placeholder scaffold only
  - [x] Use MUI `AppBar` with `position="fixed"` (NOT `"static"`) so it remains visible on scroll
  - [x] Add compensating top padding on `<main>` for the fixed header height (standard MUI AppBar height: 64px desktop → use `theme.mixins.toolbar`)
  - [x] Add semantic landmark: `AppBar` renders as `<header>` element (MUI AppBar default), wrap routes in `<Box component="main">`
  - [x] Add `<nav>` landmark wrapping the `MUI Breadcrumbs` component
  - [x] Add hidden "Skip to main content" link: `<a href="#main-content">` visible only on Tab focus via `sx` `clip`/`position` technique
  - [x] Add `id="main-content"` to the `<main>` Box element for skip-link target
  - [x] Header layout (left-to-right): `<nav>` with Breadcrumbs | spacer (`flexGrow: 1`) | TimeRange toggle | SearchBar placeholder
  - [x] Breadcrumbs: derive from current route using `useLocation()` and `useParams()` from `react-router`
  - [x] Breadcrumb logic: `/` or `/summary` → `[Summary]` (plain text, not clickable); `/lob/:lobId` → `[Summary link] > [LOB name]` (LOB name as plain text); `/dataset/:datasetId` → `[Summary link] > [LOB link] > [dataset name]`
  - [x] For LOB and Dataset breadcrumbs, display the raw ID as breadcrumb label (resolved names come in Story 4.9/4.12 when page components fetch data)
  - [x] TimeRange toggle: MUI `ToggleButtonGroup` with three `ToggleButton` options ("7d", "30d", "90d"); calls `setTimeRange` from `useTimeRange()` on change
  - [x] SearchBar placeholder: `TextField` input with placeholder text "Search datasets... (Ctrl+K)" — full autocomplete implementation in Story 4.13
  - [x] Wrap `AppLayout` internals with `TimeRangeProvider` so all descendant components can access time range

- [x] Task 3: Update `App.tsx` to add `/summary` route and wire React Query invalidation (AC: 2, 3)
  - [x] Add `<Route path="/summary" element={<SummaryPage />} />` — the existing `/` catch-all route stays as redirect/fallback
  - [x] The `QueryClient` in `App.tsx` is already initialized; pass it through React Query context (already done via `QueryClientProvider`)
  - [x] Time range change invalidation: in `TimeRangeContext.tsx`, call `queryClient.invalidateQueries()` on time range change — use `useQueryClient()` hook. **This requires access to QueryClient from within the provider context**. Solution: `TimeRangeProvider` must be rendered _inside_ `QueryClientProvider` in `App.tsx` (it already is per the component tree).
  - [x] Verify route order: `/summary` route must appear before any wildcard routes

- [x] Task 4: Add `useTimeRange` to `queries.ts` so query keys include time range (AC: 3)
  - [x] Update existing query functions in `dqs-dashboard/src/api/queries.ts` to accept `timeRange` parameter
  - [x] Update `queryKey` arrays to include `timeRange` (e.g., `['lobs', timeRange]`) so time range changes invalidate correctly
  - [x] Update `apiFetch` calls to pass `?time_range={timeRange}` query parameter where applicable (LOB datasets, trends)
  - [x] Export `TimeRange` type re-export from `api/types.ts` for convenience (or import directly from context)

- [x] Task 5: Write Vitest tests in `dqs-dashboard/tests/` (AC: 1, 2, 3, 5, 6)
  - [x] Create `dqs-dashboard/tests/layouts/AppLayout.test.tsx`
  - [x] Use `renderWithRouter` helper that wraps component with `MemoryRouter` + `ThemeProvider` + `QueryClientProvider` + `TimeRangeProvider`
  - [x] Test: fixed header is rendered (AppBar present in DOM)
  - [x] Test: `<header>` landmark element is present
  - [x] Test: `<main>` landmark element with `id="main-content"` is present
  - [x] Test: `<nav>` landmark element is present
  - [x] Test: skip link `href="#main-content"` is in DOM
  - [x] Test: time range toggle shows "7d", "30d", "90d" buttons
  - [x] Test: clicking "30d" toggle updates selected time range
  - [x] Test: search input placeholder contains "Search datasets"
  - [x] Create `dqs-dashboard/tests/context/TimeRangeContext.test.tsx`
  - [x] Test: default time range is "7d"
  - [x] Test: `setTimeRange` updates the time range value
  - [x] Test: `useTimeRange` throws when used outside provider

## Dev Notes

### Critical: What Already Exists — Do NOT Recreate

The following are **already implemented** and must be preserved:

- **`dqs-dashboard/src/App.tsx`** — already has `QueryClientProvider`, `ThemeProvider`, `CssBaseline`, `BrowserRouter`, `AppLayout`, and routes for `/`, `/lobs/:lobId`, `/datasets/:datasetId`. Story 4.8 REPLACES the skeleton `AppLayout.tsx` and adds minor App.tsx changes only.
- **`dqs-dashboard/src/layouts/AppLayout.tsx`** — exists but is a placeholder with `position="static"` AppBar and no breadcrumbs/toggle/search. REPLACE this file entirely.
- **`dqs-dashboard/src/pages/SummaryPage.tsx`**, **`LobDetailPage.tsx`**, **`DatasetDetailPage.tsx`** — all exist as placeholders. Do NOT modify them in this story.
- **`dqs-dashboard/src/api/queries.ts`** — exists with `useLobs()` and `useDatasets()`. Add time range parameter; do not remove existing functions.
- **`dqs-dashboard/src/api/types.ts`** — exists with `LobSummary`, `DatasetSummary`. Add `TimeRange` type here or in context.
- **`dqs-dashboard/src/theme.ts`** — fully implemented (Story 4.1). Import only, never modify.
- **`dqs-dashboard/src/components/`** — `DqsScoreChip`, `TrendSparkline`, `DatasetCard` all exist. Do NOT import them in AppLayout — they're not needed here.
- **132 existing tests all pass** — do NOT modify any existing test files.

### Architecture Compliance: dqs-dashboard Structure

Per `architecture.md`, the expected structure includes:

```
dqs-dashboard/src/
  layouts/
    AppLayout.tsx      ← REPLACE placeholder with full implementation
  pages/
    SummaryPage.tsx    ← do NOT touch (placeholder)
    LobDetailPage.tsx  ← do NOT touch (placeholder)
    DatasetDetailPage.tsx ← do NOT touch (placeholder)
  api/
    client.ts          ← do NOT touch
    queries.ts         ← add timeRange param
    types.ts           ← add TimeRange type
  context/             ← NEW directory (not in architecture diagram but needed)
    TimeRangeContext.tsx
    index.ts
```

The `context/` directory is not listed in the architecture diagram but is needed to support the global time range state. This is an acceptable addition — the architecture doc lists the minimum set, not an exhaustive list.

### React Query Invalidation Pattern — Critical Detail

When time range changes, ALL cached queries must be invalidated so every component refetches with the new range. The correct pattern:

```typescript
// In TimeRangeContext.tsx
import { useQueryClient } from '@tanstack/react-query'

function TimeRangeProvider({ children }: { children: ReactNode }) {
  const [timeRange, setTimeRangeState] = useState<TimeRange>('7d')
  const queryClient = useQueryClient()

  const setTimeRange = (range: TimeRange) => {
    setTimeRangeState(range)
    queryClient.invalidateQueries()  // invalidate ALL queries
  }

  return (
    <TimeRangeContext.Provider value={{ timeRange, setTimeRange }}>
      {children}
    </TimeRangeContext.Provider>
  )
}
```

**CRITICAL:** `TimeRangeProvider` MUST be rendered inside `QueryClientProvider`. In `App.tsx`, the current order is:
```tsx
<QueryClientProvider client={queryClient}>
  <ThemeProvider theme={theme}>
    <CssBaseline />
    <BrowserRouter>
      <AppLayout>  // ← TimeRangeProvider goes here or inside AppLayout
```

Wrapping with `TimeRangeProvider` inside `AppLayout` works if `AppLayout` is inside `QueryClientProvider`. It is. ✓

### Fixed Header — Exact MUI Implementation

```typescript
// AppBar must be position="fixed" per UX spec ("fixed header")
<AppBar position="fixed" sx={{ bgcolor: 'background.paper', borderBottom: `1px solid ${theme.palette.neutral[200]}`, boxShadow: 'none' }}>
  <Toolbar>
    {/* skip link — visible on tab focus only */}
    <Box
      component="a"
      href="#main-content"
      sx={{
        position: 'absolute',
        left: '-9999px',
        '&:focus': { left: 0, top: 0, zIndex: 9999, p: 1, bgcolor: 'primary.main', color: 'white' }
      }}
    >
      Skip to main content
    </Box>

    {/* Breadcrumbs nav */}
    <Box component="nav" aria-label="breadcrumb" sx={{ flexGrow: 1 }}>
      <Breadcrumbs>...</Breadcrumbs>
    </Box>

    {/* Time range toggle */}
    <ToggleButtonGroup value={timeRange} exclusive onChange={(_, val) => val && setTimeRange(val)} size="small">
      <ToggleButton value="7d">7d</ToggleButton>
      <ToggleButton value="30d">30d</ToggleButton>
      <ToggleButton value="90d">90d</ToggleButton>
    </ToggleButtonGroup>

    {/* Search placeholder */}
    <TextField size="small" placeholder="Search datasets... (Ctrl+K)" sx={{ ml: 2, width: 240 }} />
  </Toolbar>
</AppBar>

{/* Main content — offset by toolbar height */}
<Box component="main" id="main-content" sx={{ flexGrow: 1, p: 3, mt: (theme) => `${theme.mixins.toolbar.minHeight}px` }}>
  {children}
</Box>
```

**Header color:** Per UX spec, the fixed header uses `bgcolor: 'background.paper'` (white) with a `1px neutral-200` bottom border. NOT the default MUI blue AppBar — DQS uses a white/neutral header per the flat design philosophy.

**Toolbar offset:** MUI `theme.mixins.toolbar` provides the correct minimum height for different viewport sizes. Use this to add top margin/padding to `<main>` — never hardcode `64px`.

### Breadcrumb Derivation Logic

```typescript
import { useLocation, useParams, Link as RouterLink } from 'react-router'
import { Breadcrumbs, Link, Typography } from '@mui/material'

function AppBreadcrumbs() {
  const location = useLocation()
  const { lobId, datasetId } = useParams<{ lobId?: string; datasetId?: string }>()

  // Summary (root or /summary) — plain text, no link
  if (location.pathname === '/' || location.pathname === '/summary') {
    return (
      <Breadcrumbs>
        <Typography color="text.primary">Summary</Typography>
      </Breadcrumbs>
    )
  }

  // LOB detail — Summary link > LOB ID as text
  if (lobId) {
    return (
      <Breadcrumbs>
        <Link component={RouterLink} to="/summary" underline="hover">Summary</Link>
        <Typography color="text.primary">{lobId}</Typography>
      </Breadcrumbs>
    )
  }

  // Dataset detail — Summary link > LOB link (if known) > Dataset ID as text
  if (datasetId) {
    return (
      <Breadcrumbs>
        <Link component={RouterLink} to="/summary" underline="hover">Summary</Link>
        <Typography color="text.primary">{datasetId}</Typography>
      </Breadcrumbs>
    )
  }

  return null
}
```

**Note:** For dataset detail, the LOB breadcrumb segment requires knowing the LOB for the current dataset. At this story stage, show only Summary > datasetId. The full 3-level breadcrumb (`Summary > LOB > Dataset`) is completed in Story 4.12 when DatasetDetailPage fetches LOB context.

### App.tsx Changes — Minimal

The `App.tsx` file is nearly complete. Only two changes needed:

1. Add `/summary` route (in addition to existing `/`):
```tsx
<Route path="/summary" element={<SummaryPage />} />
```

2. Wrap `AppLayout` (or its contents) with `TimeRangeProvider` — inside `QueryClientProvider`:
```tsx
<QueryClientProvider client={queryClient}>
  <ThemeProvider theme={theme}>
    <CssBaseline />
    <BrowserRouter>
      <TimeRangeProvider>   {/* ← add this */}
        <AppLayout>
          <Routes>
            <Route path="/" element={<SummaryPage />} />
            <Route path="/summary" element={<SummaryPage />} />
            <Route path="/lobs/:lobId" element={<LobDetailPage />} />
            <Route path="/datasets/:datasetId" element={<DatasetDetailPage />} />
          </Routes>
        </AppLayout>
      </TimeRangeProvider>  {/* ← add this */}
    </BrowserRouter>
  </ThemeProvider>
</QueryClientProvider>
```

### queries.ts — Time Range Integration

```typescript
// Updated useLobs — adds timeRange to queryKey for cache invalidation
export function useLobs(timeRange: TimeRange = '7d') {
  return useQuery<LobSummary[]>({
    queryKey: ['lobs', timeRange],
    queryFn: () => apiFetch<LobSummary[]>(`/lobs?time_range=${timeRange}`),
  })
}

// Updated useDatasets — passes time_range to API
export function useDatasets(lobId?: number, timeRange: TimeRange = '7d') {
  return useQuery<DatasetSummary[]>({
    queryKey: ['datasets', lobId, timeRange],
    queryFn: () => apiFetch<DatasetSummary[]>(
      lobId ? `/lobs/${lobId}/datasets?time_range=${timeRange}` : `/datasets?time_range=${timeRange}`
    ),
  })
}
```

Page components (Stories 4.9, 4.10, 4.12) will call `useTimeRange()` and pass the value into these hooks. The updated signature is backward compatible — `timeRange` defaults to `'7d'`.

### File Structure — Exact Paths

```
dqs-dashboard/
  src/
    App.tsx                     ← MODIFY: add /summary route, add TimeRangeProvider wrapper
    layouts/
      AppLayout.tsx             ← REPLACE: full fixed header implementation
    context/                    ← NEW directory
      TimeRangeContext.tsx      ← NEW: TimeRange type, provider, useTimeRange hook
      index.ts                  ← NEW: barrel export
    api/
      queries.ts                ← MODIFY: add timeRange parameter to query functions
      types.ts                  ← MODIFY: add TimeRange type export
    pages/                      ← do NOT touch any page files
    components/                 ← do NOT touch any component files
    theme.ts                    ← do NOT touch
  tests/
    layouts/                    ← NEW directory
      AppLayout.test.tsx        ← NEW: layout tests
    context/                    ← NEW directory
      TimeRangeContext.test.tsx ← NEW: context tests
    components/                 ← do NOT touch existing tests
    setup.ts                    ← do NOT touch
vite.config.ts                  ← do NOT touch
package.json                    ← do NOT touch (all deps already installed)
```

### TypeScript — Strict Mode Requirements

- `TimeRange` type: `export type TimeRange = '7d' | '30d' | '90d'`
- `useTimeRange()` return type: explicitly typed `{ timeRange: TimeRange; setTimeRange: (range: TimeRange) => void }`
- `AppLayoutProps`: `interface AppLayoutProps { children: ReactNode }`
- No `any` types anywhere — strict mode
- All event handlers typed: `ToggleButton onChange`: `(_: React.MouseEvent<HTMLElement>, value: TimeRange | null) => void`
- Guard against `null` value from `ToggleButtonGroup` (MUI can pass null if user clicks already-selected): `if (value) setTimeRange(value)`

### MUI Versions — Already Installed

Per `package.json`, all required dependencies are already installed:
- `@mui/material: ^7.3.9` — includes `AppBar`, `Toolbar`, `Breadcrumbs`, `ToggleButtonGroup`, `ToggleButton`, `TextField`, `Link`, `Box`, `Typography`
- `react-router: ^7.14.0` — `useLocation`, `useParams`, `Link` (as `RouterLink`)
- `@tanstack/react-query: ^5.96.1` — `useQueryClient`, `QueryClient`, `QueryClientProvider`
- All testing dependencies (`@testing-library/react`, `jsdom`, `vitest`) already installed

Do NOT add any new packages to `package.json`.

### Test Patterns — Established in Stories 4.6/4.7

Follow the exact same test setup pattern:

```typescript
// tests/layouts/AppLayout.test.tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router'
import React from 'react'
import theme from '../../src/theme'
import AppLayout from '../../src/layouts/AppLayout'
import { TimeRangeProvider } from '../../src/context/TimeRangeContext'

const renderAppLayout = (route = '/') => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[route]}>
          <TimeRangeProvider>
            <Routes>
              <Route path="/*" element={<AppLayout><div>content</div></AppLayout>} />
            </Routes>
          </TimeRangeProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
```

**Note on `MemoryRouter` vs `BrowserRouter`**: Use `MemoryRouter` from `react-router` in tests (not `BrowserRouter`) — it doesn't require a browser environment and supports `initialEntries` for route testing.

### Anti-Patterns to Avoid

Per `_bmad-output/project-context.md` and story context:

- **NEVER use `position="static"` for the AppBar** — must be `position="fixed"` per UX spec (header persists on scroll)
- **NEVER hardcode `64px` for toolbar height** — use `theme.mixins.toolbar.minHeight` 
- **NEVER use `useEffect + fetch`** — all data fetching uses TanStack Query (applies to future page updates too)
- **NEVER use spinning loaders** — skeleton screens only (relevant for Stories 4.9+ that use this layout)
- **NEVER modify existing test files** — 132 tests currently passing, zero tolerance for regressions
- **NEVER add routing logic to component files** — routing is handled in `App.tsx` and breadcrumb derivation in `AppLayout.tsx`
- **NEVER use `any` TypeScript types** — strict mode enforced
- **NEVER hardcode color hex values** — use `theme.palette.neutral[200]`, `theme.palette.primary.main`, etc.
- **NEVER omit the skip link** — it is required by AC 6 and WCAG 2.1 AA accessibility requirement
- **NEVER render `TimeRangeProvider` outside `QueryClientProvider`** — `useQueryClient()` requires it in context
- **NEVER break the existing route `/lobs/:lobId`** — note the route uses `lobs` (plural) not `lob`

### Architecture Compliance Checklist

- [ ] `AppLayout.tsx` lives in `src/layouts/` — per architecture structure spec
- [ ] `TimeRangeContext.tsx` lives in `src/context/` — logical grouping, consistent with architecture pattern
- [ ] Tests live in `tests/layouts/` and `tests/context/` — per project-context.md testing rules (top-level `tests/` by type)
- [ ] No API calls in `AppLayout` — layout accepts children, reads time range from context
- [ ] No raw CSS files — all styling via MUI `sx` props and theme tokens
- [ ] Header is accessible: `<header>`, `<main id="main-content">`, `<nav>`, skip link
- [ ] Time range change invalidates ALL React Query cache entries
- [ ] TypeScript strict mode — no `any`, all props/returns typed

### Previous Story Intelligence (4.7)

From Story 4.7 completion notes (critical for this story):

1. **`useTheme()` hook** for accessing theme inside components: `import { useTheme } from '@mui/material/styles'`
2. **`getDqsColor` import path** from components: `import { getDqsColor } from '../theme'` (theme is at `src/theme.ts`, layouts is at `src/layouts/`, so the relative path from `AppLayout.tsx` to `theme.ts` is `'../theme'`)
3. **All 132 tests pass** after Story 4.7 — do NOT modify existing test files
4. **No `as any` casts** — TypeScript strict mode, module augmentation in `theme.ts` handles custom variants
5. **Barrel import pattern**: downstream components import from `'../components'`; AppLayout does not need to import from components barrel at all for this story

From Story 4.7, the test pattern for mocking sub-components:
```typescript
// When testing AppLayout, mock AppBreadcrumbs if extracting it as subcomponent
// Or test the full AppLayout with MemoryRouter for route-dependent breadcrumb tests
```

### UX Spec — Fixed Header Design

From `ux-design-specification/visual-design-foundation.md`:
- **Fixed header:** "Top bar with breadcrumbs + search is fixed/sticky. Content scrolls beneath. User always has navigation context and search access."
- **No sidebar navigation:** "Breadcrumbs ARE the navigation. No hamburger menu, no nav drawer."
- **Header background:** white (`background.paper`), not MUI default blue

From `ux-design-specification/ux-consistency-patterns.md`:
- **Breadcrumb navigation:** Click any breadcrumb segment to jump to that level. Current page is plain text (not clickable).
- **URL reflects state:** Deep-linkable routes. `/summary`, `/lobs/{lob_id}`, `/datasets/{dataset_id}`
- **Time range indicator** in fixed header: "Last updated: 5:42 AM ET" — amber when stale >12h. This is deferred to Story 4.14 (Loading/Error States).

From `ux-design-specification/component-strategy.md`:
- `ToggleButtonGroup` is the MUI component for time range selector (7d/30d/90d)
- `TextField` + `Autocomplete` for search — Autocomplete implementation is Story 4.13; this story adds the `TextField` placeholder only

### References

- Epic 4 Story 4.8 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md#Story 4.8`
- Architecture — dqs-dashboard structure: `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`
- Architecture — Frontend Architecture decisions: `_bmad-output/planning-artifacts/architecture.md#Frontend Architecture`
- UX — Visual Design Foundation (fixed header, spacing): `_bmad-output/planning-artifacts/ux-design-specification/visual-design-foundation.md`
- UX — Component Strategy (ToggleButtonGroup, Breadcrumbs, TextField): `_bmad-output/planning-artifacts/ux-design-specification/component-strategy.md`
- UX — Navigation Patterns: `_bmad-output/planning-artifacts/ux-design-specification/ux-consistency-patterns.md#Navigation Patterns`
- Project Context rules: `_bmad-output/project-context.md`
- Existing `App.tsx` (route structure, QueryClient setup): `dqs-dashboard/src/App.tsx`
- Existing skeleton `AppLayout.tsx` (to be replaced): `dqs-dashboard/src/layouts/AppLayout.tsx`
- Existing `queries.ts` (to be updated): `dqs-dashboard/src/api/queries.ts`
- Existing `types.ts` (to be updated): `dqs-dashboard/src/api/types.ts`
- Theme file (neutral palette, primary, getDqsColor): `dqs-dashboard/src/theme.ts`
- Vite config (already correct, do NOT modify): `dqs-dashboard/vite.config.ts`
- Test setup file: `dqs-dashboard/tests/setup.ts`
- Package.json (all deps installed — no new packages needed): `dqs-dashboard/package.json`
- Story 4.7 completion notes (test setup, import paths, 132 existing tests): `_bmad-output/implementation-artifacts/4-7-datasetcard-lob-card-component.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation completed without blocking issues.

### Completion Notes List

- Implemented `TimeRangeContext.tsx` with `TimeRange` type, `TimeRangeProvider`, and `useTimeRange` hook. Provider uses `useQueryClient()` to call `invalidateQueries()` on every time range change, requiring it to live inside `QueryClientProvider`.
- Created `context/index.ts` barrel exporting `TimeRangeProvider`, `useTimeRange`, and `TimeRange`.
- Replaced skeleton `AppLayout.tsx` with full fixed header using MUI `AppBar` `position="fixed"`, `Breadcrumbs` (renders as `<nav aria-label="breadcrumb">`), `ToggleButtonGroup` for time range, and `TextField` search placeholder.
- Skip link (`<a href="#main-content">`) implemented using CSS `position: absolute; left: -9999px` with focus-reveal via `sx` — satisfies WCAG AC6.
- Main content `<Box component="main" id="main-content">` offset using `theme.mixins.toolbar.minHeight` — no hardcoded px values.
- Breadcrumb derivation uses both `useParams()` for typed routes and pathname regex fallback for wildcard routes (`/*` in test setup).
- Updated `App.tsx`: added `/summary` route, wrapped `AppLayout` with `TimeRangeProvider` inside `QueryClientProvider`.
- Updated `queries.ts`: `useLobs(timeRange)` and `useDatasets(lobId, timeRange)` with time range in query keys and API URLs. Backward-compatible defaults (`'7d'`).
- Updated `types.ts`: re-exports `TimeRange` from context module.
- All 169 tests pass (37 new + 132 existing) — 0 skipped, 0 failures.

### File List

- `dqs-dashboard/src/context/TimeRangeContext.tsx` (new)
- `dqs-dashboard/src/context/index.ts` (new)
- `dqs-dashboard/src/layouts/AppLayout.tsx` (replaced)
- `dqs-dashboard/src/App.tsx` (modified)
- `dqs-dashboard/src/api/queries.ts` (modified)
- `dqs-dashboard/src/api/types.ts` (modified)
- `dqs-dashboard/tests/layouts/AppLayout.test.tsx` (it.skip → it, all tests enabled)
- `dqs-dashboard/tests/context/TimeRangeContext.test.tsx` (it.skip → it, all tests enabled)

### Review Findings

- [x] [Review][Patch] Responsive toolbar height offset — AppLayout.tsx used a type cast to read only the base breakpoint minHeight (56px), causing incorrect offset at desktop viewport (64px). Fixed by replacing with a sibling `<Toolbar aria-hidden="true" />` spacer per MUI recommended pattern. [AppLayout.tsx:186]
- [x] [Review][Patch] Stale RED PHASE comments in test files — 26+13 occurrences of "THIS TEST WILL FAIL" and "RED PHASE" header comments left over from ATDD red phase. Updated to GREEN PHASE and removed inline stale comments. [AppLayout.test.tsx, TimeRangeContext.test.tsx]

### Change Log

| Date | Change |
|------|--------|
| 2026-04-03 | Story 4.8 created — App Layout with Fixed Header, Routing & React Query story ready for dev. |
| 2026-04-03 | Implemented TimeRangeContext, replaced AppLayout with fixed header, updated App.tsx routes, updated queries.ts with timeRange params. All 169 tests pass. |
| 2026-04-04 | Code review complete. Fixed responsive toolbar offset (Medium) and stale ATDD comments (Low). All 169 tests still pass. Status: done. |
