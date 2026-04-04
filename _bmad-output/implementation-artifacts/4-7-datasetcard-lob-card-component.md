# Story 4.7: DatasetCard (LOB Card) Component

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want a DatasetCard component displaying LOB-level health summary,
so that the Summary view shows scannable LOB cards with scores, trends, and status counts.

## Acceptance Criteria

1. **Given** a DatasetCard with lobName="Consumer Banking", dqsScore=87, statusCounts={pass:16, warn:1, fail:1} **When** rendered **Then** it displays LOB name, dataset count, DqsScoreChip (large), score progress bar, TrendSparkline, and status count chips
2. **Given** a user hovers over the card **When** the hover state activates **Then** the border color transitions to primary (#1565C0)
3. **Given** a user clicks anywhere on the card **When** the click handler fires **Then** it navigates to the LOB Detail view for that LOB
4. **And** the card has `role="button"`, is keyboard focusable (Tab), and activatable via Enter/Space
5. **And** `aria-label` includes LOB name, DQS score, dataset count, and status summary

## Tasks / Subtasks

- [x] Task 1: Create `DatasetCard` component in `dqs-dashboard/src/components/DatasetCard.tsx` (AC: 1, 2, 3, 4, 5)
  - [x] Define `DatasetCardProps` TypeScript interface in the component file (NOT in `api/types.ts` — props interfaces live locally)
  - [x] Implement the card anatomy: LOB name (h3 typography), dataset count (body2/caption), DqsScoreChip size="lg", MUI LinearProgress, TrendSparkline size="md", status count chips (pass/warn/fail)
  - [x] Use MUI `Card` + `CardContent` as the container with `sx` props for styling — no raw CSS
  - [x] Implement hover state: border transitions from `neutral-200` to `primary.main` (#1565C0) using `sx` prop transition
  - [x] Implement click handler: accept `onClick` prop, attach to outer `Card` element; keyboard support via `role="button"`, `tabIndex={0}`, `onKeyDown` (Enter/Space)
  - [x] Build `aria-label` string: `"{lobName}, DQS Score {dqsScore}, {datasetCount} datasets, {statusCounts.pass} passing, {statusCounts.warn} warning, {statusCounts.fail} failing"`
  - [x] Status count chips: 3 MUI `Chip` components — pass=green, warn=amber, fail=red using theme semantic colors (NOT hardcoded hex)
  - [x] LinearProgress: `value={dqsScore}` (0-100), use `sx` to color the bar via `getDqsColor(dqsScore)` for the progress track
  - [x] Do NOT add any API calls or React Query hooks — component accepts data purely via props

- [x] Task 2: Export `DatasetCard` from `dqs-dashboard/src/components/index.ts` (no AC — infrastructure)
  - [x] Add `export { DatasetCard } from './DatasetCard'` to the existing barrel export
  - [x] Import for downstream: `import { DqsScoreChip, TrendSparkline, DatasetCard } from '../components'`

- [x] Task 3: Write Vitest component tests in `dqs-dashboard/tests/components/DatasetCard.test.tsx` (AC: 1, 2, 3, 4, 5)
  - [x] Use the same `renderWithTheme` helper pattern from `DqsScoreChip.test.tsx` — wrap with `ThemeProvider`
  - [x] Mock `TrendSparkline` and `DqsScoreChip` with `vi.mock` to isolate `DatasetCard` rendering (avoids Recharts canvas issues)
  - [x] Test: renders LOB name, dataset count, pass/warn/fail chip counts
  - [x] Test: calls `onClick` when card is clicked
  - [x] Test: calls `onClick` when Enter key is pressed
  - [x] Test: calls `onClick` when Space key is pressed
  - [x] Test: `aria-label` contains LOB name, score, dataset count, and status counts
  - [x] Test: card has `role="button"` and `tabIndex={0}`
  - [x] Test: LinearProgress has `value={dqsScore}` accessible via DOM

## Dev Notes

### Critical: Reuse Existing Components — Do NOT Recreate

Story 4.6 (done) created `DqsScoreChip` and `TrendSparkline`. This story MUST import them from the barrel export:

```typescript
// CORRECT — import from barrel
import { DqsScoreChip } from './DqsScoreChip'
import { TrendSparkline } from './TrendSparkline'

// WRONG — never recreate score display or sparkline logic
```

### Component Anatomy (From UX Spec)

```
┌─────────────────────────────┐
│ Consumer Banking             │  ← Typography h3 (LOB name)
│ 18 datasets                  │  ← Typography body2/caption
│                              │
│ 87  ▲ +3                     │  ← DqsScoreChip size="lg"
│ ████████████████████░░░      │  ← MUI LinearProgress, colored via getDqsColor
│ ┈┈┈╱╲┈┈┈╱╲┈┈ (sparkline)    │  ← TrendSparkline size="md"
│                              │
│ [16 pass] [1 warn] [1 fail]  │  ← 3× MUI Chip
└─────────────────────────────┘
```

### DatasetCard Props Interface

```typescript
interface DatasetCardProps {
  lobName: string
  datasetCount: number
  dqsScore: number
  previousScore?: number
  trendData: number[]
  statusCounts: { pass: number; warn: number; fail: number }
  onClick: () => void
}
```

All props are required except `previousScore` (passed through to `DqsScoreChip` for trend delta).

### Card Styling — Exact Requirements

```typescript
// Default card style
sx={{
  border: `1px solid ${theme.palette.neutral[200]}`,  // neutral-200 = #EEEEEE
  borderRadius: '8px',
  boxShadow: 'none',          // NO box-shadow — per UX spec
  bgcolor: 'background.paper', // white surface
  cursor: 'pointer',
  transition: 'border-color 0.2s ease',
  '&:hover': {
    borderColor: theme.palette.primary.main,  // #1565C0 on hover
  },
  '&:focus-visible': {
    outline: `2px solid ${theme.palette.primary.main}`,
    outlineOffset: '2px',
  },
}}
```

No box-shadow anywhere. Flatness is intentional per UX design philosophy.

### Status Chip Colors

```typescript
// Use MUI semantic palette — NEVER hardcode hex values
const chipColors = {
  pass: { bgcolor: theme.palette.success.light, color: theme.palette.success.main },
  warn: { bgcolor: theme.palette.warning.light, color: theme.palette.warning.main },
  fail: { bgcolor: theme.palette.error.light, color: theme.palette.error.main },
}
// success.light = #E8F5E9, success.main = #2E7D32
// warning.light = #FFF3E0, warning.main = #ED6C02
// error.light = #FFEBEE, error.main = #D32F2F
```

Use MUI `Chip` component (unlike `DqsScoreChip` which is a Box+Typography composition, these are genuine status chips).

Chip size: `size="small"`. Label format: `"{count} pass"`, `"{count} warn"`, `"{count} fail"`.

### LinearProgress Styling

```typescript
<LinearProgress
  variant="determinate"
  value={dqsScore}
  sx={{
    height: 6,
    borderRadius: 3,
    bgcolor: theme.palette.neutral[200],  // track (unfilled) background
    '& .MuiLinearProgress-bar': {
      bgcolor: getDqsColor(dqsScore),  // filled portion color
      borderRadius: 3,
    },
  }}
/>
```

### Keyboard Accessibility — Enter/Space Handler

```typescript
const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()  // prevent space-bar page scroll
    onClick()
  }
}

// On the Card element:
<Card
  role="button"
  tabIndex={0}
  onClick={onClick}
  onKeyDown={handleKeyDown}
  aria-label={ariaLabel}
  ...
>
```

### Aria-Label Construction

```typescript
const ariaLabel = `${lobName}, DQS Score ${dqsScore}, ${datasetCount} datasets, ${statusCounts.pass} passing, ${statusCounts.warn} warning, ${statusCounts.fail} failing`
```

### File Structure — Exact Paths

```
dqs-dashboard/
  src/
    components/
      DqsScoreChip.tsx     ← EXISTING (4.6) — import from here
      TrendSparkline.tsx   ← EXISTING (4.6) — import from here
      DatasetCard.tsx      ← NEW: create this story
      index.ts             ← EXISTING — add DatasetCard export
  tests/
    components/
      theme.test.ts        ← EXISTING — do NOT touch
      DqsScoreChip.test.tsx← EXISTING — do NOT touch
      TrendSparkline.test.tsx ← EXISTING — do NOT touch
      DatasetCard.test.tsx ← NEW: create this story
```

Do NOT create: any pages, layouts, routes, API clients, or query hooks. This story is a pure display component.

### Test Setup — Already Configured

Story 4.6 already configured `vite.config.ts` with jsdom + setupFiles. Do NOT modify `vite.config.ts`.

All testing dependencies already installed: `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`.

**Mocking DqsScoreChip and TrendSparkline in tests:**

```typescript
// In DatasetCard.test.tsx
vi.mock('../../src/components/DqsScoreChip', () => ({
  DqsScoreChip: ({ score }: { score?: number }) => (
    <div data-testid="dqs-score-chip">{score ?? '—'}</div>
  ),
}))

vi.mock('../../src/components/TrendSparkline', () => ({
  TrendSparkline: () => <div data-testid="trend-sparkline" />,
}))
```

This avoids Recharts canvas issues while testing DatasetCard behavior in isolation.

**Render helper (same pattern as 4.6):**

```typescript
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import theme from '../../src/theme'

const renderWithTheme = (ui: React.ReactElement) =>
  render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>)
```

### TypeScript — Strict Mode Rules

- No `any` types — strict mode enforced
- Props interface defined locally in `DatasetCard.tsx` — NOT in `api/types.ts` (API contracts only)
- Event handler type: `React.KeyboardEvent<HTMLDivElement>`
- Use `interface DatasetCardProps` (not `type`)

### Naming Conventions

Per `project-context.md` for TypeScript/React:
- Component file: `DatasetCard.tsx` (PascalCase)
- Test file: `DatasetCard.test.tsx`
- Props interface: `DatasetCardProps`
- Internal variables: camelCase (`ariaLabel`, `chipColors`, `handleKeyDown`)

### Previous Story Intelligence (4.6)

From Story 4.6 completion notes:

1. **`getDqsColor` import path**: `import { getDqsColor } from '../theme'` — components are in `src/components/`, theme is at `src/theme.ts`. Use relative `../theme` not `../../theme`.
2. **`useTheme()` hook** for accessing theme inside components: `import { useTheme } from '@mui/material/styles'`
3. **All 99 tests pass after 4.6** — do NOT modify existing test files.
4. **No `as any` casts** — TypeScript strict mode, module augmentation in `theme.ts` handles custom variants.
5. **Barrel import pattern**: `import { DqsScoreChip } from './DqsScoreChip'` (direct component import inside components folder) for cross-component imports; downstream imports from `'../components'`.

### Anti-Patterns to Avoid

From `_bmad-output/project-context.md`:
- **NEVER hardcode hex color values** — use `getDqsColor(score)`, `theme.palette.success.main`, etc.
- **NEVER use `any` type** — strict TypeScript
- **NEVER use `useEffect + fetch`** — no API calls in this component
- **NEVER use spinning loaders** — skeletons are handled by parent page (Story 4.9), not this component

Additional for this story:
- **NEVER recreate DqsScoreChip or TrendSparkline** — import from existing components
- **NEVER add routing logic inside DatasetCard** — it calls `onClick` prop; navigation is caller's responsibility
- **NEVER use MUI `Card` `elevation` prop** — `boxShadow: 'none'` always per UX spec
- **NEVER omit keyboard handler** — card must be keyboard-accessible (Enter/Space)

### Architecture Compliance Checklist

- [ ] Component lives in `src/components/DatasetCard.tsx` — per architecture structure spec
- [ ] Test lives in `tests/components/DatasetCard.test.tsx` — per project-context.md testing rules
- [ ] No API calls in component — accepts all data via props
- [ ] No routing inside component — calls onClick callback
- [ ] Uses MUI ThemeProvider context — no raw CSS files
- [ ] Exports via barrel `index.ts` — downstream components import from `'../components'`
- [ ] Uses existing DqsScoreChip + TrendSparkline — no wheel reinvention
- [ ] Keyboard accessible: role="button", tabIndex={0}, onKeyDown Enter/Space

### References

- Epic 4 Story 4.7 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md#Story 4.7`
- UX Component Strategy (DatasetCard anatomy): `_bmad-output/planning-artifacts/ux-design-specification/component-strategy.md#DatasetCard (LOB Card)`
- Visual Design Foundation (colors, spacing, borders): `_bmad-output/planning-artifacts/ux-design-specification/visual-design-foundation.md`
- Architecture — dqs-dashboard structure: `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`
- Theme file (getDqsColor, getDqsColorLight, palette tokens): `dqs-dashboard/src/theme.ts`
- DqsScoreChip component (import from here): `dqs-dashboard/src/components/DqsScoreChip.tsx`
- TrendSparkline component (import from here): `dqs-dashboard/src/components/TrendSparkline.tsx`
- Components barrel (update this): `dqs-dashboard/src/components/index.ts`
- Story 4.6 completion notes (test setup, import paths): `_bmad-output/implementation-artifacts/4-6-dqsscorechip-trendsparkline-components.md`
- Project Context rules: `_bmad-output/project-context.md`
- Existing tests (do NOT modify): `dqs-dashboard/tests/components/DqsScoreChip.test.tsx`, `TrendSparkline.test.tsx`, `theme.test.ts`
- Vite config (already correct, do NOT modify): `dqs-dashboard/vite.config.ts`
- Package.json (all deps already installed): `dqs-dashboard/package.json`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation completed without issues.

### Completion Notes List

- Created `DatasetCard.tsx` with full LOB card anatomy: LOB name (h3), dataset count (body2), DqsScoreChip size="lg", MUI LinearProgress colored via getDqsColor, TrendSparkline size="md", and 3 MUI Chip status indicators (pass/warn/fail).
- Card uses MUI Card+CardContent with sx props only (no raw CSS). Hover border transition to primary.main (#1565C0) and focus-visible outline implemented.
- Click handler attached to Card element; keyboard handler activates onClick on Enter/Space (Space prevents default page scroll).
- aria-label: "{lobName}, DQS Score {dqsScore}, {datasetCount} datasets, {pass} passing, {warn} warning, {fail} failing"
- DatasetCard exported from barrel index.ts.
- All 33 new DatasetCard tests pass (unskipped from it.skip → it). Total test suite: 132 tests pass, 0 skipped, 0 failures.
- No API calls, no routing, no useEffect+fetch, no hardcoded hex colors, no `any` types — all anti-patterns avoided.

### File List

- `dqs-dashboard/src/components/DatasetCard.tsx` (new)
- `dqs-dashboard/src/components/index.ts` (updated — add DatasetCard export)
- `dqs-dashboard/tests/components/DatasetCard.test.tsx` (new)

### Review Findings

- [ ] [Review][Patch] Stale TDD RED PHASE comment in test file header [dqs-dashboard/tests/components/DatasetCard.test.tsx:1-10] — **FIXED**: Updated header comment to reflect GREEN PHASE with active `it()` tests.
- [x] [Review][Defer] `previousScore` passthrough to DqsScoreChip not explicitly tested [dqs-dashboard/tests/components/DatasetCard.test.tsx] — deferred, pre-existing; implementation is correct, minor coverage gap only.

## Change Log

| Date | Change |
|------|--------|
| 2026-04-03 | Story 4.7 created — DatasetCard (LOB Card) component story ready for dev. |
| 2026-04-03 | Story 4.7 implemented — DatasetCard component, barrel export, and 33 tests (all passing). Status: review. |
| 2026-04-03 | Story 4.7 code review complete — 1 patch fixed (stale test comment), 1 deferred, 4 dismissed. Status: done. |
