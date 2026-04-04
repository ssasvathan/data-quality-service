# Story 4.13: Global Dataset Search

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **downstream consumer**,
I want a global search autocomplete in the fixed header that shows DQS scores inline in results,
so that I can check dataset health in seconds without browsing the hierarchy.

## Acceptance Criteria

1. **Given** the search bar in the fixed header **When** I type 2+ characters (e.g., "ue90") **Then** an autocomplete dropdown appears after 300ms debounce showing matching datasets with DqsScoreChip + dataset name (mono) + LOB name (gray)
2. **Given** search results are displayed **When** I see green DQS scores for my datasets in the dropdown **Then** I can close the search with Escape — health check complete without navigating
3. **Given** I click a search result (or arrow-key + Enter) **When** the result is selected **Then** I navigate directly to that dataset's Detail view
4. **Given** the keyboard shortcut `Ctrl+K` **When** pressed on any view **Then** the search bar is focused and ready for input
5. **Given** a search with no matches **When** the dropdown renders **Then** it shows "No datasets matching '{query}'" in gray text
6. **And** search is global across all LOBs, max 10 results, prefix matches first then substring

## Tasks / Subtasks

- [x] Task 1: Add `SearchResult` and `SearchResponse` types to `api/types.ts` (AC: 1, 3)
  - [x] Add `SearchResult` interface:
    ```typescript
    export interface SearchResult {
      dataset_id: number
      dataset_name: string
      lob_id: string | null
      dqs_score: number | null
      check_status: 'PASS' | 'WARN' | 'FAIL' | null
    }
    ```
  - [x] Add `SearchResponse` interface:
    ```typescript
    export interface SearchResponse {
      results: SearchResult[]
    }
    ```
  - [x] Preserve ALL existing interfaces — do NOT remove any existing type
  - [x] These types mirror the Python Pydantic models in `dqs-serve/src/serve/routes/search.py`

- [x] Task 2: Add `useSearch` query hook to `api/queries.ts` (AC: 1, 6)
  - [x] Add import: `import type { SearchResponse } from './types'`
  - [x] Add `useSearch(query: string)` hook:
    ```typescript
    export function useSearch(query: string) {
      return useQuery<SearchResponse>({
        queryKey: ['search', query],
        queryFn: () => apiFetch<SearchResponse>(`/search?q=${encodeURIComponent(query)}`),
        enabled: query.length >= 2,
        staleTime: 30_000,
      })
    }
    ```
  - [x] `enabled: query.length >= 2` — AC1 requires 2+ characters before triggering
  - [x] `staleTime: 30_000` — search results are fresh for 30s (avoids redundant refetch on same query)
  - [x] Do NOT modify existing hooks: `useLobs`, `useDatasets`, `useSummary`, `useLobDatasets`, `useDatasetDetail`, `useDatasetMetrics`, `useDatasetTrend`

- [x] Task 3: Replace search `TextField` in `AppLayout.tsx` with full `Autocomplete` implementation (AC: 1, 2, 3, 4, 5)
  - [x] Remove existing `TextField` placeholder (the one with placeholder "Search datasets... (Ctrl+K)")
  - [x] Add imports to `AppLayout.tsx`:
    - From `'@mui/material'`: add `Autocomplete`, `InputAdornment` (already has `TextField`, `Box`, etc.)
    - From `'@mui/icons-material'`: `Search as SearchIcon` (already installed)
    - From `'react'`: add `useState`, `useRef`, `useEffect`, `useDeferredValue` (add to existing React import)
    - From `'react-router'`: add `useNavigate` (add to existing react-router import)
    - From `'../api/queries'`: `useSearch`
    - From `'../api/types'`: `SearchResult`
    - From `'../components'`: `DqsScoreChip`

  - [x] Implement `GlobalSearch` as a separate named function component within `AppLayout.tsx`

  - [x] Debounce via `useDeferredValue` — React 18+ deferred value defers expensive state updates by one render cycle. `searchQuery` guards: only passes query >= 2 chars to `useSearch`.

  - [x] Implement Ctrl+K keyboard shortcut using `useEffect` with `window.addEventListener('keydown', ...)`

  - [x] MUI Autocomplete configuration: controlled `open` + `inputValue`, `filterOptions={(x) => x}` for server-side ordering, `noOptionsText` for empty results (without `freeSolo` to allow MUI to render `noOptionsText` properly)

  - [x] Place `<GlobalSearch />` in `AppLayout`'s `Toolbar` where the `TextField` placeholder currently is (after the time range toggle)
  - [x] Escape key closes the dropdown — MUI Autocomplete handles Escape natively (fires `onClose`)
  - [x] Arrow-key navigation + Enter selection — MUI Autocomplete handles this natively via `onChange`

- [x] Task 4: Write Vitest tests in `dqs-dashboard/tests/layouts/AppLayout.test.tsx` (AC: 1, 2, 3, 4, 5)
  - [x] Add mock for `../../src/api/queries` to the existing test file — mock `useSearch` alongside existing hook mocks
  - [x] Add mock for `../../src/components` to render `DqsScoreChip` as a simple span (avoid Recharts issues)
  - [x] DO NOT break existing AppLayout tests (there are 25 existing tests that must remain passing)
  - [x] New tests in `GlobalSearch.test.tsx`: all 19 tests covering AC1-AC6
  - [x] Mock `useNavigate` from `'react-router'` to capture navigation calls
  - [x] Mock `useSearch` with `vi.mocked()` for per-test control of return values

## Dev Notes

### What Exists vs What Needs Building

**Already done (do NOT recreate):**
- Backend: `GET /api/search?q=...` endpoint in `dqs-serve/src/serve/routes/search.py` — returns `SearchResponse` with up to 10 results ordered by prefix then substring
- AppLayout: Fixed header with breadcrumbs, time range toggle, and a `TextField` placeholder at the search position
- All other query hooks in `api/queries.ts`
- `DqsScoreChip`, `TrendSparkline`, `DatasetInfoPanel` components — import as-is
- `@mui/icons-material` is installed (added in Story 4.11)

**This story builds:**
- `SearchResult` / `SearchResponse` TypeScript types in `api/types.ts`
- `useSearch(query: string)` hook in `api/queries.ts`
- Replace `TextField` placeholder in `AppLayout.tsx` with full `Autocomplete`-powered search
- Ctrl+K keyboard shortcut
- Tests in `AppLayout.test.tsx`

### MUI Autocomplete — Critical Configuration

Use **MUI `Autocomplete`** (not a custom dropdown). Key props:

| Prop | Value | Why |
|------|-------|-----|
| `freeSolo` | `true` | Allows free text input without forcing selection |
| `filterOptions={(x) => x}` | pass-through | Server-side filtering via API — do NOT double-filter |
| `getOptionLabel` | returns `option.dataset_name` | Required by Autocomplete for string display |
| `inputValue` / `onInputChange` | controlled | Enables debounce and open/close control |
| `open` / `onOpen` / `onClose` | controlled | Precise control: only open when query >= 2 chars |

**Controlled vs uncontrolled:** Use fully **controlled** mode (`inputValue` + `open`) to enforce the 2-char minimum and clear state on selection.

**`filterOptions={(x) => x}`** is critical — without it, MUI will client-side filter results by `getOptionLabel`, which would break the API-driven ordering (prefix before substring). The API already returns the correctly ordered set; trust it.

**`renderOption` key extraction:** MUI v7 passes `key` inside `props`. Destructure it out: `const { key, ...optionProps } = props` to avoid React key prop spread warning.

### Debounce Implementation

The AC requires 300ms debounce. Two approaches, in order of preference:

**Option A: `useDeferredValue` (preferred for React 19):**
```typescript
const deferredQuery = useDeferredValue(inputValue)
const { data } = useSearch(deferredQuery)  // fetches deferred value
```
`useDeferredValue` tells React to defer the expensive (API fetch) work until the browser is idle, naturally creating a ~100-300ms delay on each keystroke. The hook's `enabled: query.length >= 2` prevents fetches under 2 chars.

**Option B: Explicit `useEffect` debounce (fallback):**
```typescript
const [debouncedQuery, setDebouncedQuery] = useState('')
useEffect(() => {
  if (inputValue.length < 2) { setDebouncedQuery(''); return }
  const timer = setTimeout(() => setDebouncedQuery(inputValue), 300)
  return () => clearTimeout(timer)
}, [inputValue])
const { data } = useSearch(debouncedQuery)
```

Start with Option A. If tests show timing issues in jsdom, fall back to Option B with an explicit `fake timer` in tests.

### Search API — Key Facts

The `GET /api/search?q=...` endpoint (Story 4.4):
- Returns `{ "results": [...] }` — always an object with `results` array (never 404, never null)
- Results ordered: prefix matches first, then substring, then alphabetical within each group
- Max 10 results (SQL LIMIT 10)
- Minimum query: 1 char at API level, but AC1 says 2+ chars in the UI — enforce 2-char minimum in `useSearch` via `enabled`
- `dataset_id` is the `dq_run.id` integer used in `/datasets/:datasetId` route

**Response shape (matches `SearchResult` interface):**
```json
{
  "results": [
    {
      "dataset_id": 7,
      "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
      "lob_id": "LOB_RETAIL",
      "dqs_score": 98.50,
      "check_status": "PASS"
    }
  ]
}
```

Navigate to: `/datasets/${result.dataset_id}` on selection.

### AppLayout.tsx — Current Structure

The `TextField` placeholder is rendered in `AppLayout`'s main function (not in `AppBreadcrumbs`). Current code (around line 185-189):
```tsx
{/* Search bar placeholder (AC1) — full implementation in Story 4.13 */}
<TextField
  size="small"
  placeholder="Search datasets... (Ctrl+K)"
  sx={{ ml: 2, width: 240 }}
/>
```

Replace this entire block with `<GlobalSearch />`. The `GlobalSearch` component reads from its own internal state and hooks — it doesn't need props from `AppLayout`.

**Import additions to AppLayout.tsx:**
```typescript
// Add to existing @mui/material import:
Autocomplete, InputAdornment

// New MUI icons import:
import SearchIcon from '@mui/icons-material/Search'

// Add to existing 'react' import:
useState, useRef, useEffect, useDeferredValue

// Add to existing 'react-router' import:
useNavigate

// New API imports:
import { useSearch } from '../api/queries'
import type { SearchResult } from '../api/types'

// New component import:
import { DqsScoreChip } from '../components'
```

### File Structure — Files to Create/Modify

```
dqs-dashboard/
  src/
    api/
      types.ts           ← MODIFY: add SearchResult, SearchResponse interfaces
      queries.ts         ← MODIFY: add useSearch hook
    layouts/
      AppLayout.tsx      ← MODIFY: replace TextField with GlobalSearch Autocomplete
  tests/
    layouts/
      AppLayout.test.tsx ← MODIFY: add useSearch mock + new search tests
```

**Do NOT create** new files. All changes go into existing files.

**Do NOT touch:**
- `App.tsx` — no new routes needed for search (search navigates to existing `/datasets/:datasetId`)
- `src/components/*` — DqsScoreChip/TrendSparkline/DatasetInfoPanel are used as-is
- `src/pages/*` — no page changes needed
- `src/context/TimeRangeContext.tsx` — no changes
- Any existing test files outside `AppLayout.test.tsx`

### Autocomplete Option Key Pattern (MUI v7)

MUI 7 Autocomplete passes `key` inside the render props object. You MUST destructure it:

```tsx
renderOption={(props, option) => {
  const { key, ...optionProps } = props  // Extract key separately
  return (
    <Box component="li" key={key} {...optionProps}>
      {/* ... */}
    </Box>
  )
}}
```

Spreading `key` via `{...props}` triggers a React warning. Always extract first.

### TypeScript Strict Mode

- `SearchResult` interface: `lob_id: string | null` (nullable per API) and `dqs_score: number | null`
- Pass to `DqsScoreChip`: `score={option.dqs_score ?? undefined}` (null → undefined, chip shows "—")
- `getOptionLabel`: handle `typeof option === 'string'` branch since `freeSolo` is true
- `onChange` handler: guard `typeof value !== 'string'` before accessing `value.dataset_id`
- No `any` types anywhere

### Keyboard Accessibility

Per UX spec (ux-consistency-patterns.md — Search Patterns):
- `Ctrl+K` → focus search input (AC4)
- Arrow keys → navigate options in dropdown (MUI Autocomplete built-in)
- Enter → select focused option (MUI Autocomplete built-in, triggers `onChange`)
- Escape → close dropdown (MUI Autocomplete built-in, triggers `onClose`)

The MUI `Autocomplete` handles all keyboard interactions except `Ctrl+K` (focus shortcut), which needs a `window.addEventListener('keydown', ...)` in `useEffect`.

Also support `Cmd+K` on Mac: `(e.ctrlKey || e.metaKey) && e.key === 'k'`.

### Testing — Mock Strategy

The `AppLayout.test.tsx` currently mocks nothing (no API calls). After this story, it needs:

```typescript
// At top of AppLayout.test.tsx — after existing imports:
import { vi } from 'vitest'

vi.mock('../../src/api/queries', () => ({
  useSearch: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
  useLobs: vi.fn(),
  useDatasets: vi.fn(),
  useSummary: vi.fn(),
  useLobDatasets: vi.fn(),
  useDatasetDetail: vi.fn(),
  useDatasetMetrics: vi.fn(),
  useDatasetTrend: vi.fn(),
}))

vi.mock('../../src/components', () => ({
  DqsScoreChip: ({ score }: { score?: number }) => (
    <span data-testid="dqs-score-chip">{score ?? '—'}</span>
  ),
  TrendSparkline: vi.fn(),
  DatasetCard: vi.fn(),
  DatasetInfoPanel: vi.fn(),
}))

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>()
  return {
    ...actual,
    useNavigate: vi.fn(() => vi.fn()),
  }
})
```

**Existing tests must pass:** The 25 existing `AppLayout.test.tsx` tests check for:
- Header / main / nav landmark presence
- Skip link
- Breadcrumbs at various routes
- Time range toggle interaction
- Search placeholder text: `getByPlaceholderText(/Search datasets/i)` — this must still match the new `Autocomplete`'s `TextField` placeholder

The `TextField` inside MUI `Autocomplete` renders an `<input>` with the same placeholder — the existing placeholder test (`getByPlaceholderText(/Search datasets/i)`) will still pass.

**Key concern:** Adding mocks for `useSearch` (which is now called inside `GlobalSearch` inside `AppLayout`) — without the mock, the component will throw an import error or make real fetch calls. The mock must return `{ data: undefined }` by default so existing tests see an empty search and don't crash.

### Existing Test Count

After Story 4.12: **315 tests pass**. This story adds new tests in `AppLayout.test.tsx`. All 315 must continue to pass.

### UX Design — Result Item Layout

From `ux-design-specification/ux-consistency-patterns.md` (Search Patterns):
```
Each result: [DqsScoreChip] [dataset name (mono)] [LOB name (gray)]
```

Option render layout (flex row):
```
| DqsScoreChip (sm, no trend) | dataset_name (mono, flex-grow, noWrap) | lob_id (caption, gray, noWrap) |
```

- `DqsScoreChip size="sm" showTrend={false}` — compact score chip, no trend arrow in search results
- `Typography variant="mono"` — dataset names are technical path strings (e.g., `lob=retail/src_sys_nm=alpha/dataset=sales_daily`)
- `Typography variant="caption" color="text.secondary"` — LOB ID in muted gray

**No result text:** `"No datasets matching '{query}'"` — use MUI Autocomplete's `noOptionsText` prop:
```tsx
noOptionsText={
  inputValue.length >= 2
    ? `No datasets matching '${inputValue}'`
    : 'Type to search'
}
```
The `noOptionsText` is shown when `options` is empty and the dropdown is open. Since `open` is only set to `true` when `inputValue.length >= 2`, the 'Type to search' fallback only appears if the dropdown somehow opens before 2 chars (edge case).

### Previous Story Intelligence (4-12)

From Story 4.12 completion:
- `@mui/icons-material` installed (Story 4.11) — safe to use `import SearchIcon from '@mui/icons-material/Search'`
- Import from `'react-router'` NOT `'react-router-dom'` (consistent anti-pattern note)
- `import React from 'react'` is present in AppLayout.tsx (pre-existing pattern — do not remove even though unnecessary in React 19)
- AppLayout tests currently have NO component mocks. This story introduces `useSearch` inside `GlobalSearch` inside `AppLayout` — test file MUST be updated to mock `useSearch` or all existing tests will break
- MUI v7: Autocomplete `renderOption` requires key destructuring: `const { key, ...optionProps } = props`
- No `any` types — strict TypeScript throughout
- `variant="mono"` is a custom typography variant defined in `dqs-dashboard/src/theme.ts` — safe to use (established in Story 4.1)

From Story 4.8 (AppLayout):
- `AppBreadcrumbs` is a separate function component inside `AppLayout.tsx` — `GlobalSearch` should follow the same pattern (named function inside the same file, not exported)
- `useNavigate` is NOT currently imported in `AppLayout.tsx` — must be added to the existing react-router import line
- Width 240px for search placeholder (Story 4.8) — increase to 280px for the functional autocomplete (wider for readable results)

### Anti-Patterns to Avoid

- **NEVER use `useEffect + fetch`** — use `useSearch` hook (TanStack Query)
- **NEVER use spinning loaders** — the Autocomplete loading state is handled by MUI (brief flash acceptable since debounced)
- **NEVER use `any` type** — strict TypeScript
- **NEVER import from `'react-router-dom'`** — package is `react-router@^7`
- **NEVER double-filter** — `filterOptions={(x) => x}` to pass through server-side ordered results
- **NEVER spread `key` prop** — destructure `{ key, ...optionProps }` from `renderOption` props
- **NEVER hardcode `dataset_id` route** — use `navigate(`/datasets/${value.dataset_id}`)` with the number from the API
- **NEVER query the raw search endpoint with less than 2 chars** — enforce via `enabled: query.length >= 2` in `useSearch`

### Architecture Compliance

- Search component lives in `AppLayout.tsx` (not a separate file) — it's a header-level concern, not a standalone reusable component
- `useSearch` hook lives in `api/queries.ts` — consistent with all other data hooks
- Types live in `api/types.ts` — consistent with all other API types
- No new top-level directories or files needed
- All API calls go through `apiFetch` in `api/client.ts` — proxy to `/api` prefix handled by Vite config

### References

- Epic 4 Story 4.13 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md#Story 4.13`
- UX Search Patterns: `_bmad-output/planning-artifacts/ux-design-specification/ux-consistency-patterns.md#Search Patterns`
- UX User Journey Flow 2 (Alex): `_bmad-output/planning-artifacts/ux-design-specification/user-journey-flows.md#Journey 2`
- UX Component Strategy (Phase 4 Search): `_bmad-output/planning-artifacts/ux-design-specification/component-strategy.md#Component Implementation Strategy`
- Story 4.4 (search API endpoint, done): `_bmad-output/implementation-artifacts/4-4-dataset-search-api-endpoint.md`
- Story 4.8 (AppLayout, done): `_bmad-output/implementation-artifacts/4-8-app-layout-with-fixed-header-routing-react-query.md`
- Story 4.12 (previous story, done): `_bmad-output/implementation-artifacts/4-12-dataset-detail-view-level-3.md`
- AppLayout source (file to modify): `dqs-dashboard/src/layouts/AppLayout.tsx`
- API types (file to modify): `dqs-dashboard/src/api/types.ts`
- API queries (file to modify): `dqs-dashboard/src/api/queries.ts`
- AppLayout tests (file to modify): `dqs-dashboard/tests/layouts/AppLayout.test.tsx`
- DqsScoreChip props: `dqs-dashboard/src/components/DqsScoreChip.tsx`
- API client (apiFetch): `dqs-dashboard/src/api/client.ts`
- Vite config (proxy /api): `dqs-dashboard/vite.config.ts`
- Project Context rules: `_bmad-output/project-context.md`
- Search route impl: `dqs-serve/src/serve/routes/search.py`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

Key implementation decisions:
1. Removed `freeSolo=true` from MUI Autocomplete — MUI v7 does not render `noOptionsText` when `freeSolo=true` (source: Autocomplete.js line 726: `!freeSolo` guard). Using `freeSolo=false` enables `noOptionsText` for the AC5 no-results case.
2. Used `useDeferredValue` + `searchQuery` guard (`deferredQuery.length >= 2 ? deferredQuery : ''`) to avoid passing single-char queries to `useSearch`. This satisfies the test assertion that `useSearch` is not called with non-empty strings < 2 chars.
3. Added `reason === 'reset'` guard in `onInputChange` to prevent MUI's post-selection reset from overwriting the cleared `inputValue` state.
4. Fixed ATDD tests: replaced `require()` calls (broken in ESM) with top-level `vi.mocked()` pattern; imported `useSearch` at module level for GlobalSearch.test.tsx.

### Completion Notes List

- Task 1: Added `SearchResult` and `SearchResponse` interfaces to `api/types.ts` — all existing interfaces preserved.
- Task 2: Added `useSearch(query: string)` hook to `api/queries.ts` — `enabled: query.length >= 2`, `staleTime: 30_000`, uses `encodeURIComponent`.
- Task 3: Replaced `TextField` placeholder in `AppLayout.tsx` with `GlobalSearch` function component using MUI Autocomplete (without `freeSolo`). Ctrl+K/Cmd+K shortcut via `window.addEventListener`. `useDeferredValue` for debounce.
- Task 4: Added mocks for `useSearch`, `DqsScoreChip`, and `useNavigate` to `AppLayout.test.tsx`; removed all `it.skip()` from `GlobalSearch.test.tsx` and `useSearch.test.ts`.
- All 344 tests pass (315 existing + 19 GlobalSearch + 10 useSearch), 0 skipped.

### File List

dqs-dashboard/src/api/types.ts
dqs-dashboard/src/api/queries.ts
dqs-dashboard/src/layouts/AppLayout.tsx
dqs-dashboard/tests/layouts/AppLayout.test.tsx
dqs-dashboard/tests/layouts/GlobalSearch.test.tsx
dqs-dashboard/tests/api/useSearch.test.ts

### Review Findings

- [x] [Review][Patch] Duplicate local `SearchResult` interface in GlobalSearch.test.tsx [tests/layouts/GlobalSearch.test.tsx:85] — **Fixed**: removed local interface definition, imported `SearchResult` from `api/types.ts`
- [x] [Review][Patch] Stale TDD red-phase comments ("THIS TEST WILL FAIL") in passing tests [tests/layouts/GlobalSearch.test.tsx, tests/api/useSearch.test.ts] — **Fixed**: removed all red-phase markers, updated file headers to GREEN PHASE
- [x] [Review][Patch] Ctrl+K handler called `setOpen(true)` unconditionally even when input is empty, opening a useless empty dropdown [src/layouts/AppLayout.tsx:146] — **Fixed**: removed `setOpen(true)` from Ctrl+K handler; dropdown opens naturally when user types 2+ chars
- [x] [Review][Patch] No error state handling for `useSearch` in GlobalSearch — search API failure silently showed no results with no user feedback [src/layouts/AppLayout.tsx:136] — **Fixed**: destructure `isError` from `useSearch`, clear options on error, show "Search unavailable — please try again" in `noOptionsText`
- [x] [Review][Defer] `getUseSearchMock()` wrapper function is unnecessary indirection [tests/layouts/GlobalSearch.test.tsx:147] — deferred, pre-existing style preference, not a correctness issue
- [x] [Review][Defer] Stale search results visible briefly during query transition (no stale indicator) — deferred, acceptable UX per project patterns
- [x] [Review][Defer] UX spec says Escape should also clear search text; story AC only requires closing dropdown — deferred, not an AC violation per story spec

### Change Log

- 2026-04-03: Implemented Story 4.13 — Global Dataset Search. Added SearchResult/SearchResponse types, useSearch hook, GlobalSearch Autocomplete component in AppLayout, and all ATDD tests. 344 tests pass, 0 skipped.
- 2026-04-03: Code review complete. Applied 4 patches: removed duplicate SearchResult type in tests, cleaned stale TDD comments, fixed Ctrl+K stale-closure setOpen, added isError handling to GlobalSearch. All 344 tests still pass.
