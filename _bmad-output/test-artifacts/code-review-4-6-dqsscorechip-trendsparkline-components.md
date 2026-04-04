# Code Review — Story 4.6: DqsScoreChip & TrendSparkline Components

**Date:** 2026-04-03
**Reviewer:** claude-sonnet-4-6
**Status:** PASS — all findings resolved

---

## Summary

11 findings identified and fixed (2 Medium, 9 Low). All 99 tests pass post-fix. No regressions.

---

## Findings and Resolutions

### [Med-1] `smSx` computed unconditionally — FIXED

**File:** `src/components/DqsScoreChip.tsx`

**Before:** `smSx` was built on every render with a ternary that also applied to non-`sm` sizes, creating an unnecessary object on every render for `lg` and `md`.

**Fix:** Removed `smSx` variable entirely. The `sm` branch now uses the literal inline `sx` object directly inside the `variant ? ... : ...` JSX block. No object is created unless `variant` is `undefined` (i.e., size is `sm`).

---

### [Med-2] `theme` imported as module-level singleton in TrendSparkline — FIXED

**File:** `src/components/TrendSparkline.tsx`

**Before:** `import theme from '../theme'` — a static import that would not respond to dynamic theme changes.

**Fix:** Replaced with `const theme = useTheme()` from `@mui/material/styles` inside the component function. The singleton import was removed.

---

### [Med-3] `getTypographyVariant` returns `undefined` implicitly for `sm` — FIXED

**File:** `src/components/DqsScoreChip.tsx`

**Before:** `return undefined // sm uses inline sx` — the convention was present but not prominently explained.

**Fix:** Expanded the JSDoc on the function to explicitly document the convention: `undefined` means "no named theme variant exists for this size; callers must use inline sx." The inline sx path also received a comment explaining the `sm`-specific rule.

---

### [Low-1] `previousScore!` non-null assertion — FIXED

**File:** `src/components/DqsScoreChip.tsx`

**Before:** `const delta = hasTrend ? score - previousScore! : 0` — TypeScript cannot narrow `previousScore` through the ternary despite `hasTrend` being derived from `previousScore !== undefined`.

**Fix:** Replaced the ternary with an explicit `if (hasTrend)` block. Inside the block, `previousScore` is cast to `number` via `const prev = previousScore as number` (safe because the guard guarantees it), and `delta`, `arrow`, and `deltaText` are computed only within that block.

---

### [Low-2] `delta`, `arrow`, `deltaText` computed when `hasTrend` is false — FIXED

**File:** `src/components/DqsScoreChip.tsx`

**Before:** All three variables were initialized outside any conditional, including when `hasTrend` was `false`.

**Fix:** Moved all three declarations inside the `if (hasTrend)` block with a safe default initialisation before the block (`let delta = 0; let arrow = '→'; let deltaText = '0';`). The `ariaLabel` appension remains inside its own `if (hasTrend)` guard and still reads `delta` correctly.

---

### [Low-3] `latestScore` defaults to 0 for empty data — FIXED

**File:** `src/components/TrendSparkline.tsx`

**Before:** `const latestScore = data.length > 0 ? data[data.length - 1] : 0` — silently treats empty data as score 0 (critical red).

**Fix:** Changed type to `number | undefined` and initialised as `undefined` for empty data. The `ariaLabel` computation handles both cases: `undefined` latestScore is only used in the multi-point branch where `data.length > 1` is guaranteed true, so it is safe.

---

### [Low-4] Tooltip `formatter` loose typing — FIXED

**File:** `src/components/TrendSparkline.tsx`

**Before:** `formatter={(value: number) => ...}` — implicit cast of Recharts' actual `ValueType` parameter to `number`.

**Fix:** Changed to `formatter={(value: ValueType) => [value as number, 'Score']}`. The explicit `as number` cast is inside the body where intent is clear. `ValueType` is imported as a type-only import from `recharts/types/component/DefaultTooltipContent`.

---

### [Low-5] `index` as data key — FIXED

**File:** `src/components/TrendSparkline.tsx`

**Before:** `const chartData = data.map((score, index) => ({ index, score }))` — `index` is a known Recharts internal property name.

**Fix:** Renamed to `x`: `data.map((score, x) => ({ x, score }))`. The `labelFormatter` parameter was updated from `index` to `x` to match. No other references were affected.

---

### [Low-6] Barrel comment misleading — FIXED

**File:** `src/components/index.ts`

**Before:** `Re-exports all reusable UI components.` — maintenance trap that becomes stale immediately.

**Fix:** Changed to `UI component exports — add new components here as they are created.`

---

### [Low-7] No-data path missing `gap` — FIXED

**File:** `src/components/DqsScoreChip.tsx`

**Before:** No-data `<Box>` lacked `gap={0.5}` and had no explanation.

**Fix:** Added `gap={0.5}` to the no-data `<Box>` for consistency with the main render path. Added a comment documenting the intentional consistency.

---

### [Low-8] Stale TDD RED PHASE banners — FIXED

**Files:** `tests/components/DqsScoreChip.test.tsx`, `tests/components/TrendSparkline.test.tsx`

**Before:** Both files opened with a multi-line TDD RED PHASE banner indicating the component did not exist yet. All per-test `// TDD RED:` inline comments were also stale.

**Fix:**
- Replaced the file-level docblock banner with a clean two-line header in both files.
- Removed all `// TDD RED: ...` inline comment lines (20 in DqsScoreChip.test.tsx, 20 in TrendSparkline.test.tsx) using a targeted sed pass.
- Retained all spec-context comments that still add value (boundary descriptions, explicit hex values, etc.).

---

### [Low-9] `aria-label` for empty data — FIXED

**File:** `src/components/TrendSparkline.tsx`

**Before:** Empty data produced `aria-label="Trend over 0 data points"` — uninformative.

**Fix:** Added a dedicated branch: when `data.length === 0`, `ariaLabel = 'No trend data available'`. The `data.length === 1` case retains `"Trend over 1 data points"` (single-point fallback path).

---

## Test Results

| Test File | Tests | Result |
|---|---|---|
| `tests/components/theme.test.ts` | 53 | PASS |
| `tests/components/DqsScoreChip.test.tsx` | 26 | PASS |
| `tests/components/TrendSparkline.test.tsx` | 20 | PASS |
| **Total** | **99** | **PASS** |

Python ruff check: **All checks passed.**

---

## Files Modified

- `dqs-dashboard/src/components/DqsScoreChip.tsx`
- `dqs-dashboard/src/components/TrendSparkline.tsx`
- `dqs-dashboard/src/components/index.ts`
- `dqs-dashboard/tests/components/DqsScoreChip.test.tsx`
- `dqs-dashboard/tests/components/TrendSparkline.test.tsx`
