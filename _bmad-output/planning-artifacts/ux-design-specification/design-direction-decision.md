# Design Direction Decision

## Design Directions Explored

Six design directions were generated and evaluated as interactive HTML mockups (`ux-design-directions.html`):

1. **Card Grid** — Datadog-inspired color-coded LOB cards with sparklines
2. **Stats + Table** — Dense sortable table with inline sparklines per dataset
3. **Dashboard Panels** — Grafana-style panels with large KPIs and LOB summaries
4. **Master-Detail Split** — List on left, full detail on right for investigation
5. **Status Columns** — Kanban-style triage columns (Critical/Degraded/Healthy)
6. **Minimal Density** — Maximum white space, problems-first list

## Chosen Direction

**Hybrid: Card Grid -> Stats + Table -> Master-Detail Split**

The chosen approach uses three different layout patterns, one per drill-down level, each optimized for the task at that depth:

| Drill Level | Layout | User Task | Why This Layout |
|-------------|--------|-----------|-----------------|
| **Summary** | Card Grid (Direction 1) | "Anything wrong across LOBs?" | Visual scanning of 3-6 LOB cards. Color and sparklines communicate health at a glance. Fastest path to "scan for red." |
| **LOB Detail** | Stats + Table (Direction 2) | "Which datasets in this LOB need attention?" | Stats header shows LOB aggregate metrics. Sortable table surfaces problems quickly across 10-50 datasets. Inline sparklines and per-check status chips give enough detail to decide where to drill. |
| **Dataset Detail** | Master-Detail Split (Direction 4) | "What exactly happened to this dataset?" | Full investigation view. Check results with scores and weights, score breakdown sidebar, trend chart, and complete dataset info with HDFS path for source traceability. |

## Design Rationale

1. **Each level uses the optimal layout for its task.** Card grids are best for scanning few entities visually (LOBs). Tables are best for scanning many entities with sortable attributes (datasets). Split views are best for investigation with rich context (checks + metadata).

2. **The transition feels like progressive zoom**, not separate pages. Summary (wide, visual) -> LOB (structured, scannable) -> Dataset (deep, detailed). Information density increases with each level, matching the user's intent: triage -> identify -> investigate.

3. **Both defining interactions converge naturally.** Assess-and-drill follows the full path: Card Grid -> Table -> Detail. Search-and-check lands directly on the Master-Detail view. Both arrive at the same dataset detail experience.

4. **HDFS path traceability.** The dataset detail panel includes full source path information, enabling direct tracing from a quality issue back to the HDFS directory. This is critical for ops workflows where the next step after diagnosis is checking the source data.

## Implementation Approach

### Level 1: Summary View (Card Grid)

**Layout:** Stats bar (total datasets, healthy, degraded, critical counts) + LOB card grid (3 columns).

**LOB Card contents:**
- LOB name + dataset count
- DQS Score (large, color-coded) + trend arrow with delta
- Score bar (color-coded progress)
- Sparkline (30-day trend by default, respects global time selector)
- Status chip summary (X pass, Y warn, Z fail)

**Interactions:**
- Click LOB card -> navigates to LOB Detail view
- Breadcrumb shows: `Summary`
- Search bar always visible in fixed header

### Level 2: LOB Detail View (Stats + Table)

**Layout:** Stats bar (LOB name, dataset count, avg DQS score + trend, checks passing rate, last run time) + sortable data table.

**Table columns:**
- Dataset name (monospace)
- DQS Score (color-coded)
- Trend sparkline (mini, 80px wide)
- Freshness status chip
- Volume status chip
- Schema status chip
- Overall status chip (PASS/WARN/FAIL)

**Interactions:**
- Click table row -> navigates to Dataset Detail view
- Column headers sortable (default: sort by DQS Score ascending, worst first)
- Breadcrumb shows: `Summary > {LOB Name}`
- Search bar filters the current table

### Level 3: Dataset Detail View (Master-Detail Split)

**Layout:** Left panel (dataset list for current LOB, scrollable) + Right panel (selected dataset's full detail).

**Left panel (dataset list):**
- LOB header with dataset count
- Each item shows: DQS Score + dataset name (mono) + check summary + trend arrow
- Active item highlighted with primary-light background + left border
- Sorted by DQS Score ascending (worst first)

**Right panel (dataset detail):**

**Header section:**
- Dataset name (h2, monospace) + status chip (PASS/WARN/FAIL)
- Metadata line: LOB, source system, format (Avro/Parquet), last run time

**Main area (2-column grid):**

*Left column:*
- DQS Score (large) + trend delta
- Trend chart (larger sparkline, 64px tall, 30/90 day)
- Check results list:
  - Each check: status chip + check name + weight percentage + individual score
  - Failed/warning checks visually emphasized (border color)

*Right column:*
- Score breakdown card:
  - Each check category with weighted score bar (e.g., "Freshness 19/20")
  - Visual representation of how each check contributes to the composite DQS Score
- Dataset info card:
  - **Source System:** lookup code (e.g., CB10)
  - **LOB:** resolved from reference data (e.g., Commercial Banking)
  - **Owner:** resolved from reference data (or "N/A")
  - **Format:** Avro / Parquet
  - **HDFS Path:** full path (e.g., `/prod/abc/data/consumerzone/src_sys_nm=cb10/partition_date=20260330`) — monospace, selectable for copy
  - **Parent Path:** configured scan path (e.g., `/prod/abc/data/consumerzone/`)
  - **Partition Date:** date of the data being checked
  - **Row Count:** current count with delta from previous (e.g., "4,201 (was 21,043)")
  - **Last Updated:** timestamp of most recent DQS run
  - **Run ID:** orchestration run reference
  - **Rerun #:** if applicable

**Interactions:**
- Click dataset in left list -> right panel updates (no page navigation)
- Breadcrumb shows: `Summary > {LOB Name} > {Dataset Name}`
- Search bar navigates directly to any dataset's detail view
- Clicking a check result row could expand to show metric details (future enhancement)

### Persistent Elements (All Levels)

- **Fixed header:** Breadcrumbs + Time range toggle (7d/30d/90d) + Search bar
- **Search bar:** Autocomplete with DQS Score + status inline in dropdown results
- **Time range toggle:** Global, affects all sparklines and trend calculations
- **"Last updated" indicator:** Visible in header, shows when DQS last ran successfully
