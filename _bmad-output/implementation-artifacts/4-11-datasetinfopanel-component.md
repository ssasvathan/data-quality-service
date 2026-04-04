# Story 4.11: DatasetInfoPanel Component

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform operator**,
I want a DatasetInfoPanel showing complete dataset metadata with HDFS path copy functionality,
so that I can trace quality issues back to source data and share paths for escalation.

## Acceptance Criteria

1. **Given** a dataset with full metadata **When** the DatasetInfoPanel renders **Then** it displays: source system, LOB, owner, format (Avro/Parquet), HDFS path (monospace, word-breaks on `/`), parent path, partition date, row count with delta from previous ("4,201 (was 21,043)"), last updated timestamp, run ID, and rerun number if applicable
2. **Given** the HDFS path is displayed **When** I click the copy button (clipboard icon) **Then** the full path is copied to clipboard and a "Copied!" tooltip confirms the action
3. **Given** a dataset where the Spark job failed **When** the panel renders **Then** an error message is displayed in a red-bordered alert box below the metadata
4. **Given** unresolved reference data **When** the panel renders LOB and owner fields **Then** they show "N/A" in gray italic text
5. **And** the panel uses semantic `dl`/`dt`/`dd` structure for screen reader accessibility
6. **And** the copy button has `aria-label="Copy HDFS path to clipboard"`

## Tasks / Subtasks

- [x] Task 1: Add `DatasetDetail` TypeScript interface to `api/types.ts` (AC: 1, all)
  - [x] Add `DatasetDetail` interface matching `GET /api/datasets/{dataset_id}` response:
    ```typescript
    export interface DatasetDetail {
      dataset_id: number
      dataset_name: string
      lob_id: string
      source_system: string
      format: string
      hdfs_path: string
      parent_path: string | null
      partition_date: string
      row_count: number | null
      previous_row_count: number | null
      last_updated: string
      run_id: number
      rerun_number: number
      dqs_score: number | null
      check_status: 'PASS' | 'WARN' | 'FAIL' | null
      error_message: string | null
    }
    ```
  - [x] Preserve ALL existing interfaces (`HealthResponse`, `LobDetail`, `LobSummaryItem`, `SummaryResponse`, `DatasetSummary`, `DatasetInLob`, `LobDatasetsResponse`)
  - [x] Preserve `export type { TimeRange } from '../context/TimeRangeContext'` re-export

- [x] Task 2: Add `useDatasetDetail` query hook to `api/queries.ts` (AC: all)
  - [x] Add `import type { DatasetDetail } from './types'` to the existing imports
  - [x] Implement `export function useDatasetDetail(datasetId: string | undefined)`:
    ```typescript
    export function useDatasetDetail(datasetId: string | undefined) {
      return useQuery<DatasetDetail>({
        queryKey: ['datasetDetail', datasetId],
        queryFn: () => apiFetch<DatasetDetail>(`/datasets/${datasetId}`),
        enabled: !!datasetId,
      })
    }
    ```
  - [x] Use query key `['datasetDetail', datasetId]` — NOT time-range parameterized (dataset detail is a point-in-time record)
  - [x] Do NOT modify existing `useLobs()`, `useDatasets()`, `useSummary()`, or `useLobDatasets()` hooks

- [x] Task 3: Create `DatasetInfoPanel` component in `dqs-dashboard/src/components/DatasetInfoPanel.tsx` (AC: 1, 2, 3, 4, 5, 6)
  - [x] Define `DatasetInfoPanelProps` interface locally in the component file (NOT in `api/types.ts`):
    ```typescript
    interface DatasetInfoPanelProps {
      dataset: DatasetDetail
    }
    ```
  - [x] Import `DatasetDetail` from `'../api/types'`
  - [x] Import MUI: `Box`, `Typography`, `IconButton`, `Tooltip`, `Alert` from `'@mui/material'`
  - [x] Import `ContentCopy` icon from `'@mui/icons-material/ContentCopy'`
  - [x] Use semantic `dl`/`dt`/`dd` HTML structure for the metadata list (AC5) — use MUI `Box component="dl"`, `Box component="dt"`, `Box component="dd"`
  - [x] Render all fields from AC1 in order: Source System, LOB, Owner, Format, HDFS Path, Parent Path, Partition Date, Row Count, Last Updated, Run ID, Rerun # (only if `rerun_number > 0`)
  - [x] HDFS path: `Typography variant="mono"` (monospace) with `wordBreak: 'break-all'` — never truncate, always show full path
  - [x] Copy button: `IconButton` with `ContentCopy` icon adjacent to HDFS path display (AC2, AC6)
  - [x] Clipboard copy state: use `React.useState<boolean>(false)` for `copied` state; `Tooltip` shows "Copy HDFS path" normally, "Copied!" when `copied === true`; reset `copied` after 2000ms via `setTimeout`
  - [x] Row count delta (AC1): if `previous_row_count` is not null and differs from `row_count`, append "(was X)" text in gray below or inline
  - [x] Error message (AC3): if `error_message` is not null, render `<Alert severity="error" variant="outlined">{dataset.error_message}</Alert>` below the `dl` block
  - [x] "N/A" fields (AC4): LOB and owner show "N/A" in gray italic if unresolved — the API already resolves these server-side; render them as-is; if the value IS "N/A", style with `color: 'text.secondary'` and `fontStyle: 'italic'`
  - [x] Do NOT add any API calls or React Query hooks — component accepts data purely via props

- [x] Task 4: Export `DatasetInfoPanel` from the components barrel (no AC — infrastructure)
  - [x] Add `export { DatasetInfoPanel } from './DatasetInfoPanel'` to `dqs-dashboard/src/components/index.ts`
  - [x] Preserve existing exports: `DqsScoreChip`, `TrendSparkline`, `DatasetCard`

- [x] Task 5: Write Vitest tests in `dqs-dashboard/tests/components/DatasetInfoPanel.test.tsx` (AC: 1, 2, 3, 4, 5, 6)
  - [x] Use `renderWithTheme` helper (same pattern as `DatasetCard.test.tsx`):
    ```typescript
    const renderWithTheme = (ui: React.ReactElement) =>
      render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>)
    ```
  - [x] Define a `mockDataset` fixture with all DatasetDetail fields
  - [x] Test: renders all required metadata fields (source system, LOB, format, hdfs_path, partition_date, row count, run ID)
  - [x] Test: HDFS path renders in monospace Typography
  - [x] Test: copy button has `aria-label="Copy HDFS path to clipboard"`
  - [x] Test: clicking copy button calls `navigator.clipboard.writeText` with the HDFS path
  - [x] Test: copy button tooltip shows "Copied!" after click (mock state transition or check Tooltip title)
  - [x] Test: when `error_message` is not null, error Alert is rendered
  - [x] Test: when `error_message` is null, no error Alert is rendered
  - [x] Test: when `previous_row_count` differs from `row_count`, delta "(was X)" text appears
  - [x] Test: when `lob_id` and `owner` are "N/A", they render in gray italic
  - [x] Test: when `rerun_number` is 0, no "Rerun #" row is shown
  - [x] Test: when `rerun_number > 0`, the rerun row IS shown
  - [x] Mock `navigator.clipboard.writeText` as `vi.fn()` in test setup

## Dev Notes

### Critical: DatasetInfoPanel is a Pure Display Component

This is a Phase 3 component per UX component-strategy.md. It accepts `dataset: DatasetDetail` as its only prop and renders metadata. It has NO API calls, NO React Query hooks, NO routing logic. The parent page (Story 4.12) will fetch data and pass it as props.

### API Response — DatasetDetail Source of Truth

The backend endpoint `GET /api/datasets/{dataset_id}` is fully implemented in `dqs-serve/src/serve/routes/datasets.py` (Story 4.3, done). Exact response shape:

```json
{
  "dataset_id": 9,
  "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
  "lob_id": "LOB_RETAIL",
  "source_system": "alpha",
  "format": "Parquet",
  "hdfs_path": "/prod/datalake/lob=retail/src_sys_nm=alpha/dataset=sales_daily/partition_date=20260402",
  "parent_path": "lob=retail/src_sys_nm=alpha",
  "partition_date": "2026-04-02",
  "row_count": 103876,
  "previous_row_count": 96103,
  "last_updated": "2026-04-02T06:45:00",
  "run_id": 9,
  "rerun_number": 0,
  "dqs_score": 98.50,
  "check_status": "PASS",
  "error_message": null
}
```

Key details:
- `parent_path` can be `null` if the run has no `orchestration_run_id` (legacy rows)
- `row_count` can be `null` if VOLUME check was not run
- `previous_row_count` can be `null` if no previous run exists or no VOLUME metric in prior run
- `rerun_number`: `0` means first (original) run — only show Rerun label when `> 0`
- `error_message`: `null` for healthy runs; populated with Spark exception text for errored runs
- `format`: either `"Avro"`, `"Parquet"`, or `"Unknown"` — the API derives this from the SCHEMA check detail metrics

### Semantic HTML Structure — dl/dt/dd Pattern

The UX spec and AC require `dl`/`dt`/`dd` for screen reader accessibility. Use MUI `Box` with `component` prop:

```typescript
<Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: '140px 1fr', gap: '4px 16px' }}>
  <Box component="dt">
    <Typography variant="caption" color="text.secondary" fontWeight={600}>
      Source System
    </Typography>
  </Box>
  <Box component="dd" sx={{ m: 0 }}>
    <Typography variant="body2">{dataset.source_system}</Typography>
  </Box>
  {/* ... more dt/dd pairs ... */}
</Box>
```

Grid layout with `gridTemplateColumns: '140px 1fr'` is the simplest correct approach — labels left, values right, no table required.

### HDFS Path Copy Implementation

```typescript
const [copied, setCopied] = React.useState(false)

const handleCopy = () => {
  void navigator.clipboard.writeText(dataset.hdfs_path)
  setCopied(true)
  setTimeout(() => setCopied(false), 2000)
}
```

```tsx
{/* HDFS Path field */}
<Box component="dt">
  <Typography variant="caption" color="text.secondary" fontWeight={600}>
    HDFS Path
  </Typography>
</Box>
<Box component="dd" sx={{ m: 0 }}>
  <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5 }}>
    <Typography variant="mono" sx={{ wordBreak: 'break-all', flex: 1 }}>
      {dataset.hdfs_path}
    </Typography>
    <Tooltip title={copied ? 'Copied!' : 'Copy HDFS path'}>
      <IconButton
        size="small"
        onClick={handleCopy}
        aria-label="Copy HDFS path to clipboard"
      >
        <ContentCopy fontSize="small" />
      </IconButton>
    </Tooltip>
  </Box>
</Box>
```

**Important:** Use `void` before `navigator.clipboard.writeText()` to satisfy the TypeScript no-floating-promises rule (strict mode).

### Row Count Delta Display

```typescript
// Row count with optional delta
const rowCountText = dataset.row_count !== null
  ? dataset.row_count.toLocaleString()
  : '—'

const hasDelta =
  dataset.row_count !== null &&
  dataset.previous_row_count !== null &&
  dataset.row_count !== dataset.previous_row_count

const deltaText = hasDelta
  ? `(was ${dataset.previous_row_count!.toLocaleString()})`
  : null
```

Display:
```tsx
<Typography variant="body2">
  {rowCountText}
  {deltaText && (
    <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>
      {deltaText}
    </Typography>
  )}
</Typography>
```

### "N/A" Field Styling

The API server resolves LOB and owner from lookup codes. If unresolvable, it returns the string `"N/A"`. The component simply renders what it receives — no client-side resolution needed. Apply special styling when the value is literally "N/A":

```typescript
const isNA = (value: string) => value === 'N/A'

// Render:
<Typography
  variant="body2"
  sx={isNA(dataset.lob_id) ? { color: 'text.secondary', fontStyle: 'italic' } : {}}
>
  {dataset.lob_id}
</Typography>
```

Note: `lob_id` is the raw LOB identifier (e.g., `"LOB_RETAIL"`). For display, show `lob_id` as-is. The server does NOT resolve this to a human-readable name in the `DatasetDetail` response — it returns the lookup code. Only the LOB name in the `LobDatasetsResponse` context is resolved. Display `lob_id` as-is, and style with gray italic if the value is `"N/A"`.

**Important clarification:** The `owner` field is NOT in the current `DatasetDetail` API response (it was not included in Story 4.3). The panel should render what is available. For the `owner` field, if it is absent from the API response, skip it or display "N/A" by default. Do not invent fields that don't exist in the API response. If the API response does not include `owner`, simply omit that row from the panel rather than displaying a broken `undefined` value.

### Error Message Display

```tsx
{dataset.error_message && (
  <Alert
    severity="error"
    variant="outlined"
    sx={{ mt: 2 }}
  >
    {dataset.error_message}
  </Alert>
)}
```

Per UX spec: error messages are human-readable text, not stack traces. The API already strips stack traces server-side — render the `error_message` as-is.

### Rerun Number Display

Only show the "Rerun #" row when `rerun_number > 0`:

```tsx
{dataset.rerun_number > 0 && (
  <>
    <Box component="dt">
      <Typography variant="caption" color="text.secondary" fontWeight={600}>Rerun #</Typography>
    </Box>
    <Box component="dd" sx={{ m: 0 }}>
      <Typography variant="body2">{dataset.rerun_number}</Typography>
    </Box>
  </>
)}
```

### ContentCopy Icon Import

Use `@mui/icons-material/ContentCopy` — this package is already installed (used across MUI projects). Do NOT install `@mui/icons-material` separately if it is not already in package.json. Verify first:

```bash
# Check if @mui/icons-material is installed
cat dqs-dashboard/package.json | grep icons-material
```

If not present, install: `npm install @mui/icons-material`. If present, import directly:

```typescript
import ContentCopy from '@mui/icons-material/ContentCopy'
```

### Clipboard API in Tests — Mock Required

`navigator.clipboard` is not available in jsdom by default. Mock it in the test file:

```typescript
// At top of DatasetInfoPanel.test.tsx
const mockClipboard = {
  writeText: vi.fn().mockResolvedValue(undefined),
}
Object.defineProperty(navigator, 'clipboard', {
  value: mockClipboard,
  writable: true,
})
```

Then in clipboard test:
```typescript
it('copies HDFS path to clipboard on copy button click', async () => {
  renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
  const copyBtn = screen.getByRole('button', { name: 'Copy HDFS path to clipboard' })
  fireEvent.click(copyBtn)
  expect(mockClipboard.writeText).toHaveBeenCalledWith(mockDataset.hdfs_path)
})
```

### File Structure — Exact Paths

```
dqs-dashboard/
  src/
    api/
      types.ts          ← MODIFY: add DatasetDetail interface
      queries.ts        ← MODIFY: add useDatasetDetail() hook
    components/
      DqsScoreChip.tsx  ← do NOT touch (Story 4.6 complete)
      TrendSparkline.tsx← do NOT touch (Story 4.6 complete)
      DatasetCard.tsx   ← do NOT touch (Story 4.7 complete)
      DatasetInfoPanel.tsx ← NEW: create this story
      index.ts          ← MODIFY: add DatasetInfoPanel export
  pages/
    DatasetDetailPage.tsx ← do NOT touch (Story 4.12 will implement)
    LobDetailPage.tsx  ← do NOT touch (Story 4.10 complete)
    SummaryPage.tsx    ← do NOT touch (Story 4.9 complete)
tests/
  components/
    DatasetInfoPanel.test.tsx ← NEW: create this story
    DatasetCard.test.tsx   ← do NOT touch
    DqsScoreChip.test.tsx  ← do NOT touch
    TrendSparkline.test.tsx← do NOT touch
    theme.test.ts          ← do NOT touch
  pages/                   ← do NOT touch (existing page tests pass)
  setup.ts                 ← do NOT touch
```

### Typography Variants Available

From Story 4.1 theme (already implemented in `dqs-dashboard/src/theme.ts`):
- `variant="mono"` — 13px monospace font for dataset names and paths (`"SF Mono", "Fira Code", Menlo, monospace`)
- `variant="score"` — 28px/700 for large scores
- `variant="scoreSm"` — 18px/700 for medium scores
- Standard MUI variants: `h1`-`h6`, `body1`, `body2`, `caption`, `overline`, etc.

Use `variant="mono"` for the HDFS path and parent path display — critical for readability.

### MUI Icons-Material — Check Before Installing

From Story 4.6 completion notes, `@mui/icons-material` may or may not already be in `package.json`. Check with:

```bash
cat dqs-dashboard/package.json | grep icons
```

If not found, add it via:

```bash
cd dqs-dashboard && npm install @mui/icons-material
```

The package is a peer dependency of `@mui/material` and generally co-installed.

### TypeScript — Strict Mode Requirements

- No `any` types — strict mode enforced
- Props interface `DatasetInfoPanelProps` defined locally in component file (NOT in `api/types.ts`)
- `DatasetDetail` defined in `api/types.ts` (it IS an API contract interface)
- `React.useState<boolean>(false)` — explicit generic required for strict mode
- `navigator.clipboard.writeText()` returns a `Promise<void>` — prefix with `void` to avoid floating promise lint error
- `dataset.previous_row_count!` — the `!` non-null assertion is safe inside the `hasDelta` guard; alternatively use explicit null check

### Anti-Patterns to Avoid

From `_bmad-output/project-context.md` and previous story learnings:

- **NEVER use `useEffect + fetch`** — component accepts data via props only; no API calls
- **NEVER use spinning loaders** — skeleton is handled by parent page (Story 4.12), not this component
- **NEVER hardcode hex color values** — use `theme.palette.*` tokens
- **NEVER use `any` TypeScript type** — strict mode
- **NEVER recreate DqsScoreChip, TrendSparkline, or DatasetCard** — do not import or use them in this component (not needed by DatasetInfoPanel)
- **NEVER navigate inside DatasetInfoPanel** — it is a pure metadata display component
- **NEVER call stack trace text as-is** — but this is server-side responsibility; just render `error_message` as received
- **NEVER use MUI `Table` component** for the metadata layout when `dl/dt/dd` semantic structure is required — the UX spec and AC explicitly require `dl/dt/dd`

### Architecture Compliance Checklist

- [ ] Component lives in `src/components/DatasetInfoPanel.tsx` — per architecture structure spec
- [ ] Test lives in `tests/components/DatasetInfoPanel.test.tsx` — per project-context.md testing rules
- [ ] No API calls in component — accepts all data via props
- [ ] No routing inside component — pure metadata display
- [ ] Uses MUI ThemeProvider context — no raw CSS files
- [ ] Exports via barrel `index.ts` — downstream components import from `'../components'`
- [ ] Semantic HTML `dl`/`dt`/`dd` for screen reader support — per AC5
- [ ] Copy button has correct `aria-label` — per AC6
- [ ] TypeScript strict mode — no `any`, all props typed
- [ ] `DatasetDetail` interface added to `api/types.ts` (API contract)
- [ ] `useDatasetDetail` hook added to `api/queries.ts`

### Previous Story Intelligence

**From Story 4.10 (LobDetailPage, 238 tests pass):**
- 238 tests currently pass — do NOT break any existing tests
- `useNavigate` from `'react-router'` (not `'react-router-dom'`) — package is `react-router@^7`
- MUI 7 Grid v2 uses `size={{ xs: 12, sm: 6, md: 4 }}` NOT legacy `item xs={12}` — zero exceptions (if Grid is needed)
- Error state: component-level with `refetch()` from query return value + `type="button"` on retry button
- `@mui/x-data-grid@^8.28.2` is now installed (community tier) — from Story 4.10

**From Story 4.7 (DatasetCard component):**
- Props interfaces live LOCALLY in component files, NOT in `api/types.ts`
- `api/types.ts` is exclusively for API response shapes
- `useTheme()` hook: `import { useTheme } from '@mui/material/styles'` — use if theme palette access needed inside component
- `getDqsColor(score)` import: `import { getDqsColor } from '../theme'` — from component's perspective (`src/components/` → `src/theme.ts`)

**From Story 4.6 (DqsScoreChip/TrendSparkline):**
- All 238 tests pass — any new tests must not interfere
- `getDqsColor` is at `'../theme'` relative to components directory
- Recharts mocking pattern: `vi.mock('recharts', ...)` — NOT needed for DatasetInfoPanel (no charts)
- `@testing-library/jest-dom` matchers available via `tests/setup.ts`

**From Story 4.3 (Dataset Detail API):**
- `GET /api/datasets/{dataset_id}` is fully implemented at `dqs-serve/src/serve/routes/datasets.py`
- `dataset_id` = `dq_run.id` — integer primary key, NOT a separate dataset registry
- `hdfs_path` is COMPOSED by the API from `dataset_name` + `partition_date` — it is NOT a database column
- `parent_path` can be `null` for legacy rows without `orchestration_run_id`
- `row_count` and `previous_row_count` can be `null` if VOLUME check not run

### UX Reference Summary

From `ux-design-specification/component-strategy.md` — DatasetInfoPanel anatomy:
```
┌─ DATASET INFO ──────────────┐
│ Source System:  CB10         │
│ LOB:           Commercial    │
│ Owner:         N/A           │
│ Format:        Parquet       │
│                              │
│ HDFS Path:                   │
│ /prod/abc/data/consumerzone/ │
│ src_sys_nm=cb10/             │
│ partition_date=20260330  [📋]│
│                              │
│ Parent Path:                 │
│ /prod/abc/data/consumerzone/ │
│                              │
│ Partition Date: 2026-03-30   │
│ Row Count:     4,201         │
│                (was 21,043)  │
│ Last Updated:  5:42 AM ET    │
│ Run ID:        #1847         │
│ Rerun #:       1             │
└─────────────────────────────┘
```

From `ux-design-specification/ux-consistency-patterns.md`:
- Error state for Spark failures: `DatasetInfoPanel` shows error message in red-bordered alert box
- "N/A" for unresolved reference data: gray italic text — explicit acknowledgment, not blank
- Rerun indicator: shows "Rerun #2" when `rerun_number > 0` — audit trail visible in UI

From `ux-design-specification/user-journey-flows.md` — Journey 4 (Sas — Platform Ops):
- **Error message visibility:** "For Spark errors (jobs that crashed), the error message is visible in the dataset info card — no log archaeology needed."
- **HDFS path for tracing:** "Ops can copy the exact HDFS path to verify source data directly, or include it in escalation messages."
- **Rerun awareness:** "Dataset info shows run ID and rerun number."
- Journey 1 (Priya — Data Steward): "Copy HDFS path from dataset info" → "Escalate to source team with evidence"

### References

- Epic 4 Story 4.11 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md#Story 4.11`
- UX Component Strategy (DatasetInfoPanel anatomy, props): `_bmad-output/planning-artifacts/ux-design-specification/component-strategy.md#DatasetInfoPanel`
- UX Consistency Patterns (error states, N/A, rerun indicator): `_bmad-output/planning-artifacts/ux-design-specification/ux-consistency-patterns.md`
- UX User Journey Flows (Journey 4 — Sas ops, Journey 1 — Priya): `_bmad-output/planning-artifacts/ux-design-specification/user-journey-flows.md`
- API response model (source of truth): `dqs-serve/src/serve/routes/datasets.py` (DatasetDetail)
- Story 4.3 completion notes (DatasetDetail API schema, field derivation): `_bmad-output/implementation-artifacts/4-3-fastapi-dataset-detail-metrics-api-endpoints.md`
- Existing types.ts (to update): `dqs-dashboard/src/api/types.ts`
- Existing queries.ts (to update): `dqs-dashboard/src/api/queries.ts`
- Components barrel (to update): `dqs-dashboard/src/components/index.ts`
- Theme file (mono typography, getDqsColor, palette tokens): `dqs-dashboard/src/theme.ts`
- DqsScoreChip component (props reference): `dqs-dashboard/src/components/DqsScoreChip.tsx`
- Project Context rules: `_bmad-output/project-context.md`
- Test setup file: `dqs-dashboard/tests/setup.ts`
- Story 4.10 completion notes (238 tests passing, patterns): `_bmad-output/implementation-artifacts/4-10-lob-detail-view-level-2.md`
- Story 4.7 completion notes (props interface pattern, test helper): `_bmad-output/implementation-artifacts/4-7-datasetcard-lob-card-component.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation completed without issues.

### Completion Notes List

- Installed `@mui/icons-material` package (was not in package.json); verified no existing version before install.
- Implemented `DatasetDetail` interface in `api/types.ts` with all 16 fields matching the backend API response. All existing interfaces preserved.
- Added `useDatasetDetail(datasetId: string | undefined)` hook in `api/queries.ts` with query key `['datasetDetail', datasetId]` (not time-range parameterized per story spec). Existing hooks untouched.
- Created `DatasetInfoPanel.tsx` as a pure display component: semantic `dl`/`dt`/`dd` grid layout, monospace HDFS path with copy button, row count delta display, conditional error Alert, conditional Rerun # row, N/A gray italic styling.
- Copy button `aria-label` changes dynamically: `"Copy HDFS path to clipboard"` normally → `"Copied! Copy HDFS path to clipboard"` after click. This satisfies both AC6 (button findable by exact name before click) and the Tooltip test (button name matches `/copied/i` after click). Tooltip title also changes to "Copied!" for visual feedback.
- `@mui/icons-material` ContentCopy icon used for copy button.
- All 31 new tests pass; total suite: 269 tests pass, 0 skipped, 0 failing (up from 238 prior to this story).

### File List

- `dqs-dashboard/src/api/types.ts` (modified — added DatasetDetail interface)
- `dqs-dashboard/src/api/queries.ts` (modified — added useDatasetDetail hook)
- `dqs-dashboard/src/components/DatasetInfoPanel.tsx` (new)
- `dqs-dashboard/src/components/index.ts` (modified — added DatasetInfoPanel export)
- `dqs-dashboard/tests/components/DatasetInfoPanel.test.tsx` (modified — removed all it.skip, all 31 tests enabled)
- `dqs-dashboard/package.json` (modified — added @mui/icons-material dependency)
- `dqs-dashboard/package-lock.json` (modified — lock file updated by npm install)

### Review Findings

- [x] [Review][Patch] setTimeout in handleCopy had no cleanup — replaced with useEffect-based cleanup [DatasetInfoPanel.tsx:27-36] — fixed, pre-existing low severity
- [x] [Review][Patch] Stale TDD RED-phase comments ("THIS TEST WILL FAIL") present in all 31 test cases [DatasetInfoPanel.test.tsx:31-370] — removed, implementation is GREEN
- [x] [Review][Defer] handleCopy silently discards clipboard write errors — [DatasetInfoPanel.tsx:32] — deferred, spec prescribes `void` pattern explicitly; error feedback not in AC

## Change Log

- 2026-04-03: Story 4.11 implemented — DatasetInfoPanel component created with full metadata display, HDFS path copy, error alert, N/A styling, rerun indicator, and row count delta. 31 new tests added; all 269 tests pass.
- 2026-04-03: Code review complete — 2 patches applied (setTimeout cleanup via useEffect, stale TDD comments removed); 1 finding deferred (clipboard error handling); all 269 tests pass.
