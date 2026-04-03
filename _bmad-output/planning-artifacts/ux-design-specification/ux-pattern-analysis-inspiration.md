# UX Pattern Analysis & Inspiration

## Inspiring Products Analysis

**Tableau**
- Excels at: Clean data visualization with progressive detail. Users start with a high-level view and interact to explore. Visualizations communicate before you read labels.
- Key UX strength: Visual hierarchy is impeccable — the most important metric dominates, supporting details are present but subordinate. White space is used aggressively to prevent clutter.
- Relevant to DQS: Tableau's approach to summary-then-detail is exactly the pattern DQS needs. Data stewards and executives already think in Tableau's visual language.

**Grafana**
- Excels at: Panel-based dashboard layout with consistent patterns. Each panel is a self-contained unit (metric + trend + status). Users scan panels like reading a newspaper — top-left to bottom-right, big things first.
- Key UX strength: Uniform panel structure means once you understand one panel, you understand them all. Time range selector is global — one control changes every panel. Drill-down happens by clicking into any panel.
- Relevant to DQS: Grafana's panel consistency maps directly to DQS dataset cards. Each dataset could follow the same card pattern (DQS Score + trend + status). Users who know Grafana will feel immediately at home.

**Datadog**
- Excels at: Monitoring overview with color-coded health status. The main view is a grid of services with green/yellow/red indicators. You scan for non-green, click to investigate. Information density is high but never feels cluttered because of consistent visual structure.
- Key UX strength: The "overview -> drill-down" flow is seamless. Clicking a service reveals nested layers of detail without losing context. Breadcrumbs and persistent navigation keep users oriented. Alert states are unambiguous.
- Relevant to DQS: Datadog's health grid pattern is the closest analog to what DQS summary view needs. Color-coded health across many monitored entities, with drill-down to specifics. The "scan for red" pattern is exactly Priya's morning workflow.

## Transferable UX Patterns

**Navigation Patterns:**
- **Overview-first landing** (all three) — The default view is always the highest-level summary. No onboarding, no configuration wizard, no "choose your view." You land on the overview and drill from there.
- **Breadcrumb drill-down** (Datadog) — As users drill deeper (Summary -> LOB -> Dataset -> Check), persistent breadcrumbs show exactly where they are and allow one-click navigation to any parent level.
- **Global time range selector** (Grafana) — One control changes the time window across all visualizations. Critical for DQS trend analysis — "show me last 7 days" vs. "last 30 days" should be a single interaction.

**Interaction Patterns:**
- **Click-to-drill, not hover-to-reveal** (Datadog) — Primary interactions use clicks, not hover states. Reliable on all devices, accessible, and predictable. Hover is reserved for tooltips with supplementary detail.
- **Consistent card/panel structure** (Grafana) — Every dataset rendered with identical visual structure: score, trend sparkline, status indicator. Once you read one, you can read all. No per-dataset layout variation.
- **Search as first-class navigation** (Tableau) — Prominent search bar that filters the current view. Alex's "find my dataset" flow should be as fast as Cmd+K in any modern tool.

**Visual Patterns:**
- **Muted palette with semantic color** (Datadog) — Background and structure use neutral grays/whites. Color is reserved exclusively for meaning: green = healthy, yellow = warning, red = critical. No decorative color.
- **Sparkline trends inline** (Grafana) — Small trend charts embedded directly in summary cards, not hidden behind a click. Users see direction of change without navigating.
- **White space as structure** (Tableau) — Generous spacing between elements prevents the "wall of data" feeling. Fewer things shown clearly beats more things shown densely.

## Anti-Patterns to Avoid

- **Cluttered overview with too many metrics** — Showing every metric on the summary view defeats its purpose. DQS Score is the primary signal; individual check results belong in drill-down, not the overview.
- **Configuration-heavy first experience** — Some monitoring tools require dashboard setup before showing value. DQS must work out of the box — no "add a panel" or "configure your view" before users see data.
- **Modal dialogs for drill-down** — Modals break spatial orientation. Drill-down should feel like navigating deeper into the same space, not opening pop-ups that obscure context.
- **Dense data tables as the primary view** — Tables are useful for dataset lists, but the summary should be visual (cards with scores and sparklines), not a spreadsheet. Tables belong in the detail layers.
- **Inconsistent card layouts** — If some datasets show 3 metrics and others show 5, the visual scanning pattern breaks. Every card must have identical structure regardless of which checks apply.

## Design Inspiration Strategy

**Adopt:**
- Datadog's color-coded health grid as the model for DQS summary view — scan for non-green, click to investigate
- Grafana's consistent panel/card structure for dataset representation — DQS Score + sparkline + status per dataset
- Grafana's global time range selector for trend windows
- Tableau's white space discipline to prevent clutter

**Adapt:**
- Datadog's breadcrumb drill-down — adapt for the 4-level DQS hierarchy (Summary -> LOB -> Dataset -> Check Detail)
- Grafana's panel layout — simplify to fixed card structure (DQS doesn't need Grafana's flexibility/configurability)
- Tableau's search — adapt as a prominent dataset search bar that works at any drill level

**Avoid:**
- Grafana's configuration complexity — DQS is zero-config for consumers
- Tableau's learning curve — DQS must be immediately readable without training
- Any tool's tendency toward information overload at the summary level
