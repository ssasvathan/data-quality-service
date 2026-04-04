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
story_id: 4-6-dqsscorechip-trendsparkline-components
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/4-6-dqsscorechip-trendsparkline-components.md
  - _bmad-output/project-context.md
  - dqs-dashboard/src/theme.ts
  - dqs-dashboard/tests/components/theme.test.ts
  - dqs-dashboard/vite.config.ts
  - dqs-dashboard/package.json
  - _bmad/tea/config.yaml
---

# ATDD Checklist: Story 4.6 — DqsScoreChip & TrendSparkline Components

## TDD Red Phase (Current)

All tests generated and verified FAILING.

| Test File | Status | Tests | Reason for Failure |
|---|---|---|---|
| `tests/components/DqsScoreChip.test.tsx` | RED (failing) | 22 tests | `src/components/DqsScoreChip.tsx` not yet created |
| `tests/components/TrendSparkline.test.tsx` | RED (failing) | 19 tests | `src/components/TrendSparkline.tsx` not yet created |
| `tests/components/theme.test.ts` | GREEN (passing) | 53 tests | Existing — not touched |

**Total new component tests: 41 (all failing — TDD red phase)**

---

## Step 1: Preflight & Context

### Stack Detection

- Detected: `frontend` — `dqs-dashboard/package.json` with React 19, Vite, TypeScript
- Test framework: Vitest 4.1.2 + React Testing Library (per story spec — NOT Playwright)
- Story status: `ready-for-dev` with 7 clear acceptance criteria

### Key Theme Utilities Confirmed (from `theme.ts`)

| Utility | Value | Notes |
|---|---|---|
| `getDqsColor(87)` | `#2E7D32` | success (>=80) |
| `getDqsColor(70)` | `#ED6C02` | warning (60-79) |
| `getDqsColor(55)` | `#D32F2F` | error (<60) |
| `getDqsColor(80)` | `#2E7D32` | boundary — SUCCESS |
| `getDqsColor(60)` | `#ED6C02` | boundary — WARNING |
| `theme.palette.text.disabled` | `#9E9E9E` | neutral-500 — for "—" state |
| Typography `score` variant | 28px / 700 | size=lg |
| Typography `scoreSm` variant | 18px / 700 | size=md (default) |

---

## Step 2: Generation Mode

**Mode: AI Generation** (component tests are deterministic from acceptance criteria — no browser recording needed; Vitest + RTL are unit-level tests)

---

## Step 3: Test Strategy

### Acceptance Criteria → Test Coverage Map

| AC | Description | Test File | Priority | Coverage |
|---|---|---|---|---|
| AC 1 | score=87 previousScore=84 → "87", "+3", up arrow, green | DqsScoreChip.test.tsx | P0 | Covered (4 tests) |
| AC 2 | Sizes: lg (28px), md (18px), sm (14px) | DqsScoreChip.test.tsx | P1 | Covered (4 tests) |
| AC 3 | score=55 → red text (critical < 60) | DqsScoreChip.test.tsx | P0 | Covered (4 tests + boundaries) |
| AC 4 | No score → "—" in gray | DqsScoreChip.test.tsx | P0 | Covered (3 tests) |
| AC 5 | TrendSparkline 30 pts, md (32px), Recharts, tooltip | TrendSparkline.test.tsx | P0 | Covered (6 tests) |
| AC 6 | TrendSparkline 1 pt → "First run" placeholder | TrendSparkline.test.tsx | P0 | Covered (4 tests) |
| AC 7 | Both components: aria-labels for screen readers | Both test files | P1 | Covered (6+5 tests) |

### Test Levels

- **Component tests only** (no API tests — components are display-only props-driven)
- **No E2E tests** — deferred per project-context.md ("No E2E tests for MVP")

### Priority Coverage

| Priority | DqsScoreChip | TrendSparkline | Total |
|---|---|---|---|
| P0 | 11 | 10 | 21 |
| P1 | 8 | 7 | 15 |
| P2 | 0 | 2 | 2 |
| P3 | 0 | 0 | 0 |

---

## Step 4: Test Generation (Aggregate)

### Files Created

| File | Type | Status |
|---|---|---|
| `dqs-dashboard/tests/components/DqsScoreChip.test.tsx` | Component test | NEW — 22 tests |
| `dqs-dashboard/tests/components/TrendSparkline.test.tsx` | Component test | NEW — 19 tests |
| `dqs-dashboard/tests/setup.ts` | Vitest global setup | NEW — jest-dom import |

### Config Changes

| File | Change |
|---|---|
| `dqs-dashboard/vite.config.ts` | `environment` changed from `'node'` to `'jsdom'`; added `globals: true`; added `setupFiles: ['./tests/setup.ts']` |

### Dependencies Installed

| Package | Version | Type |
|---|---|---|
| `jsdom` | latest | devDependency |
| `@testing-library/react` | latest | devDependency |
| `@testing-library/jest-dom` | latest | devDependency |

### TDD Red Phase Validation

```
TDD Red Phase Validation: PASS
- DqsScoreChip.test.tsx: FAILING (component module not found — correct red phase)
- TrendSparkline.test.tsx: FAILING (component module not found — correct red phase)
- theme.test.ts: 53 PASSING (existing tests unaffected by jsdom change)
- No placeholder assertions (expect(true).toBe(true))
- All assertions target EXPECTED behavior from acceptance criteria
```

### Test Run Output (Red Phase Confirmation)

```
Test Files  2 failed | 1 passed (3)
      Tests  53 passed (53)
   Start at  23:09:51

FAIL  tests/components/DqsScoreChip.test.tsx
Error: Failed to resolve import "../../src/components/DqsScoreChip"
Does the file exist?

FAIL  tests/components/TrendSparkline.test.tsx
Error: Failed to resolve import "../../src/components/TrendSparkline"
Does the file exist?
```

**This is INTENTIONAL — TDD red phase. Tests fail because components don't exist yet.**

---

## Step 5: Validate & Complete

### Prerequisites Checklist

- [x] Story 4.6 has approved acceptance criteria (7 ACs, all covered)
- [x] Test framework: Vitest configured with jsdom + RTL
- [x] Test environment: jsdom (required for React component rendering)
- [x] Backward compatibility: theme.test.ts (53 tests) still passes with jsdom
- [x] setupFiles configured for jest-dom matchers
- [x] No test files > 300 lines
- [x] No placeholder assertions
- [x] aria-label tests follow AC 7 spec exactly
- [x] Recharts mocked correctly (canvas unavailable in jsdom)
- [x] ThemeProvider wrapper in every test (getDqsColor works correctly)
- [x] No hardcoded hex values — assertions use theme color values from project-context.md

### Key Test Design Decisions

1. **Vitest not Playwright**: Story 4.6 specifies Vitest component tests (tasks 3 & 4). E2E is explicitly deferred.

2. **Recharts mock**: `vi.mock('recharts', ...)` replaces all chart components with `data-testid` divs. This avoids canvas-API unavailability in jsdom and lets tests focus on component behavior.

3. **ThemeProvider wrapper**: All tests use `renderWithTheme()` helper to ensure MUI theme tokens (getDqsColor, theme.palette.neutral) resolve correctly.

4. **Boundary tests for getDqsColor thresholds**: score===80 is SUCCESS (green), score===60 is WARNING (amber) — critical boundaries explicitly tested in P0 group.

5. **aria-label exact-match test**: The final aria-label test for DqsScoreChip asserts the exact string `"DQS Score 87, healthy, improving by 3 points"` to lock in the spec.

6. **`globals: true` in vitest config**: Required by `@testing-library/jest-dom` which calls `expect.extend()` — needs global `expect` to be available during setup.

### Assumptions / Risks

- `getByRole('generic', { name: /.../ })` works for `Box` components with `aria-label` — if the `Box` renders as a `<div>`, which has implicit `generic` role. If implementation uses a different element, the selector may need adjustment.
- `toHaveStyle({ height: '32px' })` requires inline styles via `sx` prop to materialize as inline style attributes. MUI's `sx` prop in jsdom generates inline styles, so this should work.

---

## Next Steps (TDD Green Phase)

After implementing the components:

1. Create `dqs-dashboard/src/components/DqsScoreChip.tsx` per story task 1 spec
2. Create `dqs-dashboard/src/components/TrendSparkline.tsx` per story task 2 spec
3. Create `dqs-dashboard/src/components/index.ts` barrel export (task 5)
4. Remove no action needed — tests are NOT skipped, they will naturally pass once components exist
5. Run: `cd dqs-dashboard && npx vitest run`
6. Verify **ALL** 53 + 22 + 19 = **94 tests pass** (green phase)
7. If any tests fail: either fix implementation (feature bug) or fix test (test bug)
8. Commit passing tests

### Components to Implement

| Component | File | ACs |
|---|---|---|
| `DqsScoreChip` | `src/components/DqsScoreChip.tsx` | 1, 2, 3, 4, 7 |
| `TrendSparkline` | `src/components/TrendSparkline.tsx` | 5, 6, 7 |
| Barrel export | `src/components/index.ts` | — |

### Critical Implementation Rules (from story dev notes)

- Use `getDqsColor(score)` from `../../theme` — NEVER hardcode hex
- Use MUI `Box` + `Typography` (NOT MUI `Chip`) for DqsScoreChip
- `sm` size uses inline `sx={{ fontSize: '0.875rem', fontWeight: 600 }}` (no theme variant)
- Recharts: always `isAnimationActive={false}` on `Line`
- Single/empty data points: render fallback Box with colored dot + "First run" caption
- Recharts: always use `ResponsiveContainer` wrapper (except mini where width is fixed)
