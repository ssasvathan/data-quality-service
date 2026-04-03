/**
 * TanStack Query query functions — all data fetching via React Query.
 * Never use useEffect + fetch directly (per project-context.md anti-patterns).
 */

import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { LobSummary, DatasetSummary } from './types'

export function useLobs() {
  return useQuery<LobSummary[]>({
    queryKey: ['lobs'],
    queryFn: () => apiFetch<LobSummary[]>('/lobs'),
  })
}

export function useDatasets(lobId?: number) {
  return useQuery<DatasetSummary[]>({
    queryKey: ['datasets', lobId],
    queryFn: () => apiFetch<DatasetSummary[]>(lobId ? `/lobs/${lobId}/datasets` : '/datasets'),
  })
}
