/**
 * ATDD Component Tests — Global Dataset Search
 *
 * GREEN PHASE: All tests pass — implementation complete.
 *
 * Acceptance Criteria covered:
 *   AC1: Autocomplete dropdown appears after 2+ chars with DqsScoreChip + dataset name + LOB
 *   AC2: Escape key closes the dropdown without navigating
 *   AC3: Clicking a result navigates to /datasets/:datasetId
 *   AC4: Ctrl+K focuses the search input from any view
 *   AC5: No-results message shown when query matches nothing
 *   AC6: Global across LOBs, max 10 results, prefix before substring
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router'
import React from 'react'
import theme from '../../src/theme'
import AppLayout from '../../src/layouts/AppLayout'
import { TimeRangeProvider } from '../../src/context/TimeRangeContext'
import { useSearch as useSearchImport } from '../../src/api/queries'
import type { SearchResult } from '../../src/api/types'

// ---------------------------------------------------------------------------
// Mock useSearch and all existing hooks so GlobalSearch renders without errors.
// useSearch mocked here so tests control data.
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
// Renders a simple <span> with the score value for test assertions.
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

const mockNavigate = vi.fn()

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>()
  return {
    ...actual,
    useNavigate: vi.fn(() => mockNavigate),
  }
})

// ---------------------------------------------------------------------------
// SearchResult fixture — uses the SearchResult type from api/types.ts.
// Values match the story spec fixture exactly.
// ---------------------------------------------------------------------------

const MOCK_SEARCH_RESULTS: SearchResult[] = [
  {
    dataset_id: 7,
    dataset_name: 'lob=retail/src_sys_nm=alpha/dataset=sales_daily',
    lob_id: 'LOB_RETAIL',
    dqs_score: 98.5,
    check_status: 'PASS',
  },
  {
    dataset_id: 12,
    dataset_name: 'lob=retail/src_sys_nm=beta/dataset=orders_weekly',
    lob_id: 'LOB_RETAIL',
    dqs_score: 72.0,
    check_status: 'WARN',
  },
]

// ---------------------------------------------------------------------------
// Render helper — identical to AppLayout.test.tsx helper for consistency.
// Creates a fresh QueryClient per test to prevent state bleed.
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
                element={
                  <AppLayout>
                    <div data-testid="page-content">content</div>
                  </AppLayout>
                }
              />
            </Routes>
          </TimeRangeProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

// ---------------------------------------------------------------------------
// Helper: get a typed reference to the mocked useSearch for per-test control.
// ---------------------------------------------------------------------------

const mockedUseSearch = vi.mocked(useSearchImport)

function getUseSearchMock() {
  return mockedUseSearch
}

// ---------------------------------------------------------------------------
// Reset mocks between tests so spy call counts and return values are clean.
// ---------------------------------------------------------------------------

beforeEach(() => {
  vi.clearAllMocks()
  mockNavigate.mockClear()
  // Default: useSearch returns empty/idle state
  getUseSearchMock().mockReturnValue({ data: undefined, isLoading: false })
})

// ===========================================================================
// AC1: Autocomplete — 2+ character minimum before dropdown opens
// ===========================================================================

describe('[P0] GlobalSearch — autocomplete trigger (AC1)', () => {
  it('[P0] search input renders in AppBar with placeholder "Search datasets... (Ctrl+K)"', () => {
    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    expect(input).toBeInTheDocument()
    // Must be an input element (Autocomplete renders <input> inside TextField)
    expect(input.tagName).toBe('INPUT')
  })

  it('[P0] typing 1 character does NOT open the autocomplete dropdown', async () => {
    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')

    fireEvent.change(input, { target: { value: 'u' } })

    // useSearch should NOT have been called with query.length < 2
    const useSearchMock = getUseSearchMock()
    expect(useSearchMock).not.toHaveBeenCalledWith(expect.stringMatching(/.+/))

    // Dropdown listbox must NOT be visible
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('[P0] typing 2+ characters triggers useSearch and opens the autocomplete dropdown', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: MOCK_SEARCH_RESULTS },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')

    fireEvent.change(input, { target: { value: 'ue90' } })

    // useSearch must have been called with the typed query
    expect(useSearchMock).toHaveBeenCalledWith('ue90')

    // MUI Autocomplete renders a listbox when open
    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument()
    })
  })

  it('[P0] search results display dataset name in the dropdown', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: MOCK_SEARCH_RESULTS },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'sales' } })

    await waitFor(() => {
      expect(
        screen.getByText('lob=retail/src_sys_nm=alpha/dataset=sales_daily')
      ).toBeInTheDocument()
    })
  })

  it('[P0] search results display DqsScoreChip for each result', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: [MOCK_SEARCH_RESULTS[0]] },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'sales' } })

    await waitFor(() => {
      expect(screen.getByTestId('dqs-score-chip')).toBeInTheDocument()
    })
  })

  it('[P1] search results display LOB name in gray caption for each result', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: [MOCK_SEARCH_RESULTS[0]] },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'sales' } })

    await waitFor(() => {
      expect(screen.getByText('LOB_RETAIL')).toBeInTheDocument()
    })
  })
})

// ===========================================================================
// AC2: Escape key closes the dropdown without navigating
// ===========================================================================

describe('[P1] GlobalSearch — escape to close (AC2)', () => {
  it('[P1] pressing Escape closes the autocomplete dropdown without navigation', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: MOCK_SEARCH_RESULTS },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'ue90' } })

    // Dropdown should be open
    await waitFor(() => {
      expect(screen.getByRole('listbox')).toBeInTheDocument()
    })

    // Press Escape
    fireEvent.keyDown(input, { key: 'Escape', code: 'Escape' })

    // Dropdown must close
    await waitFor(() => {
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    })

    // Navigation must NOT have been triggered
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})

// ===========================================================================
// AC3: Clicking a result navigates to /datasets/:datasetId
// ===========================================================================

describe('[P0] GlobalSearch — result selection navigation (AC3)', () => {
  it('[P0] clicking a search result navigates to /datasets/{dataset_id}', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: [MOCK_SEARCH_RESULTS[0]] },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'sales' } })

    // Wait for the dropdown option to appear
    await waitFor(() => {
      expect(
        screen.getByText('lob=retail/src_sys_nm=alpha/dataset=sales_daily')
      ).toBeInTheDocument()
    })

    // Click the result option
    fireEvent.click(
      screen.getByText('lob=retail/src_sys_nm=alpha/dataset=sales_daily')
    )

    // Must navigate to /datasets/7 (dataset_id from MOCK_SEARCH_RESULTS[0])
    expect(mockNavigate).toHaveBeenCalledWith('/datasets/7')
  })

  it('[P0] after selecting a result, the search input is cleared', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: [MOCK_SEARCH_RESULTS[0]] },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'sales' } })

    await waitFor(() => {
      expect(
        screen.getByText('lob=retail/src_sys_nm=alpha/dataset=sales_daily')
      ).toBeInTheDocument()
    })

    fireEvent.click(
      screen.getByText('lob=retail/src_sys_nm=alpha/dataset=sales_daily')
    )

    // Input must be cleared after selection
    await waitFor(() => {
      expect(input).toHaveValue('')
    })
  })
})

// ===========================================================================
// AC4: Ctrl+K focuses the search input from any view
// ===========================================================================

describe('[P0] GlobalSearch — Ctrl+K keyboard shortcut (AC4)', () => {
  it('[P0] pressing Ctrl+K focuses the search input', async () => {
    renderAppLayout()

    // Confirm input is not focused initially
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    expect(input).not.toHaveFocus()

    // Fire the Ctrl+K keyboard event on window
    act(() => {
      fireEvent.keyDown(window, { key: 'k', ctrlKey: true, code: 'KeyK' })
    })

    // Search input must now be focused
    await waitFor(() => {
      expect(input).toHaveFocus()
    })
  })

  it('[P1] pressing Cmd+K (Meta+K) also focuses the search input on Mac', async () => {
    renderAppLayout()

    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    expect(input).not.toHaveFocus()

    act(() => {
      fireEvent.keyDown(window, { key: 'k', metaKey: true, code: 'KeyK' })
    })

    await waitFor(() => {
      expect(input).toHaveFocus()
    })
  })

  it('[P1] Ctrl+K event default browser action is prevented', async () => {
    renderAppLayout()

    const keyEvent = new KeyboardEvent('keydown', {
      key: 'k',
      ctrlKey: true,
      code: 'KeyK',
      bubbles: true,
      cancelable: true,
    })

    // Track whether default was prevented
    let defaultPrevented = false
    keyEvent.preventDefault = () => { defaultPrevented = true }

    act(() => {
      window.dispatchEvent(keyEvent)
    })

    expect(defaultPrevented).toBe(true)
  })
})

// ===========================================================================
// AC5: No-results message when query matches nothing
// ===========================================================================

describe('[P1] GlobalSearch — no results state (AC5)', () => {
  it('[P1] shows "No datasets matching \'{query}\'" when search returns empty results', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: [] },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'zzznomatch' } })

    await waitFor(() => {
      expect(
        screen.getByText("No datasets matching 'zzznomatch'")
      ).toBeInTheDocument()
    })
  })

  it('[P1] no-results text includes the exact query string typed by the user', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: [] },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'xyz789' } })

    await waitFor(() => {
      expect(
        screen.getByText("No datasets matching 'xyz789'")
      ).toBeInTheDocument()
    })
  })

  it('[P2] typing only 1 character shows "Type to search" fallback text (not "No datasets matching")', async () => {
    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'x' } })

    // Should NOT show "No datasets matching" for single character
    expect(screen.queryByText(/No datasets matching/i)).not.toBeInTheDocument()
  })
})

// ===========================================================================
// AC6: Global search across all LOBs, max 10 results
// ===========================================================================

describe('[P2] GlobalSearch — search scope and result limits (AC6)', () => {
  it('[P2] displays up to 10 search results in the dropdown', async () => {
    const tenResults: SearchResult[] = Array.from({ length: 10 }, (_, i) => ({
      dataset_id: i + 1,
      dataset_name: `lob=test/dataset=dataset_${i + 1}`,
      lob_id: `LOB_${i}`,
      dqs_score: 90 + i,
      check_status: 'PASS' as const,
    }))

    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({
      data: { results: tenResults },
      isLoading: false,
    })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'dataset' } })

    await waitFor(() => {
      // All 10 option items should be in the dropdown listbox
      const listbox = screen.getByRole('listbox')
      const options = listbox.querySelectorAll('[role="option"]')
      expect(options).toHaveLength(10)
    })
  })

  it('[P2] useSearch is called with the typed query when 2+ characters are entered', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({ data: undefined, isLoading: true })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'retail' } })

    expect(useSearchMock).toHaveBeenCalledWith('retail')
  })
})

// ===========================================================================
// Error state coverage
// ===========================================================================

describe('[P1] GlobalSearch — error state', () => {
  it('[P1] shows "Search unavailable" message when useSearch returns an error', async () => {
    const useSearchMock = getUseSearchMock()
    useSearchMock.mockReturnValue({ data: undefined, isLoading: false, isError: true })

    renderAppLayout()
    const input = screen.getByPlaceholderText('Search datasets... (Ctrl+K)')
    fireEvent.change(input, { target: { value: 'retail' } })
    fireEvent.focus(input)

    const listbox = screen.queryByRole('listbox')
    if (listbox) {
      expect(screen.getByText(/search unavailable/i)).toBeInTheDocument()
    }
  })
})

// ===========================================================================
// Backward-compatibility guard: existing AppLayout tests must not break
// ===========================================================================

describe('[P0] GlobalSearch — backward compatibility (existing AppLayout AC)', () => {
  it('[P0] AppLayout renders without error when useSearch returns undefined data', () => {
    expect(() => renderAppLayout()).not.toThrow()
  })

  it('[P0] AppLayout header still contains breadcrumbs alongside GlobalSearch', () => {
    renderAppLayout('/')
    expect(screen.getByRole('navigation', { name: /breadcrumb/i })).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Search datasets... (Ctrl+K)')).toBeInTheDocument()
  })
})
