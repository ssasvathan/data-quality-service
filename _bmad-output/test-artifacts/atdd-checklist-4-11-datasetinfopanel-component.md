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
story_id: 4-11-datasetinfopanel-component
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/4-11-datasetinfopanel-component.md
  - _bmad-output/project-context.md
  - _bmad/tea/config.yaml
  - dqs-dashboard/vite.config.ts
  - dqs-dashboard/tests/setup.ts
  - dqs-dashboard/tests/components/DatasetCard.test.tsx
  - dqs-dashboard/src/api/types.ts
  - dqs-dashboard/src/api/queries.ts
  - dqs-dashboard/src/components/index.ts
---

# ATDD Checklist: Story 4.11 — DatasetInfoPanel Component

## TDD Red Phase (Current)

RED PHASE: Failing tests generated and verified.

- Component Tests: 29 tests (all using `it.skip()`)
- API Tests: 0 (no new endpoints — DatasetDetail API implemented in Story 4.3)
- E2E Tests: 0 (deferred for MVP per project-context.md)

## Step 1: Preflight & Context

### Stack Detection

- **Detected Stack:** `frontend`
- **Detection Evidence:** `package.json` with React/TypeScript/Vite, `vite.config.ts` with Vitest config
- **Framework:** Vitest 4.x + jsdom + @testing-library/react + @testing-library/jest-dom

### Prerequisites Verified

- [x] Story has clear acceptance criteria (6 ACs)
- [x] Vitest configured in `vite.config.ts` (test environment: jsdom, setupFiles: tests/setup.ts)
- [x] Test patterns available from DatasetCard.test.tsx, DqsScoreChip.test.tsx
- [x] `renderWithTheme` helper pattern established

### TEA Config

- `tea_use_playwright_utils: true`
- `tea_use_pactjs_utils: false`
- `tea_browser_automation: auto`
- `tea_execution_mode: auto`
- **Resolved execution mode:** `sequential` (single-agent context)

## Step 2: Generation Mode

- **Mode Selected:** AI Generation
- **Rationale:** Acceptance criteria are clear; this is a pure display component with no routing or API calls; no live browser state needed; detected stack is `frontend`

## Step 3: Test Strategy

### Acceptance Criteria → Test Mapping

| AC | Description | Test Level | Priority | Tests |
|----|-------------|-----------|----------|-------|
| AC1 | Full metadata renders (source, LOB, format, hdfs_path, partition_date, row_count, run_id, last_updated, parent_path) | Component | P0/P1 | 11 tests |
| AC2 | Copy button copies HDFS path, "Copied!" tooltip | Component | P0/P1 | 3 tests |
| AC3 | Error Alert when error_message not null | Component | P0 | 3 tests |
| AC4 | "N/A" in gray italic for unresolved fields | Component | P1 | 2 tests |
| AC5 | Semantic dl/dt/dd structure | Component | P1 | 3 tests (within AC1 group) |
| AC6 | aria-label="Copy HDFS path to clipboard" | Component | P0 | 1 test (within AC2 group) |

### Additional Test Cases (from story spec)

| Scenario | Priority | Tests |
|----------|----------|-------|
| Row count delta "(was X)" display | P1 | 4 tests |
| Rerun # conditional display (>0 shows, 0 hides) | P1 | 2 tests |
| Barrel export via components index | P1 | 1 test |
| Rendering stability (null fields) | P0/P1 | 4 tests |

### Test Level Rationale

- **No Playwright/E2E tests:** This project defers E2E tests for MVP (`project-context.md`); no E2E framework (Playwright/Cypress) is configured
- **No new API tests:** `GET /api/datasets/{dataset_id}` is fully implemented in Story 4.3; no new endpoints
- **All tests are Vitest component tests** using React Testing Library pattern established in Stories 4.6/4.7

## Step 4: Generated Tests

### Component Test File (RED PHASE)

- **File:** `dqs-dashboard/tests/components/DatasetInfoPanel.test.tsx`
- **Status:** CREATED — import fails (component not implemented yet)
- **Test count:** 29 tests (all `it.skip()`)
- **TDD Phase:** RED — will fail until DatasetInfoPanel.tsx is created

### Test Groups

1. `[P0] DatasetInfoPanel — metadata rendering (AC 1)` — 12 tests
2. `[P0] DatasetInfoPanel — copy button (AC 2, AC 6)` — 3 tests
3. `[P0] DatasetInfoPanel — error message display (AC 3)` — 3 tests
4. `[P1] DatasetInfoPanel — N/A field rendering (AC 4)` — 2 tests
5. `[P1] DatasetInfoPanel — row count delta display (AC 1)` — 4 tests
6. `[P1] DatasetInfoPanel — rerun number display` — 2 tests
7. `[P1] DatasetInfoPanel — barrel export` — 1 test
8. `[P0] DatasetInfoPanel — rendering stability` — 4 tests

### Verified RED Phase Behavior

```
Test run output:
  FAIL  tests/components/DatasetInfoPanel.test.tsx
  Error: Failed to resolve import "../../src/components/DatasetInfoPanel"
  → Component file does not exist yet (correct — RED phase)
```

Existing tests: 79 tests across DatasetCard, DqsScoreChip, TrendSparkline — all passing, unaffected.

### Fixture / Mock Summary

- `mockDataset: DatasetDetail` — full fixture with all required fields
- `navigator.clipboard.writeText` — mocked as `vi.fn()` at module level
- `renderWithTheme` — MUI ThemeProvider wrapper (same pattern as DatasetCard.test.tsx)
- No recharts mocks needed (DatasetInfoPanel has no chart components)

## Step 5: Validation

### TDD Red Phase Compliance

- [x] All 29 tests use `it.skip()` — intentionally documented as failing
- [x] All tests assert EXPECTED behavior (no placeholder `expect(true).toBe(true)`)
- [x] Test file verified to fail (import resolution error) — RED phase confirmed
- [x] Existing 79 tests pass unaffected — no regressions introduced

### Acceptance Criteria Coverage

- [x] AC1 — metadata fields: covered (source, LOB, format, hdfs, partition_date, row_count, run_id, last_updated, parent_path)
- [x] AC2 — clipboard copy + "Copied!" tooltip: covered
- [x] AC3 — error Alert (present and absent): covered
- [x] AC4 — N/A gray italic styling: covered
- [x] AC5 — semantic dl/dt/dd: covered (inside AC1 group)
- [x] AC6 — aria-label on copy button: covered

### Missing / Assumptions

- `DatasetDetail` import from `../../src/api/types` — interface not yet added; will fail until Story 4.11 implementation adds it to `api/types.ts`
- `owner` field: The story's Dev Notes clarify `owner` is NOT in the current `DatasetDetail` API response; tests do NOT assert an `owner` field (correct per spec)
- Row count locale formatting ("103,876") — test asserts `/103,876/` regex; behavior depends on `toLocaleString()` in test environment (jsdom)

## Next Steps (TDD Green Phase)

After implementing Story 4.11:

1. Add `DatasetDetail` interface to `dqs-dashboard/src/api/types.ts`
2. Add `useDatasetDetail` hook to `dqs-dashboard/src/api/queries.ts`
3. Create `dqs-dashboard/src/components/DatasetInfoPanel.tsx`
4. Add `DatasetInfoPanel` export to `dqs-dashboard/src/components/index.ts`
5. **Remove `it.skip()` from all tests** in `DatasetInfoPanel.test.tsx`
6. Run: `cd dqs-dashboard && npx vitest run tests/components/DatasetInfoPanel.test.tsx`
7. Verify all 29 tests PASS (green phase)
8. Run full suite: `npx vitest run` — verify still 79+ passing, no regressions

## Summary Statistics

| Metric | Value |
|--------|-------|
| TDD Phase | RED |
| Component Tests Generated | 29 |
| API Tests Generated | 0 (no new endpoints) |
| E2E Tests Generated | 0 (MVP deferral) |
| Tests using it.skip() | 29/29 (100%) |
| Acceptance Criteria Covered | 6/6 (100%) |
| Execution Mode | sequential |
| Existing Tests | 79 passing (unaffected) |
