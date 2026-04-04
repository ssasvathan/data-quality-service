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
story_id: 4-1-mui-theme-design-system-foundation
epic: epic-4
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/4-1-mui-theme-design-system-foundation.md
  - _bmad-output/project-context.md
  - _bmad/tea/config.yaml
  - dqs-dashboard/src/theme.ts
  - dqs-dashboard/src/App.tsx
  - dqs-dashboard/package.json
  - _bmad/tea/agents/bmad-tea/resources/knowledge/component-tdd.md
---

# ATDD Checklist: Story 4.1 — MUI Theme & Design System Foundation

## TDD Red Phase Status

**CONFIRMED RED** — 36 of 46 tests failing as of 2026-04-03.

Run: `cd dqs-dashboard && npm test`

---

## Stack Detection

- **Detected stack:** `frontend`
- **Test framework:** Vitest (installed as dev dependency)
- **Generation mode:** AI Generation (pure function unit tests — no browser needed)
- **Execution mode:** Sequential (single agent)

---

## Test File Created

| File | Tests | Status |
|------|-------|--------|
| `dqs-dashboard/tests/components/theme.test.ts` | 46 | 36 FAIL / 10 PASS (RED phase) |

---

## Acceptance Criteria Coverage

| AC | Description | Tests | Priority |
|----|-------------|-------|----------|
| AC 1 | Neutral palette applied via ThemeProvider — background.default = #FAFAFA | `should have background.default = #FAFAFA` | P0 |
| AC 2 | Semantic colors: success #2E7D32 (>=80), warning #ED6C02 (60-79), error #D32F2F (<60) | `getDqsColor` suite — 8 tests, `getDqsColorLight` suite — 5 tests, palette assertions — 8 tests | P0 |
| AC 3 | Primary accent = #1565C0 | `should have primary.main = #1565C0` | P1 |
| AC 4 | Typography: system font, h1-caption scale, score/scoreSm/mono custom variants | 11 typography tests | P1 |
| AC 5 | Spacing 8px grid | 6 spacing tests | P2 |
| AC 6 | MuiCard: 1px #EEEEEE border, 8px radius, no box-shadow | 4 MuiCard tests | P1 |

---

## Test Strategy — Level Map

This story is purely about theme infrastructure (pure TypeScript functions and theme config). No UI rendering, no API calls, no E2E needed.

| Test Level | Count | Rationale |
|------------|-------|-----------|
| Unit (Vitest) | 46 | All ACs are verifiable via pure function calls and theme object inspection |
| E2E | 0 | No UI flows in this story — deferred to Stories 4.6/4.7 which consume the theme |
| API | 0 | No backend interaction |

---

## TDD Red Phase Details

### Why Tests Fail Now

1. **`getDqsColor` / `getDqsColorLight` not exported** from `theme.ts` → `TypeError: getDqsColor is not a function` (13 tests)
2. **Wrong `palette.error.main`**: `#C62828` instead of `#D32F2F` (2 tests)
3. **Wrong `palette.warning.main`**: `#E65100` instead of `#ED6C02` (2 tests)
4. **Wrong `palette.background.default`**: `#F5F6F8` instead of `#FAFAFA` (1 test)
5. **Missing `palette.success.light`, `warning.light`, `error.light`, `primary.light`** (4 tests)
6. **Wrong font family**: Roboto-only instead of system font stack (2 tests)
7. **Missing typography scale** (h1, h2, h3, body1, caption) in custom theme (5 tests)
8. **Missing custom typography variants** (score, scoreSm, mono) (3 tests)
9. **Missing MuiCard component overrides** (4 tests)

### Tests Passing in Red Phase (Correct in Current theme.ts)

These 10 tests pass because the current theme.ts has matching values:
- `palette.primary.main = #1565C0` ✓
- `palette.background.paper = #FFFFFF` ✓
- `palette.success.main = #2E7D32` ✓
- `spacing(1) = 8px`, `spacing(0.5) = 4px`, `spacing(2) = 16px`, `spacing(3) = 24px`, `spacing(4) = 32px`, `spacing(6) = 48px` ✓ (MUI default)
- `caption.fontSize` missing but spacing tests pass ✓

---

## Infrastructure Changes

| File | Change | Reason |
|------|--------|--------|
| `dqs-dashboard/vite.config.ts` | Added `test` config block with `environment: 'node'` | Enable Vitest with correct test glob |
| `dqs-dashboard/package.json` | Added `test` and `test:watch` scripts; `vitest` + `@vitest/ui` as devDependencies | Story specifies Vitest |

---

## Next Steps — TDD Green Phase

After implementing `theme.ts` per story spec:

1. Remove any `// TDD RED` comments if desired (optional — they document intent)
2. Run tests: `cd dqs-dashboard && npm test`
3. Verify **all 46 tests PASS** (green phase)
4. If any tests fail:
   - **Test failure** = implementation doesn't match spec (fix `theme.ts`)
   - **Test wrong** = spec ambiguity (review AC with team)
5. Commit passing tests with implementation

### Implementation Checklist for Green Phase

- [ ] Replace `theme.ts` content (full rewrite per story spec)
- [ ] Export `getDqsColor(score: number): string`
- [ ] Export `getDqsColorLight(score: number): string`
- [ ] Fix `palette.error.main` → `#D32F2F`
- [ ] Fix `palette.warning.main` → `#ED6C02`
- [ ] Fix `palette.background.default` → `#FAFAFA`
- [ ] Add `palette.success.light` = `#E8F5E9`
- [ ] Add `palette.warning.light` = `#FFF3E0`
- [ ] Add `palette.error.light` = `#FFEBEE`
- [ ] Add `palette.primary.light` = `#E3F2FD`
- [ ] Fix `typography.fontFamily` → system font stack
- [ ] Add typography scale (h1-caption with fontSize/fontWeight)
- [ ] Add custom variants: `score`, `scoreSm`, `mono` with TypeScript module augmentation
- [ ] Add `components.MuiCard` overrides (border, borderRadius, boxShadow, elevation)
- [ ] Update `index.css` body background to `#FAFAFA`
- [ ] Verify `App.tsx` has ThemeProvider + CssBaseline wrapping

---

## Key Risks & Assumptions

| Risk | Mitigation |
|------|-----------|
| TypeScript module augmentation for custom typography variants may cause type errors in subsequent stories if incomplete | Tests verify `score`, `scoreSm`, `mono` variants are defined — partial mitigation |
| `caption.fontSize` test passes only once typography scale is added | Test is correctly RED now |
| Spacing tests pass already (MUI default) — if someone overrides spacing base, they'll break | Tests provide regression guard |

---

## Fixture Needs

None required for this story — all tests are pure function / theme object inspection, no DOM, no providers needed.

---

## Knowledge Fragments Used

- `component-tdd.md` — TDD Red-Green-Refactor cycle and isolation patterns
- `test-quality.md` — Test quality and naming conventions
- `data-factories.md` — N/A (no data factories needed for theme tests)
