# Defining Core Experience

## Defining Experience

**"Glance at the score, drill into what changed — or search and check instantly."**

DQS has two co-equal defining interactions, each serving different personas but converging at the same destination:

| Interaction | Entry Point | Persona | Pattern |
|-------------|-------------|---------|---------|
| **Assess-and-drill** | Summary view -> LOB -> Dataset -> Check | Priya (steward), Marcus (exec), Sas (ops) | Top-down: scan overview for anomalies, zoom in progressively |
| **Search-and-check** | Search bar -> Dataset detail | Alex (consumer), Deepa (data owner) | Direct-access: type a name, get health status immediately |

Both interactions converge at the **dataset detail view** — the hub of the DQS experience. From there, users see the DQS Score, trend, and can drill into individual check results. The dataset detail view must be equally effective whether the user arrived via 3 levels of drill-down or a direct search.

## User Mental Model

**Assess-and-drill mental model:** "This is my monitoring dashboard. I scan for red, click to investigate." Users bring this directly from Grafana/Datadog experience. Zero new concepts — DQS just needs to execute the pattern flawlessly for data quality instead of infrastructure.

**Search-and-check mental model:** "I know my dataset name. I just need to know if it's good today." This is the Google search mental model — type, get answer. Users don't want to browse a hierarchy; they want a direct line to the one thing they care about.

**Current state (no tool exists):**
- Priya's "dashboard" today: inbox + Slack complaints + manual log queries. No overview exists.
- Alex's "pre-pipeline check" today: run the pipeline and hope. No quick health lookup exists.
- Both mental models are strong, but both are currently unserved. DQS fills the gap with familiar patterns.

**Where confusion could arise:**
- If search and drill-down feel like separate apps rather than two paths into the same data
- If the dataset detail view looks different depending on how the user arrived
- If search results don't clearly show DQS Score and status inline (forcing an extra click)

## Success Criteria

| Criteria | Measure |
|----------|---------|
| **Assess-and-drill speed** | Steward goes from summary landing to specific check detail in 3 clicks or fewer |
| **Search-and-check speed** | Consumer types dataset name and sees DQS Score + pass/fail within 2 seconds, without navigating away from search results |
| **Convergence consistency** | Dataset detail view is identical regardless of entry path (drill-down vs. search) |
| **Zero learning curve** | A Grafana/Datadog user understands the interface on first visit without guidance |
| **Trend visibility** | Every DQS Score shown anywhere in the app includes trend direction — no score without context |
| **Scan-for-red efficiency** | Summary view supports visual scanning of 50+ datasets without scrolling through a table — card grid with color-coded scores |

## Novel UX Patterns

**Pattern type: Established patterns, combined well.**

DQS does not require novel interaction design. Both defining interactions use patterns users already know:

| Pattern | Source | DQS Application |
|---------|--------|-----------------|
| Health grid overview | Datadog | Summary view with color-coded DQS Score cards |
| Progressive drill-down | Datadog, Grafana | Summary -> LOB -> Dataset -> Check breadcrumb navigation |
| Instant search | Tableau, any modern app | Dataset search with inline results showing DQS Score |
| Sparkline trends | Grafana | Inline trend charts on every card and detail view |
| Semantic color coding | Universal monitoring | Green/yellow/red mapped to DQS Score thresholds |

**DQS's unique twist:** The combination of drill-down AND direct-search as co-equal entry points into the same data. Most monitoring tools favor one pattern (Datadog = drill-down first, Grafana = panel-based). DQS treats both as first-class, with the search bar permanently visible at the top of every view — including during drill-down.

## Experience Mechanics

### Interaction 1: Assess-and-Drill

**1. Initiation:**
- User opens DQS dashboard (bookmarked URL). Lands on summary view.
- No login, no configuration, no "choose your view." Data is already there.

**2. Interaction:**
- **Scan:** Eyes sweep the card grid. Green cards are calm; yellow/red cards draw attention through color alone.
- **Click LOB:** User clicks a LOB card to see datasets within that LOB. Breadcrumb updates: `Summary > Consumer Banking`.
- **Click Dataset:** User clicks a dataset card showing a red DQS Score with downward trend arrow. Breadcrumb updates: `Summary > Consumer Banking > ue90-omni-transactions`.
- **Review checks:** Dataset detail view shows DQS Score breakdown, individual check results, and trend chart.

**3. Feedback:**
- Color-coded scores provide instant feedback at every level (green = keep moving, red = investigate here).
- Trend arrows show direction without requiring comparison ("this is getting worse" vs. "this has always been bad").
- Breadcrumbs always show location — user never feels lost.

**4. Completion:**
- User has identified the specific check that degraded, sees the metric values and trend, and has enough context to take action (escalate, wait, or dismiss).
- User clicks breadcrumb to return to any higher level, or starts a new investigation.

### Interaction 2: Search-and-Check

**1. Initiation:**
- Search bar is permanently visible at the top of every view. User clicks into it or uses keyboard shortcut.

**2. Interaction:**
- **Type:** User types partial dataset name (e.g., "ue90-omni"). Autocomplete shows matching datasets with DQS Score and status inline in the dropdown.
- **Select:** User clicks a result. Navigates directly to dataset detail view.

**3. Feedback:**
- Search results show DQS Score + status chip (green/yellow/red) inline — user may not even need to click through if they just need a quick "is it green?" check.
- If multiple matches, results are sorted by relevance with scores visible for each.

**4. Completion:**
- For quick checks: User sees green in the search dropdown, closes search, starts their pipeline. Total time: under 5 seconds.
- For investigation: User clicks through to dataset detail, same view as drill-down path. Full context available.
