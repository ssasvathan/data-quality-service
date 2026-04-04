# Story 4.6: DqsScoreChip & TrendSparkline Components

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want reusable DqsScoreChip and TrendSparkline components,
so that DQS scores and trends are rendered consistently across all dashboard views.

## Acceptance Criteria

1. **Given** a DqsScoreChip with score=87 and previousScore=84 **When** rendered **Then** it displays "87" in green text with an up arrow and "+3" delta
2. **And** it supports sizes lg (28px), md (18px), sm (14px)
3. **Given** a DqsScoreChip with score=55 **When** rendered **Then** it displays "55" in red text (critical threshold < 60)
4. **Given** a DqsScoreChip with no score data **When** rendered **Then** it displays "—" in gray text
5. **Given** a TrendSparkline with 30 data points **When** rendered at size md (32px tall) **Then** it displays a Recharts line chart with no axes/labels, threshold-based line color, and tooltip on hover showing date + score
6. **Given** a TrendSparkline with only 1 data point **When** rendered **Then** it shows a single dot with a "First run" placeholder
7. **And** both components have aria-labels describing score/status/trend for screen readers

## Tasks / Subtasks

- [x] Task 1: Create `DqsScoreChip` component in `dqs-dashboard/src/components/DqsScoreChip.tsx` (AC: 1, 2, 3, 4, 7)
  - [x] Define `DqsScoreChipProps` TypeScript interface in the file with props: `score?: number`, `previousScore?: number`, `size?: 'lg' | 'md' | 'sm'` (default: `'md'`), `showTrend?: boolean` (default: `true`)
  - [x] Implement size→fontSize mapping: `lg` → `score` variant (28px/700), `md` → `scoreSm` variant (18px/700), `sm` → 14px/600 inline
  - [x] Use `getDqsColor(score)` from `../../theme` for text color — NEVER hardcode hex values
  - [x] Render score number using MUI `Typography` with the correct variant from size mapping
  - [x] When `showTrend` is true and `previousScore` is defined: compute delta = `score - previousScore`; render trend arrow (▲ up, ▼ down, → flat at delta=0) + "+{delta}" or "−{delta}" using `Typography variant="caption"` in matching status color
  - [x] No-data state: when `score` is undefined, render "—" using MUI `Typography` with `color="text.disabled"` (neutral-500 #9E9E9E)
  - [x] Add `aria-label` prop computed as: score defined → `"DQS Score {score}, {status}, {trend description}"` (e.g. `"DQS Score 87, healthy, improving by 3 points"`); no-data → `"DQS Score unavailable"`
  - [x] Wrap score + trend in a `Box` with `display: 'flex'`, `alignItems: 'center'`, `gap: theme.spacing(0.5)` (4px xs spacing)
  - [x] Do NOT use MUI `Chip` component — compose from `Box` + `Typography` (the component IS the score display, not a chip wrapper)

- [x] Task 2: Create `TrendSparkline` component in `dqs-dashboard/src/components/TrendSparkline.tsx` (AC: 5, 6, 7)
  - [x] Define `TrendSparklineProps` TypeScript interface: `data: number[]`, `size?: 'lg' | 'md' | 'mini'` (default: `'md'`), `color?: string`, `showBaseline?: boolean` (default: `false`)
  - [x] Define size dimensions: `lg` → 64px height, full width; `md` → 32px height, full width; `mini` → 24px height, 80px width
  - [x] Derive line color: if `color` prop is provided use it; else use `getDqsColor(data[data.length - 1])` from last data point; fallback to `theme.palette.neutral[500]` if data is empty
  - [x] Use Recharts `ResponsiveContainer` + `LineChart` + `Line` with: NO `XAxis`, NO `YAxis`, NO `CartesianGrid`, NO `Legend`; `dot={false}` for smooth line; strokeWidth 1.5; type `"monotone"`
  - [x] Enable Recharts `Tooltip` on hover for `md` and `lg` sizes (suppress for `mini`): display format `"{date}: {score}"` — since `data` is `number[]` (scores only), use data index as x-axis key; show value as score
  - [x] Single data point: render a single Recharts `dot` or use MUI `Box` with a colored circle (4px width/height, border-radius 50%) centered vertically; display "First run" below in `caption` variant
  - [x] Optional dashed baseline (when `showBaseline=true`): add Recharts `ReferenceLine` at y=80 (healthy threshold) with `strokeDasharray="4 2"` and `stroke={theme.palette.neutral[300]}`
  - [x] Add `role="img"` and `aria-label` describing the trend (e.g. `"Trend over {data.length} data points, current score {latestScore}"`)
  - [x] Wrap in `Box` with explicit height matching the size (use `sx` prop: `{ height: sizeMap[size], width: size === 'mini' ? 80 : '100%' }`)
  - [x] Do NOT import Recharts BarChart or AreaChart — LineChart only

- [x] Task 3: Write Vitest component tests in `dqs-dashboard/tests/components/DqsScoreChip.test.tsx` (AC: 1, 2, 3, 4, 7)
  - [x] Install `@testing-library/react` and `@testing-library/jest-dom` if not already in `package.json` devDependencies
  - [x] Update `vite.config.ts` test environment from `'node'` to `'jsdom'` — required for React component rendering
  - [x] Add `setupFiles` entry in vitest config pointing to a test setup file that imports `@testing-library/jest-dom`
  - [x] Test: `DqsScoreChip score=87 previousScore=84` → renders "87" and "+3" and up arrow; text color is green (`#2E7D32`)
  - [x] Test: `DqsScoreChip score=70` → renders "70" with amber color (`#ED6C02`)
  - [x] Test: `DqsScoreChip score=55` → renders "55" with red color (`#D32F2F`)
  - [x] Test: `DqsScoreChip score=80` → renders green (boundary: >=80 is success)
  - [x] Test: `DqsScoreChip score=60` → renders amber (boundary: >=60 is warning)
  - [x] Test: `DqsScoreChip` with no score → renders "—"
  - [x] Test: `DqsScoreChip showTrend=false` → no trend arrow rendered
  - [x] Test: aria-label contains score, status, and trend direction

- [x] Task 4: Write Vitest component tests in `dqs-dashboard/tests/components/TrendSparkline.test.tsx` (AC: 5, 6, 7)
  - [x] Mock Recharts modules (they require DOM canvas) using `vi.mock('recharts', ...)` — return minimal divs for all chart components
  - [x] Test: `TrendSparkline data={[80, 85, 82, 87]}` → renders without error; container has correct height for `md` size (32px)
  - [x] Test: `TrendSparkline data={[55]}` → single data point renders "First run" text
  - [x] Test: `TrendSparkline data={[]}` → renders gracefully without error (empty state)
  - [x] Test: aria-label is present and contains "Trend"
  - [x] Test: `TrendSparkline size="mini"` → container width is 80px

- [x] Task 5: Export components from `dqs-dashboard/src/components/index.ts` (no AC — infrastructure)
  - [x] Create or update `src/components/index.ts` to export `DqsScoreChip` and `TrendSparkline`
  - [x] Pattern: `export { DqsScoreChip } from './DqsScoreChip'` and `export { TrendSparkline } from './TrendSparkline'`

## Dev Notes

### Critical: Theme Foundation Already Implemented (Story 4.1)

Story 4.1 (done) delivers the full MUI 7 theme in `dqs-dashboard/src/theme.ts`. These exports MUST be used — do NOT hardcode colors or reimport theme inside components:

```typescript
// CORRECT — always import from theme
import { getDqsColor, getDqsColorLight } from '../../theme'
import theme from '../../theme'  // for theme.palette.neutral, theme.spacing()

// NEVER hardcode
color: '#2E7D32'  // WRONG — use getDqsColor(score)
```

**Available theme utilities:**
- `getDqsColor(score: number): string` → returns `#2E7D32` (success), `#ED6C02` (warning), `#D32F2F` (error)
- `getDqsColorLight(score: number): string` → returns light tint variants
- `theme.palette.neutral[50/100/200/300/500/700/900]` — full neutral token set
- `theme.palette.primary.main` = `#1565C0`
- Typography variants: `score` (28px/700), `scoreSm` (18px/700), `mono` (13px monospace)

**Score thresholds (must match theme exactly):**
- `>= 80` → success (green)
- `>= 60 && < 80` → warning (amber)
- `< 60` → error (red)
- Boundaries: score===80 is SUCCESS, score===60 is WARNING

### DqsScoreChip — Component Architecture

This is the **most frequently rendered component** in the application — appears in LOB cards, table rows, search results, dataset headers, and the left panel list. Keep it lean.

**Props interface:**
```typescript
interface DqsScoreChipProps {
  score?: number          // undefined = no data state
  previousScore?: number  // undefined = no trend shown
  size?: 'lg' | 'md' | 'sm'  // default: 'md'
  showTrend?: boolean     // default: true
}
```

**Size-to-typography mapping:**
| size | Typography variant | px |
|------|-------------------|----|
| `lg` | `score` | 28px |
| `md` | `scoreSm` | 18px |
| `sm` | inline sx `fontSize: '0.875rem', fontWeight: 600` | 14px |

**Important:** `sm` size does NOT have a dedicated theme variant — use inline `sx` prop for 14px/600. The theme only defines `score` (28px) and `scoreSm` (18px).

**Trend rendering:**
```typescript
const delta = score - previousScore
const arrow = delta > 0 ? '▲' : delta < 0 ? '▼' : '→'
const deltaText = delta > 0 ? `+${delta}` : `${delta}`  // negative sign included by toString()
// Trend arrow and delta use same getDqsColor(score) color as the score itself
```

**No-data state (score === undefined):**
```tsx
<Typography variant="scoreSm" color="text.disabled">—</Typography>
```
`text.disabled` maps to `theme.palette.text.disabled` = `#9E9E9E` (neutral-500).

**Aria-label computation:**
```typescript
const statusLabel = score >= 80 ? 'healthy' : score >= 60 ? 'degraded' : 'critical'
const trendLabel = delta > 0 ? `improving by ${delta} points` : delta < 0 ? `declining by ${Math.abs(delta)} points` : 'stable'
// => "DQS Score 87, healthy, improving by 3 points"
```

**Do NOT wrap in MUI `Chip`** — the UX spec describes "DqsScoreChip" as the component name, not that it uses MUI's Chip. It's a Box+Typography composition:
```tsx
<Box display="flex" alignItems="center" gap={0.5} aria-label={ariaLabel}>
  <Typography variant={typographyVariant} sx={{ color: getDqsColor(score) }}>
    {score}
  </Typography>
  {showTrend && previousScore !== undefined && (
    <Typography variant="caption" sx={{ color: getDqsColor(score) }}>
      {arrow} {deltaText}
    </Typography>
  )}
</Box>
```

### TrendSparkline — Recharts Integration

**Recharts version:** 3.8.1 (installed in `package.json`). Recharts 3 API is largely compatible with v2.

**Required imports:**
```typescript
import { LineChart, Line, ResponsiveContainer, Tooltip, ReferenceLine } from 'recharts'
```

**Data shape for Recharts:** Recharts expects objects. Transform `number[]` to `{ index: number, score: number }[]`:
```typescript
const chartData = data.map((score, index) => ({ index, score }))
```

**Minimal sparkline (no axes, no grid):**
```tsx
<ResponsiveContainer width="100%" height={heightPx}>
  <LineChart data={chartData} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
    <Line
      type="monotone"
      dataKey="score"
      stroke={lineColor}
      strokeWidth={1.5}
      dot={false}
      isAnimationActive={false}  // disable animation for performance
    />
    {showTooltip && (
      <Tooltip
        formatter={(value: number) => [value, 'Score']}
        labelFormatter={(index: number) => `Point ${index + 1}`}
      />
    )}
    {showBaseline && (
      <ReferenceLine y={80} stroke={theme.palette.neutral[300]} strokeDasharray="4 2" />
    )}
  </LineChart>
</ResponsiveContainer>
```

**Size constants:**
```typescript
const SIZE_HEIGHT: Record<'lg' | 'md' | 'mini', number> = { lg: 64, md: 32, mini: 24 }
const SIZE_WIDTH: Record<'lg' | 'md' | 'mini', number | string> = { lg: '100%', md: '100%', mini: 80 }
```

**Single data point handling:** Recharts can't draw a line with one point. Detect and render a fallback:
```tsx
if (data.length <= 1) {
  return (
    <Box sx={{ height: SIZE_HEIGHT[size], display: 'flex', alignItems: 'center', gap: 0.5 }}>
      <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: lineColor }} />
      <Typography variant="caption" color="text.secondary">First run</Typography>
    </Box>
  )
}
```

**Empty data:** When `data.length === 0`, render the single-point fallback with neutral dot color.

**Tooltip suppression for mini size:** `showTooltip = size !== 'mini'` — mini sparklines are too small for tooltips.

**isAnimationActive={false}:** Always disable Recharts animations for sparklines — they are inline elements in lists/tables and animation would be distracting.

### File Structure — Exact Paths

```
dqs-dashboard/
  src/
    components/
      DqsScoreChip.tsx        ← NEW: create
      TrendSparkline.tsx      ← NEW: create
      index.ts                ← NEW: barrel export
  tests/
    components/
      theme.test.ts           ← existing (do NOT touch)
      DqsScoreChip.test.tsx   ← NEW: create
      TrendSparkline.test.tsx ← NEW: create
```

**Do NOT create:** `DatasetCard.tsx` — that belongs to Story 4.7. This story is ONLY `DqsScoreChip` and `TrendSparkline`.

**Do NOT touch:** `theme.ts`, `api/types.ts`, `api/client.ts`, `api/queries.ts`, `App.tsx`, `main.tsx`, any pages or layouts.

### Test Setup — Vitest with jsdom

Current `vite.config.ts` uses `environment: 'node'`. React component tests require jsdom.

**Required changes to `vite.config.ts`:**
```typescript
test: {
  environment: 'jsdom',  // CHANGED from 'node'
  include: ['tests/**/*.test.ts', 'tests/**/*.test.tsx'],
  setupFiles: ['./tests/setup.ts'],  // ADD
},
```

**Create `dqs-dashboard/tests/setup.ts`:**
```typescript
import '@testing-library/jest-dom'
```

**Install testing dependencies if not present:**
```bash
cd dqs-dashboard && npm install -D @testing-library/react @testing-library/jest-dom jsdom
```

Check `package.json` first — if these are already installed, skip.

**Note on theme.test.ts:** The existing `tests/components/theme.test.ts` uses `environment: 'node'` implicitly (pure utility tests, no DOM). Changing to jsdom is backward-compatible — all pure utility tests continue to work in jsdom.

### Recharts Mocking in Tests

Recharts requires browser canvas APIs not available in jsdom. Mock the entire module:

```typescript
// In TrendSparkline.test.tsx
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  LineChart: ({ children }: { children: React.ReactNode }) => <div data-testid="line-chart">{children}</div>,
  Line: () => null,
  Tooltip: () => null,
  ReferenceLine: () => null,
}))
```

Import `vi` from `vitest` for mocking.

### Rendering Components in Tests

Use React Testing Library `render` with the MUI `ThemeProvider`:

```typescript
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import theme from '../../src/theme'

const renderWithTheme = (ui: React.ReactElement) =>
  render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>)
```

This ensures `getDqsColor` and theme values work correctly in tests.

### TypeScript — Strict Mode Rules

- No `any` types anywhere — strict mode is enforced
- Define component props interfaces in the component file (not in `api/types.ts` — that is for API contracts only)
- Use `interface` for props: `interface DqsScoreChipProps { ... }`
- Use `React.ReactNode` for children if needed (neither component has children)
- For Recharts: import `{ LineChart, Line, ResponsiveContainer, Tooltip, ReferenceLine }` — types are included in `recharts` package

### Naming Conventions

Per `project-context.md` for TypeScript/React:
- Component files: `PascalCase.tsx` → `DqsScoreChip.tsx`, `TrendSparkline.tsx`
- Test files: `PascalCase.test.tsx`
- Interfaces: `PascalCase` → `DqsScoreChipProps`, `TrendSparklineProps`
- Internal variables: `camelCase` → `lineColor`, `showTooltip`, `sizeMap`
- Constants: `UPPER_SNAKE` → `SIZE_HEIGHT`, `SIZE_WIDTH`

### Previous Story Intelligence (4.1 — Theme Foundation)

From Story 4.1 completion notes and code review findings:

1. **`getDqsColor`/`getDqsColorLight` are defined and exported from `theme.ts`** — import these, do not redefine
2. **Module augmentation for `score`, `scoreSm`, `mono` variants is in `theme.ts`** — `<Typography variant="score">` is type-safe, no `as any` needed
3. **`theme.palette.neutral` exists as a typed palette key** — use `theme.palette.neutral[500]` etc.
4. **The review found that `as any` casts for custom variants were a bug** — explicitly fixed in 4.1 review. Do not introduce `as any` casts anywhere.
5. **`theme.palette.text.disabled`** = neutral-500 (#9E9E9E) — use this for the no-data "—" state, not hardcoded hex
6. **`tests/components/theme.test.ts` exists and has 53 passing tests** — do NOT modify this file

### Recharts 3.8.1 — Key API Details

**Recharts 3 is installed** (`"recharts": "^3.8.1"` in `package.json`). The API differences from v2:
- `ResponsiveContainer` still works the same way
- `isAnimationActive` prop still supported on `Line`
- Import paths unchanged: `import { ... } from 'recharts'`
- No breaking changes that affect sparkline usage

**Recharts margin:** Always set `margin={{ top: 2, right: 2, bottom: 2, left: 2 }}` — without margin, strokes near edges clip.

### Anti-Patterns to Avoid

From `_bmad-output/project-context.md`:
- **NEVER hardcode hex color values** — use `getDqsColor(score)` or `theme.palette.*`
- **NEVER use `any` type** — strict TypeScript
- **NEVER use `useEffect + fetch`** — not relevant for this story (no API calls), but noted
- **NEVER use MUI `Chip` component** for `DqsScoreChip` — it's a Box+Typography composition per UX spec

Additional dashboard component anti-patterns for this story:
- **NEVER import from `../../api/types.ts`** for component props — API types are for API contracts; define prop interfaces locally
- **NEVER animate sparklines** — `isAnimationActive={false}` always
- **NEVER render Recharts directly at fixed pixel dimensions** — always wrap in `ResponsiveContainer` for the non-mini sizes
- **NEVER break the `theme.test.ts` tests** — changing `vite.config.ts` to jsdom must not break the 53 existing pure-function tests

### Architecture Compliance Checklist

- [ ] Components live in `src/components/` — correct location per architecture spec
- [ ] Tests live in `tests/components/` — correct per project-context.md (`dqs-dashboard: top-level tests/ by type`)
- [ ] No API calls in these components — they accept data as props
- [ ] No routing logic in these components — they are display-only
- [ ] Uses MUI ThemeProvider context — no raw CSS files
- [ ] Exports via barrel `index.ts` — downstream components import from `'../components'`

### References

- Epic 4 Story 4.6 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md#Story 4.6`
- UX Component Strategy: `_bmad-output/planning-artifacts/ux-design-specification/component-strategy.md#DqsScoreChip` and `#TrendSparkline`
- Visual Design Foundation: `_bmad-output/planning-artifacts/ux-design-specification/visual-design-foundation.md`
- Architecture — dqs-dashboard structure: `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`
- Theme file (critical reference): `dqs-dashboard/src/theme.ts`
- Story 4.1 completion notes (theme details): `_bmad-output/implementation-artifacts/4-1-mui-theme-design-system-foundation.md`
- Project Context rules: `_bmad-output/project-context.md`
- Existing tests (do not modify): `dqs-dashboard/tests/components/theme.test.ts`
- Vite config (update environment): `dqs-dashboard/vite.config.ts`
- Package.json (check installed deps): `dqs-dashboard/package.json`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Import path for theme fixed: `../../theme` → `../theme` (components are in `src/components/`, theme is at `src/theme.ts`)

### Completion Notes List

- Implemented `DqsScoreChip` (Box+Typography composition, no MUI Chip) with score display, trend arrows, delta text, size variants, and aria-labels.
- Implemented `TrendSparkline` (Recharts LineChart, no axes/grid) with size height/width, single-point fallback ("First run"), optional baseline ReferenceLine, and role="img" accessibility.
- Created barrel `src/components/index.ts` exporting both components.
- All 99 tests pass (53 existing theme tests + 46 new component tests). Zero regressions.
- No new dependencies required — `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`, and `recharts` were already in `package.json`. Vite config already configured for jsdom + setupFiles.

### File List

- `dqs-dashboard/src/components/DqsScoreChip.tsx` (new)
- `dqs-dashboard/src/components/TrendSparkline.tsx` (new)
- `dqs-dashboard/src/components/index.ts` (new)

## Change Log

| Date | Change |
|------|--------|
| 2026-04-03 | Story 4.6 created — DqsScoreChip and TrendSparkline component story ready for dev. |
| 2026-04-03 | Implemented DqsScoreChip, TrendSparkline, and components/index.ts. All 99 tests pass. Status → review. |
