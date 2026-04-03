# Epic 4: Quality Dashboard & Drill-Down Reporting

Data stewards, executives, downstream consumers, and data owners can view, search, and investigate data quality through a 3-level drill-down web dashboard backed by a REST API.

## Story 4.1: MUI Theme & Design System Foundation

As a **developer**,
I want a custom MUI 7 theme implementing the DQS visual design (color palette, typography, spacing),
So that all dashboard components render with a consistent, professional look from the start.

**Acceptance Criteria:**

**Given** the dqs-dashboard project
**When** the theme is applied via MUI ThemeProvider
**Then** the neutral palette is configured (neutral-50 #FAFAFA through neutral-900 #212121)
**And** semantic colors map to DQS Score thresholds: success #2E7D32 (>=80), warning #ED6C02 (60-79), error #D32F2F (<60) with light tint variants
**And** primary accent is #1565C0 for links, active states, and focus
**And** typography uses system font stack with defined scale (h1 24px/600 through caption 12px/400, score 28px/700, score-sm 18px/700, mono 13px)
**And** spacing follows 8px grid (xs=4, sm=8, md=16, lg=24, xl=32, 2xl=48)
**And** card styling uses 1px neutral-200 border, 8px border-radius, no box-shadow

## Story 4.2: FastAPI Summary & LOB API Endpoints

As a **data steward**,
I want REST API endpoints for summary and LOB-level data,
So that the dashboard can display overall quality status and LOB drill-down.

**Acceptance Criteria:**

**Given** the dqs-serve application is running with test data
**When** I call `GET /api/summary`
**Then** it returns aggregated LOB data: total datasets, healthy/degraded/critical counts, per-LOB aggregate DQS scores with trend data
**And** the response uses snake_case JSON field names

**Given** test data with multiple LOBs
**When** I call `GET /api/lobs`
**Then** it returns all LOBs with aggregate scores, dataset counts, and status distributions

**Given** a valid LOB identifier
**When** I call `GET /api/lobs/{lob_id}/datasets`
**Then** it returns all datasets within that LOB with DQS scores, trend sparkline data, and per-check status chips (freshness, volume, schema)
**And** results support a `time_range` query parameter (7d, 30d, 90d)

**Given** all queries
**When** the serve layer reads data
**Then** it queries active-record views (`v_*_active`), never raw tables

## Story 4.3: FastAPI Dataset Detail & Metrics API Endpoints

As a **data steward**,
I want REST API endpoints for dataset detail, metrics, and trend data,
So that the dashboard can display check results, score breakdowns, and trend charts.

**Acceptance Criteria:**

**Given** a valid dataset identifier
**When** I call `GET /api/datasets/{dataset_id}`
**Then** it returns full dataset metadata: name, LOB, source system, format, HDFS path, parent path, partition date, row count, previous row count, last updated, run ID, rerun number, DQS score, check status

**Given** a valid dataset identifier
**When** I call `GET /api/datasets/{dataset_id}/metrics`
**Then** it returns all check results: per-check status, score, weight, and metric values from both `dq_metric_numeric` and `dq_metric_detail`

**Given** a valid dataset identifier and time_range=30d
**When** I call `GET /api/datasets/{dataset_id}/trend`
**Then** it returns daily DQS score values for the past 30 days for sparkline rendering
**And** trend queries spanning 90 days respond within 2 seconds

## Story 4.4: Dataset Search API Endpoint

As a **downstream consumer**,
I want a REST API search endpoint that finds datasets by name with DQS scores inline,
So that the dashboard search can show health status without navigating to each dataset.

**Acceptance Criteria:**

**Given** datasets exist in the database
**When** I call `GET /api/search?q=ue90`
**Then** it returns matching datasets with: dataset_name, LOB, DQS score, check_status — ordered by prefix match first, then substring

**Given** a query matching multiple datasets
**When** results are returned
**Then** maximum 10 results are included

**Given** a query with no matches
**When** I call the search endpoint
**Then** it returns an empty array (not an error)

## Story 4.5: Reference Data Resolution & Caching

As a **data steward**,
I want lookup codes resolved to LOB, owner, and classification names in API responses,
So that datasets are displayed with human-readable business context instead of raw codes.

**Acceptance Criteria:**

**Given** the reference data service is configured
**When** the serve layer starts
**Then** it populates a LOB mapping cache from the lookup table

**Given** the cache is populated
**When** an API response includes a lookup code
**Then** the code is resolved to LOB name, owner, and classification in the response

**Given** the cache is older than 12 hours
**When** the refresh timer fires (twice daily)
**Then** the cache is refreshed from the source

**Given** a lookup code with no mapping
**When** the API resolves it
**Then** LOB, owner, and classification fields return "N/A" (not null, not error)

## Story 4.6: DqsScoreChip & TrendSparkline Components

As a **data steward**,
I want reusable DqsScoreChip and TrendSparkline components,
So that DQS scores and trends are rendered consistently across all dashboard views.

**Acceptance Criteria:**

**Given** a DqsScoreChip with score=87 and previousScore=84
**When** rendered
**Then** it displays "87" in green text with an up arrow and "+3" delta
**And** it supports sizes lg (28px), md (18px), sm (14px)

**Given** a DqsScoreChip with score=55
**When** rendered
**Then** it displays "55" in red text (critical threshold < 60)

**Given** a DqsScoreChip with no score data
**When** rendered
**Then** it displays "—" in gray text

**Given** a TrendSparkline with 30 data points
**When** rendered at size md (32px tall)
**Then** it displays a Recharts line chart with no axes/labels, threshold-based line color, and tooltip on hover showing date + score

**Given** a TrendSparkline with only 1 data point
**When** rendered
**Then** it shows a single dot with a "First run" placeholder

**And** both components have aria-labels describing score/status/trend for screen readers

## Story 4.7: DatasetCard (LOB Card) Component

As a **data steward**,
I want a DatasetCard component displaying LOB-level health summary,
So that the Summary view shows scannable LOB cards with scores, trends, and status counts.

**Acceptance Criteria:**

**Given** a DatasetCard with lobName="Consumer Banking", dqsScore=87, statusCounts={pass:16, warn:1, fail:1}
**When** rendered
**Then** it displays LOB name, dataset count, DqsScoreChip (large), score progress bar, TrendSparkline, and status count chips

**Given** a user hovers over the card
**When** the hover state activates
**Then** the border color transitions to primary (#1565C0)

**Given** a user clicks anywhere on the card
**When** the click handler fires
**Then** it navigates to the LOB Detail view for that LOB

**And** the card has `role="button"`, is keyboard focusable (Tab), and activatable via Enter/Space
**And** `aria-label` includes LOB name, DQS score, dataset count, and status summary

## Story 4.8: App Layout with Fixed Header, Routing & React Query

As a **user**,
I want a persistent fixed header with breadcrumbs, time range toggle, and search bar, with deep-linkable routes,
So that I always have navigation context and can bookmark or share any view.

**Acceptance Criteria:**

**Given** the dashboard is loaded
**When** any view is displayed
**Then** a fixed header is visible with breadcrumb navigation, time range toggle (7d/30d/90d), and search bar

**Given** React Router 7 is configured
**When** I navigate to `/summary`, `/lob/:id`, or `/dataset/:id`
**Then** the correct page component renders with breadcrumbs reflecting the current path

**Given** I change the time range from 7d to 90d
**When** the toggle updates
**Then** React Query invalidates all cached queries and components refetch with the new time range

**Given** I use browser back/forward buttons
**When** navigating between views
**Then** routing works correctly and breadcrumbs update to match

**And** `<header>`, `<main>`, and `<nav>` landmark regions are present for screen reader navigation
**And** a hidden "Skip to main content" link is visible on Tab focus

## Story 4.9: Summary Card Grid View (Level 1)

As a **data steward**,
I want a Summary view showing a stats bar and LOB card grid,
So that I can assess overall data quality health at a glance and identify LOBs needing attention.

**Acceptance Criteria:**

**Given** the dashboard loads on `/summary`
**When** the Summary page renders
**Then** a stats bar shows total datasets, healthy count, degraded count, and critical count
**And** a 3-column grid displays DatasetCard components for each LOB

**Given** LOB cards are displayed
**When** I scan the grid
**Then** each card shows LOB name, dataset count, DQS score (color-coded), trend sparkline, and status chip summary (pass/warn/fail counts)

**Given** an LOB with a critical DQS score (< 60)
**When** the card renders
**Then** the score displays in red, providing immediate visual signal

**Given** I click an LOB card
**When** navigation occurs
**Then** I am taken to `/lob/{lob_id}` (LOB Detail view)

**Given** the data is loading
**When** the page first renders
**Then** skeleton cards matching the target layout dimensions are displayed (no layout shift when data arrives)

## Story 4.10: LOB Detail View (Level 2)

As a **data steward**,
I want a LOB Detail view with stats header and sortable dataset table,
So that I can scan all datasets in a LOB and identify which need attention.

**Acceptance Criteria:**

**Given** I navigate to `/lob/{lob_id}`
**When** the LOB Detail page renders
**Then** a stats header shows LOB name, dataset count, average DQS score with trend, checks passing rate, and last run time
**And** a MUI DataGrid table shows all datasets with columns: dataset name (monospace), DQS Score (color-coded), trend sparkline (mini), freshness/volume/schema status chips, overall status chip

**Given** the table is displayed
**When** I click a column header
**Then** the table sorts by that column (default: DQS Score ascending — worst first)

**Given** I click a dataset row
**When** navigation occurs
**Then** I am taken to `/dataset/{dataset_id}` (Dataset Detail view)

**Given** the breadcrumb shows `Summary > {LOB Name}`
**When** I click `Summary`
**Then** I navigate back to the Summary Card Grid

**Given** the table is loading
**When** the page first renders
**Then** skeleton rows are displayed matching the table layout

## Story 4.11: DatasetInfoPanel Component

As a **platform operator**,
I want a DatasetInfoPanel showing complete dataset metadata with HDFS path copy functionality,
So that I can trace quality issues back to source data and share paths for escalation.

**Acceptance Criteria:**

**Given** a dataset with full metadata
**When** the DatasetInfoPanel renders
**Then** it displays: source system, LOB, owner, format (Avro/Parquet), HDFS path (monospace, word-breaks on `/`), parent path, partition date, row count with delta from previous ("4,201 (was 21,043)"), last updated timestamp, run ID, and rerun number if applicable

**Given** the HDFS path is displayed
**When** I click the copy button (clipboard icon)
**Then** the full path is copied to clipboard and a "Copied!" tooltip confirms the action

**Given** a dataset where the Spark job failed
**When** the panel renders
**Then** an error message is displayed in a red-bordered alert box below the metadata

**Given** unresolved reference data
**When** the panel renders LOB and owner fields
**Then** they show "N/A" in gray italic text

**And** the panel uses semantic `dl`/`dt`/`dd` structure for screen reader accessibility
**And** the copy button has `aria-label="Copy HDFS path to clipboard"`

## Story 4.12: Dataset Detail View (Level 3)

As a **data steward**,
I want a Dataset Detail view with Master-Detail split showing dataset list and full check breakdown,
So that I can investigate specific datasets and navigate between datasets without returning to the table.

**Acceptance Criteria:**

**Given** I navigate to `/dataset/{dataset_id}`
**When** the Dataset Detail page renders
**Then** a left panel shows a scrollable list of datasets for the current LOB, each with DqsScoreChip + name (mono) + trend arrow, sorted by DQS Score ascending
**And** the active dataset is highlighted with primary-light background and left border
**And** the right panel shows: dataset header (name + status chip + metadata line), 2-column grid with DQS Score + trend chart + check results list on the left, score breakdown card + DatasetInfoPanel on the right

**Given** the check results list
**When** rendered
**Then** each check shows: status chip, check name, weight percentage, individual score — with failed/warning checks visually emphasized

**Given** the score breakdown card
**When** rendered
**Then** each check category shows a weighted score bar (e.g., "Freshness 19/20") visualizing contribution to the composite DQS Score

**Given** I click a different dataset in the left panel
**When** the selection changes
**Then** the right panel updates with the new dataset's data (no full page navigation) and the URL updates to reflect the selected dataset

**Given** breadcrumb shows `Summary > {LOB} > {Dataset}`
**When** I click `{LOB}`
**Then** I navigate back to the LOB Detail table

## Story 4.13: Global Dataset Search

As a **downstream consumer**,
I want a global search autocomplete in the fixed header that shows DQS scores inline in results,
So that I can check dataset health in seconds without browsing the hierarchy.

**Acceptance Criteria:**

**Given** the search bar in the fixed header
**When** I type 2+ characters (e.g., "ue90")
**Then** an autocomplete dropdown appears after 300ms debounce showing matching datasets with DqsScoreChip + dataset name (mono) + LOB name (gray)

**Given** search results are displayed
**When** I see green DQS scores for my datasets in the dropdown
**Then** I can close the search with Escape — health check complete without navigating

**Given** I click a search result (or arrow-key + Enter)
**When** the result is selected
**Then** I navigate directly to that dataset's Detail view

**Given** the keyboard shortcut `Ctrl+K`
**When** pressed on any view
**Then** the search bar is focused and ready for input

**Given** a search with no matches
**When** the dropdown renders
**Then** it shows "No datasets matching '{query}'" in gray text

**And** search is global across all LOBs, max 10 results, prefix matches first then substring

## Story 4.14: Loading, Empty & Error States

As a **user**,
I want consistent loading skeletons, empty state messages, and error handling across all views,
So that the dashboard never shows blank pages and I always understand the current state.

**Acceptance Criteria:**

**Given** any view is loading data
**When** React Query is fetching
**Then** skeleton screens matching the target layout are displayed (cards, table rows, detail panels) — no layout shift when data arrives
**And** no spinning loaders appear anywhere in the application

**Given** a time range change (e.g., 7d to 30d)
**When** data is refetching
**Then** existing sparklines fade to 50% opacity and score numbers update in-place when new data arrives

**Given** no datasets exist in a LOB
**When** the LOB Detail view renders
**Then** it shows "No datasets monitored in {LOB Name}" centered in the content area

**Given** no data exists in the system
**When** the Summary view renders
**Then** a full-page empty state shows "No data quality results yet. Results will appear after the first DQS orchestration run completes."

**Given** the latest DQS run is >24h old
**When** the header renders
**Then** the "Last updated" indicator shows in amber text: "Last updated: 28 hours ago"

**Given** a DQS run failed entirely
**When** the dashboard loads
**Then** a yellow banner appears below the header: "Latest run failed at {time}. Showing results from {previous run date}."

**Given** one API call fails (e.g., trend data)
**When** the page renders
**Then** the affected component shows "Failed to load" with a retry link, while other components render normally

**Given** the API is unreachable
**When** any page loads
**Then** a full-page error shows "Unable to connect to DQS. Check your network connection or try again." with a retry button

## Story 4.15: Accessibility & Viewport Polish

As a **user**,
I want the dashboard to meet WCAG 2.1 AA accessibility standards and handle desktop viewports gracefully,
So that keyboard-only users and screen reader users can access all functionality.

**Acceptance Criteria:**

**Given** the dashboard is loaded
**When** I press Tab
**Then** a "Skip to main content" link is visible, and pressing Enter skips to the `<main>` region

**Given** I navigate using only the keyboard
**When** I Tab through all views
**Then** all interactive elements (cards, table rows, search, breadcrumbs, time range toggle) are focusable and activatable via Enter/Space
**And** MUI default focus rings are visible on all focused elements

**Given** a time range change triggers data reload
**When** new data arrives
**Then** `aria-live="polite"` announces "Data updated for {range} range" without interrupting screen reader flow

**Given** a viewport width of 1280px (standard)
**When** the dashboard renders
**Then** the layout uses 3-column LOB card grid, full-width table, and 380px left panel in Master-Detail

**Given** a viewport width > 1440px
**When** the dashboard renders
**Then** content is centered at max-width 1440px with side margins

**Given** a viewport width < 1280px
**When** the dashboard renders
**Then** LOB cards reflow to 2 columns, table gains horizontal scroll, and left panel narrows to 300px

**And** axe-core (@axe-core/react) is installed and configured to run during development
**And** all custom components pass axe-core automated checks with zero violations

---
