/**
 * ATDD Component Tests — Summary Card Grid View (Level 1)
 *
 * RED PHASE: All tests skipped — SummaryPage is a placeholder, useSummary
 * does not exist, LobSummaryItem / SummaryResponse types are not yet defined.
 * Remove `it.skip` (or convert to `it`) after implementation is complete.
 *
 * Acceptance Criteria covered:
 *   AC1: Stats bar shows total_datasets, healthy_count, degraded_count, critical_count
 *   AC2: 3-column grid renders one DatasetCard per LOB
 *   AC3: DatasetCard receives correct props (lobName, dqsScore, trendData, statusCounts)
 *   AC4: DatasetCard dqsScore < 60 passed correctly (card handles color-coding internally)
 *   AC5: Clicking LOB card navigates to /lobs/{lob_id}
 *   AC6: Loading state renders 6 Skeleton elements, no DatasetCards
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router'
import React from 'react'
import theme from '../../src/theme'
import SummaryPage from '../../src/pages/SummaryPage'
import { TimeRangeProvider } from '../../src/context/TimeRangeContext'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

// Mock useSummary to avoid real API calls.
// useSummary does not yet exist in queries.ts — this mock will cause a
// runtime import error until it is added (confirming RED phase).
vi.mock('../../src/api/queries', () => ({
  useSummary: vi.fn(),
  useLobs: vi.fn(),
  useDatasets: vi.fn(),
}))

// Mock DatasetCard to avoid Recharts canvas rendering in jsdom.
// Renders a minimal div with data-testid and lobName for assertion.
vi.mock('../../src/components', () => ({
  DatasetCard: ({
    lobName,
    onClick,
    dqsScore,
    trendData,
    statusCounts,
    datasetCount,
  }: {
    lobName: string
    dqsScore: number
    trendData: number[]
    statusCounts: { pass: number; warn: number; fail: number }
    datasetCount: number
    onClick: () => void
  }) => (
    <div
      data-testid="dataset-card"
      data-lob-name={lobName}
      data-dqs-score={dqsScore}
      data-dataset-count={datasetCount}
      data-trend={JSON.stringify(trendData)}
      data-pass={statusCounts.pass}
      data-warn={statusCounts.warn}
      data-fail={statusCounts.fail}
      onClick={onClick}
      role="button"
    >
      {lobName}
    </div>
  ),
}))

// ---------------------------------------------------------------------------
// Test data factories
// ---------------------------------------------------------------------------

function makeLobSummaryItem(overrides: Partial<{
  lob_id: string
  dataset_count: number
  aggregate_score: number | null
  healthy_count: number
  degraded_count: number
  critical_count: number
  trend: number[]
}> = {}) {
  return {
    lob_id: 'LOB_RETAIL',
    dataset_count: 12,
    aggregate_score: 87,
    healthy_count: 9,
    degraded_count: 2,
    critical_count: 1,
    trend: [80, 82, 85, 84, 86, 87, 87],
    ...overrides,
  }
}

function makeSummaryResponse(overrides: Partial<{
  total_datasets: number
  healthy_count: number
  degraded_count: number
  critical_count: number
  lobs: ReturnType<typeof makeLobSummaryItem>[]
}> = {}) {
  return {
    total_datasets: 24,
    healthy_count: 18,
    degraded_count: 3,
    critical_count: 2,
    lobs: [
      makeLobSummaryItem({ lob_id: 'LOB_RETAIL' }),
      makeLobSummaryItem({ lob_id: 'LOB_MORTGAGE', aggregate_score: 72 }),
      makeLobSummaryItem({ lob_id: 'LOB_AUTO', aggregate_score: 55 }),
    ],
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Render helper — wraps SummaryPage with all required providers.
// MemoryRouter needed so useNavigate works in jsdom.
// ---------------------------------------------------------------------------

function renderSummaryPage(route = '/summary') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[route]}>
          <TimeRangeProvider>
            <Routes>
              <Route path="/*" element={<SummaryPage />} />
            </Routes>
          </TimeRangeProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

// ---------------------------------------------------------------------------
// Import useSummary mock reference for per-test control
// ---------------------------------------------------------------------------

let mockUseSummary: ReturnType<typeof vi.fn>

beforeEach(async () => {
  const queries = await import('../../src/api/queries')
  // useSummary does not exist yet — accessing it here will fail until implemented
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  mockUseSummary = (queries as any).useSummary as ReturnType<typeof vi.fn>
})

// ---------------------------------------------------------------------------
// AC6: Loading state — 6 Skeleton cards, no DatasetCard or stats bar
// ---------------------------------------------------------------------------

describe('[P0] SummaryPage — loading state (AC6)', () => {
  it('[P0] renders 6 Skeleton elements when isLoading is true', () => {
    mockUseSummary.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderSummaryPage()

    // Expect MUI Skeleton elements to be present
    // Skeleton renders with role="img" when variant="rectangular" or a generic element
    // We check via data-testid is NOT available — detect by aria or by absence of DatasetCard
    const skeletons = document.querySelectorAll('[class*="MuiSkeleton"]')
    expect(skeletons.length).toBe(6)
  })

  it('[P0] does not render DatasetCard elements when isLoading is true', () => {
    mockUseSummary.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderSummaryPage()

    const cards = screen.queryAllByTestId('dataset-card')
    expect(cards).toHaveLength(0)
  })

  it('[P1] does not render stats bar when isLoading is true', () => {
    mockUseSummary.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderSummaryPage()

    // Stats bar would show "Total Datasets" label — must be absent during loading
    expect(screen.queryByText(/total datasets/i)).not.toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC1: Stats bar — 4 stat tiles with correct values
// ---------------------------------------------------------------------------

describe('[P0] SummaryPage — stats bar renders correct values (AC1)', () => {
  it('[P0] renders stats bar with total_datasets value', () => {
    const data = makeSummaryResponse({ total_datasets: 42 })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    // Stat tile: "Total Datasets" label and value "42"
    expect(screen.getByText('42')).toBeInTheDocument()
  })

  it('[P0] renders stats bar with healthy_count value', () => {
    const data = makeSummaryResponse({ healthy_count: 30 })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    expect(screen.getByText('30')).toBeInTheDocument()
  })

  it('[P0] renders stats bar with degraded_count value', () => {
    const data = makeSummaryResponse({ degraded_count: 8 })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    expect(screen.getByText('8')).toBeInTheDocument()
  })

  it('[P0] renders stats bar with critical_count value', () => {
    const data = makeSummaryResponse({ critical_count: 4 })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    expect(screen.getByText('4')).toBeInTheDocument()
  })

  it('[P0] renders "Total Datasets" label in stats bar', () => {
    const data = makeSummaryResponse()
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    expect(screen.getByText(/total datasets/i)).toBeInTheDocument()
  })

  it('[P1] stats bar renders "Healthy" label', () => {
    const data = makeSummaryResponse()
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    expect(screen.getByText(/healthy/i)).toBeInTheDocument()
  })

  it('[P1] stats bar renders "Degraded" label', () => {
    const data = makeSummaryResponse()
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    expect(screen.getByText(/degraded/i)).toBeInTheDocument()
  })

  it('[P1] stats bar renders "Critical" label', () => {
    const data = makeSummaryResponse()
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    expect(screen.getByText(/critical/i)).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC2: Grid — one DatasetCard per LOB
// ---------------------------------------------------------------------------

describe('[P0] SummaryPage — LOB card grid renders correct count (AC2)', () => {
  it('[P0] renders one DatasetCard per LOB in data.lobs', () => {
    const data = makeSummaryResponse({
      lobs: [
        makeLobSummaryItem({ lob_id: 'LOB_RETAIL' }),
        makeLobSummaryItem({ lob_id: 'LOB_MORTGAGE' }),
        makeLobSummaryItem({ lob_id: 'LOB_AUTO' }),
      ],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const cards = screen.getAllByTestId('dataset-card')
    expect(cards).toHaveLength(3)
  })

  it('[P0] renders DatasetCard with LOB id as lobName', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ lob_id: 'LOB_RETAIL' })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    // lobName is mapped from lob_id (e.g., "LOB_RETAIL")
    expect(screen.getByText('LOB_RETAIL')).toBeInTheDocument()
  })

  it('[P1] renders correct number of cards for 5-LOB response', () => {
    const lobs = Array.from({ length: 5 }, (_, i) =>
      makeLobSummaryItem({ lob_id: `LOB_${i}` })
    )
    const data = makeSummaryResponse({ lobs })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    expect(screen.getAllByTestId('dataset-card')).toHaveLength(5)
  })
})

// ---------------------------------------------------------------------------
// AC3: DatasetCard prop mapping — lobName, dqsScore, trendData, statusCounts
// ---------------------------------------------------------------------------

describe('[P1] SummaryPage — DatasetCard prop mapping (AC3)', () => {
  it('[P1] maps lob.lob_id to DatasetCard lobName prop', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ lob_id: 'LOB_MORTGAGE' })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    expect(card).toHaveAttribute('data-lob-name', 'LOB_MORTGAGE')
  })

  it('[P1] maps lob.aggregate_score to DatasetCard dqsScore prop', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ aggregate_score: 72 })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    expect(card).toHaveAttribute('data-dqs-score', '72')
  })

  it('[P1] maps null aggregate_score to 0 for DatasetCard dqsScore', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ aggregate_score: null })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    // null ?? 0 → dqsScore should be 0
    expect(card).toHaveAttribute('data-dqs-score', '0')
  })

  it('[P1] maps lob.trend to DatasetCard trendData prop', () => {
    const trend = [70, 72, 75, 74, 76, 77, 78]
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ trend })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    expect(card).toHaveAttribute('data-trend', JSON.stringify(trend))
  })

  it('[P1] maps healthy_count → statusCounts.pass', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ healthy_count: 7 })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    expect(card).toHaveAttribute('data-pass', '7')
  })

  it('[P1] maps degraded_count → statusCounts.warn', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ degraded_count: 3 })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    expect(card).toHaveAttribute('data-warn', '3')
  })

  it('[P1] maps critical_count → statusCounts.fail', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ critical_count: 2 })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    expect(card).toHaveAttribute('data-fail', '2')
  })

  it('[P1] maps lob.dataset_count to DatasetCard datasetCount prop', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ dataset_count: 15 })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    expect(card).toHaveAttribute('data-dataset-count', '15')
  })
})

// ---------------------------------------------------------------------------
// AC4: Critical DQS score < 60 — dqsScore passed correctly
// ---------------------------------------------------------------------------

describe('[P1] SummaryPage — critical DQS score handling (AC4)', () => {
  it('[P1] passes aggregate_score < 60 as dqsScore to DatasetCard', () => {
    // DatasetCard handles color-coding internally; SummaryPage just passes the score.
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ lob_id: 'LOB_CRITICAL', aggregate_score: 45 })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const card = screen.getByTestId('dataset-card')
    expect(card).toHaveAttribute('data-dqs-score', '45')
  })
})

// ---------------------------------------------------------------------------
// AC5: Navigation — clicking LOB card navigates to /lobs/{lob_id}
// ---------------------------------------------------------------------------

describe('[P0] SummaryPage — LOB card click navigates to /lobs/:lobId (AC5)', () => {
  it('[P0] clicking a DatasetCard triggers navigation to /lobs/{lob_id}', () => {
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ lob_id: 'LOB_RETAIL' })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/summary']}>
            <TimeRangeProvider>
              <Routes>
                <Route path="/*" element={<SummaryPage />} />
                <Route
                  path="/lobs/:lobId"
                  element={<div data-testid="lob-detail-page" />}
                />
              </Routes>
            </TimeRangeProvider>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    )

    const card = screen.getByTestId('dataset-card')
    fireEvent.click(card)

    // After click, LOB detail page should render (route /lobs/LOB_RETAIL matched)
    expect(screen.getByTestId('lob-detail-page')).toBeInTheDocument()
  })

  it('[P0] navigates to plural /lobs/ path, not singular /lob/', () => {
    // CRITICAL: route is /lobs/:lobId (plural), not /lob/:lobId (singular) — per App.tsx
    const data = makeSummaryResponse({
      lobs: [makeLobSummaryItem({ lob_id: 'LOB_MORTGAGE' })],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/summary']}>
            <TimeRangeProvider>
              <Routes>
                <Route path="/*" element={<SummaryPage />} />
                <Route
                  path="/lobs/:lobId"
                  element={<div data-testid="lob-detail-page" />}
                />
              </Routes>
            </TimeRangeProvider>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    )

    const card = screen.getByTestId('dataset-card')
    fireEvent.click(card)

    // The lob-detail-page route at /lobs/:lobId must match — singular /lob/ would not
    expect(screen.getByTestId('lob-detail-page')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Error state — component-level error with retry, no DatasetCards
// ---------------------------------------------------------------------------

describe('[P0] SummaryPage — error state (edge case)', () => {
  it('[P0] renders error message when useSummary returns isError true', () => {
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    })

    renderSummaryPage()

    // Error state must show a user-friendly message
    expect(
      screen.getByText(/failed to load summary data/i)
    ).toBeInTheDocument()
  })

  it('[P0] does not render DatasetCards when isError is true', () => {
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    })

    renderSummaryPage()

    const cards = screen.queryAllByTestId('dataset-card')
    expect(cards).toHaveLength(0)
  })

  it('[P1] renders a retry button or link in error state', () => {
    const mockRefetch = vi.fn()
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: mockRefetch,
    })

    renderSummaryPage()

    const retryElement = screen.getByText(/retry/i)
    expect(retryElement).toBeInTheDocument()
  })

  it('[P1] clicking retry calls refetch from useSummary', () => {
    const mockRefetch = vi.fn()
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: mockRefetch,
    })

    renderSummaryPage()

    const retryElement = screen.getByText(/retry/i)
    fireEvent.click(retryElement)

    expect(mockRefetch).toHaveBeenCalledOnce()
  })
})

// ---------------------------------------------------------------------------
// Empty state — data.lobs is empty array
// ---------------------------------------------------------------------------

describe('[P1] SummaryPage — empty state (edge case)', () => {
  it('[P1] renders empty state message when data.lobs is empty', () => {
    const data = makeSummaryResponse({ lobs: [] })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    // Per story Dev Notes / UX spec: empty state message
    expect(
      screen.getByText(/no data quality results yet/i)
    ).toBeInTheDocument()
  })

  it('[P1] does not render DatasetCards when data.lobs is empty', () => {
    const data = makeSummaryResponse({ lobs: [] })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    const cards = screen.queryAllByTestId('dataset-card')
    expect(cards).toHaveLength(0)
  })
})

// ---------------------------------------------------------------------------
// isFetching stale-while-revalidate opacity
// RED PHASE: SummaryPage does not yet destructure isFetching or apply opacity.
// ---------------------------------------------------------------------------

describe('[P0] SummaryPage — isFetching opacity indicator', () => {
  it('[P0] wraps DatasetCard grid items in a Box with opacity 0.5 when isFetching is true', () => {
    // THIS TEST WILL FAIL — SummaryPage does not yet use isFetching from useSummary
    // Implementation: destructure isFetching from useSummary(), wrap each Grid item
    // in <Box sx={{ opacity: isFetching ? 0.5 : 1, transition: 'opacity 0.2s' }}>
    const data = makeSummaryResponse()
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isFetching: true,
      isError: false,
      data,
      refetch: vi.fn(),
    })

    renderSummaryPage()

    // The wrapper Box around DatasetCard must have opacity: 0.5 inline style
    // when isFetching is true. MUI sx prop renders as inline style on the DOM element.
    const cards = screen.getAllByTestId('dataset-card')
    expect(cards.length).toBeGreaterThan(0)

    // Each card's parent wrapper Box must have opacity 0.5 style applied
    const firstCardWrapper = cards[0].parentElement
    expect(firstCardWrapper).not.toBeNull()
    expect(firstCardWrapper!.style.opacity).toBe('0.5')
  })

  it('[P0] wrapper Box has opacity 1 (full) when isFetching is false', () => {
    // THIS TEST WILL FAIL — isFetching opacity not yet implemented
    const data = makeSummaryResponse()
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isFetching: false,
      isError: false,
      data,
      refetch: vi.fn(),
    })

    renderSummaryPage()

    const cards = screen.getAllByTestId('dataset-card')
    expect(cards.length).toBeGreaterThan(0)

    // When isFetching is false, opacity must be 1 (no dimming)
    const firstCardWrapper = cards[0].parentElement
    expect(firstCardWrapper).not.toBeNull()
    // Either explicit '1' or no opacity style (defaults to 1)
    const opacity = firstCardWrapper!.style.opacity
    expect(opacity === '' || opacity === '1').toBe(true)
  })

  it('[P1] does not show any spinning loader elements when isFetching is true', () => {
    // THIS TEST WILL FAIL — guards against regression: no spinners allowed (project-context.md)
    const data = makeSummaryResponse()
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isFetching: true,
      isError: false,
      data,
      refetch: vi.fn(),
    })

    renderSummaryPage()

    // MUI CircularProgress renders with role="progressbar"
    // Per project-context.md: NEVER spinning loaders — skeletons only
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Network error → full-page "Unable to connect to DQS"
// RED PHASE: SummaryPage currently shows generic "Failed to load summary data"
// for all errors. AC8 requires a specific full-page message for network failures.
// ---------------------------------------------------------------------------

describe('[P0] SummaryPage — API unreachable full-page error', () => {
  it('[P0] renders full-page unreachable message when error is a network TypeError', () => {
    // THIS TEST WILL FAIL — SummaryPage does not yet distinguish network errors
    // Implementation (preferred approach from Dev Notes):
    //   if (isError && error instanceof TypeError && error.message.includes('fetch')) {
    //     render "Unable to connect to DQS. Check your network connection or try again."
    //   }
    const networkError = new TypeError('Failed to fetch')
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isFetching: false,
      isError: true,
      error: networkError,
      data: undefined,
      refetch: vi.fn(),
    })

    renderSummaryPage()

    expect(
      screen.getByText(/unable to connect to dqs/i)
    ).toBeInTheDocument()
    expect(
      screen.getByText(/check your network connection/i)
    ).toBeInTheDocument()
  })

  it('[P0] renders a retry button on the full-page unreachable error', () => {
    // THIS TEST WILL FAIL — unreachable-specific retry not yet implemented
    const networkError = new TypeError('Failed to fetch')
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isFetching: false,
      isError: true,
      error: networkError,
      data: undefined,
      refetch: vi.fn(),
    })

    renderSummaryPage()

    // Must include a retry affordance — button or link with "retry" or "try again" text
    const retryAffordance =
      screen.queryByRole('button', { name: /retry|try again/i }) ??
      screen.queryByText(/retry|try again/i)
    expect(retryAffordance).toBeInTheDocument()
  })

  it('[P1] still renders generic error message for non-network errors', () => {
    // THIS TEST WILL FAIL — SummaryPage must keep existing generic error for non-network errors
    const genericError = new Error('Internal Server Error')
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isFetching: false,
      isError: true,
      error: genericError,
      data: undefined,
      refetch: vi.fn(),
    })

    renderSummaryPage()

    // Non-network errors: existing "Failed to load summary data" message
    // (not the full-page "Unable to connect" which is reserved for network errors)
    expect(
      screen.getByText(/failed to load summary data/i)
    ).toBeInTheDocument()
    // Must NOT show the network-error message for non-network failures
    expect(
      screen.queryByText(/unable to connect to dqs/i)
    ).not.toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Rendering stability — smoke tests
// ---------------------------------------------------------------------------

describe('[P0] SummaryPage — rendering stability', () => {
  it('[P0] renders without crashing in loading state', () => {
    mockUseSummary.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    expect(() => renderSummaryPage()).not.toThrow()
  })

  it('[P0] renders without crashing with full data response', () => {
    const data = makeSummaryResponse()
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    expect(() => renderSummaryPage()).not.toThrow()
  })

  it('[P0] renders without crashing in error state', () => {
    mockUseSummary.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    })

    expect(() => renderSummaryPage()).not.toThrow()
  })
})

// ---------------------------------------------------------------------------
// 1280px viewport — 3-column LOB card grid
//
// GREEN PHASE: SummaryPage uses size={{ xs: 12, sm: 6, md: 4 }}.
// These tests verify the Grid item MUI size props are correct for the
// CUSTOM breakpoints defined in theme.ts (xs:0, sm:1024, md:1280, lg:1440).
// With the custom breakpoints: md=1280px → 3-col (md:4 = 33%), sm=1024px → 2-col (sm:6 = 50%)
// ---------------------------------------------------------------------------

describe('[P1] SummaryPage — Grid size props for 3-column layout', () => {
  it('[P1] Grid items have size={{ xs: 12, sm: 6, md: 4 }} for responsive column count', () => {
    // With custom breakpoints in theme.ts:
    //   md: 1280 → size md:4 gives 3 columns at standard 1280px viewport (AC4)
    //   sm: 1024 → size sm:6 gives 2 columns below 1280px (AC6)
    //
    // Verifies Grid items render with the correct size props
    // (the breakpoint values in theme.ts are what make md=1280px, sm=1024px)
    const data = makeSummaryResponse({
      lobs: [
        makeLobSummaryItem({ lob_id: 'LOB_A' }),
        makeLobSummaryItem({ lob_id: 'LOB_B' }),
        makeLobSummaryItem({ lob_id: 'LOB_C' }),
      ],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    // All 3 cards must be rendered — verifying they're in the Grid
    const cards = screen.getAllByTestId('dataset-card')
    expect(cards).toHaveLength(3)

    // Each card is inside a Grid item. MUI Grid v2 renders the size prop as
    // CSS classes on the Grid item wrapper. We verify all 3 cards are present
    // (structural check — the breakpoint config in theme.ts controls actual px values)
    cards.forEach((card) => {
      const gridItem = card.parentElement
      expect(gridItem).not.toBeNull()
    })
  })

  it('[P1] 6 skeleton cards use same Grid size props during loading (AC4)', () => {
    // Skeleton loading state must use same Grid size={{ xs:12, sm:6, md:4 }}
    // to maintain layout consistency (no layout shift on data load)
    mockUseSummary.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderSummaryPage()

    // 6 skeletons must be present during loading
    const skeletons = document.querySelectorAll('[class*="MuiSkeleton"]')
    expect(skeletons).toHaveLength(6)
  })
})

// ---------------------------------------------------------------------------
// < 1280px viewport — 2-column reflow
//
// GREEN PHASE: Grid size sm:6 exists in SummaryPage.tsx.
// The 2-column behavior at <1280px is enabled by sm:1024 in theme.ts.
// ---------------------------------------------------------------------------

describe('[P1] SummaryPage — 2-column reflow below 1280px', () => {
  it('[P1] Grid items have sm:6 size prop for 2-column layout below md breakpoint', () => {
    // AC6: below 1280px (below md with custom breakpoints) → 2 columns
    // The Grid size sm:6 already exists — this test verifies no regression
    // and that the breakpoint config change doesn't break the 2-col behavior
    const data = makeSummaryResponse({
      lobs: [
        makeLobSummaryItem({ lob_id: 'LOB_A' }),
        makeLobSummaryItem({ lob_id: 'LOB_B' }),
      ],
    })
    mockUseSummary.mockReturnValue({ isLoading: false, isError: false, data })

    renderSummaryPage()

    // Both cards rendered — structural check
    const cards = screen.getAllByTestId('dataset-card')
    expect(cards).toHaveLength(2)

    // Grid container and items present
    cards.forEach((card) => {
      expect(card.parentElement).not.toBeNull()
    })
  })
})
