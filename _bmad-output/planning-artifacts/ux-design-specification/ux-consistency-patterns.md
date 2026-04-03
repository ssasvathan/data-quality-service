# UX Consistency Patterns

## Loading States

**Philosophy:** Loading should feel fast and predictable. Use skeleton screens (not spinners) to maintain layout stability.

| Scenario | Pattern | Detail |
|----------|---------|--------|
| **Initial page load** | Skeleton screen matching target layout | Cards show skeleton rectangles for score, title, sparkline. Table shows skeleton rows. Layout doesn't shift when data arrives. |
| **Drill-down navigation** | Instant breadcrumb update + skeleton content | Breadcrumb updates immediately (confirms navigation happened). Content area shows skeletons while data loads. |
| **Search typing** | Debounced autocomplete (300ms) | No loading indicator for <300ms. After 300ms without results, show subtle "Searching..." text in dropdown. |
| **Time range change** | Sparklines fade to 50% opacity, then update | Existing data stays visible (dimmed) while new data loads. Prevents blank flash. Score numbers update in-place. |
| **Left panel dataset switch** | Right panel skeletons, left panel highlights immediately | Left panel selection is instant (confirms click). Right panel content loads with skeletons. |

**Rules:**
- Never show a blank white page. Skeleton or previous data is always visible.
- Never use spinning loaders. Skeletons communicate "content is coming in this shape."
- Layout must not shift when data arrives — skeletons match exact dimensions of real content.
- If data takes >3 seconds, show a subtle "Taking longer than usual..." caption below the skeleton.

## Empty & No-Data States

| Scenario | Display | Message |
|----------|---------|---------|
| **No datasets in LOB** | Empty card area with centered message | "No datasets monitored in {LOB Name}" — gray text, neutral icon |
| **No check results for dataset** | Empty check results list | "No check results available. This dataset may not have been processed in the current run." |
| **No trend data (new dataset)** | Single dot on sparkline + label | Sparkline shows single data point. Label: "First run — trend data available after 2+ runs" |
| **Search returns no results** | Empty dropdown with message | "No datasets matching '{query}'" — gray text in dropdown |
| **No LOBs (system empty)** | Full-page empty state | "No data quality results yet. Results will appear after the first DQS orchestration run completes." |
| **Reference data unresolved** | "N/A" in field | LOB, Owner fields show "N/A" in gray italic. Not blank — explicit acknowledgment that resolution is pending. |

**Rules:**
- Empty states always explain WHY there's no data and WHEN it's expected.
- Never show blank space where data should be. Always show an explicit "No data" message.
- Empty states use neutral gray text and a minimal icon — not alarming, just informative.

## Error & Stale Data States

| Scenario | Display | Detail |
|----------|---------|--------|
| **DQS run failed entirely** | Banner at top of page | Yellow banner below fixed header: "Latest run failed at {time}. Showing results from {previous run date}." Dismissible, but reappears on page reload until a new run succeeds. |
| **Dataset Spark job failed** | Error badge on dataset + error panel | Dataset row/card shows red "ERROR" chip instead of DQS Score. In detail view, DatasetInfoPanel shows error message in red-bordered alert box. |
| **Stale data (run >24h old)** | "Last updated" turns amber | The "Last updated" indicator in the fixed header changes from neutral gray to amber text with a clock icon: "Last updated: 28 hours ago". |
| **API unreachable** | Full-page error state | Centered message: "Unable to connect to DQS. Check your network connection or try again." Retry button. |
| **Partial load failure** | Component-level error | If one API call fails (e.g., trend data), that specific component shows "Failed to load" with a retry link. Other components render normally. |

**Rules:**
- Stale data is always preferable to no data. Show previous run's results with a clear staleness indicator rather than showing empty states.
- Error messages are human-readable sentences, never stack traces or error codes.
- Component-level failures don't take down the whole page. Isolate failures to the affected area.
- The "Last updated" indicator in the fixed header is the universal signal for data freshness — always visible, always accurate.

## Navigation Patterns

| Pattern | Behavior | Detail |
|---------|----------|--------|
| **Breadcrumb navigation** | Click any breadcrumb segment to jump to that level | `Summary` > `Consumer Banking` > `ue90-omni-transactions`. Each segment is a link. Current page is plain text (not clickable). |
| **Breadcrumb on Summary** | Single non-clickable label | Shows "Summary" as plain text — no back navigation needed at top level. |
| **Card/row click** | Full element is clickable | LOB cards and table rows navigate on click anywhere within the element. Cursor changes to pointer on hover. |
| **Browser back button** | Standard SPA routing | React Router manages history. Back button returns to previous drill level. Forward button works too. |
| **URL reflects state** | Deep-linkable routes | `/summary`, `/lob/consumer-banking`, `/dataset/ue90-omni-transactions`. Users can bookmark or share URLs that land on the exact view. |
| **Left panel navigation** | Click to switch, no route change | In Master-Detail, clicking a dataset in the left panel updates the right panel content. URL updates to reflect the selected dataset. No full page navigation. |

**Rules:**
- Every view is deep-linkable. The summary email can link directly to a specific LOB or dataset.
- Browser back/forward always works as expected. No broken history states.
- Breadcrumbs are the ONLY navigation mechanism. No sidebar, no hamburger menu, no tab bar.
- The fixed header (breadcrumbs + search + time range) is always visible. Users never lose their orientation.

## Search Patterns

| Behavior | Detail |
|----------|--------|
| **Activation** | Click search bar or press `Ctrl+K` (keyboard shortcut). Search bar in fixed header is always visible. |
| **Autocomplete** | Results appear after 2+ characters. Debounced at 300ms. Max 10 results shown. |
| **Result format** | Each result: `[DqsScoreChip] [dataset name (mono)] [LOB name (gray)]`. Score and status visible without clicking. |
| **Result ordering** | Alphabetical by dataset name within matches. Exact prefix matches first, then substring matches. |
| **No results** | Gray text: "No datasets matching '{query}'". No suggestions or "did you mean" (dataset names are technical identifiers, not prose). |
| **Selection** | Click or arrow-key + Enter to navigate to dataset detail. `Escape` closes dropdown and clears search. |
| **Keyboard flow** | `Ctrl+K` -> type -> arrow down -> Enter. Entire search-to-detail flow works without mouse. |
| **Search scope** | Global — searches all datasets across all LOBs. Not scoped to current view. |
| **While on LOB table** | Search bar filters globally (navigates away from current LOB). For within-LOB filtering, the table has its own column filter. |

**Rules:**
- Search is always global. It's the "direct access" entry point, not a filter on the current view.
- DQS Score must be visible in search results — Alex's "is it green?" check happens in the dropdown without clicking through.
- Keyboard-only flow must be complete: activate, type, navigate results, select — all without mouse.

## Data Freshness Indicators

| Location | Display | Detail |
|----------|---------|--------|
| **Fixed header** | "Last updated: 5:42 AM ET" | Always visible. Shows timestamp of most recent successful DQS run. Gray text when fresh (<12h), amber when stale (>12h). |
| **LOB card** | None (inherits from header) | LOB cards don't show individual timestamps — the global indicator covers this. |
| **Dataset table** | None (inherits from header) | Same — all datasets in a run share the same timestamp. |
| **Dataset detail** | "Last Updated: 5:42 AM ET" in DatasetInfoPanel | Per-dataset timestamp. Usually matches global, but may differ for reruns. |
| **Rerun indicator** | "Rerun #2" badge in DatasetInfoPanel | When a dataset has been rerun, the rerun number is visible. Previous run's data was expired — user sees current rerun's results. |
| **Stale data banner** | Yellow banner below header | Appears when latest run is >24h old. "Showing results from {date}. Latest run failed." |

**Rules:**
- Freshness is always visible — users should never wonder "when was this data from?"
- The fixed header indicator is the single source of truth for "when did DQS last run?"
- Reruns are explicitly labeled — users can distinguish a first run from a rerun.
- Stale data is always shown with explanation — never hidden or silently outdated.
