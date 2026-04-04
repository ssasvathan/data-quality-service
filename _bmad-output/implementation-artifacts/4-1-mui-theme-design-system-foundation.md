# Story 4.1: MUI Theme & Design System Foundation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **developer**,
I want a custom MUI 7 theme implementing the DQS visual design (color palette, typography, spacing),
so that all dashboard components render with a consistent, professional look from the start.

## Acceptance Criteria

1. **Given** the dqs-dashboard project **When** the theme is applied via MUI ThemeProvider **Then** the neutral palette is configured (neutral-50 #FAFAFA through neutral-900 #212121)
2. **And** semantic colors map to DQS Score thresholds: success #2E7D32 (>=80), warning #ED6C02 (60-79), error #D32F2F (<60) with light tint variants
3. **And** primary accent is #1565C0 for links, active states, and focus
4. **And** typography uses system font stack with defined scale (h1 24px/600 through caption 12px/400, score 28px/700, score-sm 18px/700, mono 13px)
5. **And** spacing follows 8px grid (xs=4, sm=8, md=16, lg=24, xl=32, 2xl=48)
6. **And** card styling uses 1px neutral-200 border, 8px border-radius, no box-shadow

## Tasks / Subtasks

- [x] Task 1: Replace existing `theme.ts` with complete MUI 7 custom theme (AC: 1, 2, 3, 4, 5, 6)
  - [x] Define `palette` with full neutral tokens (#FAFAFA to #212121), semantic colors (success/warning/error with light variants), and primary (#1565C0 / #E3F2FD)
  - [x] Define `typography` with system font stack, custom scale including `score`, `score-sm`, and `mono` variants
  - [x] Define `spacing` override using 8px base unit and named tokens (xs=4, sm=8, md=16, lg=24, xl=32, 2xl=48)
  - [x] Define `components` overrides for `MuiCard` (1px neutral-200 border, 8px border-radius, elevation 0)
  - [x] Export `getDqsColor(score: number): string` utility function from `theme.ts` (returns success/warning/error main color based on thresholds)
  - [x] Export `getDqsColorLight(score: number): string` utility returning light tint variant
- [x] Task 2: Verify ThemeProvider is correctly applied in `App.tsx` (AC: 1)
  - [x] Confirm `ThemeProvider` wraps `CssBaseline` and application tree
  - [x] Confirm `CssBaseline` is present to normalize browser styles
- [x] Task 3: Write Vitest unit tests for theme utilities (AC: 2)
  - [x] Test `getDqsColor(87)` returns success color (#2E7D32)
  - [x] Test `getDqsColor(70)` returns warning color (#ED6C02)
  - [x] Test `getDqsColor(55)` returns error color (#D32F2F)
  - [x] Test `getDqsColor(80)` (boundary) returns success color
  - [x] Test `getDqsColor(60)` (boundary) returns warning color
  - [x] Test `getDqsColorLight(87)` returns success-light (#E8F5E9)
  - [x] Test theme object: verify palette.primary.main equals #1565C0
  - [x] Test theme object: verify palette.background.default equals #FAFAFA
- [x] Task 4: Update `index.css` baseline styles to align with theme (AC: 5)
  - [x] Set `body` background to `neutral-50` (#FAFAFA) — matches `palette.background.default`
  - [x] Remove any conflicting default styles from Vite scaffold

## Dev Notes

### Critical: Existing `theme.ts` — What Is Wrong and Must Be Fixed

The existing `dqs-dashboard/src/theme.ts` (initialised during project scaffold) has **incorrect values** that do not match the UX spec:

| Field | Current (WRONG) | Required (UX Spec) |
|---|---|---|
| `palette.error.main` | `#C62828` | `#D32F2F` |
| `palette.warning.main` | `#E65100` | `#ED6C02` |
| `palette.background.default` | `#F5F6F8` | `#FAFAFA` (neutral-50) |
| `typography.fontFamily` | `"Roboto", "Helvetica", "Arial"` | System font stack (see below) |
| `palette` | Missing neutral tokens, missing light tints, missing score typography variants | All required |

**This story is a full replacement of `theme.ts`, not a patch. Delete the existing content and write from scratch.**

### MUI 7 Theme API — createTheme

MUI 7.3.9 (installed) uses the same `createTheme` API as MUI 5/6 with minor additions:

```typescript
import { createTheme } from '@mui/material/styles'
```

Key MUI 7 patterns to follow:
- Use `palette.augmentColor()` for custom palette keys if needed — but for this story, override standard keys (`primary`, `secondary`, `error`, `warning`, `success`, `background`)
- Custom typography variants (`score`, `score-sm`, `mono`) require both `createTheme` definition AND TypeScript module augmentation to be type-safe
- `components.MuiCard.styleOverrides.root` for global card defaults
- `components.MuiCssBaseline.styleOverrides` can inject global CSS if needed

### Complete Palette Specification

```typescript
// Neutral tokens — for background.default, card backgrounds, borders, text
neutral: {
  50: '#FAFAFA',   // Page background
  100: '#F5F5F5',  // Card background
  200: '#EEEEEE',  // Borders, dividers
  300: '#E0E0E0',  // Disabled states
  500: '#9E9E9E',  // Secondary text, icons
  700: '#616161',  // Body text
  900: '#212121',  // Headings, primary text
}

// Semantic — DQS Score status
palette.success.main = '#2E7D32'  // DQS Score >= 80
palette.success.light = '#E8F5E9' // success-light tint

palette.warning.main = '#ED6C02'  // DQS Score 60-79
palette.warning.light = '#FFF3E0' // warning-light tint

palette.error.main = '#D32F2F'    // DQS Score < 60
palette.error.light = '#FFEBEE'   // error-light tint

// Accent — links, active states, focus
palette.primary.main = '#1565C0'
palette.primary.light = '#E3F2FD' // primary-light for selected/active

// Backgrounds
palette.background.default = '#FAFAFA'  // neutral-50 — page
palette.background.paper = '#FFFFFF'    // Card/panel surfaces
```

### Complete Typography Specification

```typescript
// System font stack — no web font downloads
fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
fontFamilyMono: '"SF Mono", "Fira Code", "Fira Mono", Menlo, monospace'

// Type scale (all sizes in px, converted to rem for MUI)
h1: { fontSize: '1.5rem', fontWeight: 600, lineHeight: 1.3 }    // 24px — page titles
h2: { fontSize: '1.25rem', fontWeight: 600, lineHeight: 1.3 }   // 20px — section headers
h3: { fontSize: '1rem', fontWeight: 600, lineHeight: 1.4 }      // 16px — card titles
body1: { fontSize: '0.875rem', fontWeight: 400, lineHeight: 1.5 }  // 14px — primary body
body2: { fontSize: '0.8125rem', fontWeight: 400, lineHeight: 1.5 } // 13px — secondary text
caption: { fontSize: '0.75rem', fontWeight: 400, lineHeight: 1.4 } // 12px — timestamps, labels

// Custom variants — require module augmentation
score: { fontSize: '1.75rem', fontWeight: 700, lineHeight: 1.0 }   // 28px — DQS Score large
scoreSm: { fontSize: '1.125rem', fontWeight: 700, lineHeight: 1.0 } // 18px — DQS Score compact
mono: { fontSize: '0.8125rem', fontWeight: 400, lineHeight: 1.4, fontFamily: monospace }  // 13px technical values
```

**TypeScript module augmentation required** for custom variants. Add to `theme.ts`:
```typescript
declare module '@mui/material/styles' {
  interface TypographyVariants {
    score: React.CSSProperties
    scoreSm: React.CSSProperties
    mono: React.CSSProperties
  }
  interface TypographyVariantsOptions {
    score?: React.CSSProperties
    scoreSm?: React.CSSProperties
    mono?: React.CSSProperties
  }
}
declare module '@mui/material/Typography' {
  interface TypographyPropsVariantOverrides {
    score: true
    scoreSm: true
    mono: true
  }
}
```

### Spacing Specification

MUI's `spacing` function maps integer indices. Use theme.spacing() calls like `theme.spacing(1)` = 8px.

For named tokens, document the mapping:
- xs = `theme.spacing(0.5)` → 4px
- sm = `theme.spacing(1)` → 8px
- md = `theme.spacing(2)` → 16px
- lg = `theme.spacing(3)` → 24px
- xl = `theme.spacing(4)` → 32px
- 2xl = `theme.spacing(6)` → 48px

The default MUI `spacing` base is 8px — **do not override** `theme.spacing`. It already matches the UX spec. Document the named aliases as comments in the theme file for developer reference.

### Card Component Overrides

```typescript
components: {
  MuiCard: {
    defaultProps: {
      elevation: 0,  // No box-shadow
    },
    styleOverrides: {
      root: {
        border: '1px solid #EEEEEE',  // neutral-200
        borderRadius: '8px',
        boxShadow: 'none',
      },
    },
  },
  MuiCardContent: {
    styleOverrides: {
      root: {
        padding: '16px',  // md spacing
        '&:last-child': { paddingBottom: '16px' },
      },
    },
  },
}
```

### getDqsColor Utility Functions

These must live in `theme.ts` and be exported — they are used by DqsScoreChip (Story 4.6) and TrendSparkline (Story 4.6). Getting the threshold boundaries right is critical.

```typescript
// Thresholds per UX spec: success >= 80, warning 60-79, error < 60
export function getDqsColor(score: number): string {
  if (score >= 80) return '#2E7D32'  // success
  if (score >= 60) return '#ED6C02'  // warning
  return '#D32F2F'                    // error
}

export function getDqsColorLight(score: number): string {
  if (score >= 80) return '#E8F5E9'  // success-light
  if (score >= 60) return '#FFF3E0'  // warning-light
  return '#FFEBEE'                    // error-light
}
```

**Note:** score === 80 is success (>=80). score === 60 is warning (60-79, i.e., >=60). These boundary conditions MUST be tested.

### File Structure

Only the following files are touched in this story:

```
dqs-dashboard/
  src/
    theme.ts          ← Full rewrite (primary deliverable)
    index.css         ← Minor update: body background to #FAFAFA
    App.tsx           ← Verify only — no changes expected
  tests/
    components/
      theme.test.ts   ← New: unit tests for getDqsColor, getDqsColorLight, palette constants
```

**Do NOT create** `DqsScoreChip`, `TrendSparkline`, or `DatasetCard` components in this story. They belong to Stories 4.6 and 4.7. This story is exclusively about the theme foundation.

**Do NOT touch** any backend files, docker-compose, or non-dashboard components.

### Test Setup — Vitest

The project uses Vitest (installed as dev dependency from scaffold, check `package.json`). If Vitest is not yet installed:
```bash
npm install -D vitest @vitest/ui
```

Tests for the theme utility go in `dqs-dashboard/tests/components/theme.test.ts`.

**Test pattern (Vitest, no React Testing Library needed for pure utility tests):**

```typescript
import { describe, it, expect } from 'vitest'
import { getDqsColor, getDqsColorLight } from '../../src/theme'
import theme from '../../src/theme'
```

No DOM rendering needed — theme utilities are pure functions.

### App.tsx — Verify No Changes Needed

The existing `App.tsx` already correctly wraps the app with `ThemeProvider` and `CssBaseline`. After replacing `theme.ts`, the new theme values will automatically propagate. No `App.tsx` changes are required unless the verification reveals missing wrapping.

### Anti-Patterns to Avoid

- **DO NOT** hardcode color values (`#2E7D32`, `#D32F2F`, etc.) outside of `theme.ts`. All downstream components must use `theme.palette.success.main`, `theme.palette.error.main`, or the exported `getDqsColor()` function.
- **DO NOT** add `fontFamily: '"Roboto", "Helvetica", "Arial"'` — this is the wrong font stack. Use the system font stack specified above.
- **DO NOT** use `@fontsource/roboto` or any web font imports — system fonts only, per UX spec ("zero-load-time").
- **DO NOT** set `elevation` > 0 on Card components — flat design, no shadow stack.
- **DO NOT** use MUI's default primary color (#1976D2) — override to #1565C0 exactly.
- **DO NOT** skip the TypeScript module augmentation for custom typography variants — without it, `<Typography variant="score">` will produce TypeScript errors in subsequent stories.

### Project Context Rules (dashboard-specific)

From `_bmad-output/project-context.md`:
- Strict mode TypeScript — no `any` types
- Define API response types in `api/types.ts` — never inline (not relevant this story, but don't add types here)
- Use `interface` for API contracts, `type` for unions/intersections
- **Never use `any` type in TypeScript** — applies to module augmentation declarations too
- **MUI 7 custom theme with muted palette and semantic colors per UX spec** — this story is the single implementation point for that rule

### Component Structure Notes

The `dqs-dashboard/src/` directory already has the correct structure matching architecture:
```
src/
  theme.ts        ← This story's primary file
  App.tsx         ← Already wired correctly
  main.tsx        ← No changes
  api/            ← No changes
  components/     ← No changes (DqsScoreChip etc. in later stories)
  layouts/        ← No changes
  pages/          ← No changes
  index.css       ← Minor baseline update
```

The `tests/components/` directory exists but is empty — create `theme.test.ts` there.

### References

- UX Spec — Visual Design Foundation: `_bmad-output/planning-artifacts/ux-design-specification/visual-design-foundation.md`
- UX Spec — Design System Foundation: `_bmad-output/planning-artifacts/ux-design-specification/design-system-foundation.md`
- UX Spec — Component Strategy: `_bmad-output/planning-artifacts/ux-design-specification/component-strategy.md`
- Architecture — Frontend Architecture table: `_bmad-output/planning-artifacts/architecture.md#Frontend Architecture`
- Architecture — dqs-dashboard structure: `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`
- Project Context — TypeScript rules: `_bmad-output/project-context.md#Language-Specific Rules`
- Project Context — React rules: `_bmad-output/project-context.md#Framework-Specific Rules`
- Epic 4 Story 4.1 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md`
- Existing theme.ts (to replace): `dqs-dashboard/src/theme.ts`
- Existing App.tsx (verify only): `dqs-dashboard/src/App.tsx`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

No blocking issues encountered. Implementation was straightforward.

### Completion Notes List

- **Task 1 (theme.ts full rewrite):** Replaced scaffold theme entirely. Full palette (neutral tokens, semantic with light tints, primary #1565C0/#E3F2FD), system font stack, complete typography scale (h1–caption + score/scoreSm/mono custom variants with TypeScript module augmentation), MuiCard overrides (1px #EEEEEE border, 8px radius, elevation 0, no shadow), MuiCardContent padding 16px. Spacing uses default MUI 8px base — not overridden, named aliases documented in comments.
- **Task 2 (App.tsx verification):** App.tsx already correctly wraps with `<ThemeProvider theme={theme}><CssBaseline />`. No changes needed.
- **Task 3 (tests):** All 46 ATDD tests in `tests/components/theme.test.ts` pass — covers getDqsColor thresholds (including boundaries at 80 and 60), getDqsColorLight variants, palette assertions (primary, background, success/warning/error with light tints), typography font family and scale, spacing grid, and MuiCard overrides.
- **Task 4 (index.css):** Replaced Vite scaffold CSS with minimal baseline — body background #FAFAFA (neutral-50), all conflicting scaffold styles removed.

### File List

- dqs-dashboard/src/theme.ts (modified — full rewrite)
- dqs-dashboard/src/index.css (modified — replaced Vite scaffold with DQS baseline)
- dqs-dashboard/tests/components/theme.test.ts (pre-existing ATDD file — no changes required, all 46 tests now pass)

## Tasks / Subtasks (continued)

### Review Findings

- [x] [Review][Patch] `import React from 'react'` should be `import type React from 'react'` — only used as type in module augmentation [theme.ts:1] — **fixed**
- [x] [Review][Patch] Utility functions `getDqsColor`/`getDqsColorLight` defined after `export default` — unconventional layout [theme.ts:156-176] — **fixed** (moved before `export default`)
- [x] [Review][Patch] `index.css` hardcodes `background-color: #FAFAFA` duplicating theme palette — dual source of truth; MUI CssBaseline sets body background from theme automatically [index.css:8] — **fixed** (removed hardcoded color, added clarifying comment)
- [x] [Review][Patch] AC 1 requires "neutral palette is configured" but no `theme.palette.neutral` key existed — downstream components (Stories 4.6/4.7) would need to hardcode neutral values [theme.ts] — **fixed** (added `palette.neutral` with full token set + TypeScript module augmentation + 7 new tests)
- [x] [Review][Patch] Test uses `as any` cast for custom typography variants — violates "Never use `any`" project rule; module augmentation makes direct access type-safe [theme.test.ts:208-230] — **fixed** (removed `as any` and eslint-disable comments)
- [x] [Review][Defer] `getDqsColor`/`getDqsColorLight` have no guard for `NaN`/`Infinity`/negative inputs — unspecified behavior for invalid scores [theme.ts:82-95] — deferred, not in AC scope for Story 4.1; consumer validation responsibility
- [x] [Review][Defer] `fontFamilySans`/`fontFamilyMono` constants not exported — may force duplication in future stories needing direct font stack reference [theme.ts:46-50] — deferred, no downstream need identified yet

## Change Log

| Date | Change |
|---|---|
| 2026-04-03 | Full implementation of Story 4.1 — rewrote theme.ts with correct MUI 7 theme (palette, typography, spacing, MuiCard overrides), added getDqsColor/getDqsColorLight utility functions, updated index.css baseline. All 46 ATDD tests pass. |
| 2026-04-03 | Code review — fixed 5 patch findings: import type for React, utility function placement, index.css dual-source-of-truth, missing theme.palette.neutral key (AC 1 compliance), removed as-any casts in tests. Added 7 neutral palette tests. Total: 53 tests passing. |
