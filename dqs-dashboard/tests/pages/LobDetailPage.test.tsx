/**
 * ATDD Component Tests — LOB Detail View (Level 2)
 *
 * RED PHASE: All tests use it() — LobDetailPage is a placeholder,
 * useLobDatasets does not exist, DatasetInLob / LobDatasetsResponse types
 * are not yet defined, and @mui/x-data-grid is not installed.
 * Remove `it.skip` (convert to `it`) after implementation is complete.
 *
 * Acceptance Criteria covered:
 *   AC1: Stats header shows LOB name, dataset count, avg DQS score, checks passing rate, last run time
 *   AC2: Sortable table shows all datasets with dataset_name, DQS Score, trend, status chips
 *   AC3: Click column header sorts table (default: DQS Score ascending — worst first)
 *   AC4: Click dataset row navigates to /datasets/{dataset_id}
 *   AC5: Breadcrumb Summary > {LOB Name} click navigates back (handled by AppLayout, not asserted here)
 *   AC6: Loading state renders skeleton rows matching table layout, no DataGrid or stats
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
import LobDetailPage from '../../src/pages/LobDetailPage'
import { TimeRangeProvider } from '../../src/context/TimeRangeContext'

// ---------------------------------------------------------------------------
// ResizeObserver polyfill — required for MUI X DataGrid in jsdom
// ---------------------------------------------------------------------------

global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

// Mock useLobDatasets to avoid real API calls.
// useLobDatasets does not yet exist in queries.ts — this mock will cause a
// runtime import error until it is added (confirming RED phase).
vi.mock('../../src/api/queries', () => ({
  useLobDatasets: vi.fn(),
  useLobs: vi.fn(),
  useDatasets: vi.fn(),
  useSummary: vi.fn(),
}))

// Mock DqsScoreChip and TrendSparkline to avoid Recharts canvas issues in jsdom.
vi.mock('../../src/components', () => ({
  DqsScoreChip: ({ score }: { score?: number }) => (
    <span data-testid="dqs-score-chip">{score !== undefined ? score : '—'}</span>
  ),
  TrendSparkline: ({ data }: { data: number[] }) => (
    <span data-testid="trend-sparkline">{data.length} points</span>
  ),
  DatasetCard: ({ lobName }: { lobName: string }) => <div>{lobName}</div>,
}))

// Mock useTimeRange hook so LobDetailPage can call it from context.
vi.mock('../../src/context/TimeRangeContext', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../src/context/TimeRangeContext')>()
  return {
    ...actual,
    useTimeRange: vi.fn(() => ({ timeRange: '7d' as const, setTimeRange: vi.fn() })),
  }
})

// ---------------------------------------------------------------------------
// Test data factories
// ---------------------------------------------------------------------------

function makeDatasetInLob(overrides: Partial<{
  dataset_id: number
  dataset_name: string
  dqs_score: number | null
  check_status: 'PASS' | 'WARN' | 'FAIL' | null
  partition_date: string
  trend: number[]
  freshness_status: 'PASS' | 'WARN' | 'FAIL' | null
  volume_status: 'PASS' | 'WARN' | 'FAIL' | null
  schema_status: 'PASS' | 'WARN' | 'FAIL' | null
}> = {}) {
  return {
    dataset_id: 101,
    dataset_name: 'retail_transactions',
    dqs_score: 87,
    check_status: 'PASS',
    partition_date: '2026-03-30',
    trend: [80, 82, 85, 84, 86, 87, 87],
    freshness_status: 'PASS',
    volume_status: 'PASS',
    schema_status: null,
    ...overrides,
  }
}

function makeLobDatasetsResponse(overrides: Partial<{
  lob_id: string
  datasets: ReturnType<typeof makeDatasetInLob>[]
}> = {}) {
  return {
    lob_id: 'LOB_RETAIL',
    datasets: [
      makeDatasetInLob({ dataset_id: 101, dataset_name: 'retail_transactions', dqs_score: 87, check_status: 'PASS' }),
      makeDatasetInLob({ dataset_id: 102, dataset_name: 'retail_customers', dqs_score: 55, check_status: 'FAIL' }),
      makeDatasetInLob({ dataset_id: 103, dataset_name: 'retail_products', dqs_score: 72, check_status: 'WARN' }),
    ],
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Render helper — wraps LobDetailPage with all required providers.
// Uses MemoryRouter with /lobs/:lobId route so useParams resolves correctly.
// ---------------------------------------------------------------------------

function renderLobDetailPage(lobId = 'LOB_RETAIL') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[`/lobs/${lobId}`]}>
          <TimeRangeProvider>
            <Routes>
              <Route path="/lobs/:lobId" element={<LobDetailPage />} />
            </Routes>
          </TimeRangeProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

// ---------------------------------------------------------------------------
// Import useLobDatasets mock reference for per-test control
// ---------------------------------------------------------------------------

let mockUseLobDatasets: ReturnType<typeof vi.fn>

beforeEach(async () => {
  const queries = await import('../../src/api/queries')
  // useLobDatasets does not exist yet — accessing it here will fail until implemented
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  mockUseLobDatasets = (queries as any).useLobDatasets as ReturnType<typeof vi.fn>
})

// ---------------------------------------------------------------------------
// AC6: Loading state — skeleton rows, no DataGrid or stats
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — loading state (AC6)', () => {
  it('[P0] renders skeleton elements when isLoading is true', () => {
    // THIS TEST WILL FAIL — LobDetailPage is a placeholder, renders none of this
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderLobDetailPage()

    // Expect MUI Skeleton elements to be present for stats header and table rows
    const skeletons = document.querySelectorAll('[class*="MuiSkeleton"]')
    expect(skeletons.length).toBeGreaterThanOrEqual(2) // At least 1 stats + 8 table rows
  })

  it('[P0] does not render DataGrid when isLoading is true', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid at all
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderLobDetailPage()

    // DataGrid renders with role="grid" — must be absent during loading
    expect(document.querySelector('[role="grid"]')).toBeNull()
  })

  it('[P0] does not render stats bar when isLoading is true', () => {
    // THIS TEST WILL FAIL — placeholder shows no stats
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderLobDetailPage()

    // Stats bar would show dataset count — must be absent during loading
    expect(screen.queryByText(/datasets/i)).not.toBeInTheDocument()
  })

  it('[P1] renders exactly 8 skeleton table row elements when isLoading is true', () => {
    // THIS TEST WILL FAIL — placeholder page renders no skeletons
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderLobDetailPage()

    // Per Dev Notes: 8 skeleton rows at 52px height match DataGrid default row height
    const skeletons = document.querySelectorAll('[class*="MuiSkeleton"]')
    // Stats header skeleton + 8 table row skeletons = at least 9
    expect(skeletons.length).toBeGreaterThanOrEqual(9)
  })
})

// ---------------------------------------------------------------------------
// AC1: Stats header — LOB name, dataset count, avg DQS score, passing rate, last run
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — stats header renders correct values (AC1)', () => {
  it('[P0] renders LOB name in stats header', () => {
    // THIS TEST WILL FAIL — placeholder only shows "LOB Detail: LOB_RETAIL" as h4, no stats header
    const data = makeLobDatasetsResponse({ lob_id: 'LOB_RETAIL' })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage('LOB_RETAIL')

    // Stats header must contain LOB name prominently (not just page title)
    // The stats header is a Box with bgcolor: 'background.paper'
    // LOB name displayed as a tile in the stats bar
    expect(screen.getByText('LOB_RETAIL')).toBeInTheDocument()
  })

  it('[P0] renders dataset count in stats header', () => {
    // THIS TEST WILL FAIL — placeholder shows no stats
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ dataset_id: 101 }),
        makeDatasetInLob({ dataset_id: 102 }),
        makeDatasetInLob({ dataset_id: 103 }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // datasetCount = data.datasets.length = 3
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('[P0] renders avg DQS score in stats header', () => {
    // THIS TEST WILL FAIL — placeholder shows no stats
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ dqs_score: 80 }),
        makeDatasetInLob({ dataset_id: 102, dqs_score: 90 }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // avgScore = Math.round((80 + 90) / 2) = 85
    expect(screen.getByText('85')).toBeInTheDocument()
  })

  it('[P1] renders checks passing rate in stats header', () => {
    // THIS TEST WILL FAIL — placeholder shows no stats
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ check_status: 'PASS' }),
        makeDatasetInLob({ dataset_id: 102, check_status: 'PASS' }),
        makeDatasetInLob({ dataset_id: 103, check_status: 'FAIL' }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // passingRate = Math.round((2 / 3) * 100) = 67
    expect(screen.getByText('67%')).toBeInTheDocument()
  })

  it('[P1] renders last run partition_date in stats header', () => {
    // THIS TEST WILL FAIL — placeholder shows no stats
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ partition_date: '2026-03-30' }),
        makeDatasetInLob({ dataset_id: 102, partition_date: '2026-03-29' }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // lastRun = data.datasets[0].partition_date = '2026-03-30'
    expect(screen.getByText('2026-03-30')).toBeInTheDocument()
  })

  it('[P1] renders "—" for avg DQS score when all scores are null', () => {
    // THIS TEST WILL FAIL — placeholder shows no stats
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ dqs_score: null }),
        makeDatasetInLob({ dataset_id: 102, dqs_score: null }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // avgScore should show "—" when no scores available (empty array after null filter)
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })
})

// ---------------------------------------------------------------------------
// AC2: Dataset table — columns: dataset_name, DQS Score, trend, status chips
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — dataset table renders rows and columns (AC2)', () => {
  it('[P0] renders dataset names in DataGrid rows', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ dataset_name: 'retail_transactions' }),
        makeDatasetInLob({ dataset_id: 102, dataset_name: 'retail_customers' }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    expect(screen.getByText('retail_transactions')).toBeInTheDocument()
    expect(screen.getByText('retail_customers')).toBeInTheDocument()
  })

  it('[P0] renders DqsScoreChip for each dataset row', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ dqs_score: 87 }),
        makeDatasetInLob({ dataset_id: 102, dqs_score: 55 }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    const chips = screen.getAllByTestId('dqs-score-chip')
    expect(chips).toHaveLength(2)
  })

  it('[P0] renders TrendSparkline for each dataset row', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ trend: [80, 82, 85, 84, 86, 87, 87] }),
        makeDatasetInLob({ dataset_id: 102, trend: [55, 58, 60] }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    const sparklines = screen.getAllByTestId('trend-sparkline')
    expect(sparklines).toHaveLength(2)
  })

  it('[P0] renders PASS status chip for dataset with check_status=PASS', () => {
    // THIS TEST WILL FAIL — placeholder page has no status chips
    const data = makeLobDatasetsResponse({
      datasets: [makeDatasetInLob({ check_status: 'PASS' })],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // StatusChip renders Chip with label "PASS"
    expect(screen.getByText('PASS')).toBeInTheDocument()
  })

  it('[P0] renders FAIL status chip for dataset with check_status=FAIL', () => {
    // THIS TEST WILL FAIL — placeholder page has no status chips
    const data = makeLobDatasetsResponse({
      datasets: [makeDatasetInLob({ check_status: 'FAIL' })],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    expect(screen.getByText('FAIL')).toBeInTheDocument()
  })

  it('[P1] renders WARN status chip for dataset with check_status=WARN', () => {
    // THIS TEST WILL FAIL — placeholder page has no status chips
    const data = makeLobDatasetsResponse({
      datasets: [makeDatasetInLob({ check_status: 'WARN' })],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    expect(screen.getByText('WARN')).toBeInTheDocument()
  })

  it('[P1] renders dash typography for null status (freshness_status=null)', () => {
    // THIS TEST WILL FAIL — placeholder page has no status chips
    const data = makeLobDatasetsResponse({
      datasets: [makeDatasetInLob({ freshness_status: null })],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // StatusChip returns Typography "—" for null status
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })

  it('[P1] renders "Dataset" column header in DataGrid', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    expect(screen.getByText('Dataset')).toBeInTheDocument()
  })

  it('[P1] renders "DQS Score" column header in DataGrid', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    expect(screen.getByText('DQS Score')).toBeInTheDocument()
  })

  it('[P1] renders "Trend" column header in DataGrid', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    expect(screen.getByText('Trend')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC3: Column sorting — click header to sort (default DQS Score ascending)
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — DataGrid column sorting (AC3)', () => {
  it('[P0] DataGrid renders with role="grid" when data is loaded', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // MUI X DataGrid renders with role="grid"
    expect(document.querySelector('[role="grid"]')).toBeInTheDocument()
  })

  it('[P1] DataGrid is initialized with DQS Score ascending sort (worst first)', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ dataset_id: 101, dataset_name: 'high_score', dqs_score: 90 }),
        makeDatasetInLob({ dataset_id: 102, dataset_name: 'low_score', dqs_score: 40 }),
        makeDatasetInLob({ dataset_id: 103, dataset_name: 'mid_score', dqs_score: 65 }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    // With default sort dqs_score ascending, worst (40) should appear before best (90)
    // MUI DataGrid renders rows in sorted order in the DOM
    const rows = document.querySelectorAll('[role="row"][data-rowindex]')
    expect(rows.length).toBeGreaterThanOrEqual(3)

    // First visible data row should contain low_score (DQS 40, worst first)
    const firstRowText = rows[0]?.textContent ?? ''
    expect(firstRowText).toContain('low_score')
  })
})

// ---------------------------------------------------------------------------
// AC4: Row click navigates to /datasets/{dataset_id}
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — dataset row click navigates to /datasets/:id (AC4)', () => {
  it('[P0] clicking a dataset row navigates to /datasets/{dataset_id}', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid or navigation
    const data = makeLobDatasetsResponse({
      datasets: [makeDatasetInLob({ dataset_id: 201, dataset_name: 'retail_transactions' })],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/lobs/LOB_RETAIL']}>
            <TimeRangeProvider>
              <Routes>
                <Route path="/lobs/:lobId" element={<LobDetailPage />} />
                <Route
                  path="/datasets/:datasetId"
                  element={<div data-testid="dataset-detail-page" />}
                />
              </Routes>
            </TimeRangeProvider>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    )

    // Click the row containing 'retail_transactions'
    const row = screen.getByText('retail_transactions').closest('[role="row"]')
    expect(row).toBeTruthy()
    fireEvent.click(row!)

    // After click, dataset detail page should render (route /datasets/201 matched)
    expect(screen.getByTestId('dataset-detail-page')).toBeInTheDocument()
  })

  it('[P0] navigates to plural /datasets/ path, not singular /dataset/', () => {
    // THIS TEST WILL FAIL — placeholder page has no navigation
    // CRITICAL: route is /datasets/:datasetId (plural), not /dataset/:datasetId
    const data = makeLobDatasetsResponse({
      datasets: [makeDatasetInLob({ dataset_id: 202, dataset_name: 'some_dataset' })],
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/lobs/LOB_RETAIL']}>
            <TimeRangeProvider>
              <Routes>
                <Route path="/lobs/:lobId" element={<LobDetailPage />} />
                <Route
                  path="/datasets/:datasetId"
                  element={<div data-testid="dataset-detail-page" />}
                />
              </Routes>
            </TimeRangeProvider>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    )

    const row = screen.getByText('some_dataset').closest('[role="row"]')
    fireEvent.click(row!)

    // Plural /datasets/:datasetId must be matched
    expect(screen.getByTestId('dataset-detail-page')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Error state — component-level error with retry, no DataGrid
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — error state', () => {
  it('[P0] renders error message when useLobDatasets returns isError true', () => {
    // THIS TEST WILL FAIL — placeholder page has no error state
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    })

    renderLobDetailPage()

    // Error state must show user-friendly message (not a full-page crash)
    expect(screen.getByText(/failed to load lob data/i)).toBeInTheDocument()
  })

  it('[P0] does not render DataGrid when isError is true', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid anyway
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    })

    renderLobDetailPage()

    expect(document.querySelector('[role="grid"]')).toBeNull()
  })

  it('[P1] renders a retry button in error state', () => {
    // THIS TEST WILL FAIL — placeholder page has no error state
    const mockRefetch = vi.fn()
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: mockRefetch,
    })

    renderLobDetailPage()

    const retryElement = screen.getByText(/retry/i)
    expect(retryElement).toBeInTheDocument()
  })

  it('[P1] clicking retry calls refetch from useLobDatasets', () => {
    // THIS TEST WILL FAIL — placeholder page has no retry button
    const mockRefetch = vi.fn()
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: mockRefetch,
    })

    renderLobDetailPage()

    const retryElement = screen.getByText(/retry/i)
    fireEvent.click(retryElement)

    expect(mockRefetch).toHaveBeenCalledOnce()
  })
})

// ---------------------------------------------------------------------------
// Empty state — data.datasets is empty array
// ---------------------------------------------------------------------------

describe('[P1] LobDetailPage — empty state', () => {
  it('[P1] renders empty state message when data.datasets is empty', () => {
    // THIS TEST WILL FAIL — placeholder page has no empty state
    const data = makeLobDatasetsResponse({ datasets: [] })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage('LOB_RETAIL')

    // Per Dev Notes and UX spec: "No datasets monitored in {lobId}"
    expect(
      screen.getByText(/no datasets monitored in lob_retail/i)
    ).toBeInTheDocument()
  })

  it('[P1] does not render DataGrid when data.datasets is empty', () => {
    // THIS TEST WILL FAIL — placeholder page has no DataGrid
    const data = makeLobDatasetsResponse({ datasets: [] })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage()

    expect(document.querySelector('[role="grid"]')).toBeNull()
  })
})

// ---------------------------------------------------------------------------
// Hook integration — useLobDatasets called with correct args
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — useLobDatasets hook integration', () => {
  it('[P0] calls useLobDatasets with lobId from useParams', () => {
    // THIS TEST WILL FAIL — useLobDatasets does not exist in queries.ts
    const data = makeLobDatasetsResponse({ lob_id: 'LOB_MORTGAGE' })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage('LOB_MORTGAGE')

    // Must be called with the lobId extracted from URL params
    expect(mockUseLobDatasets).toHaveBeenCalledWith('LOB_MORTGAGE', '7d')
  })

  it('[P0] calls useLobDatasets with timeRange from useTimeRange context', () => {
    // THIS TEST WILL FAIL — useLobDatasets does not exist in queries.ts
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    renderLobDetailPage('LOB_RETAIL')

    // useTimeRange returns '7d' per mock — must be passed to useLobDatasets
    expect(mockUseLobDatasets).toHaveBeenCalledWith('LOB_RETAIL', '7d')
  })
})

// ---------------------------------------------------------------------------
// isFetching stale-while-revalidate opacity
// RED PHASE: LobDetailPage does not yet destructure isFetching or apply opacity
// to the TrendSparkline renderCell in the DataGrid columns definition.
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — isFetching opacity on trend sparklines', () => {
  it('[P0] TrendSparkline wrapper has opacity 0.5 when isFetching is true', () => {
    // THIS TEST WILL FAIL — LobDetailPage does not yet use isFetching from useLobDatasets
    // Implementation: destructure isFetching from useLobDatasets(), apply to renderCell:
    //   renderCell: (params) => (
    //     <Box sx={{ opacity: isFetching ? 0.5 : 1, transition: 'opacity 0.2s' }}>
    //       <TrendSparkline data={params.row.trend} size="mini" />
    //     </Box>
    //   )
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isFetching: true,
      isError: false,
      data,
      refetch: vi.fn(),
    })

    renderLobDetailPage()

    // Each TrendSparkline must be wrapped in a Box with opacity: 0.5
    // The mock TrendSparkline renders <span data-testid="trend-sparkline">
    const sparklines = screen.getAllByTestId('trend-sparkline')
    expect(sparklines.length).toBeGreaterThan(0)

    // Each sparkline's parent wrapper Box must have opacity 0.5 style
    const firstSparklineWrapper = sparklines[0].parentElement
    expect(firstSparklineWrapper).not.toBeNull()
    expect(firstSparklineWrapper!.style.opacity).toBe('0.5')
  })

  it('[P0] TrendSparkline wrapper has opacity 1 (full) when isFetching is false', () => {
    // THIS TEST WILL FAIL — isFetching opacity not yet implemented in LobDetailPage
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isFetching: false,
      isError: false,
      data,
      refetch: vi.fn(),
    })

    renderLobDetailPage()

    const sparklines = screen.getAllByTestId('trend-sparkline')
    expect(sparklines.length).toBeGreaterThan(0)

    // When isFetching is false, no dimming — opacity must be 1 or absent
    const firstSparklineWrapper = sparklines[0].parentElement
    expect(firstSparklineWrapper).not.toBeNull()
    const opacity = firstSparklineWrapper!.style.opacity
    expect(opacity === '' || opacity === '1').toBe(true)
  })

  it('[P1] existing data rows remain visible (not hidden) during isFetching', () => {
    // THIS TEST WILL FAIL — guards stale-while-revalidate correctness
    // Per UX spec: "Existing data stays visible (dimmed) while new data loads"
    // Data must not be replaced by skeleton or hidden during isFetching
    const data = makeLobDatasetsResponse({
      datasets: [
        makeDatasetInLob({ dataset_id: 101, dataset_name: 'retail_transactions' }),
        makeDatasetInLob({ dataset_id: 102, dataset_name: 'retail_customers' }),
      ],
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isFetching: true,
      isError: false,
      data,
      refetch: vi.fn(),
    })

    renderLobDetailPage()

    // Dataset names must still be visible (just dimmed, not hidden)
    expect(screen.getByText('retail_transactions')).toBeInTheDocument()
    expect(screen.getByText('retail_customers')).toBeInTheDocument()
  })

  it('[P1] does not show any spinning loader when isFetching is true', () => {
    // THIS TEST WILL FAIL — guards against forbidden spinner pattern
    // Per project-context.md anti-patterns: NEVER spinning loaders
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isFetching: true,
      isError: false,
      data,
      refetch: vi.fn(),
    })

    renderLobDetailPage()

    // MUI CircularProgress renders with role="progressbar"
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Rendering stability — smoke tests
// ---------------------------------------------------------------------------

describe('[P0] LobDetailPage — rendering stability', () => {
  it('[P0] renders without crashing in loading state', () => {
    // THIS TEST WILL FAIL — loading state not implemented
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    expect(() => renderLobDetailPage()).not.toThrow()
  })

  it('[P0] renders without crashing with full data response', () => {
    // THIS TEST WILL FAIL — full data state not implemented
    const data = makeLobDatasetsResponse()
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data })

    expect(() => renderLobDetailPage()).not.toThrow()
  })

  it('[P0] renders without crashing in error state', () => {
    // THIS TEST WILL FAIL — error state not implemented
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    })

    expect(() => renderLobDetailPage()).not.toThrow()
  })
})
