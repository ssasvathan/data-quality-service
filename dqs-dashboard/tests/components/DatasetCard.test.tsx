/**
 * Component Tests — Story 4.7: DatasetCard (LOB Card)
 *
 * GREEN PHASE: All tests are active (using it() — not it.skip()).
 * DatasetCard is fully implemented. All 33 tests pass.
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import React from 'react'
import theme from '../../src/theme'

// ---------------------------------------------------------------------------
// Mock DqsScoreChip and TrendSparkline to isolate DatasetCard rendering.
// This avoids Recharts canvas issues and makes tests deterministic.
// ---------------------------------------------------------------------------

vi.mock('../../src/components/DqsScoreChip', () => ({
  DqsScoreChip: ({ score }: { score?: number }) => (
    <div data-testid="dqs-score-chip">{score ?? '—'}</div>
  ),
}))

vi.mock('../../src/components/TrendSparkline', () => ({
  TrendSparkline: () => <div data-testid="trend-sparkline" />,
}))

import { DatasetCard } from '../../src/components/DatasetCard'

// ---------------------------------------------------------------------------
// Render helper: wraps component with MUI ThemeProvider so theme tokens
// (palette.neutral, palette.success, etc.) work correctly in tests.
// ---------------------------------------------------------------------------

const renderWithTheme = (ui: React.ReactElement) =>
  render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>)

// ---------------------------------------------------------------------------
// Default props fixture — used across multiple test groups
// ---------------------------------------------------------------------------

const DEFAULT_PROPS = {
  lobName: 'Consumer Banking',
  datasetCount: 18,
  dqsScore: 87,
  previousScore: 84,
  trendData: [80, 82, 85, 84, 87],
  statusCounts: { pass: 16, warn: 1, fail: 1 },
  onClick: vi.fn(),
}

// ---------------------------------------------------------------------------
// AC 1: Renders LOB name, dataset count, DqsScoreChip, LinearProgress,
//        TrendSparkline, and status count chips
// ---------------------------------------------------------------------------

describe('[P0] DatasetCard — content rendering (AC 1)', () => {
  it('[P0] renders LOB name "Consumer Banking"', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByText('Consumer Banking')).toBeInTheDocument()
  })

  it('[P0] renders dataset count "18 datasets"', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByText(/18 datasets/i)).toBeInTheDocument()
  })

  it('[P0] renders DqsScoreChip with score=87', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByTestId('dqs-score-chip')).toBeInTheDocument()
    expect(screen.getByTestId('dqs-score-chip')).toHaveTextContent('87')
  })

  it('[P0] renders TrendSparkline', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByTestId('trend-sparkline')).toBeInTheDocument()
  })

  it('[P0] renders LinearProgress with value=87 (dqsScore)', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    // MUI LinearProgress[variant="determinate"] exposes aria-valuenow
    const progressBar = screen.getByRole('progressbar')
    expect(progressBar).toBeInTheDocument()
    expect(progressBar).toHaveAttribute('aria-valuenow', '87')
  })

  it('[P0] renders pass status chip with count "16 pass"', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByText('16 pass')).toBeInTheDocument()
  })

  it('[P0] renders warn status chip with count "1 warn"', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByText('1 warn')).toBeInTheDocument()
  })

  it('[P0] renders fail status chip with count "1 fail"', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByText('1 fail')).toBeInTheDocument()
  })

  it('[P0] renders all three status chips simultaneously', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByText('16 pass')).toBeInTheDocument()
    expect(screen.getByText('1 warn')).toBeInTheDocument()
    expect(screen.getByText('1 fail')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC 1 edge cases: different data values render correctly
// ---------------------------------------------------------------------------

describe('[P1] DatasetCard — content rendering edge cases (AC 1)', () => {
  it('[P1] renders different LOB name "Mortgage"', () => {
    renderWithTheme(
      <DatasetCard
        {...DEFAULT_PROPS}
        lobName="Mortgage"
      />
    )
    expect(screen.getByText('Mortgage')).toBeInTheDocument()
  })

  it('[P1] renders dataset count "5 datasets" for datasetCount=5', () => {
    renderWithTheme(
      <DatasetCard
        {...DEFAULT_PROPS}
        datasetCount={5}
      />
    )
    expect(screen.getByText(/5 datasets/i)).toBeInTheDocument()
  })

  it('[P1] renders pass chip with zero count "0 pass"', () => {
    renderWithTheme(
      <DatasetCard
        {...DEFAULT_PROPS}
        statusCounts={{ pass: 0, warn: 2, fail: 3 }}
      />
    )
    expect(screen.getByText('0 pass')).toBeInTheDocument()
  })

  it('[P1] renders LinearProgress with value=55 for critical score', () => {
    renderWithTheme(
      <DatasetCard
        {...DEFAULT_PROPS}
        dqsScore={55}
      />
    )
    const progressBar = screen.getByRole('progressbar')
    expect(progressBar).toHaveAttribute('aria-valuenow', '55')
  })
})

// ---------------------------------------------------------------------------
// AC 3: Clicking anywhere on the card fires the onClick handler
// ---------------------------------------------------------------------------

describe('[P0] DatasetCard — click interaction (AC 3)', () => {
  it('[P0] calls onClick when card is clicked', () => {
    const onClick = vi.fn()
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} onClick={onClick} />)
    const card = screen.getByRole('button')
    fireEvent.click(card)
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('[P0] calls onClick exactly once per click', () => {
    const onClick = vi.fn()
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} onClick={onClick} />)
    const card = screen.getByRole('button')
    fireEvent.click(card)
    fireEvent.click(card)
    expect(onClick).toHaveBeenCalledTimes(2)
  })
})

// ---------------------------------------------------------------------------
// AC 4: Keyboard accessibility — role="button", tabIndex=0,
//        Enter and Space activate the card
// ---------------------------------------------------------------------------

describe('[P0] DatasetCard — keyboard accessibility (AC 4)', () => {
  it('[P0] card has role="button"', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    expect(screen.getByRole('button')).toBeInTheDocument()
  })

  it('[P0] card has tabIndex=0 (keyboard focusable)', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    const card = screen.getByRole('button')
    expect(card).toHaveAttribute('tabindex', '0')
  })

  it('[P0] calls onClick when Enter key is pressed on the card', () => {
    const onClick = vi.fn()
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} onClick={onClick} />)
    const card = screen.getByRole('button')
    fireEvent.keyDown(card, { key: 'Enter', code: 'Enter' })
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('[P0] calls onClick when Space key is pressed on the card', () => {
    const onClick = vi.fn()
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} onClick={onClick} />)
    const card = screen.getByRole('button')
    fireEvent.keyDown(card, { key: ' ', code: 'Space' })
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('[P1] does NOT call onClick when an unrelated key (e.g. Tab) is pressed', () => {
    const onClick = vi.fn()
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} onClick={onClick} />)
    const card = screen.getByRole('button')
    fireEvent.keyDown(card, { key: 'Tab', code: 'Tab' })
    expect(onClick).not.toHaveBeenCalled()
  })

  it('[P1] does NOT call onClick when Escape is pressed', () => {
    const onClick = vi.fn()
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} onClick={onClick} />)
    const card = screen.getByRole('button')
    fireEvent.keyDown(card, { key: 'Escape', code: 'Escape' })
    expect(onClick).not.toHaveBeenCalled()
  })
})

// ---------------------------------------------------------------------------
// AC 5: aria-label includes LOB name, DQS score, dataset count, status counts
// ---------------------------------------------------------------------------

describe('[P1] DatasetCard — accessibility aria-label (AC 5)', () => {
  it('[P1] aria-label contains the LOB name', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    const card = screen.getByRole('button')
    expect(card).toHaveAttribute('aria-label', expect.stringContaining('Consumer Banking'))
  })

  it('[P1] aria-label contains the DQS score', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    const card = screen.getByRole('button')
    expect(card).toHaveAttribute('aria-label', expect.stringContaining('87'))
  })

  it('[P1] aria-label contains the dataset count', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    const card = screen.getByRole('button')
    expect(card).toHaveAttribute('aria-label', expect.stringContaining('18 datasets'))
  })

  it('[P1] aria-label contains passing count', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    const card = screen.getByRole('button')
    expect(card).toHaveAttribute('aria-label', expect.stringContaining('16 passing'))
  })

  it('[P1] aria-label contains warning count', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    const card = screen.getByRole('button')
    expect(card).toHaveAttribute('aria-label', expect.stringContaining('1 warning'))
  })

  it('[P1] aria-label contains failing count', () => {
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    const card = screen.getByRole('button')
    expect(card).toHaveAttribute('aria-label', expect.stringContaining('1 failing'))
  })

  it('[P1] aria-label matches full expected format', () => {
    // Full format: "{lobName}, DQS Score {dqsScore}, {datasetCount} datasets,
    //               {pass} passing, {warn} warning, {fail} failing"
    renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)
    const card = screen.getByRole('button')
    expect(card).toHaveAttribute(
      'aria-label',
      'Consumer Banking, DQS Score 87, 18 datasets, 16 passing, 1 warning, 1 failing'
    )
  })
})

// ---------------------------------------------------------------------------
// Barrel export — DatasetCard is accessible via the components index
// ---------------------------------------------------------------------------

describe('[P1] DatasetCard — barrel export', () => {
  it('[P1] DatasetCard is exported from the components barrel index', async () => {
    // Dynamic import verifies the barrel export works at runtime
    const components = await import('../../src/components/index')
    expect(components).toHaveProperty('DatasetCard')
  })
})

// ---------------------------------------------------------------------------
// Rendering stability — no errors thrown with valid props
// ---------------------------------------------------------------------------

describe('[P0] DatasetCard — rendering stability', () => {
  it('[P0] renders without throwing with standard props', () => {
    expect(() => renderWithTheme(<DatasetCard {...DEFAULT_PROPS} />)).not.toThrow()
  })

  it('[P1] renders without throwing when previousScore is omitted (optional prop)', () => {
    const { previousScore: _, ...propsWithoutPrevious } = DEFAULT_PROPS
    expect(() => renderWithTheme(<DatasetCard {...propsWithoutPrevious} />)).not.toThrow()
  })

  it('[P1] renders without throwing with all-zero status counts', () => {
    expect(() =>
      renderWithTheme(
        <DatasetCard
          {...DEFAULT_PROPS}
          statusCounts={{ pass: 0, warn: 0, fail: 0 }}
        />
      )
    ).not.toThrow()
  })

  it('[P1] renders without throwing with empty trendData array', () => {
    expect(() =>
      renderWithTheme(<DatasetCard {...DEFAULT_PROPS} trendData={[]} />)
    ).not.toThrow()
  })
})
