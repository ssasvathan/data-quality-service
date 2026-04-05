/**
 * ATDD Hook Tests — Global Dataset Search
 *
 * GREEN PHASE: All tests pass — implementation complete.
 *
 * Acceptance Criteria covered:
 *   AC1: useSearch hook triggers API call for 2+ char queries (enabled guard)
 *   AC1: useSearch hook is disabled for queries under 2 characters
 *   AC6: hook fetches from GET /api/search?q={query} (global, all LOBs)
 *
 * @testing-framework Vitest + @tanstack/react-query renderHook
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'

// ---------------------------------------------------------------------------
// Mock apiFetch to intercept HTTP calls without a real server.
// ---------------------------------------------------------------------------

vi.mock('../../src/api/client', () => ({
  apiFetch: vi.fn(),
}))

import { apiFetch as apiFetchMock } from '../../src/api/client'
const apiFetch = vi.mocked(apiFetchMock)

// ---------------------------------------------------------------------------
// Helper: create a fresh QueryClient + wrapper for each renderHook call.
// Fresh client per test prevents cache pollution between tests.
// ---------------------------------------------------------------------------

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        // Disable stale-time for test isolation — force refetch on each mount
        staleTime: 0,
      },
    },
  })
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children)
  return { queryClient, wrapper }
}

import { useSearch } from '../../src/api/queries'
import type { SearchResponse } from '../../src/api/types'

// ---------------------------------------------------------------------------
// Fixture: mock API response matching the SearchResponse shape.
// ---------------------------------------------------------------------------

const MOCK_SEARCH_RESPONSE: SearchResponse = {
  results: [
    {
      dataset_id: 7,
      dataset_name: 'lob=retail/src_sys_nm=alpha/dataset=sales_daily',
      lob_id: 'LOB_RETAIL',
      dqs_score: 98.5,
      check_status: 'PASS',
    },
    {
      dataset_id: 12,
      dataset_name: 'lob=mortgage/src_sys_nm=core/dataset=loan_origination',
      lob_id: 'LOB_MORTGAGE',
      dqs_score: null,
      check_status: null,
    },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
})

// ===========================================================================
// useSearch — enabled guard: 2+ character minimum
// ===========================================================================

describe('[P0] useSearch — enabled guard (AC1)', () => {
  it('[P0] does NOT fire an API call when query is empty string', () => {
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useSearch(''), { wrapper })

    // Hook must be in idle/disabled state — no fetch attempted
    expect(result.current.fetchStatus).toBe('idle')
    expect(result.current.data).toBeUndefined()

    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('[P0] does NOT fire an API call when query is a single character', () => {
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useSearch('u'), { wrapper })

    expect(result.current.fetchStatus).toBe('idle')
    expect(result.current.data).toBeUndefined()

    expect(apiFetch).not.toHaveBeenCalled()
  })

  it('[P0] fires an API call when query has exactly 2 characters', async () => {
    apiFetch.mockResolvedValueOnce(MOCK_SEARCH_RESPONSE)

    const { wrapper } = createWrapper()
    renderHook(() => useSearch('ue'), { wrapper })

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith('/search?q=ue')
    })
  })

  it('[P0] fires an API call when query has 3+ characters', async () => {
    apiFetch.mockResolvedValueOnce(MOCK_SEARCH_RESPONSE)

    const { wrapper } = createWrapper()
    renderHook(() => useSearch('ue90'), { wrapper })

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith('/search?q=ue90')
    })
  })
})

// ===========================================================================
// useSearch — URL construction and query key
// ===========================================================================

describe('[P0] useSearch — API request shape (AC1, AC6)', () => {
  it('[P0] encodes special characters in query via encodeURIComponent', async () => {
    apiFetch.mockResolvedValueOnce({ results: [] })

    const { wrapper } = createWrapper()
    renderHook(() => useSearch('lob=retail/src'), { wrapper })

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith(
        `/search?q=${encodeURIComponent('lob=retail/src')}`
      )
    })
  })

  it('[P0] uses queryKey ["search", query] for cache segmentation', async () => {
    apiFetch.mockResolvedValueOnce(MOCK_SEARCH_RESPONSE)

    const { wrapper, queryClient } = createWrapper()
    renderHook(() => useSearch('retail'), { wrapper })

    await waitFor(() => {
      const cachedData = queryClient.getQueryData<SearchResponse>(['search', 'retail'])
      expect(cachedData).toBeDefined()
      expect(cachedData?.results).toHaveLength(2)
    })
  })
})

// ===========================================================================
// useSearch — response shape (SearchResult/SearchResponse types)
// ===========================================================================

describe('[P0] useSearch — response data shape (AC1, AC3)', () => {
  it('[P0] returns data.results array on successful fetch', async () => {
    apiFetch.mockResolvedValueOnce(MOCK_SEARCH_RESPONSE)

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSearch('retail'), { wrapper })

    await waitFor(() => {
      expect(result.current.data).toBeDefined()
    })

    expect(result.current.data?.results).toHaveLength(2)
    expect(result.current.data?.results[0].dataset_id).toBe(7)
    expect(result.current.data?.results[0].dataset_name).toBe(
      'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
    )
    expect(result.current.data?.results[0].lob_id).toBe('LOB_RETAIL')
    expect(result.current.data?.results[0].dqs_score).toBe(98.5)
    expect(result.current.data?.results[0].check_status).toBe('PASS')
  })

  it('[P1] handles null dqs_score and check_status in results', async () => {
    apiFetch.mockResolvedValueOnce(MOCK_SEARCH_RESPONSE)

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSearch('loan'), { wrapper })

    await waitFor(() => {
      expect(result.current.data).toBeDefined()
    })

    // Second result has null dqs_score and check_status
    const nullScoreResult = result.current.data?.results[1]
    expect(nullScoreResult?.dqs_score).toBeNull()
    expect(nullScoreResult?.check_status).toBeNull()
  })

  it('[P1] returns empty results array when API returns no matches', async () => {
    apiFetch.mockResolvedValueOnce({ results: [] })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSearch('zzznomatch'), { wrapper })

    await waitFor(() => {
      expect(result.current.data).toBeDefined()
    })

    expect(result.current.data?.results).toHaveLength(0)
  })
})

// ===========================================================================
// useSearch — staleTime cache behaviour
// ===========================================================================

describe('[P2] useSearch — staleTime caching (AC6)', () => {
  it('[P2] does not re-fetch when the same query is called within staleTime window', async () => {
    apiFetch.mockResolvedValueOnce(MOCK_SEARCH_RESPONSE)

    const { wrapper, queryClient } = createWrapper()
    // Override staleTime to 30s to match implementation
    queryClient.setDefaultOptions({ queries: { retry: false, staleTime: 30_000 } })

    const { rerender } = renderHook(() => useSearch('sales'), { wrapper })

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledTimes(1)
    })

    // Re-render with same query — should use cache, not re-fetch
    rerender()

    expect(apiFetch).toHaveBeenCalledTimes(1)
  })
})
