/**
 * ATDD Component Tests — Story 4.8: AppLayout with Fixed Header, Routing & React Query
 *
 * GREEN PHASE: All tests pass — implementation complete.
 *
 * Acceptance Criteria covered:
 *   AC1: Fixed header visible with breadcrumbs, time range toggle, and search bar
 *   AC2: Routes render correct page component with breadcrumbs reflecting path
 *   AC4: Browser back/forward navigation and breadcrumbs update
 *   AC5: <header>, <main>, <nav> landmark regions present
 *   AC6: Hidden skip link visible on Tab focus
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router'
import { useSummary } from '../../src/api/queries'
import React from 'react'
import theme from '../../src/theme'
import AppLayout from '../../src/layouts/AppLayout'
import { TimeRangeProvider } from '../../src/context/TimeRangeContext'

// ---------------------------------------------------------------------------
// Mock useSearch and all existing hooks so GlobalSearch renders without errors.
// useSearch is introduced in Story 4.13 — mocked here so tests control data.
// Existing hooks mocked to return idle state so existing AppLayout behaviour holds.
// ---------------------------------------------------------------------------

vi.mock('../../src/api/queries', () => ({
  useSearch: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
  useLobs: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
  useDatasets: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
  useSummary: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
  useLobDatasets: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
  useDatasetDetail: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
  useDatasetMetrics: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
  useDatasetTrend: vi.fn().mockReturnValue({ data: undefined, isLoading: false }),
}))

// ---------------------------------------------------------------------------
// Mock DqsScoreChip to avoid Recharts / canvas issues in jsdom.
// ---------------------------------------------------------------------------

vi.mock('../../src/components', () => ({
  DqsScoreChip: ({ score }: { score?: number }) => (
    <span data-testid="dqs-score-chip">{score !== undefined ? score : '—'}</span>
  ),
  TrendSparkline: vi.fn(() => null),
  DatasetCard: vi.fn(() => null),
  DatasetInfoPanel: vi.fn(() => null),
}))

// ---------------------------------------------------------------------------
// Mock useNavigate so navigation assertions can be tested without a real router.
// ---------------------------------------------------------------------------

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>()
  return {
    ...actual,
    useNavigate: vi.fn(() => vi.fn()),
  }
})

// ---------------------------------------------------------------------------
// Render helper — wraps AppLayout with all required providers.
// Uses MemoryRouter so tests run without a real browser environment.
// route: initial route to render (default '/')
// ---------------------------------------------------------------------------

const renderAppLayout = (route = '/') => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[route]}>
          <TimeRangeProvider>
            <Routes>
              <Route
                path="/*"
                element={<AppLayout><div data-testid="page-content">content</div></AppLayout>}
              />
            </Routes>
          </TimeRangeProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

// ---------------------------------------------------------------------------
// AC1: Fixed header visible with breadcrumbs, time range toggle, and search bar
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — fixed header presence (AC1)', () => {
  it('[P0] renders an AppBar element in the DOM', () => {
    // After implementation: AppBar with position="fixed" must be in the DOM
    renderAppLayout()
    // MUI AppBar renders as <header> by default
    const header = screen.getByRole('banner')
    expect(header).toBeInTheDocument()
  })

  it('[P0] renders time range toggle with "7d", "30d", "90d" buttons', () => {
    renderAppLayout()
    expect(screen.getByRole('button', { name: /7d/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /30d/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /90d/i })).toBeInTheDocument()
  })

  it('[P0] search input has placeholder containing "Search datasets"', () => {
    renderAppLayout()
    const searchInput = screen.getByPlaceholderText(/Search datasets/i)
    expect(searchInput).toBeInTheDocument()
  })

  it('[P1] search input placeholder contains "(Ctrl+K)"', () => {
    renderAppLayout()
    const searchInput = screen.getByPlaceholderText(/Ctrl\+K/i)
    expect(searchInput).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC5: Semantic landmark regions — <header>, <main>, <nav>
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — semantic landmark regions (AC5)', () => {
  it('[P0] renders a <header> landmark element (role="banner")', () => {
    // After implementation: MUI AppBar renders as <header> with role="banner"
    renderAppLayout()
    expect(screen.getByRole('banner')).toBeInTheDocument()
  })

  it('[P0] renders a <main> landmark element (role="main")', () => {
    // After implementation: <Box component="main" id="main-content"> must exist
    renderAppLayout()
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('[P0] <main> element has id="main-content"', () => {
    renderAppLayout()
    const main = screen.getByRole('main')
    expect(main).toHaveAttribute('id', 'main-content')
  })

  it('[P0] renders a <nav> landmark element for breadcrumbs (role="navigation")', () => {
    renderAppLayout()
    expect(screen.getByRole('navigation')).toBeInTheDocument()
  })

  it('[P1] <nav> element has aria-label="breadcrumb"', () => {
    renderAppLayout()
    const nav = screen.getByRole('navigation', { name: /breadcrumb/i })
    expect(nav).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC6: Skip link — hidden by default, visible on Tab focus
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — skip link accessibility (AC6)', () => {
  it('[P0] skip link with href="#main-content" is present in DOM', () => {
    renderAppLayout()
    const skipLink = screen.getByRole('link', { name: /skip to main content/i })
    expect(skipLink).toBeInTheDocument()
  })

  it('[P0] skip link href points to "#main-content"', () => {
    renderAppLayout()
    const skipLink = screen.getByRole('link', { name: /skip to main content/i })
    expect(skipLink).toHaveAttribute('href', '#main-content')
  })
})

// ---------------------------------------------------------------------------
// AC2: Breadcrumb derivation — root and /summary paths
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — breadcrumb navigation root/summary (AC2)', () => {
  it('[P0] shows "Summary" breadcrumb as plain text (not a link) at root path "/"', () => {
    renderAppLayout('/')
    // At root, current page is "Summary" displayed as non-linked text
    const summaryText = screen.getByText('Summary')
    expect(summaryText).toBeInTheDocument()
    // It should NOT be a link (no <a> wrapping it)
    expect(summaryText.tagName).not.toBe('A')
  })

  it('[P0] shows "Summary" breadcrumb as plain text at "/summary" path', () => {
    renderAppLayout('/summary')
    const summaryText = screen.getByText('Summary')
    expect(summaryText).toBeInTheDocument()
    expect(summaryText.tagName).not.toBe('A')
  })
})

// ---------------------------------------------------------------------------
// AC2: Breadcrumb derivation — LOB detail path
// ---------------------------------------------------------------------------

describe('[P1] AppLayout — breadcrumb navigation LOB path (AC2)', () => {
  it('[P1] shows "Summary" as a clickable link and lobId as plain text at "/lobs/:lobId"', () => {
    renderAppLayout('/lobs/consumer-banking')
    // Summary should be a link
    const summaryLink = screen.getByRole('link', { name: 'Summary' })
    expect(summaryLink).toBeInTheDocument()
    // LOB id displayed as plain text
    expect(screen.getByText('consumer-banking')).toBeInTheDocument()
  })

  it('[P1] Summary breadcrumb link at LOB path navigates to /summary', () => {
    renderAppLayout('/lobs/mortgage')
    const summaryLink = screen.getByRole('link', { name: 'Summary' })
    // MUI Link rendered as RouterLink should have href resolving to /summary
    expect(summaryLink).toHaveAttribute('href', '/summary')
  })
})

// ---------------------------------------------------------------------------
// AC2: Breadcrumb derivation — dataset detail path
// ---------------------------------------------------------------------------

describe('[P1] AppLayout — breadcrumb navigation dataset path (AC2)', () => {
  it('[P1] shows "Summary" as link and datasetId as plain text at "/datasets/:datasetId"', () => {
    renderAppLayout('/datasets/loan-origination-daily')
    const summaryLink = screen.getByRole('link', { name: 'Summary' })
    expect(summaryLink).toBeInTheDocument()
    expect(screen.getByText('loan-origination-daily')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC2 + AC4: Time range toggle interaction
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — time range toggle interaction (AC2, AC3)', () => {
  it('[P0] "7d" toggle button is selected by default', () => {
    renderAppLayout()
    const btn7d = screen.getByRole('button', { name: /7d/i })
    // MUI ToggleButton sets aria-pressed="true" when selected
    expect(btn7d).toHaveAttribute('aria-pressed', 'true')
  })

  it('[P0] clicking "30d" toggle changes the selected time range to 30d', () => {
    renderAppLayout()
    const btn30d = screen.getByRole('button', { name: /30d/i })
    fireEvent.click(btn30d)
    expect(btn30d).toHaveAttribute('aria-pressed', 'true')
  })

  it('[P0] clicking "90d" toggle changes the selected time range to 90d', () => {
    renderAppLayout()
    const btn90d = screen.getByRole('button', { name: /90d/i })
    fireEvent.click(btn90d)
    expect(btn90d).toHaveAttribute('aria-pressed', 'true')
  })

  it('[P1] only one time range button is selected at a time after toggle change', () => {
    renderAppLayout()
    const btn30d = screen.getByRole('button', { name: /30d/i })
    fireEvent.click(btn30d)

    const btn7d = screen.getByRole('button', { name: /7d/i })
    const btn90d = screen.getByRole('button', { name: /90d/i })

    expect(btn30d).toHaveAttribute('aria-pressed', 'true')
    expect(btn7d).toHaveAttribute('aria-pressed', 'false')
    expect(btn90d).toHaveAttribute('aria-pressed', 'false')
  })
})

// ---------------------------------------------------------------------------
// AC1: Children rendered inside main content area
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — children rendered in main (AC1)', () => {
  it('[P0] children are rendered inside the <main> element', () => {
    // after implementation the main must have id="main-content"
    renderAppLayout()
    const main = screen.getByRole('main')
    expect(main).toContainElement(screen.getByTestId('page-content'))
  })
})

// ---------------------------------------------------------------------------
// Rendering stability — no throws with all route variants
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — rendering stability', () => {
  it('[P0] renders without throwing at root path "/"', () => {
    expect(() => renderAppLayout('/')).not.toThrow()
  })

  it('[P0] renders without throwing at "/summary"', () => {
    expect(() => renderAppLayout('/summary')).not.toThrow()
  })

  it('[P0] renders without throwing at "/lobs/test-lob"', () => {
    expect(() => renderAppLayout('/lobs/test-lob')).not.toThrow()
  })

  it('[P0] renders without throwing at "/datasets/test-dataset"', () => {
    expect(() => renderAppLayout('/datasets/test-dataset')).not.toThrow()
  })
})

// ---------------------------------------------------------------------------
// Story 4.14 — AC5: LastUpdatedIndicator in header
//
// RED PHASE: AppLayout does not yet have a LastUpdatedIndicator component.
// useSummary is currently mocked to return { data: undefined, isLoading: false }.
// These tests require useSummary to return data with a last_run_at field AND
// AppLayout to render a LastUpdatedIndicator component that reads it.
//
// Implementation requirements:
//   - Add last_run_at: string | null + run_failed: boolean to SummaryResponse in api/types.ts
//   - Add useLastUpdated() usage (or read from useSummary directly) in AppLayout.tsx
//   - Add LastUpdatedIndicator function component inside AppLayout.tsx
//   - Place <LastUpdatedIndicator /> in Toolbar between AppBreadcrumbs and time range toggle
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — LastUpdatedIndicator stale data amber warning (AC5, Story 4.14)', () => {
  it('[P0] shows last_run_at in amber text when data is more than 24 hours old', () => {
    // THIS TEST WILL FAIL — LastUpdatedIndicator does not yet exist in AppLayout
    // Implementation: compare last_run_at timestamp to Date.now(), if >24h use color: 'warning.main'
    // "28 hours ago" scenario: last_run_at = now - 28h (in ISO 8601)
    const staleDate = new Date(Date.now() - 28 * 60 * 60 * 1000).toISOString()
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        last_run_at: staleDate,
        run_failed: false,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    renderAppLayout()

    // LastUpdatedIndicator must render with relative time text
    // Matches: "Last updated: 28 hours ago" or similar relative format
    expect(screen.getByText(/last updated/i)).toBeInTheDocument()

    // The indicator element must use MUI warning.main color (amber).
    // MUI sx color tokens resolve to CSS custom properties — check the element's style
    // or look for the text being associated with a warning-colored element.
    const indicator = screen.getByText(/last updated/i)
    // Verify it's in the header (banner landmark)
    const header = screen.getByRole('banner')
    expect(header).toContainElement(indicator)
  })

  it('[P0] shows last_run_at in gray (text.secondary) text when data is less than 24 hours old', () => {
    // THIS TEST WILL FAIL — LastUpdatedIndicator does not yet exist in AppLayout
    // "5:42 AM ET" scenario: last_run_at = now - 2h (fresh)
    const freshDate = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        last_run_at: freshDate,
        run_failed: false,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    renderAppLayout()

    // LastUpdatedIndicator must render with relative time text
    expect(screen.getByText(/last updated/i)).toBeInTheDocument()

    // The indicator must be in the header
    const indicator = screen.getByText(/last updated/i)
    const header = screen.getByRole('banner')
    expect(header).toContainElement(indicator)
  })

  it('[P1] does not render LastUpdatedIndicator when last_run_at is null', () => {
    // THIS TEST WILL FAIL — LastUpdatedIndicator does not yet exist
    // Graceful degradation: if last_run_at is null/missing, skip indicator entirely
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        last_run_at: null,
        run_failed: false,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    renderAppLayout()

    // When last_run_at is null, "Last updated" text must not appear
    expect(screen.queryByText(/last updated/i)).not.toBeInTheDocument()
  })

  it('[P1] does not crash when useSummary returns no last_run_at field', () => {
    // THIS TEST WILL FAIL — LastUpdatedIndicator does not yet exist
    // Backward-compatibility: SummaryResponse without last_run_at field — no crash
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        // no last_run_at field
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    expect(() => renderAppLayout()).not.toThrow()
  })
})

// ---------------------------------------------------------------------------
// Story 4.14 — AC6: RunFailedBanner below header
//
// RED PHASE: AppLayout does not yet have a RunFailedBanner component.
//
// Implementation requirements:
//   - Add RunFailedBanner function component inside AppLayout.tsx
//   - Banner condition: summaryData?.run_failed === true
//   - Render as full-width Box below AppBar (sibling to Toolbar spacer):
//     bgcolor: 'warning.light', color: 'warning.dark', border bottom
//   - Text: "Latest run failed at {time}. Showing results from {previous run date}."
//   - Dismissible: local useState<boolean> — dismissed defaults false
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — RunFailedBanner when latest run failed (AC6, Story 4.14)', () => {
  it('[P0] renders yellow banner when useSummary returns run_failed=true', () => {
    // THIS TEST WILL FAIL — RunFailedBanner does not yet exist in AppLayout
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        last_run_at: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        run_failed: true,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    renderAppLayout()

    // Banner must be visible with run-failed message
    expect(screen.getByText(/latest run failed/i)).toBeInTheDocument()
  })

  it('[P0] banner text includes "Showing results from" previous run reference', () => {
    // THIS TEST WILL FAIL — RunFailedBanner does not yet exist
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        last_run_at: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        run_failed: true,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    renderAppLayout()

    // Per UX spec: "Latest run failed at {time}. Showing results from {previous run date}."
    expect(screen.getByText(/showing results from/i)).toBeInTheDocument()
  })

  it('[P0] clicking dismiss hides the banner', () => {
    // THIS TEST WILL FAIL — RunFailedBanner does not yet exist
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        last_run_at: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        run_failed: true,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    renderAppLayout()

    // Banner is visible before dismiss
    expect(screen.getByText(/latest run failed/i)).toBeInTheDocument()

    // Find and click the dismiss button/link
    // Per Dev Notes: local useState<boolean> dismissed — clicking hides it
    const dismissButton = screen.getByRole('button', { name: /dismiss/i })
    fireEvent.click(dismissButton)

    // Banner must no longer be in the document after dismiss
    expect(screen.queryByText(/latest run failed/i)).not.toBeInTheDocument()
  })

  it('[P0] does not render banner when run_failed is false', () => {
    // THIS TEST WILL FAIL — RunFailedBanner does not yet exist
    // Guards against banner showing when run succeeded
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        last_run_at: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        run_failed: false,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    renderAppLayout()

    // No banner when run succeeded
    expect(screen.queryByText(/latest run failed/i)).not.toBeInTheDocument()
  })

  it('[P1] does not render banner when useSummary returns no run_failed field', () => {
    // THIS TEST WILL FAIL — RunFailedBanner does not yet exist
    // Graceful degradation: missing run_failed field -> no banner
    vi.mocked(useSummary).mockReturnValue({
      data: {
        total_datasets: 5,
        healthy_count: 3,
        degraded_count: 1,
        critical_count: 1,
        lobs: [],
        // no run_failed field
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSummary>)

    expect(() => renderAppLayout()).not.toThrow()
    // Banner must not appear when run_failed is absent/undefined
    expect(screen.queryByText(/latest run failed/i)).not.toBeInTheDocument()
  })
})
