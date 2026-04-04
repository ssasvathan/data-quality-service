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
story_id: 4-7-datasetcard-lob-card-component
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/4-7-datasetcard-lob-card-component.md
  - _bmad-output/project-context.md
  - dqs-dashboard/tests/components/DqsScoreChip.test.tsx
  - dqs-dashboard/tests/components/TrendSparkline.test.tsx
  - dqs-dashboard/src/components/DqsScoreChip.tsx
  - dqs-dashboard/src/components/index.ts
  - dqs-dashboard/vite.config.ts
---

# ATDD Checklist: Story 4.7 — DatasetCard (LOB Card) Component

## TDD Red Phase (Current)

All tests generated with `it.skip()` — they assert EXPECTED behavior but cannot
pass until `DatasetCard` is implemented.

- Component Tests: **33 tests** (all skipped via `it.skip()`)
- Test file: `dqs-dashboard/tests/components/DatasetCard.test.tsx`
- Status: RED (failing — `DatasetCard.tsx` does not exist)

---

## Step 1: Preflight & Context

### Stack Detection

- Detected stack: `frontend` (React/TypeScript/Vite, dqs-dashboard/)
- Test framework: Vitest + React Testing Library + jsdom (configured in 4.6)
- Config source: No `.claude/settings.json` found — defaults used
  - `tea_use_playwright_utils`: false (default)
  - `tea_use_pactjs_utils`: false (default)
  - `tea_browser_automation`: none (component tests only)
  - `tea_execution_mode`: sequential (default)

### Prerequisites

- [x] Story has clear acceptance criteria (5 ACs)
- [x] Test framework configured (vite.config.ts with jsdom + setupFiles from 4.6)
- [x] Existing test patterns available (DqsScoreChip.test.tsx, TrendSparkline.test.tsx)
- [x] All testing dependencies installed (@testing-library/react, @testing-library/jest-dom, jsdom)
- [x] Story status: ready-for-dev

### Loaded Context

- Story 4.7 acceptance criteria (5 ACs)
- DqsScoreChip.test.tsx — `renderWithTheme` helper pattern
- TrendSparkline.test.tsx — `vi.mock('recharts')` pattern
- DatasetCardProps interface (from story dev notes)
- Aria-label format (from story dev notes)
- Mock pattern for DqsScoreChip + TrendSparkline (from story dev notes)

---

## Step 2: Generation Mode

**Mode selected: AI Generation**

Rationale: Acceptance criteria are clear and unambiguous. All scenarios are
standard UI component behaviors (rendering, click, keyboard, accessibility).
No live browser recording needed.

---

## Step 3: Test Strategy

### Acceptance Criteria → Test Scenarios Mapping

| AC | Description | Level | Priority | Tests |
|---|---|---|---|---|
| AC1 | Renders LOB name, dataset count, DqsScoreChip, LinearProgress, TrendSparkline, status chips | Component | P0 | 13 tests |
| AC2 | Hover border transitions to primary (#1565C0) | Component | P1 | Note: CSS transitions not testable in jsdom — deferred |
| AC3 | Click fires onClick handler | Component | P0 | 2 tests |
| AC4 | role="button", tabIndex=0, Enter/Space activate | Component | P0 | 6 tests |
| AC5 | aria-label contains LOB name, score, count, statuses | Component | P1 | 7 tests |
| — | Barrel export (DatasetCard in index.ts) | Component | P1 | 1 test |
| — | Rendering stability (no throw on valid/edge props) | Component | P0+P1 | 4 tests |

**Note on AC2 (hover border transition):** CSS `:hover` pseudo-class transitions
are not testable in jsdom. This is a known limitation. The styling will be
validated by visual inspection during green phase. No test generated for AC2.

### Test Level Rationale

All tests are **component-level** using Vitest + React Testing Library:
- No E2E tests: DatasetCard is a pure display component with no routing logic
- No API tests: DatasetCard is a pure props-driven component with no API calls
- No unit tests for helpers: aria-label construction is tested via rendered output

### TDD Red Phase Design

All tests are intentionally failing:
1. `DatasetCard.tsx` does not exist → import resolution fails
2. Once stub created, all 30 tests skip cleanly via `it.skip()`
3. Tests assert EXPECTED behavior — they will pass once implementation matches

---

## Step 4: Generated Tests

### Test File

**Path:** `dqs-dashboard/tests/components/DatasetCard.test.tsx`

### Test Suites and Coverage

| Suite | AC Coverage | Priority | Count |
|---|---|---|---|
| DatasetCard — content rendering | AC1 | P0 | 9 tests |
| DatasetCard — content rendering edge cases | AC1 | P1 | 4 tests |
| DatasetCard — click interaction | AC3 | P0 | 2 tests |
| DatasetCard — keyboard accessibility | AC4 | P0+P1 | 6 tests |
| DatasetCard — accessibility aria-label | AC5 | P1 | 7 tests |
| DatasetCard — barrel export | — | P1 | 1 test |
| DatasetCard — rendering stability | — | P0+P1 | 4 tests |
| **Total** | **AC1,3,4,5** | | **33 tests** |

### Key Test Design Decisions

1. **Mock DqsScoreChip + TrendSparkline**: Avoids Recharts canvas issues in jsdom.
   Both are replaced with `data-testid` divs for isolated testing.

2. **progressbar role**: MUI `LinearProgress variant="determinate"` renders with
   `role="progressbar"` and `aria-valuenow` — tests use `getByRole('progressbar')`.

3. **fireEvent.keyDown** for keyboard tests: Tests `{ key: 'Enter' }` and `{ key: ' ' }`
   to match the `onKeyDown` handler pattern from the story dev notes.

4. **Barrel export test**: Uses dynamic `import()` to verify `DatasetCard` appears
   in the `index.ts` barrel at runtime.

5. **vi.fn() reset**: Each test using `onClick` creates a fresh `vi.fn()` — no
   cross-test pollution.

---

## Step 5: Validation

### Prerequisites Check

- [x] Story has approved acceptance criteria
- [x] Test framework available (Vitest configured in 4.6, no changes needed)
- [x] Test file written to correct path per project-context.md rules
- [x] All tests use `it.skip()` (TDD red phase compliant)
- [x] No placeholder assertions (`expect(true).toBe(true)`)
- [x] Tests assert EXPECTED behavior (real assertions against component output)
- [x] No temp browser sessions to clean up (AI generation mode, no recording)

### TDD Red Phase Verification

```
$ cd dqs-dashboard && npx vitest run tests/components/DatasetCard.test.tsx
→ FAIL: "Failed to resolve import ../../src/components/DatasetCard"
```

This is the CORRECT red-phase failure — the component file does not exist yet.
Once implementation begins:
1. `DatasetCard.tsx` created → tests load and show as SKIPPED (33 skips)
2. Remove `it.skip()` → tests execute
3. Implement component to match test expectations → tests go GREEN

### Acceptance Criteria Coverage

| AC | Covered | Notes |
|---|---|---|
| AC1 (renders anatomy) | YES | 13 tests covering all 7 elements |
| AC2 (hover border) | NO | CSS transitions not testable in jsdom — visual only |
| AC3 (click nav) | YES | 2 tests — single click and repeated clicks |
| AC4 (keyboard a11y) | YES | 6 tests — role, tabindex, Enter, Space, negative cases |
| AC5 (aria-label) | YES | 7 tests including full-format match |

---

## Next Steps (TDD Green Phase)

After implementing the feature per story 4.7 tasks:

1. Verify tests load (DatasetCard.tsx created → import resolves)
2. Remove `it.skip()` → `it()` in `DatasetCard.test.tsx`
3. Run tests: `cd dqs-dashboard && npx vitest run tests/components/DatasetCard.test.tsx`
4. Verify all 33 tests PASS (green phase)
5. If any fail: fix implementation (not the tests)
6. Run full suite: `npx vitest run` — verify existing 99 tests still pass
7. Commit: test file + implementation files together

## Implementation Guidance

Components to implement (per story tasks):

1. **`dqs-dashboard/src/components/DatasetCard.tsx`** — new component
   - Props: `DatasetCardProps` interface (lobName, datasetCount, dqsScore, previousScore?,
     trendData, statusCounts, onClick)
   - Elements: LOB name (h3), dataset count (body2), DqsScoreChip, LinearProgress,
     TrendSparkline, 3× Chip (pass/warn/fail)
   - Keyboard: `role="button"`, `tabIndex={0}`, `onKeyDown` (Enter/Space)
   - Aria-label: `"{lobName}, DQS Score {dqsScore}, {datasetCount} datasets, {pass} passing, {warn} warning, {fail} failing"`

2. **`dqs-dashboard/src/components/index.ts`** — add barrel export
   - `export { DatasetCard } from './DatasetCard'`

---

## Generated Files

- `dqs-dashboard/tests/components/DatasetCard.test.tsx` — **33 failing tests (RED)**
- `_bmad-output/implementation-artifacts/atdd-checklist-4-7-datasetcard-lob-card-component.md` — this file
