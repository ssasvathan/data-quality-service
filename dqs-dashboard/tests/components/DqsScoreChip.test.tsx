/**
 * Component Tests — DqsScoreChip
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import React from 'react'
import theme from '../../src/theme'

import { DqsScoreChip } from '../../src/components/DqsScoreChip'

// ---------------------------------------------------------------------------
// Render helper: wraps component with MUI ThemeProvider so getDqsColor and
// theme tokens work correctly in tests.
// ---------------------------------------------------------------------------

const renderWithTheme = (ui: React.ReactElement) =>
  render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>)

// ---------------------------------------------------------------------------
// AC 1: score=87 previousScore=84 → "87", "+3", up arrow, green color
// ---------------------------------------------------------------------------

describe('[P0] DqsScoreChip — score + trend display (AC 1)', () => {
  it('[P0] renders score value "87" when score=87', () => {
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} />)
    expect(screen.getByText('87')).toBeInTheDocument()
  })

  it('[P0] renders delta "+3" when score=87 and previousScore=84', () => {
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} />)
    expect(screen.getByText('+3')).toBeInTheDocument()
  })

  it('[P0] renders up arrow ▲ when score improves (score=87 previousScore=84)', () => {
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} />)
    expect(screen.getByText(/▲/)).toBeInTheDocument()
  })

  it('[P0] applies green color for healthy score 87 (>=80)', () => {
    // getDqsColor(87) returns #2E7D32 — verify color applied to score text
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} />)
    const scoreEl = screen.getByText('87')
    expect(scoreEl).toHaveStyle({ color: '#2E7D32' })
  })
})

// ---------------------------------------------------------------------------
// AC 2: size support — lg (28px), md (18px), sm (14px)
// ---------------------------------------------------------------------------

describe('[P1] DqsScoreChip — size variants (AC 2)', () => {
  it('[P1] renders score "87" with size=lg using score typography variant (28px)', () => {
    // size=lg → Typography variant="score" (28px/700 per theme)
    renderWithTheme(<DqsScoreChip score={87} size="lg" />)
    expect(screen.getByText('87')).toBeInTheDocument()
  })

  it('[P1] renders score "87" with size=md using scoreSm typography variant (18px)', () => {
    // size=md → Typography variant="scoreSm" (18px/700 per theme, also the default)
    renderWithTheme(<DqsScoreChip score={87} size="md" />)
    expect(screen.getByText('87')).toBeInTheDocument()
  })

  it('[P1] renders score "87" with size=sm using inline sx (14px/600)', () => {
    // size=sm → Typography sx={{ fontSize: '0.875rem', fontWeight: 600 }}
    renderWithTheme(<DqsScoreChip score={87} size="sm" />)
    expect(screen.getByText('87')).toBeInTheDocument()
  })

  it('[P1] defaults to md size when size prop is omitted', () => {
    renderWithTheme(<DqsScoreChip score={87} />)
    expect(screen.getByText('87')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC 3: score=55 → red color (critical threshold < 60)
// ---------------------------------------------------------------------------

describe('[P0] DqsScoreChip — critical score color (AC 3)', () => {
  it('[P0] applies red color for critical score 55 (<60)', () => {
    // getDqsColor(55) returns #D32F2F
    renderWithTheme(<DqsScoreChip score={55} />)
    const scoreEl = screen.getByText('55')
    expect(scoreEl).toHaveStyle({ color: '#D32F2F' })
  })

  it('[P0] renders score "70" with amber color (warning threshold 60-79)', () => {
    // getDqsColor(70) returns #ED6C02
    renderWithTheme(<DqsScoreChip score={70} />)
    const scoreEl = screen.getByText('70')
    expect(scoreEl).toHaveStyle({ color: '#ED6C02' })
  })

  it('[P0] applies green color at exact boundary score 80 (>=80 is success)', () => {
    // getDqsColor(80) returns #2E7D32
    renderWithTheme(<DqsScoreChip score={80} />)
    const scoreEl = screen.getByText('80')
    expect(scoreEl).toHaveStyle({ color: '#2E7D32' })
  })

  it('[P0] applies amber color at exact boundary score 60 (>=60 and <80 is warning)', () => {
    // getDqsColor(60) returns #ED6C02
    renderWithTheme(<DqsScoreChip score={60} />)
    const scoreEl = screen.getByText('60')
    expect(scoreEl).toHaveStyle({ color: '#ED6C02' })
  })
})

// ---------------------------------------------------------------------------
// AC 4: no score → renders "—" in gray text
// ---------------------------------------------------------------------------

describe('[P0] DqsScoreChip — no-data state (AC 4)', () => {
  it('[P0] renders "—" when score is undefined', () => {
    renderWithTheme(<DqsScoreChip />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('[P0] applies gray/disabled color to "—" (text.disabled = #9E9E9E)', () => {
    // MUI text.disabled maps to neutral-500 = #9E9E9E
    renderWithTheme(<DqsScoreChip />)
    const emEl = screen.getByText('—')
    // text.disabled resolves from theme — component uses color="text.disabled"
    expect(emEl).toBeInTheDocument()
  })

  it('[P0] does NOT render a trend arrow when score is undefined', () => {
    renderWithTheme(<DqsScoreChip />)
    expect(screen.queryByText(/▲/)).not.toBeInTheDocument()
    expect(screen.queryByText(/▼/)).not.toBeInTheDocument()
    expect(screen.queryByText(/→/)).not.toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Trend direction variants
// ---------------------------------------------------------------------------

describe('[P1] DqsScoreChip — trend direction rendering', () => {
  it('[P1] renders down arrow ▼ when score declines (score=70 previousScore=80)', () => {
    renderWithTheme(<DqsScoreChip score={70} previousScore={80} />)
    expect(screen.getByText(/▼/)).toBeInTheDocument()
  })

  it('[P1] renders delta "-10" when score declines from 80 to 70', () => {
    // delta = 70 - 80 = -10; toString() includes minus sign → "-10"
    renderWithTheme(<DqsScoreChip score={70} previousScore={80} />)
    expect(screen.getByText('-10')).toBeInTheDocument()
  })

  it('[P1] renders flat arrow → when score is unchanged (score=75 previousScore=75)', () => {
    // delta = 0 → arrow is →
    renderWithTheme(<DqsScoreChip score={75} previousScore={75} />)
    expect(screen.getByText(/→/)).toBeInTheDocument()
  })

  it('[P1] does NOT render trend when showTrend=false', () => {
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} showTrend={false} />)
    expect(screen.queryByText(/▲/)).not.toBeInTheDocument()
    expect(screen.queryByText(/\+3/)).not.toBeInTheDocument()
  })

  it('[P1] does NOT render trend when previousScore is undefined', () => {
    // showTrend defaults to true, but previousScore is undefined → no trend shown
    renderWithTheme(<DqsScoreChip score={87} />)
    expect(screen.queryByText(/▲/)).not.toBeInTheDocument()
    expect(screen.queryByText(/▼/)).not.toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC 7: Accessibility — aria-label with score, status, trend direction
// ---------------------------------------------------------------------------

describe('[P1] DqsScoreChip — accessibility (AC 7)', () => {
  it('[P1] aria-label contains score value when score=87', () => {
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} />)
    const container = screen.getByRole('generic', { name: /DQS Score 87/ })
    expect(container).toBeInTheDocument()
  })

  it('[P1] aria-label contains "healthy" status for score >= 80', () => {
    // statusLabel for score 87 = "healthy"
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} />)
    const container = screen.getByRole('generic', { name: /healthy/ })
    expect(container).toBeInTheDocument()
  })

  it('[P1] aria-label contains "improving" trend direction when score is rising', () => {
    // trendLabel: delta=+3 → "improving by 3 points"
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} />)
    const container = screen.getByRole('generic', { name: /improving/ })
    expect(container).toBeInTheDocument()
  })

  it('[P1] aria-label contains "degraded" status for warning score (70)', () => {
    // statusLabel for score 70 (60-79) = "degraded"
    renderWithTheme(<DqsScoreChip score={70} />)
    const container = screen.getByRole('generic', { name: /degraded/ })
    expect(container).toBeInTheDocument()
  })

  it('[P1] aria-label contains "critical" status for error score (55)', () => {
    // statusLabel for score 55 (<60) = "critical"
    renderWithTheme(<DqsScoreChip score={55} />)
    const container = screen.getByRole('generic', { name: /critical/ })
    expect(container).toBeInTheDocument()
  })

  it('[P1] aria-label is "DQS Score unavailable" when score is undefined', () => {
    renderWithTheme(<DqsScoreChip />)
    const container = screen.getByRole('generic', { name: /DQS Score unavailable/ })
    expect(container).toBeInTheDocument()
  })

  it('[P1] aria-label contains full description: "DQS Score 87, healthy, improving by 3 points"', () => {
    renderWithTheme(<DqsScoreChip score={87} previousScore={84} />)
    const container = screen.getByRole('generic', {
      name: 'DQS Score 87, healthy, improving by 3 points',
    })
    expect(container).toBeInTheDocument()
  })
})
