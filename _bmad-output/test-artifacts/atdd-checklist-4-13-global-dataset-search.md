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
inputDocuments:
  - _bmad-output/implementation-artifacts/4-13-global-dataset-search.md
  - dqs-dashboard/tests/layouts/AppLayout.test.tsx
  - dqs-dashboard/src/layouts/AppLayout.tsx
  - dqs-dashboard/src/api/queries.ts
  - dqs-dashboard/src/api/types.ts
  - dqs-dashboard/src/components/DqsScoreChip.tsx
  - _bmad/tea/config.yaml
  - .claude/skills/bmad-testarch-atdd/resources/knowledge/component-tdd.md
tddPhase: RED
---

# ATDD Checklist: Story 4.13 — Global Dataset Search

## TDD Red Phase Summary

**Status:** RED PHASE COMPLETE — Failing tests generated and skipped.

- **Component Tests:** 19 tests (all skipped with `it.skip()`) in `tests/layouts/GlobalSearch.test.tsx`
- **Hook Tests:** 10 tests (all skipped with `it.skip()`) in `tests/api/useSearch.test.ts`
- **Total new tests:** 29 skipped (failing as expected)
- **Existing tests:** 315 passing (no regression)

---

## Step 1: Preflight & Context

### Stack Detection

- **Detected stack:** `frontend`
- **Framework:** Vitest + React Testing Library (jsdom) — NOT Playwright
- **Detection evidence:** `dqs-dashboard/vite.config.ts` has `test.environment: 'jsdom'`; no Playwright config found
- **Story status:** `ready-for-dev` ✅
- **Acceptance criteria:** 6 ACs, all clear ✅

### Prerequisites

- Story file loaded: `_bmad-output/implementation-artifacts/4-13-global-dataset-search.md` ✅
- Existing test patterns inspected: `tests/layouts/AppLayout.test.tsx` (271 lines, 25 tests) ✅
- Framework config verified: Vitest v4.1.2, jsdom environment ✅

---

## Step 2: Generation Mode

**Selected:** AI Generation (no browser recording)
**Reason:** ACs are clear and specific; UI interactions are standard autocomplete patterns; Vitest/RTL environment cannot use Playwright CLI

---

## Step 3: Test Strategy

### Acceptance Criteria Mapping

| AC | Description | Level | Priority | Test File |
|----|-------------|-------|----------|-----------|
| AC1 | 2+ chars triggers autocomplete with DqsScoreChip + name + LOB | Component | P0 | GlobalSearch.test.tsx |
| AC1 | 1 char does NOT open dropdown (enabled guard) | Component | P0 | GlobalSearch.test.tsx |
| AC1 | Placeholder text rendered | Component | P0 | GlobalSearch.test.tsx |
| AC2 | Escape closes dropdown without navigation | Component | P1 | GlobalSearch.test.tsx |
| AC3 | Click result navigates to `/datasets/:datasetId` | Component | P0 | GlobalSearch.test.tsx |
| AC3 | Input cleared after selection | Component | P0 | GlobalSearch.test.tsx |
| AC4 | Ctrl+K focuses search input | Component | P0 | GlobalSearch.test.tsx |
| AC4 | Cmd+K (Mac) also works | Component | P1 | GlobalSearch.test.tsx |
| AC4 | Ctrl+K calls `e.preventDefault()` | Component | P1 | GlobalSearch.test.tsx |
| AC5 | No-results message shows `"No datasets matching '{query}'"` | Component | P1 | GlobalSearch.test.tsx |
| AC5 | Message includes exact query string | Component | P1 | GlobalSearch.test.tsx |
| AC5 | Single char does not show no-results message | Component | P2 | GlobalSearch.test.tsx |
| AC6 | Displays up to 10 results | Component | P2 | GlobalSearch.test.tsx |
| AC6 | useSearch called with typed query | Component | P2 | GlobalSearch.test.tsx |
| — | AppLayout still renders cleanly with GlobalSearch present | Component | P0 | GlobalSearch.test.tsx |
| — | Breadcrumbs still present alongside GlobalSearch | Component | P0 | GlobalSearch.test.tsx |
| AC1 | useSearch disabled for empty string | Hook | P0 | useSearch.test.ts |
| AC1 | useSearch disabled for 1 char | Hook | P0 | useSearch.test.ts |
| AC1 | useSearch fires API call for exactly 2 chars | Hook | P0 | useSearch.test.ts |
| AC1 | useSearch fires API call for 3+ chars | Hook | P0 | useSearch.test.ts |
| AC6 | encodeURIComponent used in URL | Hook | P0 | useSearch.test.ts |
| AC6 | queryKey is `["search", query]` | Hook | P0 | useSearch.test.ts |
| AC1,3 | Returns results array on success | Hook | P0 | useSearch.test.ts |
| — | Handles null dqs_score / check_status | Hook | P1 | useSearch.test.ts |
| AC5 | Returns empty array when no matches | Hook | P1 | useSearch.test.ts |
| AC6 | staleTime 30s — no re-fetch within window | Hook | P2 | useSearch.test.ts |

### Test Levels Used

- **No E2E tests** — this project uses Vitest/RTL only; no Playwright E2E framework configured
- **Component tests** — React Testing Library tests in `tests/layouts/GlobalSearch.test.tsx`
- **Hook/unit tests** — Vitest renderHook tests in `tests/api/useSearch.test.ts`

---

## Step 4: Test Generation (Sequential Mode)

### Worker A: Hook Tests (API layer)
**Output file:** `tests/api/useSearch.test.ts`
- 10 tests generated, all with `it.skip()`
- Covers: enabled guard, URL construction, response shape, staleTime caching
- Mock strategy: `vi.mock('../../src/api/client')` for `apiFetch`

### Worker B: Component Tests (UI layer)
**Output file:** `tests/layouts/GlobalSearch.test.tsx`
- 19 tests generated, all with `it.skip()`
- Covers: AC1 (autocomplete), AC2 (escape), AC3 (navigation), AC4 (Ctrl+K), AC5 (no-results), AC6 (limits), backward compat
- Mock strategy: `vi.mock('../../src/api/queries')`, `vi.mock('../../src/components')`, `vi.mock('react-router')`

**Execution mode:** SEQUENTIAL (auto-resolved — single agent context)

---

## Step 4C: Aggregation

### TDD Red Phase Validation

- ✅ All 29 new tests use `it.skip()` (Vitest equivalent of `test.skip()`)
- ✅ All tests assert expected behavior (not placeholder `expect(true).toBe(true)`)
- ✅ All tests marked with inline comment: `// THIS TEST WILL FAIL — [reason]`
- ✅ No passing tests generated (correct red phase)

### Fixture Needs

- `SearchResult` / `SearchResponse` interfaces (to be added in `api/types.ts`)
- `MOCK_SEARCH_RESULTS` fixture defined inline in `GlobalSearch.test.tsx`
- `MOCK_SEARCH_RESPONSE` fixture defined inline in `useSearch.test.ts`
- No shared fixture files needed — fixtures are small and test-local

### Generated Files

| File | Tests | Status |
|------|-------|--------|
| `dqs-dashboard/tests/layouts/GlobalSearch.test.tsx` | 19 | RED (all `it.skip()`) |
| `dqs-dashboard/tests/api/useSearch.test.ts` | 10 | RED (all `it.skip()`) |

---

## Step 5: Validate & Complete

### Checklist Validation

- [x] Prerequisites satisfied (story approved, framework configured)
- [x] Test files created correctly (TypeScript, Vitest syntax, correct imports)
- [x] Checklist matches acceptance criteria (all 6 ACs covered)
- [x] Tests designed to fail before implementation (it.skip() on all 29)
- [x] No CLI sessions to clean up (AI generation mode, no browser)
- [x] Temp artifacts in `_bmad-output/test-artifacts/` ✅
- [x] Existing 315 tests still pass after adding new test files
- [x] Mock strategy does NOT break existing AppLayout tests (new tests are in a separate file)

### Key Decisions & Assumptions

1. **Separate test file** (`GlobalSearch.test.tsx`) rather than modifying `AppLayout.test.tsx`:
   Adding `vi.mock('../../src/api/queries')` at module level to the existing file would affect all 25 existing tests. Keeping the new tests in a separate file avoids this risk and keeps concerns clean.

2. **`fireEvent.change` instead of `userEvent.type`**:
   `@testing-library/user-event` is not installed in the project. Tests use `fireEvent.change(input, { target: { value: '...' } })` from `@testing-library/react` instead.

3. **`it.skip()` (Vitest)** is used instead of `test.skip()` (Playwright) to match the existing Vitest test style in the project.

4. **`@ts-expect-error` in useSearch.test.ts** for `useSearch` and `SearchResponse` imports — these do not exist yet (red phase). The comment documents the expected failure reason.

5. **`require()` pattern** for mock introspection (`getUseSearchMock()` helper) — required because Vitest hoists `vi.mock()` calls before imports, so the mock reference must be fetched dynamically inside tests.

### Risks & Assumptions

| Risk | Mitigation |
|------|-----------|
| `GlobalSearch` uses `useDeferredValue` — `fireEvent.change` may not trigger deferred updates in tests | Story fallback (Option B) uses explicit debounce; if needed, tests can use `act()` + `await waitFor()` |
| MUI Autocomplete popup requires specific DOM interaction to open | Tests assert presence of `role="listbox"` after input change — may need `act()` wrapping in green phase |
| `useNavigate` mock in `GlobalSearch.test.tsx` may conflict with existing AppLayout routing | The mock spreads `actual` so routing components still work; only `useNavigate` return is replaced |

---

## Next Steps (TDD Green Phase)

After implementing the feature (Story 4.13 dev implementation):

1. Add `SearchResult` and `SearchResponse` to `dqs-dashboard/src/api/types.ts`
2. Add `useSearch` hook to `dqs-dashboard/src/api/queries.ts`
3. Replace `TextField` placeholder in `AppLayout.tsx` with `GlobalSearch` Autocomplete component
4. **Remove `it.skip()` from `tests/layouts/GlobalSearch.test.tsx`** — all 19 tests
5. **Remove `it.skip()` from `tests/api/useSearch.test.ts`** — all 10 tests
6. Run: `cd dqs-dashboard && npm test`
7. Verify all 344 tests PASS (315 existing + 29 new)
8. If any tests fail: fix implementation (or fix test if assertion is wrong)
9. Commit passing tests

### Implementation Files to Create/Modify

| File | Change |
|------|--------|
| `dqs-dashboard/src/api/types.ts` | Add `SearchResult`, `SearchResponse` interfaces |
| `dqs-dashboard/src/api/queries.ts` | Add `useSearch(query: string)` hook |
| `dqs-dashboard/src/layouts/AppLayout.tsx` | Replace `TextField` with `GlobalSearch` Autocomplete component |

**Do NOT create new files** — all changes go into existing files per story dev notes.
