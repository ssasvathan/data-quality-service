# Story 4.15: Accessibility & Viewport Polish

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user**,
I want the dashboard to meet WCAG 2.1 AA accessibility standards and handle desktop viewports gracefully,
so that keyboard-only users and screen reader users can access all functionality.

## Acceptance Criteria

1. **Given** the dashboard is loaded **When** I press Tab **Then** a "Skip to main content" link is visible, and pressing Enter skips to the `<main>` region
2. **Given** I navigate using only the keyboard **When** I Tab through all views **Then** all interactive elements (cards, table rows, search, breadcrumbs, time range toggle) are focusable and activatable via Enter/Space **And** MUI default focus rings are visible on all focused elements
3. **Given** a time range change triggers data reload **When** new data arrives **Then** `aria-live="polite"` announces "Data updated for {range} range" without interrupting screen reader flow
4. **Given** a viewport width of 1280px (standard) **When** the dashboard renders **Then** the layout uses 3-column LOB card grid, full-width table, and 380px left panel in Master-Detail
5. **Given** a viewport width > 1440px **When** the dashboard renders **Then** content is centered at max-width 1440px with side margins
6. **Given** a viewport width < 1280px **When** the dashboard renders **Then** LOB cards reflow to 2 columns, table gains horizontal scroll, and left panel narrows to 300px
7. **And** axe-core (@axe-core/react) is installed and configured to run during development
8. **And** all custom components pass axe-core automated checks with zero violations

## Tasks / Subtasks

- [x] Task 1: Add `aria-live` region for time-range data-update announcements (AC: 3)
  - [x] Add a visually-hidden `<div aria-live="polite" aria-atomic="true">` to `AppLayout.tsx`, positioned outside the viewport (CSS off-screen technique, same as skip link) — render it unconditionally
  - [x] In `AppLayout.tsx`, track a `liveMessage` state with `useState<string>('')`; set it when `timeRange` changes AND after React Query refetch completes (use `isFetching` transition: `isFetching` changes from `true` to `false` after time range change)
  - [x] Message format: `"Data updated for {timeRange} range"` (e.g., "Data updated for 30d range")
  - [x] Import `useSummary` is already in `AppLayout.tsx`; destructure `isFetching: summaryFetching` from it to detect when global refetch completes
  - [x] Use `useEffect` watching `summaryFetching`: when it transitions `true → false` AND timeRange has been changed, set the live message; clear it after 3 seconds with `setTimeout`
  - [x] Place the live region `<div>` as the FIRST child inside the outer `<Box>` wrapper (before the skip link), so screen readers encounter it early

- [x] Task 2: Verify and reinforce skip link (AC: 1)
  - [x] The skip link `href="#main-content"` already exists in `AppLayout.tsx` — verify the `<main id="main-content">` target is present
  - [x] Add Vitest test to confirm skip link renders with correct `href="#main-content"` text "Skip to main content"
  - [x] Add Vitest test to confirm `<main>` with `id="main-content"` is present in the DOM

- [x] Task 3: Implement MUI breakpoint configuration for viewport polish (AC: 4, 5, 6)
  - [x] In `theme.ts`, add `breakpoints` config to the `createTheme()` call:
    ```ts
    breakpoints: {
      values: { xs: 0, sm: 1024, md: 1280, lg: 1440, xl: 1920 }
    }
    ```
  - [x] In `SummaryPage.tsx`, update the LOB card `Grid` item sizing:
    - At `md` (1280px): 3 columns — `size={{ xs: 12, sm: 6, md: 4 }}`
    - At `sm` (< 1280px): 2 columns — the `sm: 6` already achieves this
    - Verify the existing `size={{ xs: 12, sm: 6, md: 4 }}` props already implement the correct responsive column counts with the new breakpoint values
  - [x] In `AppLayout.tsx`, wrap the `<Box component="main">` content in a centering Box:
    ```tsx
    <Box component="main" id="main-content" sx={{ flexGrow: 1, p: 3 }}>
      <Box style={{ maxWidth: '1440px', marginLeft: 'auto', marginRight: 'auto', width: '100%' }}>
        {children}
      </Box>
    </Box>
    ```
    This centers content at max 1440px for wide viewports (AC5) while filling standard 1280px (AC4)
  - [x] In `LobDetailPage.tsx`, wrap the DataGrid in a Box with `sx={{ overflowX: 'auto' }}` to enable horizontal scroll on narrow viewports (AC6)
  - [x] In `DatasetDetailPage.tsx`, update the left panel `sx` width: change from static `380px` to responsive:
    ```tsx
    sx={{ width: { sm: 300, md: 380 }, ... }}
    ```
    This collapses to 300px below `md` (1280px) breakpoint (AC6)

- [x] Task 4: Install and configure @axe-core/react (AC: 7, 8)
  - [x] Install `@axe-core/react` as a devDependency: `npm install --save-dev @axe-core/react`
  - [x] In `main.tsx`, add conditional axe initialization for development mode only:
    ```tsx
    if (import.meta.env.DEV) {
      const axe = await import('@axe-core/react')
      const ReactDOM = await import('react-dom')
      axe.default(React, ReactDOM, 1000)
    }
    ```
  - [x] Wrap the dynamic import in a conditional `import.meta.env.DEV` guard — axe-core must NOT run in production builds
  - [x] Run axe in dev mode (`npm run dev`) and fix any violations reported in the browser console before this story is marked done

- [x] Task 5: Write Vitest tests for accessibility and responsive behaviors (AC: 1–8)
  - [x] **`tests/layouts/AppLayout.test.tsx`** — add new `describe('accessibility and viewport')` block:
    - Skip-link renders with correct `href` and text (AC1)
    - `<main id="main-content">` landmark present (AC1)
    - `aria-live="polite"` region exists in DOM (AC3)
    - Time range toggle has `aria-label="time range"` (AC2)
    - After timeRange changes from `7d` to `30d`, live region text is set (AC3) — mock `useSummary` to return `isFetching: false` after simulated update
  - [x] **`tests/components/DatasetCard.test.tsx`** — add new tests:
    - Card has `role="button"` and `tabIndex={0}` (AC2 — already passes, just confirm no regression)
    - Card `aria-label` includes LOB name, score, count, and status summary
    - Enter key triggers `onClick` (AC2)
    - Space key triggers `onClick` (AC2)
  - [x] **`tests/pages/SummaryPage.test.tsx`** — add viewport test:
    - Grid items rendered with correct MUI Grid size props for 3-column layout at md breakpoint
  - [x] All 374 existing tests continue to pass — final count: 398 passing, 0 skipped

## Dev Notes

### What Already Exists (DO NOT RECREATE)

The following accessibility features were implemented in earlier stories. This story EXTENDS and POLISHES — it does NOT rewrite:

**AppLayout.tsx** (`dqs-dashboard/src/layouts/AppLayout.tsx`):
- Skip link (`href="#main-content"`) — fully implemented, visually hidden, visible on focus
- `<main id="main-content">` landmark region — present
- `<nav aria-label="breadcrumb">` — provided by MUI Breadcrumbs via `AppBreadcrumbs`
- `<header>` landmark — AppBar renders as `<header>` by default in MUI 7
- Time range toggle: `aria-label="time range"` on ToggleButtonGroup
- **Missing:** `aria-live` region for data update announcements (AC3)

**DatasetCard.tsx** (`dqs-dashboard/src/components/DatasetCard.tsx`):
- `role="button"`, `tabIndex={0}` — present
- `aria-label` with full context — present
- Enter/Space keyboard handlers — present
- `&:focus-visible` outline — present

**DqsScoreChip.tsx** (`dqs-dashboard/src/components/DqsScoreChip.tsx`):
- `aria-label` with score, status, and trend info — present

**TrendSparkline.tsx** (`dqs-dashboard/src/components/TrendSparkline.tsx`):
- `role="img"` with descriptive `aria-label` — present

**DatasetInfoPanel.tsx** (`dqs-dashboard/src/components/DatasetInfoPanel.tsx`):
- Uses `<dl>/<dt>/<dd>` semantic structure — present
- Copy button `aria-label="Copy HDFS path to clipboard"` — present

### Breakpoint Configuration — Critical Detail

The UX spec defines custom MUI breakpoint values. These must be added to `theme.ts`:

```ts
// From responsive-design-accessibility.md#Breakpoint Strategy
breakpoints: {
  values: { xs: 0, sm: 1024, md: 1280, lg: 1440, xl: 1920 }
}
```

**Impact on existing Grid usage:** `SummaryPage.tsx` already uses `size={{ xs: 12, sm: 6, md: 4 }}`. With these breakpoint values:
- `xs: 12` → single column below 1024px (not a supported use case per UX spec)
- `sm: 6` → 2 columns at 1024px–1279px (compact degradation per AC6)
- `md: 4` → 3 columns at 1280px+ (standard layout per AC4)

This is correct — the Grid `size` props are already written correctly, the breakpoints just need to be configured in the theme.

**CAUTION:** Adding breakpoints to `theme.ts` affects ALL MUI components that use theme breakpoints. Verify that `DatasetDetailPage.tsx` and `LobDetailPage.tsx` don't use hardcoded breakpoint names (`xs`, `sm`, `md`) with assumptions about their default pixel values.

### aria-live Region Pattern

Standard off-screen technique for screen reader announcements that are not visible in the DOM:

```tsx
// In AppLayout.tsx — add to state
const [liveMessage, setLiveMessage] = useState('')

// Add to JSX (first child of outer Box, before skip link)
<Box
  aria-live="polite"
  aria-atomic="true"
  sx={{
    position: 'absolute',
    width: '1px',
    height: '1px',
    padding: 0,
    margin: '-1px',
    overflow: 'hidden',
    clip: 'rect(0, 0, 0, 0)',
    whiteSpace: 'nowrap',
    border: 0,
  }}
>
  {liveMessage}
</Box>

// Trigger in useEffect watching isFetching transition
const prevFetchingRef = useRef(false)
useEffect(() => {
  if (prevFetchingRef.current && !summaryFetching) {
    setLiveMessage(`Data updated for ${timeRange} range`)
    const timer = setTimeout(() => setLiveMessage(''), 3000)
    return () => clearTimeout(timer)
  }
  prevFetchingRef.current = summaryFetching
}, [summaryFetching, timeRange])
```

Note: `useSummary` is already imported in `AppLayout.tsx`. Destructure `isFetching` as `summaryFetching` to avoid name collision with the existing `dismissed` state variable.

### Max-Width Centering Pattern (AC5)

Standard approach — wrap page content with a max-width Box:

```tsx
// In AppLayout.tsx, modify the <Box component="main"> section:
<Box
  component="main"
  id="main-content"
  sx={{ flexGrow: 1, p: 3 }}
>
  <Box sx={{ maxWidth: 1440, mx: 'auto', width: '100%' }}>
    {children}
  </Box>
</Box>
```

This achieves:
- 1280px standard: content fills width normally (container wider than max)
- > 1440px wide: content centers at 1440px with side margins growing
- Does NOT require changing any page-level component

### DatasetDetailPage Left Panel Responsive Width (AC6)

In `DatasetDetailPage.tsx`, the left panel is a MUI `Box` with hardcoded width. Find it by looking for `width: 380` or similar. Update to:

```tsx
sx={{
  width: { sm: 300, md: 380 },
  minWidth: { sm: 300, md: 380 },
  // ... other existing styles
}}
```

With the custom breakpoints (`sm: 1024`, `md: 1280`), this means:
- Below 1280px: 300px left panel
- At 1280px+: 380px left panel

### @axe-core/react Integration Notes

- Version: use `@axe-core/react` latest compatible with React 19 (project uses React `^19.2.4`)
- axe-core version 4.x supports React 19
- The `main.tsx` change uses top-level `await` with dynamic import — Vite supports this natively
- Do NOT add axe to `vite.config.ts` or vitest config — development-only, browser-only
- axe reports violations to the browser DevTools console — not a test runner tool
- The `1000` ms delay parameter gives React time to render before axe scans

### LobDetailPage DataGrid Horizontal Scroll (AC6)

In `LobDetailPage.tsx`, the DataGrid should already be inside a container. Add `overflowX: 'auto'` to the DataGrid's parent Box:

```tsx
<Box sx={{ overflowX: 'auto', width: '100%' }}>
  <DataGrid ... />
</Box>
```

This ensures that at compact breakpoints, the full DataGrid remains accessible via scroll rather than being truncated.

### File Structure — Files to Create/Modify

```
dqs-dashboard/
  src/
    theme.ts                          ← MODIFY: add breakpoints config
    main.tsx                          ← MODIFY: add conditional axe-core init
    layouts/
      AppLayout.tsx                   ← MODIFY: add aria-live region, max-width centering
    pages/
      SummaryPage.tsx                 ← VERIFY: Grid size props correct with new breakpoints (likely no change needed)
      LobDetailPage.tsx               ← MODIFY: add overflowX: auto to DataGrid container
      DatasetDetailPage.tsx           ← MODIFY: responsive left panel width
  tests/
    layouts/
      AppLayout.test.tsx              ← MODIFY: add aria-live, skip link, landmark tests
    components/
      DatasetCard.test.tsx            ← MODIFY: add/verify keyboard and aria tests
    pages/
      SummaryPage.test.tsx            ← MODIFY: add breakpoint-related grid test

package.json                          ← MODIFY: add @axe-core/react devDependency
```

**Do NOT touch:**
- `src/components/DqsScoreChip.tsx` — aria-label already complete
- `src/components/TrendSparkline.tsx` — aria-label already complete
- `src/components/DatasetInfoPanel.tsx` — semantic dl/dt/dd already present
- `src/api/types.ts` — no new API types needed
- `src/api/queries.ts` — no new hooks needed
- `src/App.tsx` — no routing changes needed

### MUI Patterns — Established in this Codebase

- Theme tokens only: `theme.palette.*`, `theme.spacing()` — NEVER hardcoded hex or px values
- Box `sx` prop for layout — never inline `style` except when test assertions require `element.style.*` (pattern established in Story 4.14)
- Named internal function components inside `AppLayout.tsx` (established pattern: `AppBreadcrumbs`, `GlobalSearch`, `LastUpdatedIndicator`, `RunFailedBanner`) — follow this pattern for any new internal component
- No `outline: none` on interactive elements — MUI focus rings preserved per UX spec
- React Router 7: import from `'react-router'` NOT `'react-router-dom'`
- No `any` TypeScript types

### Testing — Patterns from Previous Stories

```typescript
// AppLayout.test.tsx — add to existing describe block or new describe block:

it('renders skip link with correct href', () => {
  renderAppLayout('/')
  const skipLink = screen.getByText('Skip to main content')
  expect(skipLink).toHaveAttribute('href', '#main-content')
})

it('renders main landmark with correct id', () => {
  renderAppLayout('/')
  const main = screen.getByRole('main')
  expect(main).toHaveAttribute('id', 'main-content')
})

it('renders aria-live region', () => {
  renderAppLayout('/')
  const liveRegion = document.querySelector('[aria-live="polite"]')
  expect(liveRegion).toBeTruthy()
})
```

For the time range live message test, mock `useSummary` returning `isFetching: false` initially then `isFetching: false` after a simulated state change — this verifies the live region element exists and is structurally correct.

### Existing Test Count and Baseline

After Story 4.14: **374 tests pass, 0 skipped**. All 374 must continue to pass after this story.

### Anti-Patterns to Avoid

- **NEVER hardcode breakpoint pixel values in component `sx` props** — use theme breakpoint keys (`xs`, `sm`, `md`, `lg`) after configuring them in theme.ts
- **NEVER add `outline: none` or `outline: 0`** to any element — per UX spec, MUI focus rings must be preserved
- **NEVER include axe-core in production builds** — `import.meta.env.DEV` guard in main.tsx is mandatory
- **NEVER use `useEffect + fetch`** — TanStack Query only
- **NEVER use `any` type** — strict TypeScript
- **NEVER import from `'react-router-dom'`** — use `'react-router'` (React Router 7)
- **NEVER add spinning loaders** — skeletons only
- **NEVER recreate what already exists** — the skip link, landmark regions, and component aria-labels are already implemented; only extend

### Architecture Compliance

- Breakpoints in `theme.ts` only — single source of truth, never duplicated per-component
- `aria-live` region managed by `AppLayout` (the root layout) — not by individual pages or components
- `axe-core` as devDependency only — zero production bundle impact
- Max-width centering via `AppLayout` wrapper — no page-level changes required

### UX Spec References

From `ux-design-specification/responsive-design-accessibility.md`:

**Breakpoints (exact values required):**
```
breakpoints: { values: { xs: 0, sm: 1024, md: 1280, lg: 1440, xl: 1920 } }
```

**Viewport behaviors:**
- `< 1280px` (below `md`): 2-column card grid, horizontal table scroll, 300px left panel
- `1280px–1440px` (`md` to `lg`): Standard layout. 3-column cards, full table, 380px left panel
- `> 1440px` (above `lg`): Max-width 1440px centered, side margins grow

**Accessibility:**
- WCAG 2.1 Level AA target
- `aria-live="polite"` for time range change announcements
- Skip link, landmark regions, focus rings — all must be present and functional
- axe-core automated scan catches 60%+ of accessibility issues — run on every component in dev

From `ux-design-specification/ux-consistency-patterns.md`:
- `isFetching` refetch: sparklines fade to 50% opacity — already implemented in Story 4.14
- Live regions: announce "Data updated for 30-day range" without interrupting screen reader

### References

- Epic 4 Story 4.15 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md`
- UX Responsive/Accessibility spec: `_bmad-output/planning-artifacts/ux-design-specification/responsive-design-accessibility.md`
- Project Context rules (critical): `_bmad-output/project-context.md`
- Theme file (add breakpoints): `dqs-dashboard/src/theme.ts`
- AppLayout (add aria-live, max-width): `dqs-dashboard/src/layouts/AppLayout.tsx`
- main.tsx (add axe init): `dqs-dashboard/src/main.tsx`
- LobDetailPage (add overflow scroll): `dqs-dashboard/src/pages/LobDetailPage.tsx`
- DatasetDetailPage (responsive left panel): `dqs-dashboard/src/pages/DatasetDetailPage.tsx`
- SummaryPage (verify grid sizing): `dqs-dashboard/src/pages/SummaryPage.tsx`
- DatasetCard (keyboard/aria already done): `dqs-dashboard/src/components/DatasetCard.tsx`
- Story 4.14 learnings (MUI patterns, test patterns): `_bmad-output/implementation-artifacts/4-14-loading-empty-error-states.md`
- AppLayout tests: `dqs-dashboard/tests/layouts/AppLayout.test.tsx`
- DatasetCard tests: `dqs-dashboard/tests/components/DatasetCard.test.tsx`
- SummaryPage tests: `dqs-dashboard/tests/pages/SummaryPage.test.tsx`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation completed without blockers.

### Completion Notes List

- Task 1: Added `aria-live="polite" aria-atomic="true"` off-screen Box as FIRST child of AppLayout outer Box. Tracks `summaryFetching` isFetching transition (true→false) AND `timeRangeChangedRef` to fire announcement only after a user-initiated time range change. Message: "Data updated for {timeRange} range", cleared after 3s.
- Task 2: Skip link and `<main id="main-content">` already present; tests formally cover AC1 compliance. All tests activated (removed it.skip).
- Task 3: Added `breakpoints: { values: { xs: 0, sm: 1024, md: 1280, lg: 1440, xl: 1920 } }` to `theme.ts`. `SummaryPage.tsx` Grid size props `{ xs: 12, sm: 6, md: 4 }` were already correct. AppLayout max-width centering uses inline `style` prop (not sx) for `maxWidth: 1440px` and `marginLeft/Right: auto` because test assertions check `element.style.*`. `DatasetDetailPage.tsx` left panel updated from `xs: 300` to `sm: 300` with added `minWidth`. `LobDetailPage.tsx` DataGrid wrapped in `Box sx={{ overflowX: 'auto', width: '100%' }}`.
- Task 4: Installed `@axe-core/react` as devDependency. Added `import.meta.env.DEV` guard in `main.tsx` with top-level await dynamic import pattern. Also added `import React from 'react'` for axe.default(React, ...) call.
- Task 5: All 24 `it.skip` calls replaced with `it` across AppLayout.test.tsx (13), DatasetCard.test.tsx (8), SummaryPage.test.tsx (3). Final test count: 398 passing, 0 skipped.

### File List

- `dqs-dashboard/src/layouts/AppLayout.tsx` — added aria-live region, liveMessage state, isFetching useEffect, max-width centering Box
- `dqs-dashboard/src/theme.ts` — added custom breakpoints config
- `dqs-dashboard/src/main.tsx` — added @axe-core/react dev-mode initialization
- `dqs-dashboard/src/pages/LobDetailPage.tsx` — wrapped DataGrid in overflowX auto Box
- `dqs-dashboard/src/pages/DatasetDetailPage.tsx` — updated left panel width to responsive { sm: 300, md: 380 } with minWidth
- `dqs-dashboard/tests/layouts/AppLayout.test.tsx` — removed all it.skip (13 tests activated)
- `dqs-dashboard/tests/components/DatasetCard.test.tsx` — removed all it.skip (8 tests activated)
- `dqs-dashboard/tests/pages/SummaryPage.test.tsx` — removed all it.skip (3 tests activated)
- `dqs-dashboard/package.json` — added @axe-core/react devDependency
- `dqs-dashboard/package-lock.json` — updated with @axe-core/react dependencies
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — updated story status to review

### Review Findings

- [x] [Review][Patch] Duplicate React import in main.tsx [dqs-dashboard/src/main.tsx:1-2] — fixed: merged into single import
- [x] [Review][Patch] Unnecessary `?? false` null coalescing on summaryFetching [dqs-dashboard/src/layouts/AppLayout.tsx] — fixed: removed unnecessary operator
- [x] [Review][Patch] Wrong AC reference in skip link comment — AC6 should be AC1 [dqs-dashboard/src/layouts/AppLayout.tsx:382] — fixed: corrected AC reference
- [x] [Review][Patch] Stale "THIS TEST WILL FAIL" / RED PHASE comments in passing tests [tests/*.test.tsx] — fixed: removed stale ATDD-phase annotations from all 3 test files

## Change Log

- 2026-04-03: Implemented Story 4.15 — Accessibility & Viewport Polish. Added aria-live region (AC3), skip link tests (AC1/AC2), custom MUI breakpoints (AC4/AC5/AC6), max-width centering (AC5), DataGrid overflow scroll (AC6), responsive left panel (AC6), @axe-core/react devDependency (AC7/AC8). Activated all 24 previously skipped tests. Final: 398 tests pass, 0 skipped.
- 2026-04-03: Code review complete. 4 Low-severity patches applied (duplicate import, unnecessary null coalescing, wrong AC comment reference, stale test comments). 0 decision-needed, 0 deferred. Story status updated to done.
