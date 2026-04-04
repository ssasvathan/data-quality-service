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
story_id: 4-8-app-layout-with-fixed-header-routing-react-query
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/4-8-app-layout-with-fixed-header-routing-react-query.md
  - _bmad-output/project-context.md
  - _bmad/tea/config.yaml
  - dqs-dashboard/tests/setup.ts
  - dqs-dashboard/vite.config.ts
  - dqs-dashboard/src/layouts/AppLayout.tsx
  - dqs-dashboard/src/App.tsx
  - dqs-dashboard/src/api/queries.ts
  - dqs-dashboard/src/api/types.ts
  - .claude/skills/bmad-testarch-atdd/resources/knowledge/component-tdd.md
  - .claude/skills/bmad-testarch-atdd/resources/knowledge/data-factories.md
---

# ATDD Checklist: Story 4.8 — App Layout with Fixed Header, Routing & React Query

## TDD Red Phase (Current)

All tests generated with `it.skip()` — failing until feature implemented.

- **AppLayout Tests:** 23 tests (all skipped) — `tests/layouts/AppLayout.test.tsx`
- **TimeRangeContext Tests:** 14 tests (all skipped) — `tests/context/TimeRangeContext.test.tsx`
- **Total:** 37 failing acceptance tests

## Stack Detection

- **Detected stack:** `frontend`
- **Test framework:** Vitest + React Testing Library (jsdom)
- **Generation mode:** AI generation (no browser recording — component tests only)
- **Execution mode:** Sequential (no Playwright/E2E framework configured)
- **Note:** No API subagent or E2E subagent needed — this story introduces no new backend API endpoints and the project has no Playwright/Cypress configured. All ATDD coverage is component-level.

## Acceptance Criteria Coverage

| AC | Description | Test File | Tests | Priority |
|----|-------------|-----------|-------|----------|
| AC1 | Fixed header visible with breadcrumbs, time range toggle, and search bar | AppLayout.test.tsx | 4 | P0 |
| AC2 | Routes render correct page component with breadcrumbs reflecting path | AppLayout.test.tsx | 7 | P0/P1 |
| AC3 | Time range change invalidates all React Query cached queries | TimeRangeContext.test.tsx + AppLayout.test.tsx | 8 | P0 |
| AC4 | Browser back/forward navigation and breadcrumbs update | AppLayout.test.tsx (routing tests) | 3 | P1 |
| AC5 | `<header>`, `<main>`, `<nav>` landmark regions present | AppLayout.test.tsx | 5 | P0 |
| AC6 | Skip link visible on Tab focus, href="#main-content" | AppLayout.test.tsx | 2 | P0 |

## Test Files Generated

### `dqs-dashboard/tests/layouts/AppLayout.test.tsx`

23 tests covering:
- Fixed header (AppBar) presence in DOM
- Time range toggle (7d/30d/90d) buttons rendered
- Search input placeholder text
- `<header>` landmark (role="banner")
- `<main>` landmark (role="main") with `id="main-content"`
- `<nav>` landmark (role="navigation") with `aria-label="breadcrumb"`
- Skip link with `href="#main-content"`, text "Skip to main content"
- Breadcrumb: "/" and "/summary" → "Summary" as plain text (not link)
- Breadcrumb: "/lobs/:lobId" → "Summary" link + lobId as text
- Breadcrumb: "/datasets/:datasetId" → "Summary" link + datasetId as text
- Time range toggle: "7d" selected by default (aria-pressed="true")
- Time range toggle: clicking "30d"/"90d" updates selection
- Time range toggle: exclusive selection (only one active)
- Children rendered inside `<main>` element
- Rendering stability at all four route variants

### `dqs-dashboard/tests/context/TimeRangeContext.test.tsx`

14 tests covering:
- `TimeRange` type accepts "7d", "30d", "90d"
- Default time range is "7d"
- `setTimeRange("30d")` updates value to "30d"
- `setTimeRange("90d")` updates value to "90d"
- `setTimeRange("7d")` resets from "30d" back to "7d"
- `queryClient.invalidateQueries` called when time range changes
- Guard: no double-invalidation edge case
- `useTimeRange()` throws when used outside `TimeRangeProvider`
- `TimeRangeProvider` renders children inside `QueryClientProvider`
- Barrel export: `TimeRangeProvider` in `context/index.ts`
- Barrel export: `useTimeRange` in `context/index.ts`
- Named export: `TimeRangeProvider` from `TimeRangeContext` module

## Red Phase Verification

Run from `dqs-dashboard/`:
```
npx vitest run tests/layouts/AppLayout.test.tsx tests/context/TimeRangeContext.test.tsx
```

**Expected result:** 2 failed suites — import errors for `../../src/context/TimeRangeContext`
(module does not exist, tests fail at import stage — correct TDD red phase behavior)

## Zero Regressions

```
npx vitest run tests/components/
```
**Result:** 4 test files, 132 tests — all PASS. No existing tests broken.

## Next Steps (TDD Green Phase)

After implementing the feature per story tasks:

1. Create `dqs-dashboard/src/context/TimeRangeContext.tsx` (Task 1)
2. Replace `dqs-dashboard/src/layouts/AppLayout.tsx` (Task 2)
3. Update `dqs-dashboard/src/App.tsx` (Task 3)
4. Update `dqs-dashboard/src/api/queries.ts` (Task 4)
5. Remove `it.skip()` from both test files
6. Run: `npx vitest run tests/layouts/ tests/context/`
7. Verify all 37 tests PASS (green phase)
8. Run full suite: `npx vitest run` — verify all 169 tests pass

## Implementation Files Required

| File | Action | Story Task |
|------|--------|------------|
| `src/context/TimeRangeContext.tsx` | CREATE | Task 1 |
| `src/context/index.ts` | CREATE | Task 1 |
| `src/layouts/AppLayout.tsx` | REPLACE | Task 2 |
| `src/App.tsx` | MODIFY | Task 3 |
| `src/api/queries.ts` | MODIFY | Task 4 |
| `src/api/types.ts` | MODIFY | Task 4 |

## Anti-Patterns Guarded Against

The generated tests enforce these story anti-patterns cannot pass:
- `position="static"` AppBar: AC1 tests check for fixed header presence (will fail if static)
- Missing `id="main-content"`: AC5 test explicitly checks this attribute
- Missing skip link: AC6 tests will fail if link absent
- Missing `<nav>` landmark: AC5 test checks `role="navigation"`
- Default time range not "7d": TimeRangeContext default test fails
- `invalidateQueries` not called: spy test captures the call
- `useTimeRange` outside provider not throwing: error boundary test verifies

## Knowledge Fragments Applied

- `component-tdd.md` — Red-Green-Refactor cycle, provider isolation pattern
- `data-factories.md` — Factory functions for component props (no external factories needed for layout tests)
