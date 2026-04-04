/**
 * API response type definitions — all types defined here, never inline.
 * snake_case matches Postgres columns and Python API conventions.
 */

export interface HealthResponse {
  status: string
}

/**
 * LobDetail — shape returned by GET /api/lobs (no trend field).
 * Used by useLobs() for the LOB listing endpoint.
 */
export interface LobDetail {
  lob_id: string
  dataset_count: number
  aggregate_score: number | null
  healthy_count: number
  degraded_count: number
  critical_count: number
}

/**
 * LobSummaryItem — shape returned per-LOB inside GET /api/summary response.
 * Includes trend array for sparkline rendering.
 */
export interface LobSummaryItem {
  lob_id: string
  dataset_count: number
  aggregate_score: number | null
  healthy_count: number
  degraded_count: number
  critical_count: number
  trend: number[]
}

/**
 * SummaryResponse — shape returned by GET /api/summary.
 */
export interface SummaryResponse {
  total_datasets: number
  healthy_count: number
  degraded_count: number
  critical_count: number
  lobs: LobSummaryItem[]
}

export interface DatasetSummary {
  dataset_id: number
  dataset_name: string
  lob_id: number
  dqs_score: number
  partition_date: string
  last_run_date: string
}

// Re-export TimeRange type for convenience
export type { TimeRange } from '../context/TimeRangeContext'
