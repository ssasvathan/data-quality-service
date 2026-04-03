# Core User Experience

## Defining Experience

The core DQS experience is **assess-then-drill**: users open the dashboard, instantly read overall data quality health from the **DQS Score** — a weighted composite score that rolls up from individual checks -> dataset -> LOB -> summary. Every user journey begins with this score: Priya sees which datasets degraded, Marcus sees which LOBs underperform, Alex sees pass/fail at a glance. The DQS Score is the universal language of the dashboard — it translates statistical check results into a single, comparable health signal at every level of the hierarchy.

The dashboard is not a reporting tool you query; it's a health monitor you read. The DQS Score is its heartbeat.

## Platform Strategy

| Dimension | Decision |
|-----------|----------|
| Platform | Web application (React SPA) |
| Rendering | Client-side, no SSR (internal tool, no SEO) |
| Target devices | Desktop workstations (mouse/keyboard primary) |
| Browsers | Chrome, Edge (latest versions only) |
| Network | Internal network/VPN only |
| Authentication | None (network boundary security) |
| Offline | Not required |
| Responsive | Desktop-only; no tablet/mobile optimization |

## DQS Score as Core UX Element

The **DQS Score (#25)** is the primary visual anchor across all dashboard views. It is a configurable weighted composite of all executed checks for a dataset, and it drives the UX at every level:

| Level | DQS Score Role |
|-------|---------------|
| **Summary view** | Aggregate score across all datasets — overall platform health. Distribution of scores (how many green/yellow/red) gives instant triage signal. |
| **LOB view** | Average/aggregate DQS Score per LOB — enables cross-LOB comparison for executives. Trend line shows LOB health over time. |
| **Dataset view** | Individual dataset DQS Score with trend sparkline — the steward's primary indicator. Score breakdown shows which check categories contribute to the score. |
| **Check detail view** | Individual check pass/fail with the weight each check contributes to the dataset's DQS Score — transparency into how the score is computed. |

**Score visualization principles:**
- Color-coded thresholds (green/yellow/red) provide instant signal without reading numbers
- Trend direction arrow (up/down/stable) accompanies every score display
- Score breakdown is always one click away — users can see *why* a score is what it is
- Configurable weights are visible in check detail so users understand scoring logic

## Effortless Interactions

1. **Assess-at-a-glance** — The summary view communicates overall health via DQS Scores without reading raw metrics. Color-coded scores and trend arrows convey status instantly.
2. **Seamless drill-down** — Moving from summary -> LOB -> dataset -> check detail should feel like zooming into a map, not navigating separate pages. The DQS Score carries through every level, providing a consistent anchor. Context carries forward; the user always knows where they are and how to zoom back out.
3. **Find-my-dataset** — Alex types a dataset name and gets DQS Score, pass/fail status, and key indicators immediately. No browsing, no multi-step filtering, no waiting.
4. **Automatic trend context** — Every DQS Score and metric includes its trend direction. Users never have to manually compare today vs. yesterday — the system surfaces what changed.
5. **Score transparency** — Users can always drill into a DQS Score to see the weighted breakdown of contributing checks. No black-box scoring — trust comes from transparency.

## Critical Success Moments

1. **"Monday morning confidence"** — Priya opens the dashboard, sees DQS Scores across her LOB's datasets, and within 5 minutes knows if the weekend was clean or needs action. Green scores = confidence. Yellow/red = drill in. This is the defining success moment.
2. **"Caught it before they did"** — The first time a steward spots a dropping DQS Score and flags it to the source team before downstream consumers complain. This is the moment DQS proves its value.
3. **"Data-driven quality argument"** — Marcus presents DQS Score trends by LOB in a quarterly review instead of anecdotes. "Consumer Banking LOB improved from 72 to 89 over 3 months" replaces "we think data quality is okay."
4. **"Confident pipeline kick-off"** — Alex checks three datasets, sees green DQS Scores, and starts the fraud pipeline knowing the data is good. Two minutes of dashboard time saves hours of potential rework.

## Experience Principles

1. **DQS Score is the universal language** — Every view, every level, every persona uses the DQS Score as the primary health indicator. It is the consistent thread from summary to check detail.
2. **Signal over noise** — Show DQS Score and health status first, details on demand. Never force users to interpret raw numbers when a color, score, or trend arrow communicates the same thing faster.
3. **Progressive disclosure** — Summary -> LOB -> Dataset -> Check. Each level adds detail without losing context. Users control their depth; the system never overwhelms.
4. **Trend-first, not snapshot-first** — A single score in isolation is rarely actionable. Always show direction of change alongside current state. "DQS Score dropped from 95 to 62 since Saturday" is useful; "DQS Score: 62" alone is not.
5. **Zero-config consumption** — The dashboard works for all personas without configuration, personalization, or onboarding. A new user opens it and immediately understands the DQS Score and what the colors mean.
