# Code Review: Story 4-13 Global Dataset Search

**Date:** 2026-04-03
**Reviewer:** Code Review Agent (claude-sonnet-4-6)
**Story:** `4-13-global-dataset-search`
**Review Mode:** full (spec file provided)
**Status after review:** done

---

## Summary

Code review conducted using three adversarial layers: Blind Hunter (code-only), Edge Case Hunter (code + project access), and Acceptance Auditor (code + spec + ACs). 4 patch findings were fixed; 3 findings were deferred. All 6 acceptance criteria verified as implemented. Test suite: 344 tests passing, 0 skipped, 0 failed.

---

## Review Layers

| Layer | Status | Findings |
|-------|--------|----------|
| Blind Hunter | Completed | 4 findings (2 patch, 2 dismiss) |
| Edge Case Hunter | Completed | 4 findings (2 patch, 1 defer, 1 dismiss) |
| Acceptance Auditor | Completed | 1 defer (UX spec vs story AC discrepancy on Escape) |

---

## Findings and Resolutions

### Medium Severity (Patched)

**F4 — No error state handling for `useSearch` in GlobalSearch component**
- **Source:** Edge Case Hunter
- **Location:** `dqs-dashboard/src/layouts/AppLayout.tsx:136`
- **Issue:** `const { data } = useSearch(searchQuery)` did not destructure `isError`. When the search API failed, the component silently showed an empty result set with no user feedback. Per project-context.md rule: "Component-level error display ('Failed to load') — never full-page crashes for partial failures."
- **Fix:** Destructured `isError` from `useSearch`. Options array now returns `[]` on error (`isError ? [] : (data?.results ?? [])`). Added `noOptionsText` branch: `'Search unavailable — please try again'` when `isError` is true, shown instead of the normal "No datasets matching" message.
- **Status:** Fixed

### Low Severity (Patched)

**F1 — Duplicate local `SearchResult` interface in test file**
- **Source:** Blind Hunter + Acceptance Auditor
- **Location:** `dqs-dashboard/tests/layouts/GlobalSearch.test.tsx:85`
- **Issue:** Test file redefined a local `SearchResult` interface (lines 85-91) identical to the one in `api/types.ts`. Maintenance debt: if `SearchResult` changes in `api/types.ts`, the local copy must be manually updated.
- **Fix:** Removed the local interface definition. Added `import type { SearchResult } from '../../src/api/types'` to use the canonical type.
- **Status:** Fixed

**F2 — Stale TDD red-phase comments in passing tests**
- **Source:** Blind Hunter
- **Location:** `dqs-dashboard/tests/layouts/GlobalSearch.test.tsx` (all 19 tests), `dqs-dashboard/tests/api/useSearch.test.ts` (all 10 tests)
- **Issue:** All test cases contained `// THIS TEST WILL FAIL: ...` markers from the ATDD red phase. The implementation is complete and all tests pass — these comments were stale and misleading, implying tests were expected to fail.
- **Fix:** Removed all red-phase inline markers. Updated file-level doc comment headers from "RED PHASE" to "GREEN PHASE" in both test files.
- **Status:** Fixed

**F3 — Ctrl+K handler called `setOpen(true)` unconditionally with stale closure**
- **Source:** Edge Case Hunter
- **Location:** `dqs-dashboard/src/layouts/AppLayout.tsx:146`
- **Issue:** The Ctrl+K `useEffect` handler (with `[]` dep array) closed over the initial `inputValue = ''`. The `setOpen(true)` call always opened the dropdown even when the input was empty, showing the "Type to search" noOptionsText — an unhelpful empty dropdown. Additionally, using `inputValue` from a stale closure inside the effect could not reflect the current value.
- **Fix:** Removed `setOpen(true)` from the Ctrl+K handler entirely. The handler now only calls `inputRef.current?.focus()`. The dropdown opens naturally when the user types 2+ chars (via `onInputChange`), which is the correct UX: Ctrl+K focuses the input and the user starts typing.
- **Status:** Fixed

### Dismissed

**D1 — `disableClearable` prop vs `DisableClearable=false` generic type inconsistency**
- **Source:** Blind Hunter
- **Issue:** `Autocomplete<SearchResult, false, false, false>` specifies `DisableClearable=false` as the 3rd generic but the `disableClearable` prop is passed. TypeScript diagnostics show no errors; MUI runtime uses the prop value. Behaviour is correct.
- **Classification:** Dismiss — TypeScript reports no errors, runtime behaviour is correct, MUI accepts this combination.

**D2 — `GlobalSearch.test.tsx` imports `useSearch as useSearchImport` then also mocks the module**
- **Source:** Blind Hunter
- **Issue:** Queried whether importing the mocked module was a pattern issue.
- **Classification:** Dismiss — Standard Vitest pattern: import the mock reference at module level so `vi.mocked(useSearchImport)` works in tests. Correct and idiomatic.

### Deferred

**F5 — `getUseSearchMock()` function wrapper is unnecessary indirection**
- **Source:** Blind Hunter
- **Location:** `dqs-dashboard/tests/layouts/GlobalSearch.test.tsx:147`
- **Issue:** A function that simply returns `mockedUseSearch` adds indirection without value.
- **Classification:** Defer — Pre-existing style preference, not a correctness issue. Removing it would touch 8 call sites for no functional gain.

**F6 — Stale search results visible briefly during query transition**
- **Source:** Edge Case Hunter
- **Issue:** When `deferredQuery` changes from one valid query to another, the previous `options` remain visible until the new query resolves. No visual stale indicator.
- **Classification:** Defer — Acceptable UX per project patterns (stale-while-revalidate). Search dropdowns conventionally show stale results during re-fetch.

**F7 — UX spec says Escape should also clear search text; story AC only requires closing dropdown**
- **Source:** Acceptance Auditor
- **Issue:** `ux-consistency-patterns.md` states "Escape closes dropdown and clears search." The implementation only closes the dropdown via MUI's native `onClose`, without clearing `inputValue`. The story AC2 says "close the search with Escape — health check complete without navigating" (no mention of clearing). The test for AC2 only asserts dropdown closes and no navigation occurs.
- **Classification:** Defer — Not an AC violation per story spec. Story AC2 does not require clearing `inputValue` on Escape. The UX spec is more strict, but the story is the binding document. Recommend addressing in Story 4-15 (accessibility/polish).

---

## Acceptance Criteria Verification

| AC | Description | Status |
|----|-------------|--------|
| AC1 | Autocomplete dropdown appears after 2+ characters, 300ms debounce, shows DqsScoreChip + dataset name (mono) + LOB (gray) | PASS |
| AC2 | Escape key closes the dropdown without navigating | PASS |
| AC3 | Clicking/selecting a result navigates to `/datasets/{dataset_id}` and clears input | PASS |
| AC4 | `Ctrl+K` (and `Cmd+K` on Mac) focuses the search input from any view | PASS |
| AC5 | No results shows "No datasets matching '{query}'" in gray text | PASS |
| AC6 | Global search across all LOBs, max 10 results, prefix matches first then substring | PASS |

---

## Test Results

```
Test Files  12 passed (12)
     Tests  344 passed (344)
  Start at  02:59:51
  Duration  6.42s
```

- 0 skipped tests
- 0 failing tests
- All 12 test files passed
- 344 total: 315 pre-existing + 19 GlobalSearch + 10 useSearch

---

## Files Modified

| File | Changes |
|------|---------|
| `dqs-dashboard/src/layouts/AppLayout.tsx` | Fixed F4 (added `isError` handling, error noOptionsText) and F3 (removed stale `setOpen(true)` from Ctrl+K handler) |
| `dqs-dashboard/tests/layouts/GlobalSearch.test.tsx` | Fixed F1 (removed duplicate `SearchResult` interface, added import from `api/types`), F2 (removed red-phase TDD comments, updated header to GREEN PHASE) |
| `dqs-dashboard/tests/api/useSearch.test.ts` | Fixed F2 (removed red-phase TDD comments, updated header to GREEN PHASE) |
| `dqs-dashboard/src/api/types.ts` | No review fixes (types correct as-implemented) |
| `dqs-dashboard/src/api/queries.ts` | No review fixes (hook correct as-implemented) |
| `dqs-dashboard/tests/layouts/AppLayout.test.tsx` | No review fixes (mocks correctly set up as-implemented) |

---

## Quality Gates

- [x] All tests pass: `344 passed (344)`, 0 skipped, 0 failed
- [x] Ruff linting: clean (`All checks passed!`)
- [x] All AC findings fixed or deferred with justification
- [x] Story status: `done`
- [x] Sprint status synced: `4-13-global-dataset-search: done`
