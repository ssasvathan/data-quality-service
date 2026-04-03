# Design System Foundation

## Design System Choice

**MUI (Material UI)** — React component library with theming, chosen for speed of solo development and comprehensive pre-built components that map directly to DQS dashboard needs.

## Rationale for Selection

| Factor | Decision Driver |
|--------|----------------|
| **Development speed** | Solo developer needs pre-built components. MUI provides DataGrid, Card, Breadcrumb, Chip, LinearProgress, and Table out of the box — all core DQS UI elements. |
| **Theming system** | MUI's theme provider sets the muted professional palette once (semantic green/yellow/red, neutral grays) and propagates to all components. Supports the "calm over clever" design principle. |
| **User familiarity** | Users accustomed to Tableau/Grafana/Datadog find MUI's clean structured layouts natural. No visual learning curve. |
| **Charting compatibility** | Pairs well with lightweight charting libraries (Recharts or Nivo) for sparkline trends and time-series visualizations. |
| **Enterprise fit** | Material Design's structured, grid-based layout is appropriate for an internal data platform. No brand uniqueness required. |
| **Accessibility** | Built-in ARIA support, keyboard navigation, and focus management — appropriate baseline for an internal tool. |

## Implementation Approach

**Core MUI components mapped to DQS UI elements:**

| DQS Element | MUI Component | Usage |
|-------------|---------------|-------|
| Dataset cards | `Card` + `CardContent` | Consistent card structure: DQS Score + sparkline + status per dataset |
| Summary health grid | `Grid` + `Card` | Responsive grid of dataset/LOB cards in overview |
| Drill-down navigation | `Breadcrumbs` | Summary > LOB > Dataset > Check Detail path |
| Dataset search | `TextField` with `Autocomplete` | Prominent search bar for find-my-dataset flow |
| Data tables | `DataGrid` | Check results, metric details, dataset lists in detail views |
| Status indicators | `Chip` + semantic colors | Green/yellow/red health status badges |
| DQS Score display | `Typography` + `LinearProgress` | Score number with color-coded progress bar |
| Trend sparklines | Recharts `Sparkline` or Nivo `Line` | Inline trend charts in cards and detail views |
| Time range selector | `ToggleButtonGroup` | Global time window control (7d / 30d / 90d) |
| Score breakdown | `List` + `LinearProgress` | Weighted check contribution to DQS Score |

**Charting library:** Recharts (lightweight, React-native, sufficient for sparklines and trend lines). Nivo as alternative if richer visualization needed later.

## Customization Strategy

**Theme overrides (minimal, applied globally):**

- **Palette:** Muted neutral background (gray-50/white). Semantic colors only: `success` (green), `warning` (amber), `error` (red) mapped to DQS Score thresholds. No decorative color.
- **Typography:** System font stack for performance. Clear hierarchy: page title > section header > card title > body > caption.
- **Spacing:** Generous — Tableau-inspired white space. Default MUI spacing scale with increased card gaps.
- **Border radius:** Subtle (4-8px) — professional, not playful.
- **Shadows:** Minimal elevation. Cards use light border rather than heavy shadows.

**Custom components (build on top of MUI):**
- `DqsScoreChip` — Color-coded score badge with trend arrow (wraps MUI Chip)
- `DatasetCard` — Standardized card with DQS Score, sparkline, check summary (wraps MUI Card)
- `DrilldownBreadcrumb` — 4-level breadcrumb with LOB/dataset context (wraps MUI Breadcrumbs)
- `TrendSparkline` — Inline trend chart component (wraps Recharts)

**What is NOT customized:** Standard MUI behavior for DataGrid, Tables, Autocomplete, navigation. Default MUI interaction patterns are familiar and tested — no need to override.
