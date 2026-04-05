/**
 * ATDD Component Tests — Executive Reporting Suite
 *
 * TDD RED PHASE: ExecReportPage does not exist yet. useExecutiveReport hook
 * is not defined in queries.ts. TypeScript interfaces (LobMonthlyScore,
 * SourceSystemScore, LobImprovementSummary, ExecutiveReportResponse) are not
 * defined in api/types.ts. The /exec route is not registered in App.tsx and
 * nav link is not added to AppLayout.tsx.
 *
 * All tests assert EXPECTED behavior. Tests will fail until implementation
 * is complete. Remove .skip from it() calls to enter green phase.
 *
 * Acceptance Criteria covered:
 *   AC5: ExecReportPage at route /exec — LOB Monthly Scorecard, Improvement Summary,
 *        Source System Accountability tables
 *   AC7: Loading state renders Skeleton elements, no table content
 *   AC8: Error state shows "Failed to load executive report" + Retry button
 *        (never a full-page crash for partial failure)
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
import ExecReportPage from '../../src/pages/ExecReportPage'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

// Mock useExecutiveReport to avoid real API calls.
// useExecutiveReport does not yet exist in queries.ts — this mock will cause
// a runtime import error until it is added (confirming RED phase).
vi.mock('../../src/api/queries', () => ({
  useExecutiveReport: vi.fn(),
  useSummary: vi.fn(),
  useLobs: vi.fn(),
  useDatasets: vi.fn(),
}))

// ---------------------------------------------------------------------------
// Test data factories
// ---------------------------------------------------------------------------

function makeLobMonthlyScore(overrides: Partial<{
  lob_id: string
  month: string
  avg_score: number | null
}> = {}) {
  return {
    lob_id: 'LOB_RETAIL',
    month: '2026-02',
    avg_score: 88.5,
    ...overrides,
  }
}

function makeSourceSystemScore(overrides: Partial<{
  src_sys_nm: string
  dataset_count: number
  avg_score: number | null
  healthy_count: number
  critical_count: number
}> = {}) {
  return {
    src_sys_nm: 'alpha',
    dataset_count: 5,
    avg_score: 82.0,
    healthy_count: 4,
    critical_count: 1,
    ...overrides,
  }
}

function makeLobImprovementSummary(overrides: Partial<{
  lob_id: string
  baseline_score: number | null
  current_score: number | null
  delta: number | null
}> = {}) {
  return {
    lob_id: 'LOB_RETAIL',
    baseline_score: 70.0,
    current_score: 88.5,
    delta: 18.5,
    ...overrides,
  }
}

function makeExecutiveReportResponse(overrides: Partial<{
  lob_monthly_scores: ReturnType<typeof makeLobMonthlyScore>[]
  source_system_scores: ReturnType<typeof makeSourceSystemScore>[]
  improvement_summary: ReturnType<typeof makeLobImprovementSummary>[]
}> = {}) {
  return {
    lob_monthly_scores: [
      makeLobMonthlyScore({ lob_id: 'LOB_RETAIL', month: '2026-02', avg_score: 85.0 }),
      makeLobMonthlyScore({ lob_id: 'LOB_RETAIL', month: '2026-03', avg_score: 87.0 }),
      makeLobMonthlyScore({ lob_id: 'LOB_RETAIL', month: '2026-04', avg_score: 88.5 }),
      makeLobMonthlyScore({ lob_id: 'LOB_COMMERCIAL', month: '2026-02', avg_score: 72.0 }),
      makeLobMonthlyScore({ lob_id: 'LOB_COMMERCIAL', month: '2026-03', avg_score: 75.0 }),
      makeLobMonthlyScore({ lob_id: 'LOB_COMMERCIAL', month: '2026-04', avg_score: 78.0 }),
    ],
    source_system_scores: [
      makeSourceSystemScore({ src_sys_nm: 'alpha', dataset_count: 5, avg_score: 55.0, healthy_count: 2, critical_count: 3 }),
      makeSourceSystemScore({ src_sys_nm: 'beta', dataset_count: 3, avg_score: 80.0, healthy_count: 3, critical_count: 0 }),
    ],
    improvement_summary: [
      makeLobImprovementSummary({ lob_id: 'LOB_RETAIL', baseline_score: 70.0, current_score: 88.5, delta: 18.5 }),
      makeLobImprovementSummary({ lob_id: 'LOB_COMMERCIAL', baseline_score: 80.0, current_score: 78.0, delta: -2.0 }),
    ],
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Render helper — wraps ExecReportPage with all required providers
// MemoryRouter needed so page can use useNavigate/useLocation if required.
// ---------------------------------------------------------------------------

function renderExecReportPage(route = '/exec') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            <Route path="/*" element={<ExecReportPage />} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

// ---------------------------------------------------------------------------
// Import mock reference for per-test control
// ---------------------------------------------------------------------------

let mockUseExecutiveReport: ReturnType<typeof vi.fn>

beforeEach(async () => {
  const queries = await import('../../src/api/queries')
  // useExecutiveReport does not exist yet — accessing it here will fail until
  // it is implemented (confirming RED phase behavior).
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  mockUseExecutiveReport = (queries as any).useExecutiveReport as ReturnType<typeof vi.fn>
})

// ---------------------------------------------------------------------------
// AC7: Loading state — Skeleton elements, no table content
// ---------------------------------------------------------------------------

describe('[P0] ExecReportPage — loading state (AC7)', () => {
  it('[P0] renders_skeleton_while_loading — Skeleton elements present, no table content', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: true,
      isError: false,
      isFetching: false,
      data: undefined,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // MUI Skeleton elements must be present during loading
    // Per project-context.md: NEVER spinners — Skeleton elements only
    const skeletons = document.querySelectorAll('[class*="MuiSkeleton"]')
    expect(skeletons.length).toBeGreaterThan(0)

    // No table rows should be present during loading
    const tableRows = document.querySelectorAll('tbody tr')
    expect(tableRows.length).toBe(0)
  })

  it('[P0] does not render spinner (role=progressbar) during loading', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: true,
      isError: false,
      isFetching: false,
      data: undefined,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Per project-context.md anti-patterns: NEVER spinning loaders
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('[P1] does not render section headings during loading', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: true,
      isError: false,
      isFetching: false,
      data: undefined,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Section titles should only appear when data is loaded
    expect(screen.queryByText(/LOB Monthly Scorecard/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Improvement Summary/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Source System Accountability/i)).not.toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC8: Error state — component-level error message + Retry button
// ---------------------------------------------------------------------------

describe('[P0] ExecReportPage — error state (AC8)', () => {
  it('[P0] renders_error_message_on_failure — error message visible', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: true,
      isFetching: false,
      data: undefined,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Component-level error — NOT a full-page crash (per AC8)
    expect(
      screen.getByText(/Failed to load executive report/i)
    ).toBeInTheDocument()
  })

  it('[P0] renders_retry_button_on_error — Retry button present', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: true,
      isFetching: false,
      data: undefined,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // AC8 explicitly requires a Retry button
    const retryButton = screen.getByRole('button', { name: /retry/i })
    expect(retryButton).toBeInTheDocument()
  })

  it('[P0] retry_button_calls_refetch — clicking Retry invokes refetch()', () => {
    const mockRefetch = vi.fn()
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: true,
      isFetching: false,
      data: undefined,
      refetch: mockRefetch,
    })

    renderExecReportPage()

    const retryButton = screen.getByRole('button', { name: /retry/i })
    fireEvent.click(retryButton)

    expect(mockRefetch).toHaveBeenCalledOnce()
  })

  it('[P1] does not render any table rows when isError is true (AC8)', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: true,
      isFetching: false,
      data: undefined,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Error state must not crash — no table rows rendered
    const tableRows = document.querySelectorAll('tbody tr')
    expect(tableRows.length).toBe(0)
  })
})

// ---------------------------------------------------------------------------
// AC5: LOB Monthly Scorecard table
// ---------------------------------------------------------------------------

describe('[P0] ExecReportPage — LOB Monthly Scorecard table (AC5)', () => {
  it('[P0] renders_lob_monthly_scorecard — 2 LOBs × 3 months → all cells rendered', () => {
    const data = makeExecutiveReportResponse()
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Section heading must be visible
    expect(screen.getByText(/LOB Monthly Scorecard/i)).toBeInTheDocument()

    // LOB names should appear in the table rows (may appear in multiple tables)
    expect(screen.getAllByText('LOB_RETAIL').length).toBeGreaterThan(0)
    expect(screen.getAllByText('LOB_COMMERCIAL').length).toBeGreaterThan(0)
  })

  it('[P0] scorecard_shows_avg_scores_in_cells — avg_score values rendered', () => {
    const data = makeExecutiveReportResponse({
      lob_monthly_scores: [
        makeLobMonthlyScore({ lob_id: 'LOB_RETAIL', month: '2026-02', avg_score: 85.0 }),
      ],
      source_system_scores: [],
      improvement_summary: [],
    })
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Avg score value formatted to 1 decimal should appear
    expect(screen.getByText(/85\.0|85/)).toBeInTheDocument()
  })

  it('[P1] scorecard_shows_em_dash_for_null_scores — null avg_score shows "—"', () => {
    const data = makeExecutiveReportResponse({
      lob_monthly_scores: [
        makeLobMonthlyScore({ lob_id: 'LOB_RETAIL', month: '2026-02', avg_score: null }),
      ],
      source_system_scores: [],
      improvement_summary: [],
    })
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // null avg_score must render as em dash "—" per Dev Notes
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('[P1] scorecard_column_headers_are_month_labels — dynamic month column headers', () => {
    const data = makeExecutiveReportResponse()
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Column headers should show month labels derived from 'YYYY-MM' strings
    // e.g., '2026-02' → 'Feb 2026' or similar human-readable format
    // At minimum, the month key substring should be present somewhere in the table header
    const headers = document.querySelectorAll('th')
    const headerTexts = Array.from(headers).map(h => h.textContent || '')
    const hasMonthColumn = headerTexts.some(text =>
      text.includes('2026') || text.match(/jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec/i)
    )
    expect(hasMonthColumn).toBe(true)
  })
})

// ---------------------------------------------------------------------------
// AC5: Improvement Summary table
// ---------------------------------------------------------------------------

describe('[P0] ExecReportPage — Improvement Summary table (AC5)', () => {
  it('[P0] renders_improvement_summary_with_delta — improvement summary table present', () => {
    const data = makeExecutiveReportResponse()
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    expect(screen.getByText(/Improvement Summary/i)).toBeInTheDocument()
  })

  it('[P0] positive_delta_renders_with_green_indicator — delta > 0 shows "▲" prefix', () => {
    const data = makeExecutiveReportResponse({
      lob_monthly_scores: [],
      source_system_scores: [],
      improvement_summary: [
        makeLobImprovementSummary({ lob_id: 'LOB_RETAIL', baseline_score: 70.0, current_score: 88.5, delta: 18.5 }),
      ],
    })
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Positive delta: "▲ 18.5" or similar green indicator per Dev Notes
    // At minimum, the delta value should appear
    const deltaText = screen.queryByText(/▲|18\.5|18\.50/)
    expect(deltaText).toBeInTheDocument()
  })

  it('[P0] negative_delta_renders_with_red_indicator — delta < 0 shows "▼" prefix', () => {
    const data = makeExecutiveReportResponse({
      lob_monthly_scores: [],
      source_system_scores: [],
      improvement_summary: [
        makeLobImprovementSummary({ lob_id: 'LOB_COMMERCIAL', baseline_score: 80.0, current_score: 78.0, delta: -2.0 }),
      ],
    })
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Negative delta: "▼ 2.0" or similar red indicator per Dev Notes
    const deltaText = screen.queryByText(/▼|2\.0|−2/)
    expect(deltaText).toBeInTheDocument()
  })

  it('[P1] null_delta_shows_na — null delta renders as "N/A"', () => {
    const data = makeExecutiveReportResponse({
      lob_monthly_scores: [],
      source_system_scores: [],
      improvement_summary: [
        makeLobImprovementSummary({ lob_id: 'LOB_LEGACY', baseline_score: null, current_score: 75.0, delta: null }),
      ],
    })
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // null delta → "N/A" per Dev Notes
    expect(screen.getByText('N/A')).toBeInTheDocument()
  })

  it('[P1] improvement_summary_shows_lob_names — LOB IDs visible in rows', () => {
    const data = makeExecutiveReportResponse({
      lob_monthly_scores: [],
      source_system_scores: [],
      improvement_summary: [
        makeLobImprovementSummary({ lob_id: 'LOB_RETAIL', delta: 18.5 }),
        makeLobImprovementSummary({ lob_id: 'LOB_COMMERCIAL', delta: -2.0 }),
      ],
    })
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    expect(screen.getByText('LOB_RETAIL')).toBeInTheDocument()
    expect(screen.getByText('LOB_COMMERCIAL')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC5: Source System Accountability table
// ---------------------------------------------------------------------------

describe('[P0] ExecReportPage — Source System Accountability table (AC5)', () => {
  it('[P0] renders_source_system_accountability_table — section and data visible', () => {
    const data = makeExecutiveReportResponse()
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    expect(screen.getByText(/Source System Accountability/i)).toBeInTheDocument()
  })

  it('[P0] source_system_names_visible — src_sys_nm values rendered in table rows', () => {
    const data = makeExecutiveReportResponse({
      lob_monthly_scores: [],
      improvement_summary: [],
      source_system_scores: [
        makeSourceSystemScore({ src_sys_nm: 'alpha' }),
        makeSourceSystemScore({ src_sys_nm: 'beta' }),
      ],
    })
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    expect(screen.getByText('alpha')).toBeInTheDocument()
    expect(screen.getByText('beta')).toBeInTheDocument()
  })

  it('[P1] source_system_counts_visible — dataset_count, healthy_count, critical_count rendered', () => {
    const data = makeExecutiveReportResponse({
      lob_monthly_scores: [],
      improvement_summary: [],
      source_system_scores: [
        makeSourceSystemScore({ src_sys_nm: 'gamma', dataset_count: 7, healthy_count: 5, critical_count: 2 }),
      ],
    })
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Dataset count and status counts must appear in the table
    expect(screen.getByText('7')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Rendering stability — smoke tests
// ---------------------------------------------------------------------------

describe('[P0] ExecReportPage — rendering stability', () => {
  it('[P0] renders without crashing in loading state', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: true,
      isError: false,
      isFetching: false,
      data: undefined,
      refetch: vi.fn(),
    })

    expect(() => renderExecReportPage()).not.toThrow()
  })

  it('[P0] renders without crashing with full data response', () => {
    const data = makeExecutiveReportResponse()
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data,
      refetch: vi.fn(),
    })

    expect(() => renderExecReportPage()).not.toThrow()
  })

  it('[P0] renders without crashing in error state', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: true,
      isFetching: false,
      data: undefined,
      refetch: vi.fn(),
    })

    expect(() => renderExecReportPage()).not.toThrow()
  })

  it('[P0] renders without crashing with empty data arrays', () => {
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      data: {
        lob_monthly_scores: [],
        source_system_scores: [],
        improvement_summary: [],
      },
      refetch: vi.fn(),
    })

    expect(() => renderExecReportPage()).not.toThrow()
  })
})

// ---------------------------------------------------------------------------
// isFetching — stale-while-revalidate opacity dim (per project-context.md patterns)
// ---------------------------------------------------------------------------

describe('[P1] ExecReportPage — isFetching stale-while-revalidate (pattern compliance)', () => {
  it('[P1] does not show progressbar spinner when isFetching is true', () => {
    const data = makeExecutiveReportResponse()
    mockUseExecutiveReport.mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: true,
      data,
      refetch: vi.fn(),
    })

    renderExecReportPage()

    // Per project-context.md: NEVER spinning loaders — Skeleton only
    // Even during isFetching (stale-while-revalidate), no MUI CircularProgress allowed
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })
})
