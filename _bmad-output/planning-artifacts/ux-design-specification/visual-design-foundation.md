# Visual Design Foundation

## Color System

**Philosophy:** Color is reserved for meaning. The interface is neutral; color appears only to communicate status.

**Neutral Palette (structure & content):**

| Token | Value | Usage |
|-------|-------|-------|
| `neutral-50` | `#FAFAFA` | Page background |
| `neutral-100` | `#F5F5F5` | Card background |
| `neutral-200` | `#EEEEEE` | Borders, dividers |
| `neutral-300` | `#E0E0E0` | Disabled states |
| `neutral-500` | `#9E9E9E` | Secondary text, icons |
| `neutral-700` | `#616161` | Body text |
| `neutral-900` | `#212121` | Headings, primary text |

**Semantic Palette (DQS Score status):**

| Token | Value | Usage | Threshold |
|-------|-------|-------|-----------|
| `success` | `#2E7D32` | Healthy / passing | DQS Score >= 80 |
| `success-light` | `#E8F5E9` | Healthy card background tint | |
| `warning` | `#ED6C02` | Degraded / attention needed | DQS Score 60-79 |
| `warning-light` | `#FFF3E0` | Warning card background tint | |
| `error` | `#D32F2F` | Critical / failing | DQS Score < 60 |
| `error-light` | `#FFEBEE` | Error card background tint | |

**Accent (minimal):**

| Token | Value | Usage |
|-------|-------|-------|
| `primary` | `#1565C0` | Links, active breadcrumb, search focus |
| `primary-light` | `#E3F2FD` | Selected/active state background |

**Color rules:**
- Green/yellow/red appear ONLY on DQS Score indicators, status chips, and trend arrows. Never as decoration.
- Card backgrounds use the `-light` tint variants — subtle, not saturated.
- When everything is healthy, the page is predominantly neutral with small green accents. The visual calm IS the signal.
- Dark mode: not in scope for MVP. Single light theme only.

## Typography System

**Font stack:** System fonts for performance and zero-load-time. No custom font downloads.

```
font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
font-family-mono: 'SF Mono', 'Fira Code', 'Fira Mono', Menlo, monospace;
```

**Type scale:**

| Level | Size | Weight | Line Height | Usage |
|-------|------|--------|-------------|-------|
| `h1` | 24px | 600 | 1.3 | Page titles ("Summary", "Consumer Banking LOB") |
| `h2` | 20px | 600 | 1.3 | Section headers ("Datasets", "Check Results") |
| `h3` | 16px | 600 | 1.4 | Card titles (dataset names) |
| `body1` | 14px | 400 | 1.5 | Primary body text, table content |
| `body2` | 13px | 400 | 1.5 | Secondary text, descriptions |
| `caption` | 12px | 400 | 1.4 | Timestamps, labels, helper text |
| `score` | 28px | 700 | 1.0 | DQS Score display (large, prominent) |
| `score-sm` | 18px | 700 | 1.0 | DQS Score in cards (compact) |
| `mono` | 13px | 400 | 1.4 | Dataset names, lookup codes, technical values |

**Typography rules:**
- Monospace font for all dataset names, lookup codes, and technical identifiers — visually distinct from prose
- DQS Score uses the largest weight/size in any context — it is always the visual anchor
- No uppercase text except status chip labels ("PASS", "FAIL")
- Maximum line length: 80ch for readability in detail views

## Spacing & Layout Foundation

**Base unit:** 8px grid. All spacing is a multiple of 8.

| Token | Value | Usage |
|-------|-------|-------|
| `spacing-xs` | 4px | Inline gaps (icon-to-text, chip padding) |
| `spacing-sm` | 8px | Tight grouping (label-to-value) |
| `spacing-md` | 16px | Standard component padding, card internal spacing |
| `spacing-lg` | 24px | Section gaps, card-to-card gaps |
| `spacing-xl` | 32px | Major section separations |
| `spacing-2xl` | 48px | Page-level margins |

**Layout grid:**
- **Max content width:** 1440px, centered. Generous side margins on wide screens.
- **Card grid:** 12-column MUI Grid. LOB cards = 4 columns (3 per row). Dataset cards = 3 columns (4 per row). Responsive down to 2 per row at narrow widths.
- **Detail views:** Single-column content area (max 960px) with sidebar for score breakdown on wide screens.

**Layout principles:**
- **White space is structural** — Generous `spacing-lg` (24px) between cards. The space between elements communicates grouping as effectively as borders.
- **Consistent card dimensions** — All cards at a given drill-down level have identical height. No ragged grids. Content that varies in length is truncated with tooltip on hover.
- **Fixed header** — Top bar with breadcrumbs + search is fixed/sticky. Content scrolls beneath. User always has navigation context and search access.
- **No sidebar navigation** — Breadcrumbs ARE the navigation. No hamburger menu, no nav drawer. The hierarchy is: Summary -> LOB -> Dataset -> Check. Breadcrumbs handle this cleanly.

**Border & elevation:**
- Cards: 1px `neutral-200` border, `border-radius: 8px`. No box-shadow.
- Hover state on clickable cards: border color transitions to `primary`. Subtle, not dramatic.
- No elevation/shadow stack. Flatness reinforces the calm, professional aesthetic.

## Accessibility Considerations

- **Contrast ratios:** All text meets WCAG 2.1 AA. `neutral-700` on `neutral-50` = 9.7:1. `neutral-900` on `neutral-100` = 13.5:1. Semantic colors on white all exceed 4.5:1.
- **Color is never the only indicator** — DQS Score status uses color + text label ("PASS"/"WARN"/"FAIL") + trend arrow. Color-blind users can distinguish states without color.
- **Focus indicators:** MUI default focus rings preserved. Keyboard navigation works for all interactive elements.
- **Minimum touch targets:** 44x44px for all clickable elements (cards, chips, breadcrumb links).
- **Screen reader support:** MUI's built-in ARIA attributes. Cards have `aria-label` with dataset name + DQS Score + status.
