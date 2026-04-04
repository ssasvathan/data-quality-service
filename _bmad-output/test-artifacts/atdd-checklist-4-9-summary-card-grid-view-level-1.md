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
story_id: 4-9-summary-card-grid-view-level-1
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/4-9-summary-card-grid-view-level-1.md
  - _bmad-output/project-context.md
  - dqs-dashboard/src/api/types.ts
  - dqs-dashboard/src/api/queries.ts
  - dqs-dashboard/src/pages/SummaryPage.tsx
  - dqs-dashboard/src/components/DatasetCard.tsx
  - dqs-dashboard/src/theme.ts
  - dqs-dashboard/src/context/TimeRangeContext.tsx
  - dqs-dashboard/vite.config.ts
  - dqs-dashboard/tests/setup.ts
  - dqs-dashboard/tests/layouts/AppLayout.test.tsx
  - _bmad/tea/config.yaml
  - .claude/skills/bmad-testarch-atdd/resources/knowledge/component-tdd.md
---

# ATDD Checklist: Story 4.9 — Summary Card Grid View (Level 1)

## TDD Red Phase (Current)

All 34 tests are **skipped** with `it.skip()` — intentional TDD red phase.
Tests assert EXPECTED behavior that does not yet exist in the codebase.

- Component Tests: 34 tests (all skipped / red phase)
- API Tests: N/A (no new API endpoints — story consumes existing GET /api/summary)
- E2E Tests: N/A — deferred for MVP per project-context.md

---

## Test File Generated

| File | Status | Tests |
|------|--------|-------|
| `dqs-dashboard/tests/pages/SummaryPage.test.tsx` | RED (skipped) | 34 |

---

## Acceptance Criteria Coverage

| AC | Description | Test Groups | Priority | Covered |
|----|-------------|-------------|----------|---------|
| AC1 | Stats bar shows total_datasets, healthy_count, degraded_count, critical_count | `stats bar renders correct values (AC1)` | P0 | Yes |
| AC2 | 3-column grid displays DatasetCard per LOB | `LOB card grid renders correct count (AC2)` | P0 | Yes |
| AC3 | Each card shows LOB name, dataset count, DQS score, trend sparkline, status chip summary | `DatasetCard prop mapping (AC3)` | P1 | Yes |
| AC4 | Critical DQS score (< 60) displays in red | `critical DQS score handling (AC4)` | P1 | Yes (score passed correctly to DatasetCard) |
| AC5 | Clicking LOB card navigates to /lobs/{lob_id} | `LOB card click navigates to /lobs/:lobId (AC5)` | P0 | Yes |
| AC6 | Loading state: 6 skeleton cards, no layout shift | `loading state (AC6)` | P0 | Yes |

### Additional Edge Cases Covered

| Scenario | Test Group | Priority |
|----------|------------|----------|
| Error state: message shown, no cards | `error state (edge case)` | P0 |
| Error state: retry button calls refetch | `error state (edge case)` | P1 |
| Empty lobs array: empty state message | `empty state (edge case)` | P1 |
| null aggregate_score → dqsScore 0 | `DatasetCard prop mapping (AC3)` | P1 |
| Plural /lobs/ route (not singular /lob/) | `LOB card click navigates…` | P0 |
| Smoke: renders without crashing | `rendering stability` | P0 |

---

## Why Tests Will Fail (Red Phase Evidence)

The following is absent from the codebase as of 2026-04-03:

1. **`useSummary()` not in `dqs-dashboard/src/api/queries.ts`**
   - `vi.mock('../../src/api/queries', ...)` mocks it, but tests that call
     `mockUseSummary.mockReturnValue(...)` will fail when `useSummary` is
     `undefined` in the mock (mock module factory doesn't include it yet).
   - When story Task 2 is complete, the mock will resolve.

2. **`LobSummaryItem` and `SummaryResponse` not in `dqs-dashboard/src/api/types.ts`**
   - TypeScript strict mode will reject any imports of these types until
     Task 1 is complete.

3. **`SummaryPage.tsx` is a placeholder** — renders static Typography only.
   - Does not call `useSummary()`, renders no stats bar, no grid, no skeletons.
   - Every behavioral assertion will fail until Task 3 is complete.

4. **`DatasetCard` not exported from `dqs-dashboard/src/components/index.ts`**
   - (To verify — check components barrel; if missing, Task 3 import will fail.)

---

## Test Priority Distribution

| Priority | Count | Key Scenarios |
|----------|-------|---------------|
| P0 | 17 | Stats bar values, card count, card click navigation, loading skeletons, error message, smoke tests |
| P1 | 17 | All prop mapping details, label texts, null score handling, retry behavior, empty state |

---

## Next Steps (TDD Green Phase)

After implementing Story 4.9 Tasks 1–4:

1. Remove `it.skip` → convert to `it` in `tests/pages/SummaryPage.test.tsx`
2. Run: `cd dqs-dashboard && npx vitest run tests/pages/SummaryPage.test.tsx`
3. Verify all 34 tests PASS
4. Run full suite: `npx vitest run` — verify existing 169 tests still pass
5. If any tests fail after implementation, diagnose:
   - Wrong prop name → fix prop mapping in SummaryPage.tsx
   - Wrong route → verify `/lobs/` plural in navigate call
   - Wrong null handling → verify `?? 0` coalescing
6. Commit green tests

### Implementation Checklist (Story Tasks)

- [ ] Task 1: Fix `LobSummary` → `LobSummaryItem` + add `SummaryResponse` in `api/types.ts`
- [ ] Task 2: Add `useSummary()` hook to `api/queries.ts` with `['summary']` query key
- [ ] Task 3: Replace `SummaryPage.tsx` placeholder with full implementation
- [ ] Task 4: Stats bar visual design with MUI theme palette tokens
- [ ] Task 5: (This file) — DONE

---

## Key Implementation Constraints (From Story Dev Notes)

- **MUI 7 Grid v2**: use `size={{ xs: 12, sm: 6, md: 4 }}` NOT legacy `item xs=` props
- **Navigation**: `useNavigate` from `'react-router'` (not `react-router-dom`)
- **Route**: `/lobs/${lob.lob_id}` (plural) — matches App.tsx `<Route path="/lobs/:lobId" />`
- **No spinners**: `Skeleton` components only — per project-context.md anti-patterns
- **No `any` types**: strict TypeScript throughout
- **No `useEffect + fetch`**: `useSummary()` via TanStack Query only
- **Null guard**: `lob.aggregate_score ?? 0` for DatasetCard dqsScore

---

## Step-by-Step Execution Log

### Step 1: Preflight & Context
- Stack: `fullstack` (React frontend + Python backend)
- Frontend test framework: Vitest (jsdom, React Testing Library)
- No Playwright/Cypress (E2E deferred for MVP)
- Story loaded: 6 ACs, 5 tasks
- Existing test patterns: AppLayout.test.tsx render helper pattern

### Step 2: Generation Mode
- Mode: AI Generation (clear ACs, standard component behaviors, Vitest stack)

### Step 3: Test Strategy
- 34 scenarios mapped across 6 ACs + 3 edge case groups
- All Component level (Vitest + RTL)
- No E2E (deferred), No API (no new endpoints)

### Step 4: Test Generation (Sequential)
- Worker A (API): Skipped — no new API endpoints
- Worker B (Component): Generated 34 tests in `tests/pages/SummaryPage.test.tsx`

### Step 4C: Aggregation
- TDD compliance: All 34 tests use `it.skip()` ✓
- All tests assert expected behavior (not placeholders) ✓
- Vitest run confirmed: 34 skipped, 169 existing tests passed ✓

### Step 5: Validation
- Prerequisites satisfied ✓
- Test file at correct path per project-context.md (`tests/pages/`) ✓
- All ACs covered ✓
- No existing tests broken ✓
- Skipped tests confirm red phase ✓
