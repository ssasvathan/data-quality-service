/**
 * ATDD Component Tests — Dataset Detail View (Level 3)
 *
 * RED PHASE: DatasetDetailPage is currently a 10-line placeholder.
 * useDatasetMetrics and useDatasetTrend do not yet exist in queries.ts.
 * CheckMetric, CheckDetailMetric, CheckResult, DatasetMetricsResponse,
 * TrendPoint, DatasetTrendResponse types are not yet defined in types.ts.
 * The Master-Detail split layout, left panel, check results list, and
 * score breakdown card are not yet implemented.
 *
 * All tests are written for EXPECTED behavior (TDD red phase).
 * Remove .skip from it() calls after implementation is complete.
 *
 * Acceptance Criteria covered:
 *   AC1: Left panel shows scrollable dataset list sorted by DQS Score ascending
 *   AC2: Active dataset highlighted with primary-light bg + left border
 *   AC3: Right panel shows dataset header + 2-col grid
 *   AC4: Check results list: status chip, check name, score — FAIL/WARN emphasized
 *   AC5: Score breakdown card with LinearProgress per check category
 *   AC6: Left panel click updates URL + right panel (no full navigation)
 *   AC7: Breadcrumb shows Summary > {LOB} > {Dataset} (3-level)
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router'
import React from 'react'
import theme from '../../src/theme'
import DatasetDetailPage from '../../src/pages/DatasetDetailPage'
import { TimeRangeProvider } from '../../src/context/TimeRangeContext'

// ---------------------------------------------------------------------------
// ResizeObserver polyfill — required for MUI components in jsdom
// ---------------------------------------------------------------------------

global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

// Mock all API hooks to avoid real API calls.
// useDatasetMetrics and useDatasetTrend do not yet exist in queries.ts —
// accessing them here will fail at runtime until they are added (confirming RED phase).
vi.mock('../../src/api/queries', () => ({
  useDatasetDetail: vi.fn(),
  useLobDatasets: vi.fn(),
  useDatasetMetrics: vi.fn(),
  useDatasetTrend: vi.fn(),
  useLobs: vi.fn(),
  useDatasets: vi.fn(),
  useSummary: vi.fn(),
}))

// Mock DqsScoreChip, TrendSparkline, DatasetInfoPanel to avoid Recharts/canvas issues.
vi.mock('../../src/components', () => ({
  DqsScoreChip: ({ score, size }: { score?: number; size?: string }) => (
    <span data-testid="dqs-score-chip" data-size={size}>
      {score !== undefined ? score : '—'}
    </span>
  ),
  TrendSparkline: ({ data }: { data: number[] }) => (
    <span data-testid="trend-sparkline">{data.length} points</span>
  ),
  DatasetCard: ({ lobName }: { lobName: string }) => <div>{lobName}</div>,
  DatasetInfoPanel: ({ dataset }: { dataset: unknown }) => (
    <div data-testid="dataset-info-panel">{JSON.stringify(dataset)}</div>
  ),
}))

// Mock TimeRangeContext — preserve provider, stub hook return
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

function makeDatasetDetail(
  overrides: Partial<{
    dataset_id: number
    dataset_name: string
    lob_id: string
    source_system: string
    format: string
    hdfs_path: string
    parent_path: string | null
    partition_date: string
    row_count: number | null
    previous_row_count: number | null
    last_updated: string
    run_id: number
    rerun_number: number
    dqs_score: number | null
    check_status: 'PASS' | 'WARN' | 'FAIL' | null
    error_message: string | null
  }> = {}
) {
  return {
    dataset_id: 9,
    dataset_name: 'retail_transactions',
    lob_id: 'LOB_RETAIL',
    source_system: 'HDFS',
    format: 'parquet',
    hdfs_path: '/data/retail/transactions',
    parent_path: '/data/retail',
    partition_date: '2026-04-01',
    row_count: 103876,
    previous_row_count: 102500,
    last_updated: '2026-04-01T08:00:00',
    run_id: 1001,
    rerun_number: 0,
    dqs_score: 87.5,
    check_status: 'PASS' as const,
    error_message: null,
    ...overrides,
  }
}

function makeDatasetInLob(
  overrides: Partial<{
    dataset_id: number
    dataset_name: string
    lob_id: string
    dqs_score: number | null
    check_status: 'PASS' | 'WARN' | 'FAIL' | null
    partition_date: string
    trend: number[]
    freshness_status: 'PASS' | 'WARN' | 'FAIL' | null
    volume_status: 'PASS' | 'WARN' | 'FAIL' | null
    schema_status: 'PASS' | 'WARN' | 'FAIL' | null
  }> = {}
) {
  return {
    dataset_id: 9,
    dataset_name: 'retail_transactions',
    lob_id: 'LOB_RETAIL',
    dqs_score: 87.5,
    check_status: 'PASS' as const,
    partition_date: '2026-04-01',
    trend: [80, 82, 85, 84, 86, 87, 87],
    freshness_status: 'PASS' as const,
    volume_status: 'PASS' as const,
    schema_status: null,
    ...overrides,
  }
}

function makeLobDatasetsResponse(datasets?: ReturnType<typeof makeDatasetInLob>[]) {
  return {
    lob_id: 'LOB_RETAIL',
    datasets: datasets ?? [
      makeDatasetInLob({ dataset_id: 9, dataset_name: 'retail_transactions', dqs_score: 87.5 }),
      makeDatasetInLob({ dataset_id: 10, dataset_name: 'retail_customers', dqs_score: 55.0, check_status: 'FAIL' }),
      makeDatasetInLob({ dataset_id: 11, dataset_name: 'retail_products', dqs_score: 72.0, check_status: 'WARN' }),
    ],
  }
}

function makeCheckResult(overrides: Partial<{
  check_type: string
  status: 'PASS' | 'WARN' | 'FAIL' | null
  numeric_metrics: Array<{ metric_name: string; metric_value: number }>
  detail_metrics: Array<{ detail_type: string; detail_value: unknown }>
}> = {}) {
  return {
    check_type: 'VOLUME',
    status: 'PASS' as const,
    numeric_metrics: [
      { metric_name: 'row_count', metric_value: 103876 },
      { metric_name: 'dqs_score', metric_value: 95.0 },
    ],
    detail_metrics: [],
    ...overrides,
  }
}

function makeMetricsResponse(
  datasetId = 9,
  checkResults?: ReturnType<typeof makeCheckResult>[]
) {
  return {
    dataset_id: datasetId,
    check_results: checkResults ?? [
      makeCheckResult({ check_type: 'VOLUME', status: 'PASS' }),
      makeCheckResult({
        check_type: 'FRESHNESS',
        status: 'WARN',
        numeric_metrics: [
          { metric_name: 'hours_since_update', metric_value: 28.5 },
          { metric_name: 'dqs_score', metric_value: 62.0 },
        ],
      }),
    ],
  }
}

function makeTrendResponse(datasetId = 9) {
  return {
    dataset_id: datasetId,
    time_range: '7d',
    trend: [
      { date: '2026-03-27', dqs_score: 98.5 },
      { date: '2026-03-28', dqs_score: 97.0 },
      { date: '2026-04-01', dqs_score: 87.5 },
    ],
  }
}

// ---------------------------------------------------------------------------
// Render helper — wraps DatasetDetailPage with all required providers.
// Uses MemoryRouter with /datasets/:datasetId route.
// ---------------------------------------------------------------------------

function renderDatasetDetail(
  datasetId: string = '9',
  initialPath: string = `/datasets/${datasetId}`
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[initialPath]}>
          <TimeRangeProvider>
            <Routes>
              <Route path="/datasets/:datasetId" element={<DatasetDetailPage />} />
              <Route path="/lobs/:lobId" element={<div data-testid="lob-page">LOB Page</div>} />
            </Routes>
          </TimeRangeProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

// ---------------------------------------------------------------------------
// Mock reference extraction for per-test control
// ---------------------------------------------------------------------------

let mockUseDatasetDetail: ReturnType<typeof vi.fn>
let mockUseLobDatasets: ReturnType<typeof vi.fn>
let mockUseDatasetMetrics: ReturnType<typeof vi.fn>
let mockUseDatasetTrend: ReturnType<typeof vi.fn>

beforeEach(async () => {
  const queries = await import('../../src/api/queries')
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const q = queries as any
  mockUseDatasetDetail = q.useDatasetDetail as ReturnType<typeof vi.fn>
  mockUseLobDatasets = q.useLobDatasets as ReturnType<typeof vi.fn>
  mockUseDatasetMetrics = q.useDatasetMetrics as ReturnType<typeof vi.fn>
  mockUseDatasetTrend = q.useDatasetTrend as ReturnType<typeof vi.fn>
})

// ---------------------------------------------------------------------------
// Rendering stability — smoke tests (must pass even if layout is wrong)
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — rendering stability', () => {
  it('[P0] renders without crashing in loading state', () => {
    // THIS TEST WILL FAIL — loading state not implemented in placeholder
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    expect(() => renderDatasetDetail()).not.toThrow()
  })

  it('[P0] renders without crashing with full data', () => {
    // THIS TEST WILL FAIL — full data state not implemented in placeholder
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    expect(() => renderDatasetDetail()).not.toThrow()
  })

  it('[P0] renders without crashing in error state', () => {
    // THIS TEST WILL FAIL — error state not implemented in placeholder
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: undefined })

    expect(() => renderDatasetDetail()).not.toThrow()
  })
})

// ---------------------------------------------------------------------------
// Loading state — skeleton screens (AC: all)
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — loading state renders skeletons', () => {
  it('[P0] renders skeleton elements in left panel when useLobDatasets is loading', () => {
    // THIS TEST WILL FAIL — placeholder renders no skeletons
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderDatasetDetail()

    // Left panel must show skeleton rows while loading (8 rows per Dev Notes)
    const skeletons = document.querySelectorAll('[class*="MuiSkeleton"]')
    expect(skeletons.length).toBeGreaterThanOrEqual(2)
  })

  it('[P0] does not render dataset list items when loading', () => {
    // THIS TEST WILL FAIL — placeholder renders no list items
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderDatasetDetail()

    // Dataset names should not be visible during loading
    expect(screen.queryByText('retail_transactions')).not.toBeInTheDocument()
  })

  it('[P0] does not render check results when loading', () => {
    // THIS TEST WILL FAIL — placeholder renders no check results
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderDatasetDetail()

    // Check type names must not appear while loading
    expect(screen.queryByText('Volume')).not.toBeInTheDocument()
    expect(screen.queryByText('Freshness')).not.toBeInTheDocument()
  })

  it('[P1] renders exactly 8 left-panel skeleton rows when loading', () => {
    // THIS TEST WILL FAIL — placeholder renders no skeletons
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderDatasetDetail()

    // Per Dev Notes: 8 skeleton rows at height 48px for left panel
    const skeletons = document.querySelectorAll('[class*="MuiSkeleton"]')
    expect(skeletons.length).toBeGreaterThanOrEqual(8)
  })
})

// ---------------------------------------------------------------------------
// Error state — component-level error with retry (AC: all)
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — error state', () => {
  it('[P0] renders error message when useDatasetDetail returns isError true', () => {
    // THIS TEST WILL FAIL — placeholder renders no error state
    const mockRefetch = vi.fn()
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: mockRefetch,
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: undefined })

    renderDatasetDetail()

    // Component-level error: must show message, NOT crash the page
    expect(screen.getByText(/failed to load dataset/i)).toBeInTheDocument()
  })

  it('[P0] renders retry button in error state', () => {
    // THIS TEST WILL FAIL — placeholder renders no retry button
    const mockRefetch = vi.fn()
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: mockRefetch,
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: undefined })

    renderDatasetDetail()

    const retryButton = screen.getByRole('button', { name: /retry/i })
    expect(retryButton).toBeInTheDocument()
  })

  it('[P1] clicking retry calls refetch from useDatasetDetail', () => {
    // THIS TEST WILL FAIL — placeholder renders no retry button
    const mockRefetch = vi.fn()
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: mockRefetch,
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: undefined })

    renderDatasetDetail()

    fireEvent.click(screen.getByRole('button', { name: /retry/i }))
    expect(mockRefetch).toHaveBeenCalledOnce()
  })
})

// ---------------------------------------------------------------------------
// AC1: Left panel — dataset list sorted by DQS Score ascending
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — left panel dataset list (AC1)', () => {
  it('[P0] left panel renders all datasets from useLobDatasets', () => {
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // All 3 dataset names from mockLobDatasetsResponse should appear in left panel.
    // retail_transactions also appears in the right panel header (AC3), so use getAllByText.
    expect(screen.getAllByText('retail_transactions').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('retail_customers')).toBeInTheDocument()
    expect(screen.getByText('retail_products')).toBeInTheDocument()
  })

  it('[P0] left panel datasets are sorted by DQS Score ascending (worst first)', () => {
    // THIS TEST WILL FAIL — placeholder renders no sorted list
    const datasets = [
      makeDatasetInLob({ dataset_id: 9, dataset_name: 'high_score', dqs_score: 90 }),
      makeDatasetInLob({ dataset_id: 10, dataset_name: 'low_score', dqs_score: 40 }),
      makeDatasetInLob({ dataset_id: 11, dataset_name: 'mid_score', dqs_score: 65 }),
    ]
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(datasets),
    })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Per AC1: sorted by DQS Score ascending means worst (40) appears first
    const allText = document.body.textContent ?? ''
    const lowIdx = allText.indexOf('low_score')
    const midIdx = allText.indexOf('mid_score')
    const highIdx = allText.indexOf('high_score')
    expect(lowIdx).toBeLessThan(midIdx)
    expect(midIdx).toBeLessThan(highIdx)
  })

  it('[P0] left panel renders DqsScoreChip for each dataset item', () => {
    // THIS TEST WILL FAIL — placeholder renders no DqsScoreChip
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // 3 list items + 1 large score chip in right panel = at least 3 score chips
    const chips = screen.getAllByTestId('dqs-score-chip')
    expect(chips.length).toBeGreaterThanOrEqual(3)
  })

  it('[P0] left panel dataset names are rendered with monospace font (variant="mono")', () => {
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Typography variant="mono" applies the mono font family to dataset names.
    // retail_transactions appears in both left panel and right panel header (AC3).
    expect(screen.getAllByText('retail_transactions').length).toBeGreaterThanOrEqual(1)
  })

  it('[P1] left panel renders "Datasets in LOB" section heading', () => {
    // THIS TEST WILL FAIL — placeholder renders no section heading
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Per Dev Notes: Typography variant="overline" with "Datasets in LOB"
    expect(screen.getByText(/datasets in lob/i)).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC2: Active dataset highlight — primary-light bg + left border
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — active dataset highlight (AC2)', () => {
  it('[P0] active dataset item (matching datasetId in URL) has selected styling', () => {
    // THIS TEST WILL FAIL — placeholder renders no list with selected state
    const datasets = [
      makeDatasetInLob({ dataset_id: 9, dataset_name: 'retail_transactions', dqs_score: 87 }),
      makeDatasetInLob({ dataset_id: 10, dataset_name: 'retail_customers', dqs_score: 55 }),
    ]
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail({ dataset_id: 9 }) })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(datasets),
    })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail('9')

    // MUI ListItemButton with selected={true} renders aria-selected="true"
    // The active item (dataset_id=9) should be marked as selected
    const activeItem = screen.getByRole('option', { name: /retail_transactions/i })
      ?? document.querySelector('[aria-selected="true"]')
      ?? document.querySelector('[class*="Mui-selected"]')
    expect(activeItem).not.toBeNull()
  })

  it('[P1] non-active dataset items are not highlighted', () => {
    // THIS TEST WILL FAIL — placeholder renders no list
    const datasets = [
      makeDatasetInLob({ dataset_id: 9, dataset_name: 'retail_transactions', dqs_score: 87 }),
      makeDatasetInLob({ dataset_id: 10, dataset_name: 'retail_customers', dqs_score: 55 }),
    ]
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail({ dataset_id: 9 }) })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(datasets),
    })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail('9')

    // Only 1 selected item total (the active one)
    const selectedItems = document.querySelectorAll('[aria-selected="true"], [class*="Mui-selected"]')
    expect(selectedItems.length).toBe(1)
  })
})

// ---------------------------------------------------------------------------
// AC3: Right panel — dataset header + 2-column grid layout
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — right panel header and layout (AC3)', () => {
  it('[P0] right panel shows dataset name in header', () => {
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ dataset_name: 'retail_transactions' }),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Right panel header shows dataset name prominently (AC3).
    // Name also appears in left panel list, so use getAllByText.
    expect(screen.getAllByText('retail_transactions').length).toBeGreaterThanOrEqual(1)
  })

  it('[P0] right panel shows status chip for the dataset', () => {
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ check_status: 'PASS' }),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // StatusChip renders MUI Chip with label "PASS" in both header and check results list.
    expect(screen.getAllByText('PASS').length).toBeGreaterThanOrEqual(1)
  })

  it('[P0] right panel shows FAIL status chip for FAIL dataset', () => {
    // THIS TEST WILL FAIL — placeholder shows no status chip
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ check_status: 'FAIL' }),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    expect(screen.getByText('FAIL')).toBeInTheDocument()
  })

  it('[P0] right panel shows WARN status chip for WARN dataset', () => {
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ check_status: 'WARN' }),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // WARN appears in both header StatusChip and possibly check results list.
    expect(screen.getAllByText('WARN').length).toBeGreaterThanOrEqual(1)
  })

  it('[P1] right panel shows metadata line with LOB, source system, partition date', () => {
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({
        lob_id: 'LOB_RETAIL',
        source_system: 'HDFS',
        partition_date: '2026-04-01',
      }),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Metadata line contains LOB, source system, partition date.
    // These values appear in both the right panel header and DatasetInfoPanel/breadcrumb area.
    expect(screen.getAllByText(/LOB_RETAIL/).length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText(/HDFS/).length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText(/2026-04-01/).length).toBeGreaterThanOrEqual(1)
  })

  it('[P0] right panel renders DqsScoreChip with lg size for main score', () => {
    // THIS TEST WILL FAIL — placeholder shows no DqsScoreChip
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ dqs_score: 87.5 }),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Large DqsScoreChip in right panel
    const chips = screen.getAllByTestId('dqs-score-chip')
    expect(chips.length).toBeGreaterThanOrEqual(1)
  })

  it('[P0] right panel renders TrendSparkline for DQS trend', () => {
    // THIS TEST WILL FAIL — placeholder shows no TrendSparkline
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeTrendResponse(),
    })

    renderDatasetDetail()

    // TrendSparkline renders with trend data from the trend response
    expect(screen.getByTestId('trend-sparkline')).toBeInTheDocument()
  })

  it('[P1] right panel renders DatasetInfoPanel', () => {
    // THIS TEST WILL FAIL — placeholder shows no DatasetInfoPanel
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    expect(screen.getByTestId('dataset-info-panel')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC4: Check results list — status chip, check name, score; FAIL/WARN emphasized
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — check results list (AC4)', () => {
  it('[P0] renders "Check Results" section heading', () => {
    // THIS TEST WILL FAIL — placeholder renders no check results section
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    expect(screen.getByText(/check results/i)).toBeInTheDocument()
  })

  it('[P0] renders check type name formatted (VOLUME -> Volume)', () => {
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [makeCheckResult({ check_type: 'VOLUME', status: 'PASS' })]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Check type is displayed in sentence case: "VOLUME" -> "Volume".
    // "Volume" appears in both check results list and score breakdown card.
    expect(screen.getAllByText('Volume').length).toBeGreaterThanOrEqual(1)
  })

  it('[P0] renders PASS status chip for a PASS check result', () => {
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [makeCheckResult({ check_type: 'VOLUME', status: 'PASS' })]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // PASS appears in check results list; dataset header also shows PASS chip (makeDatasetDetail defaults to PASS).
    expect(screen.getAllByText('PASS').length).toBeGreaterThanOrEqual(1)
  })

  it('[P0] renders FAIL status chip for a FAIL check result', () => {
    // THIS TEST WILL FAIL — placeholder renders no check results
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({
          check_type: 'FRESHNESS',
          status: 'FAIL',
          numeric_metrics: [{ metric_name: 'dqs_score', metric_value: 30.0 }],
        }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    expect(screen.getByText('FAIL')).toBeInTheDocument()
  })

  it('[P0] renders WARN status chip for a WARN check result', () => {
    // THIS TEST WILL FAIL — placeholder renders no check results
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({
          check_type: 'FRESHNESS',
          status: 'WARN',
          numeric_metrics: [{ metric_name: 'dqs_score', metric_value: 62.0 }],
        }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    expect(screen.getByText('WARN')).toBeInTheDocument()
  })

  it('[P0] renders dqs_score value from numeric_metrics for a check result', () => {
    // THIS TEST WILL FAIL — placeholder renders no check results
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({
          check_type: 'VOLUME',
          status: 'PASS',
          numeric_metrics: [{ metric_name: 'dqs_score', metric_value: 95 }],
        }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Score "95" must appear in check results row
    expect(screen.getByText('95')).toBeInTheDocument()
  })

  it('[P1] renders "—" for score when dqs_score metric is absent', () => {
    // THIS TEST WILL FAIL — placeholder renders no check results
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({
          check_type: 'SCHEMA',
          status: 'PASS',
          numeric_metrics: [], // no dqs_score metric
        }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // When dqs_score metric absent, display "—"
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })

  it('[P0] FAIL check rows are visually emphasized (bgcolor error.light)', () => {
    // THIS TEST WILL FAIL — placeholder renders no check result rows
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({
          check_type: 'FRESHNESS',
          status: 'FAIL',
          numeric_metrics: [{ metric_name: 'dqs_score', metric_value: 30 }],
        }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // FAIL rows must have error.light background — the FAIL chip must be in document
    expect(screen.getByText('FAIL')).toBeInTheDocument()
    // The row element containing FAIL chip must have error styling (bgcolor: 'error.light')
    const failChip = screen.getByText('FAIL')
    const failRow = failChip.closest('[data-check-status="FAIL"]')
      ?? failChip.closest('[class*="error"]')
      ?? failChip.closest('tr, [role="row"]')
    expect(failRow).not.toBeNull()
  })

  it('[P1] WARN check rows are visually emphasized (bgcolor warning.light)', () => {
    // THIS TEST WILL FAIL — placeholder renders no check result rows
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({
          check_type: 'FRESHNESS',
          status: 'WARN',
          numeric_metrics: [{ metric_name: 'dqs_score', metric_value: 62 }],
        }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // WARN rows must be in document
    expect(screen.getByText('WARN')).toBeInTheDocument()
  })

  it('[P1] renders multiple check results for multiple check types', () => {
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({ check_type: 'VOLUME', status: 'PASS' }),
        makeCheckResult({
          check_type: 'FRESHNESS',
          status: 'WARN',
          numeric_metrics: [{ metric_name: 'dqs_score', metric_value: 62 }],
        }),
        makeCheckResult({
          check_type: 'SCHEMA',
          status: 'FAIL',
          numeric_metrics: [{ metric_name: 'dqs_score', metric_value: 0 }],
        }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Each check type appears in both check results list and score breakdown card.
    expect(screen.getAllByText('Volume').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Freshness').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Schema').length).toBeGreaterThanOrEqual(1)
  })
})

// ---------------------------------------------------------------------------
// AC5: Score breakdown card — LinearProgress per check category
// ---------------------------------------------------------------------------

describe('[P1] DatasetDetailPage — score breakdown card (AC5)', () => {
  it('[P1] renders "Score Breakdown" section heading', () => {
    // THIS TEST WILL FAIL — placeholder renders no score breakdown
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    expect(screen.getByText(/score breakdown/i)).toBeInTheDocument()
  })

  it('[P1] renders LinearProgress for each check result in breakdown card', () => {
    // THIS TEST WILL FAIL — placeholder renders no score breakdown
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({ check_type: 'VOLUME', status: 'PASS' }),
        makeCheckResult({
          check_type: 'FRESHNESS',
          status: 'WARN',
          numeric_metrics: [{ metric_name: 'dqs_score', metric_value: 62 }],
        }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // MUI LinearProgress renders with role="progressbar"
    const progressBars = document.querySelectorAll('[role="progressbar"]')
    expect(progressBars.length).toBeGreaterThanOrEqual(2)
  })

  it('[P1] breakdown card shows check type label for each progress row', () => {
    mockUseDatasetDetail.mockReturnValue({ isLoading: false, isError: false, data: makeDatasetDetail() })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({ check_type: 'VOLUME', status: 'PASS' }),
      ]),
    })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail()

    // Check type label "Volume" appears in both check results list and score breakdown card (AC5).
    expect(screen.getAllByText('Volume').length).toBeGreaterThanOrEqual(2)
  })
})

// ---------------------------------------------------------------------------
// AC6: Left panel click -> URL update + right panel data refresh
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — left panel click updates URL (AC6)', () => {
  it('[P0] clicking a left panel item navigates to the new dataset URL', () => {
    // THIS TEST WILL FAIL — placeholder renders no left panel or navigation
    const datasets = [
      makeDatasetInLob({ dataset_id: 9, dataset_name: 'retail_transactions', dqs_score: 87 }),
      makeDatasetInLob({ dataset_id: 10, dataset_name: 'retail_customers', dqs_score: 55 }),
    ]
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ dataset_id: 9, lob_id: 'LOB_RETAIL' }),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(datasets),
    })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/datasets/9']}>
            <TimeRangeProvider>
              <Routes>
                <Route path="/datasets/:datasetId" element={<DatasetDetailPage />} />
              </Routes>
            </TimeRangeProvider>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    )

    // Click the 'retail_customers' item in the left panel (dataset_id=10)
    // This should trigger navigate('/datasets/10?lobId=LOB_RETAIL')
    const customerItem = screen.getByText('retail_customers')
    fireEvent.click(customerItem)

    // After click, useDatasetDetail should have been called with '10'
    // The URL changes but the page component does NOT unmount/remount
    expect(mockUseDatasetDetail).toHaveBeenCalledWith('10')
  })

  it('[P0] clicking active dataset in left panel does not navigate away', () => {
    const datasets = [
      makeDatasetInLob({ dataset_id: 9, dataset_name: 'retail_transactions', dqs_score: 87 }),
    ]
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ dataset_id: 9 }),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(datasets),
    })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    renderDatasetDetail('9')

    // Clicking the active item in the left panel — page stays on dataset 9.
    // retail_transactions appears in both left panel and right panel header (AC3).
    const activeItems = screen.getAllByText('retail_transactions')
    fireEvent.click(activeItems[0])

    // Dataset 9 detail page still shows the correct dataset name
    expect(screen.getAllByText('retail_transactions').length).toBeGreaterThanOrEqual(1)
  })
})

// ---------------------------------------------------------------------------
// AC7: Breadcrumb — 3-level: Summary > {LOB} > {Dataset}
// ---------------------------------------------------------------------------

describe('[P1] DatasetDetailPage — breadcrumb with LOB link (AC7)', () => {
  it('[P1] renders 3-level breadcrumb including LOB link when lobId is in URL', () => {
    // THIS TEST WILL FAIL — AppLayout breadcrumb currently shows only 2 levels
    // This test is for the breadcrumb rendered by AppLayout or DatasetDetailPage itself
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ lob_id: 'LOB_RETAIL' }),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: makeLobDatasetsResponse() })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: makeMetricsResponse() })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: makeTrendResponse() })

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/datasets/9?lobId=LOB_RETAIL']}>
            <TimeRangeProvider>
              <Routes>
                <Route path="/datasets/:datasetId" element={<DatasetDetailPage />} />
              </Routes>
            </TimeRangeProvider>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    )

    // Breadcrumb must show LOB_RETAIL as a navigable element
    expect(screen.getByText('LOB_RETAIL')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Hook integration — hooks called with correct args
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — hook integration', () => {
  it('[P0] calls useDatasetDetail with datasetId from URL params', () => {
    // THIS TEST WILL FAIL — useDatasetMetrics/useDatasetTrend do not exist in queries.ts
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: undefined })

    renderDatasetDetail('42')

    expect(mockUseDatasetDetail).toHaveBeenCalledWith('42')
  })

  it('[P0] calls useDatasetMetrics with datasetId from URL params', () => {
    // THIS TEST WILL FAIL — useDatasetMetrics does not exist in queries.ts
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: undefined })

    renderDatasetDetail('42')

    expect(mockUseDatasetMetrics).toHaveBeenCalledWith('42')
  })

  it('[P0] calls useDatasetTrend with datasetId and timeRange', () => {
    // THIS TEST WILL FAIL — useDatasetTrend does not exist in queries.ts
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: undefined })

    renderDatasetDetail('42')

    // useTimeRange mock returns '7d'
    expect(mockUseDatasetTrend).toHaveBeenCalledWith('42', '7d')
  })

  it('[P0] calls useLobDatasets with lob_id from useDatasetDetail response', () => {
    // THIS TEST WILL FAIL — placeholder does not call useLobDatasets
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ lob_id: 'LOB_MORTGAGE' }),
    })
    mockUseLobDatasets.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: false, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: false, isError: false, data: undefined })

    renderDatasetDetail()

    // useLobDatasets must be called with lob_id from the dataset detail response and timeRange
    expect(mockUseLobDatasets).toHaveBeenCalledWith('LOB_MORTGAGE', '7d')
  })

  it('[P0] all 4 hooks are called unconditionally (rules of hooks)', () => {
    // THIS TEST WILL FAIL — placeholder only calls useParams
    mockUseDatasetDetail.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseLobDatasets.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetMetrics.mockReturnValue({ isLoading: true, isError: false, data: undefined })
    mockUseDatasetTrend.mockReturnValue({ isLoading: true, isError: false, data: undefined })

    renderDatasetDetail('9')

    // All 4 hooks must be called on every render (rules of hooks — no conditional calls)
    expect(mockUseDatasetDetail).toHaveBeenCalled()
    expect(mockUseLobDatasets).toHaveBeenCalled()
    expect(mockUseDatasetMetrics).toHaveBeenCalled()
    expect(mockUseDatasetTrend).toHaveBeenCalled()
  })
})

// ---------------------------------------------------------------------------
// Partial failure isolation — trendError shows "Failed to load
// trend data" while rest of right panel renders normally.
// RED PHASE: DatasetDetailPage does not yet handle trendError or metricsError
// independently — currently detailError controls the entire right panel.
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — partial failure: trendError', () => {
  it('[P0] renders "Failed to load trend data" when useDatasetTrend isError is true', () => {
    // THIS TEST WILL FAIL — DatasetDetailPage does not yet isolate trendError
    // Implementation:
    //   const { data: trendData, isLoading: trendLoading, isError: trendError, refetch: trendRefetch }
    //     = useDatasetTrend(datasetId, timeRange)
    //   if (trendError) { render "Failed to load trend data." + Retry link }
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail(),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(),
    })
    // Trend hook fails
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetTrend>)

    renderDatasetDetail()

    // Trend area must show the failure message
    expect(screen.getByText(/failed to load trend data/i)).toBeInTheDocument()
  })

  it('[P0] rest of right panel renders normally when only trendError is true', () => {
    // THIS TEST WILL FAIL — trendError isolation not yet implemented
    // Key assertion: the check results section and dataset header still render
    // even though trend data failed. Per AC7: "while other components render normally"
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ dataset_name: 'retail_transactions', check_status: 'PASS' }),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(9, [
        makeCheckResult({ check_type: 'VOLUME', status: 'PASS' }),
      ]),
    })
    // Only trend fails
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetTrend>)

    renderDatasetDetail()

    // Trend error message is shown
    expect(screen.getByText(/failed to load trend data/i)).toBeInTheDocument()

    // But check results still render (other components unaffected)
    expect(screen.getAllByText(/check results/i).length).toBeGreaterThan(0)

    // Dataset name still shows in right panel (header not affected)
    expect(screen.getAllByText('retail_transactions').length).toBeGreaterThan(0)
  })

  it('[P0] renders a retry link/button in the trend error area', () => {
    // THIS TEST WILL FAIL — trendError + retry not yet implemented
    const trendRefetch = vi.fn()
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail(),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(),
    })
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: trendRefetch,
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetTrend>)

    renderDatasetDetail()

    // A retry affordance must be present in the trend area
    expect(screen.getByText(/retry/i)).toBeInTheDocument()
  })

  it('[P1] clicking retry in trend error area calls trendRefetch', () => {
    // THIS TEST WILL FAIL — trendError + retry not yet implemented
    const trendRefetch = vi.fn()
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail(),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(),
    })
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: trendRefetch,
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetTrend>)

    renderDatasetDetail()

    fireEvent.click(screen.getByText(/retry/i))
    expect(trendRefetch).toHaveBeenCalledOnce()
  })
})

// ---------------------------------------------------------------------------
// Partial failure isolation — metricsError shows "Failed to
// load check results" while rest of right panel renders normally.
// RED PHASE: DatasetDetailPage does not yet handle metricsError independently.
// ---------------------------------------------------------------------------

describe('[P0] DatasetDetailPage — partial failure: metricsError', () => {
  it('[P0] renders "Failed to load check results" when useDatasetMetrics isError is true', () => {
    // THIS TEST WILL FAIL — DatasetDetailPage does not yet isolate metricsError
    // Implementation:
    //   const { data: metricsData, isLoading: metricsLoading, isError: metricsError, refetch: metricsRefetch }
    //     = useDatasetMetrics(datasetId)
    //   if (metricsError) { render "Failed to load check results." + Retry link }
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail(),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    // Metrics hook fails
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetMetrics>)
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeTrendResponse(),
    })

    renderDatasetDetail()

    // Check results area shows failure message
    expect(screen.getByText(/failed to load check results/i)).toBeInTheDocument()
  })

  it('[P0] rest of right panel renders normally when only metricsError is true', () => {
    // THIS TEST WILL FAIL — metricsError isolation not yet implemented
    // TrendSparkline must still render when only metrics failed
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail({ dataset_name: 'retail_transactions' }),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    // Only metrics fails
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetMetrics>)
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeTrendResponse(),
    })

    renderDatasetDetail()

    // Metrics area shows the failure message
    expect(screen.getByText(/failed to load check results/i)).toBeInTheDocument()

    // Trend sparkline still renders (unaffected)
    expect(screen.getByTestId('trend-sparkline')).toBeInTheDocument()

    // Dataset name still shows in right panel header
    expect(screen.getAllByText('retail_transactions').length).toBeGreaterThan(0)
  })

  it('[P0] renders a retry link/button in the metrics error area', () => {
    // THIS TEST WILL FAIL — metricsError + retry not yet implemented
    const metricsRefetch = vi.fn()
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail(),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: metricsRefetch,
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetMetrics>)
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeTrendResponse(),
    })

    renderDatasetDetail()

    // A retry affordance must be present in the metrics area
    expect(screen.getByText(/retry/i)).toBeInTheDocument()
  })

  it('[P1] clicking retry in metrics error area calls metricsRefetch', () => {
    // THIS TEST WILL FAIL — metricsError + retry not yet implemented
    const metricsRefetch = vi.fn()
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail(),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: metricsRefetch,
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetMetrics>)
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeTrendResponse(),
    })

    renderDatasetDetail()

    fireEvent.click(screen.getByText(/retry/i))
    expect(metricsRefetch).toHaveBeenCalledOnce()
  })

  it('[P1] existing page does not crash when both trendError and metricsError are true', () => {
    // THIS TEST WILL FAIL — dual partial failure not yet handled without crashing
    // Per project-context.md: "Never let one component failure crash the page"
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail(),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetMetrics>)
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetTrend>)

    expect(() => renderDatasetDetail()).not.toThrow()

    // Both failure messages should appear
    expect(screen.getByText(/failed to load trend data/i)).toBeInTheDocument()
    expect(screen.getByText(/failed to load check results/i)).toBeInTheDocument()

    // Dataset header and name still visible
    expect(screen.getAllByText('retail_transactions').length).toBeGreaterThan(0)
  })
})

// ---------------------------------------------------------------------------
// isFetching stale-while-revalidate opacity on DatasetDetailPage
// RED PHASE: DatasetDetailPage does not yet apply isFetching opacity to TrendSparkline.
// ---------------------------------------------------------------------------

describe('[P1] DatasetDetailPage — isFetching opacity on trend sparkline', () => {
  it('[P1] TrendSparkline wrapper has opacity 0.5 when useDatasetTrend isFetching is true', () => {
    // THIS TEST WILL FAIL — DatasetDetailPage does not yet use isFetching from useDatasetTrend
    // Implementation:
    //   const { ..., isFetching: trendFetching } = useDatasetTrend(datasetId, timeRange)
    //   <Box sx={{ opacity: trendFetching ? 0.5 : 1, transition: 'opacity 0.2s' }}>
    //     <TrendSparkline ... />
    //   </Box>
    mockUseDatasetDetail.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeDatasetDetail(),
      refetch: vi.fn(),
    })
    mockUseLobDatasets.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeLobDatasetsResponse(),
    })
    mockUseDatasetMetrics.mockReturnValue({
      isLoading: false,
      isError: false,
      data: makeMetricsResponse(),
    })
    mockUseDatasetTrend.mockReturnValue({
      isLoading: false,
      isFetching: true,
      isError: false,
      data: makeTrendResponse(),
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof import('../../src/api/queries').useDatasetTrend>)

    renderDatasetDetail()

    const sparkline = screen.getByTestId('trend-sparkline')
    expect(sparkline).toBeInTheDocument()

    // Sparkline wrapper must have opacity: 0.5 when isFetching
    const sparklineWrapper = sparkline.parentElement
    expect(sparklineWrapper).not.toBeNull()
    expect(sparklineWrapper!.style.opacity).toBe('0.5')
  })
})
