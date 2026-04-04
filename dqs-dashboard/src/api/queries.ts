/**
 * TanStack Query query functions — all data fetching via React Query.
 * Never use useEffect + fetch directly (per project-context.md anti-patterns).
 */

import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { LobDetail, DatasetSummary, SummaryResponse } from './types'
import type { TimeRange } from '../context/TimeRangeContext'

export function useLobs(timeRange: TimeRange = '7d') {
  return useQuery<LobDetail[]>({
    queryKey: ['lobs', timeRange],
    queryFn: () => apiFetch<LobDetail[]>(`/lobs?time_range=${timeRange}`),
  })
}

export function useDatasets(lobId?: number, timeRange: TimeRange = '7d') {
  return useQuery<DatasetSummary[]>({
    queryKey: ['datasets', lobId, timeRange],
    queryFn: () =>
      apiFetch<DatasetSummary[]>(
        lobId
          ? `/lobs/${lobId}/datasets?time_range=${timeRange}`
          : `/datasets?time_range=${timeRange}`
      ),
  })
}

/**
 * useSummary — fetches GET /api/summary.
 * Query key is intentionally NOT time-range parameterized:
 * the summary endpoint uses a fixed 7-day trend window per API design.
 */
export function useSummary() {
  return useQuery<SummaryResponse>({
    queryKey: ['summary'],
    queryFn: () => apiFetch<SummaryResponse>('/summary'),
  })
}
