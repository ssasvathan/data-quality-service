/**
 * TanStack Query query functions — all data fetching via React Query.
 * Never use useEffect + fetch directly (per project-context.md anti-patterns).
 */

import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { LobDetail, DatasetSummary, SummaryResponse, LobDatasetsResponse, DatasetDetail, DatasetMetricsResponse, DatasetTrendResponse, SearchResponse } from './types'
import type { TimeRange } from '../context/TimeRangeContext'

export function useLobs(timeRange: TimeRange = '7d') {
  return useQuery<LobDetail[]>({
    queryKey: ['lobs', timeRange],
    queryFn: () => apiFetch<LobDetail[]>(`/lobs?time_range=${timeRange}`),
  })
}

/**
 * useDatasets — legacy placeholder hook for dataset listing.
 * @deprecated Not used by any current page. Prefer useLobDatasets for
 * LOB-scoped dataset queries. This hook is retained for backward compatibility
 * but should be removed once confirmed fully unused.
 */
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

/**
 * useLobDatasets — fetches GET /api/lobs/{lobId}/datasets?time_range={timeRange}.
 * Query key includes both lobId and timeRange so time-range changes trigger refetch.
 */
export function useLobDatasets(lobId: string, timeRange: TimeRange = '7d') {
  return useQuery<LobDatasetsResponse>({
    queryKey: ['lobDatasets', lobId, timeRange],
    queryFn: () => apiFetch<LobDatasetsResponse>(`/lobs/${lobId}/datasets?time_range=${timeRange}`),
    enabled: !!lobId,
  })
}

/**
 * useDatasetDetail — fetches GET /api/datasets/{datasetId}.
 * NOT time-range parameterized — dataset detail is a point-in-time record.
 */
export function useDatasetDetail(datasetId: string | undefined) {
  return useQuery<DatasetDetail>({
    queryKey: ['datasetDetail', datasetId],
    queryFn: () => apiFetch<DatasetDetail>(`/datasets/${datasetId}`),
    enabled: !!datasetId,
  })
}

/**
 * useDatasetMetrics — fetches GET /api/datasets/{datasetId}/metrics.
 * NOT time-range parameterized — metrics are per run/partition record.
 */
export function useDatasetMetrics(datasetId: string | undefined) {
  return useQuery<DatasetMetricsResponse>({
    queryKey: ['datasetMetrics', datasetId],
    queryFn: () => apiFetch<DatasetMetricsResponse>(`/datasets/${datasetId}/metrics`),
    enabled: !!datasetId,
  })
}

/**
 * useDatasetTrend — fetches GET /api/datasets/{datasetId}/trend?time_range={timeRange}.
 * Time-range parameterized — trend window changes with global time range toggle.
 */
export function useDatasetTrend(datasetId: string | undefined, timeRange: TimeRange) {
  return useQuery<DatasetTrendResponse>({
    queryKey: ['datasetTrend', datasetId, timeRange],
    queryFn: () => apiFetch<DatasetTrendResponse>(`/datasets/${datasetId}/trend?time_range=${timeRange}`),
    enabled: !!datasetId,
  })
}

/**
 * useSearch — fetches GET /api/search?q={query}.
 * Enabled only when query has 2+ characters (AC1).
 * Results cached for 30s to avoid redundant refetch on same query (staleTime: 30_000).
 * filterOptions is handled server-side — the API orders prefix before substring.
 */
export function useSearch(query: string) {
  return useQuery<SearchResponse>({
    queryKey: ['search', query],
    queryFn: () => apiFetch<SearchResponse>(`/search?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 2,
    staleTime: 30_000,
  })
}
