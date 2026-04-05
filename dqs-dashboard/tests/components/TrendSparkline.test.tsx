/**
 * Component Tests — TrendSparkline
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import React from 'react'
import theme from '../../src/theme'

// ---------------------------------------------------------------------------
// Recharts mock — Recharts requires browser canvas APIs not available in jsdom.
// All Recharts components are replaced with minimal testable divs.
// ---------------------------------------------------------------------------

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  LineChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="line-chart">{children}</div>
  ),
  Line: () => null,
  Tooltip: () => null,
  ReferenceLine: () => null,
}))

import { TrendSparkline } from '../../src/components/TrendSparkline'

// ---------------------------------------------------------------------------
// Render helper: wraps component with MUI ThemeProvider so theme tokens
// (getDqsColor, theme.palette.neutral) work correctly in tests.
// ---------------------------------------------------------------------------

const renderWithTheme = (ui: React.ReactElement) =>
  render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>)

// ---------------------------------------------------------------------------
// AC 5: 30 data points, size=md (32px) → Recharts line chart, correct height
// ---------------------------------------------------------------------------

describe('[P0] TrendSparkline — multi-point rendering (AC 5)', () => {
  it('[P0] renders without error with 30 data points at md size', () => {
    const data = Array.from({ length: 30 }, (_, i) => 70 + (i % 10))
    expect(() =>
      renderWithTheme(<TrendSparkline data={data} size="md" />)
    ).not.toThrow()
  })

  it('[P0] renders Recharts line chart container for 4+ data points', () => {
    const data = [80, 85, 82, 87]
    renderWithTheme(<TrendSparkline data={data} />)
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()
  })

  it('[P0] outer container has height 32px for md size', () => {
    // SIZE_HEIGHT: md → 32px. Container uses sx={{ height: 32 }}
    const data = [80, 85, 82, 87]
    const { container } = renderWithTheme(<TrendSparkline data={data} size="md" />)
    const outerBox = container.firstChild as HTMLElement
    expect(outerBox).toHaveStyle({ height: '32px' })
  })

  it('[P0] outer container has height 64px for lg size', () => {
    // SIZE_HEIGHT: lg → 64px
    const data = [80, 85, 82, 87]
    const { container } = renderWithTheme(<TrendSparkline data={data} size="lg" />)
    const outerBox = container.firstChild as HTMLElement
    expect(outerBox).toHaveStyle({ height: '64px' })
  })

  it('[P0] outer container has height 24px for mini size', () => {
    // SIZE_HEIGHT: mini → 24px
    const data = [80, 85, 82, 87]
    const { container } = renderWithTheme(<TrendSparkline data={data} size="mini" />)
    const outerBox = container.firstChild as HTMLElement
    expect(outerBox).toHaveStyle({ height: '24px' })
  })

  it('[P0] defaults to md size when size prop is omitted', () => {
    const data = [80, 85, 82, 87]
    const { container } = renderWithTheme(<TrendSparkline data={data} />)
    const outerBox = container.firstChild as HTMLElement
    expect(outerBox).toHaveStyle({ height: '32px' })
  })
})

// ---------------------------------------------------------------------------
// AC 6: single data point → shows "First run" placeholder
// ---------------------------------------------------------------------------

describe('[P0] TrendSparkline — single data point fallback (AC 6)', () => {
  it('[P0] renders "First run" text when data has exactly 1 point', () => {
    renderWithTheme(<TrendSparkline data={[55]} />)
    expect(screen.getByText('First run')).toBeInTheDocument()
  })

  it('[P0] does NOT render a line chart when data has exactly 1 point', () => {
    // Recharts cannot draw a line with a single point; use fallback dot+label
    renderWithTheme(<TrendSparkline data={[55]} />)
    expect(screen.queryByTestId('line-chart')).not.toBeInTheDocument()
  })

  it('[P0] renders "First run" text when data array is empty', () => {
    // Empty data renders the same single-point fallback gracefully
    renderWithTheme(<TrendSparkline data={[]} />)
    expect(screen.getByText('First run')).toBeInTheDocument()
  })

  it('[P0] renders without error when data array is empty', () => {
    expect(() =>
      renderWithTheme(<TrendSparkline data={[]} />)
    ).not.toThrow()
  })
})

// ---------------------------------------------------------------------------
// AC 7: Accessibility — role="img" and aria-label on TrendSparkline
// ---------------------------------------------------------------------------

describe('[P1] TrendSparkline — accessibility (AC 7)', () => {
  it('[P1] has role="img" on the container element', () => {
    const data = [80, 85, 82, 87]
    renderWithTheme(<TrendSparkline data={data} />)
    expect(screen.getByRole('img')).toBeInTheDocument()
  })

  it('[P1] aria-label contains "Trend" for multi-point data', () => {
    const data = [80, 85, 82, 87]
    renderWithTheme(<TrendSparkline data={data} />)
    const img = screen.getByRole('img')
    expect(img).toHaveAccessibleName(/Trend/)
  })

  it('[P1] aria-label contains data point count for multi-point sparkline', () => {
    // e.g. "Trend over 4 data points, current score 87"
    const data = [80, 85, 82, 87]
    renderWithTheme(<TrendSparkline data={data} />)
    const img = screen.getByRole('img')
    expect(img).toHaveAccessibleName(/4 data points/)
  })

  it('[P1] aria-label contains current (last) score in multi-point sparkline', () => {
    // last element is 87 → aria-label contains "current score 87"
    const data = [80, 85, 82, 87]
    renderWithTheme(<TrendSparkline data={data} />)
    const img = screen.getByRole('img')
    expect(img).toHaveAccessibleName(/current score 87/)
  })

  it('[P1] full aria-label is "Trend over 4 data points, current score 87"', () => {
    const data = [80, 85, 82, 87]
    renderWithTheme(<TrendSparkline data={data} />)
    const img = screen.getByRole('img')
    expect(img).toHaveAccessibleName('Trend over 4 data points, current score 87')
  })
})

// ---------------------------------------------------------------------------
// Mini size — width constraint
// ---------------------------------------------------------------------------

describe('[P1] TrendSparkline — mini size width (story task 4)', () => {
  it('[P1] outer container has width 80px for mini size', () => {
    // SIZE_WIDTH: mini → 80px; sx={{ width: 80 }}
    const data = [80, 85, 82, 87]
    const { container } = renderWithTheme(<TrendSparkline data={data} size="mini" />)
    const outerBox = container.firstChild as HTMLElement
    expect(outerBox).toHaveStyle({ width: '80px' })
  })

  it('[P1] outer container has width 100% for md size', () => {
    // SIZE_WIDTH: md → '100%'
    const data = [80, 85, 82, 87]
    const { container } = renderWithTheme(<TrendSparkline data={data} size="md" />)
    const outerBox = container.firstChild as HTMLElement
    expect(outerBox).toHaveStyle({ width: '100%' })
  })
})

// ---------------------------------------------------------------------------
// Baseline ReferenceLine — optional dashed line at y=80
// ---------------------------------------------------------------------------

describe('[P2] TrendSparkline — optional baseline (story task 2)', () => {
  it('[P2] renders line chart container when showBaseline=true', () => {
    // showBaseline=true adds a Recharts ReferenceLine at y=80
    const data = [80, 85, 82, 87]
    renderWithTheme(<TrendSparkline data={data} showBaseline={true} />)
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()
  })

  it('[P2] renders without error when showBaseline=false (default)', () => {
    const data = [80, 85, 82, 87]
    expect(() =>
      renderWithTheme(<TrendSparkline data={data} showBaseline={false} />)
    ).not.toThrow()
  })
})
