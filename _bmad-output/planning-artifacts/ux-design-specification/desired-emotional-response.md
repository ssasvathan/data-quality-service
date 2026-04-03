# Desired Emotional Response

## Primary Emotional Goals

| Emotional Goal | Description | Before DQS | After DQS |
|---------------|-------------|------------|-----------|
| **Calm confidence** | The primary emotional state. Users check DQS like a trusted instrument panel — not with excitement, but with the quiet assurance that they'll see what they need to see. | Anxiety, uncertainty, dread | "I know the state of my data" |
| **Equipped to act** | When issues surface, users feel empowered rather than alarmed. Red scores aren't sirens — they're clear signals with enough context to take the next step. | Helplessness, reactive scrambling | "I know what's wrong and what to do next" |
| **Trust** | The foundational emotion. Users believe the DQS Score accurately reflects reality. Without trust, no other emotional goal matters — users simply stop checking. | Skepticism about data quality tools | "If DQS says it's green, it's green" |

## Emotional Journey Mapping

| Stage | Desired Emotion | Design Implication |
|-------|----------------|-------------------|
| **First encounter** | Clarity — "I immediately understand what I'm looking at" | Zero-config consumption; DQS Score color system is self-explanatory |
| **Morning triage** | Calm confidence — "Everything I need is right here" | Summary view answers "anything wrong?" without clicks |
| **Spotting an issue** | Equipped — "I can see exactly what changed and when" | Drill-down preserves context; trend shows the story |
| **After drill-down** | Accomplishment — "I diagnosed this in minutes, not hours" | Clear path from signal to root cause; no dead ends |
| **Reporting to leadership** | Authority — "I have the data to back this up" | Export-ready trend views; DQS Scores speak for themselves |
| **When DQS itself fails** | Informed patience — "I know the run failed; I still have yesterday's data" | Stale data clearly labeled; orchestration errors visible |
| **Returning daily** | Habitual trust — "This is just how I start my morning" | Consistency; the dashboard always works the same way |

## Micro-Emotions

**Critical to cultivate:**
- **Confidence over confusion** — Every element should be self-evident. If a user pauses to wonder "what does this mean?", the design has failed.
- **Trust over skepticism** — Score transparency (visible weights, breakdowns) builds trust. Black-box scoring destroys it.
- **Accomplishment over frustration** — Drill-down should always lead somewhere useful. No dead ends, no "go check the logs."

**Critical to prevent:**
- **Alert fatigue** — If everything is yellow, nothing is yellow. Thresholds must be meaningful; false positives erode trust faster than missed issues.
- **Overwhelm** — Hundreds of datasets must never feel like hundreds of datasets. Aggregation and progressive disclosure protect the user.
- **Distrust** — One inaccurate score that a user catches manually will undo weeks of earned trust. Accuracy is an emotional requirement, not just a technical one.

## Design Implications

| Emotion | UX Design Approach |
|---------|-------------------|
| Calm confidence | Muted, professional color palette. Green/yellow/red used sparingly and meaningfully. No animations, no attention-grabbing UI elements competing for focus. The dashboard should feel like a Bloomberg terminal, not a consumer dashboard. |
| Equipped to act | Every red/yellow indicator is clickable and leads to context. Trend lines accompany every score. Error messages are human-readable, not stack traces. |
| Trust | DQS Score breakdown always accessible. Weights visible. "Last updated" timestamp prominent. Stale data clearly marked. No data = explicit "No data" label, never blank space. |
| Habitual return | Consistent layout that never changes. Same place, same patterns, every day. Muscle memory develops; users navigate without thinking. |

## Emotional Design Principles

1. **Calm over clever** — No UI surprises, no clever interactions, no gamification. This is a tool people use at 6am to make decisions that affect enterprise data. The design should feel like a trusted instrument — reliable, predictable, and quiet.
2. **Transparency builds trust** — Every score, every color, every indicator should be explainable in one click. Users should never wonder "why is this red?" without an immediate answer.
3. **Context prevents panic** — A red score with a trend line showing "this dropped Saturday" is informative. A red score alone is alarming. Always provide temporal context alongside current state.
4. **Silence is a feature** — When everything is green, the dashboard should feel calm and sparse. The absence of alerts IS the positive signal. Don't fill green states with noise — let the quiet communicate "all is well."
5. **Consistency earns habit** — The dashboard must look and work identically every single day. Layout changes, redesigns, and UI experiments erode the habitual trust that drives daily adoption.
