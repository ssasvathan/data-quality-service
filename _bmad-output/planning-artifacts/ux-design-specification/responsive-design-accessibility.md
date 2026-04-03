# Responsive Design & Accessibility

## Responsive Strategy

**Desktop-only.** DQS is an internal tool accessed from workstations on the corporate network. No mobile or tablet support required.

**Desktop viewport handling:**

| Viewport | Layout Behavior |
|----------|----------------|
| **1280px - 1440px** | Standard layout. 3-column LOB card grid. Full table width. Master-Detail split at 380px/remaining. |
| **1440px - 1920px** | Max content width 1440px, centered with side margins. Cards and tables don't stretch — white space increases on sides. |
| **1920px+ (ultrawide)** | Same 1440px centered content. No ultrawide-specific layout. Generous margins keep content scannable. |
| **< 1280px** | Graceful degradation. LOB cards stack to 2 columns. Table gains horizontal scroll. Master-Detail left panel collapses to 300px. No breakage, but not optimized. |

**Not supported (by design):**
- Tablet layouts (< 1024px)
- Mobile layouts (< 768px)
- Touch-optimized interactions
- Offline functionality
- PWA / app-like behavior

## Breakpoint Strategy

**Single breakpoint approach** — desktop-only means minimal responsive complexity:

| Breakpoint | Value | Purpose |
|------------|-------|---------|
| `compact` | < 1280px | 2-column card grid, narrower left panel, horizontal table scroll |
| `standard` | 1280px - 1440px | Default layout. 3-column cards, full table, 380px left panel |
| `wide` | > 1440px | Centered content at 1440px max-width. Side margins increase. |

**MUI breakpoint configuration:**
```
breakpoints: {
  values: { xs: 0, sm: 1024, md: 1280, lg: 1440, xl: 1920 }
}
```

Content is designed for `md` (1280px) as the baseline. `sm` is fallback, `lg`/`xl` add whitespace.

## Accessibility Strategy

**Target: WCAG 2.1 Level AA** — industry standard appropriate for an internal enterprise tool.

**Already addressed in component specs:**

| Requirement | Where Defined | Status |
|-------------|--------------|--------|
| Color contrast (4.5:1 min) | Visual Foundation | All text/background combos verified |
| Color not sole indicator | Visual Foundation, Components | Score uses color + number + trend arrow + text label |
| Keyboard navigation | Component specs | All interactive elements focusable, Enter/Space to activate |
| Focus indicators | Component specs | MUI default focus rings preserved |
| Touch/click targets | Component specs | Minimum 44x44px for all interactive elements |
| ARIA labels | Component specs | Each custom component specifies `aria-label` patterns |
| Screen reader structure | Component specs | Semantic HTML, definition lists for metadata |

**Additional accessibility patterns:**

| Pattern | Implementation |
|---------|---------------|
| **Skip to content link** | Hidden link at top of page, visible on Tab focus: "Skip to main content". Bypasses fixed header for keyboard users. |
| **Landmark regions** | `<header>` for fixed bar, `<main>` for content area, `<nav>` for breadcrumbs. Screen readers can jump between landmarks. |
| **Live regions for updates** | When time range changes and data reloads, use `aria-live="polite"` to announce "Data updated for 30-day range" without interrupting screen reader flow. |
| **Table accessibility** | DataGrid uses `role="grid"` with proper `aria-colcount`, `aria-rowcount`. Column headers linked to cells via `aria-describedby`. |
| **Sparkline alt text** | Each TrendSparkline has `aria-label` summarizing the trend direction and magnitude — visual chart content communicated as text. |

## Testing Strategy

**Responsive testing (minimal — desktop only):**
- Test at 1280px, 1440px, and 1920px viewport widths
- Verify card grid reflows at compact breakpoint
- Verify table horizontal scroll at narrow widths
- Verify Master-Detail left panel narrows gracefully

**Accessibility testing:**

| Method | Tool/Approach | Scope |
|--------|---------------|-------|
| **Automated scan** | axe-core (via @axe-core/react) | Run on every component in development. Catches contrast, missing labels, ARIA issues. |
| **Keyboard walkthrough** | Manual | Verify complete Tab order through all views. Verify Enter/Space activates all interactive elements. Verify Escape closes search. Verify Ctrl+K opens search. |
| **Screen reader spot-check** | NVDA (Windows) | Verify LOB cards, table rows, and dataset detail are announced with meaningful context. Verify DqsScoreChip aria-labels read correctly. |
| **Color blindness check** | Chrome DevTools color vision simulation | Verify all views remain usable under protanopia, deuteranopia, tritanopia simulations. Status must be distinguishable without color. |

**Not in scope (internal tool):**
- JAWS testing (enterprise cost; NVDA sufficient for internal validation)
- VoiceOver testing (macOS — users are on Windows workstations)
- Mobile screen reader testing
- Formal VPAT documentation

## Implementation Guidelines

**For developers:**

1. **Use MUI's `sx` prop and theme tokens** — never hardcode colors, spacing, or typography values. All visual decisions flow from the theme.
2. **Use semantic HTML** — `<nav>` for breadcrumbs, `<main>` for content, `<table>` for tabular data (not div grids). MUI components handle this correctly by default.
3. **Test keyboard navigation** as you build — don't retrofit. Every interactive element must be Tab-focusable and activatable via Enter/Space from the start.
4. **Run axe-core** in development mode — it catches 60%+ of accessibility issues automatically. Fix violations before merging.
5. **Don't suppress focus outlines** — MUI's default focus rings are appropriate. Never add `outline: none` to interactive elements.
6. **Use `aria-label` on custom components** — DqsScoreChip, DatasetCard, and TrendSparkline all have specified aria-label patterns. Follow the component specs.
7. **Test at 1280px** — this is the design baseline. If it works at 1280px, it works everywhere DQS will be used.
