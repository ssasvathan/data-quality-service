/**
 * TanStack Query query functions — all data fetching via React Query.
 * Never use useEffect + fetch directly (per project-context.md anti-patterns).
 */

import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { LobSummary, DatasetSummary } from './types'
import type { TimeRange } from '../context/TimeRangeContext'

export function useLobs(timeRange: TimeRange = '7d') {
  return useQuery<LobSummary[]>({
    queryKey: ['lobs', timeRange],
    queryFn: () => apiFetch<LobSummary[]>(`/lobs?time_range=${timeRange}`),
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
