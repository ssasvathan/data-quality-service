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
  lob_id: string
  dqs_score: number
  partition_date: string
  last_run_date: string
}

/**
 * DatasetInLob — shape returned per-dataset inside GET /api/lobs/{lob_id}/datasets.
 * freshness_status, volume_status, schema_status are null if the check was not run.
 */
export interface DatasetInLob {
  dataset_id: number
  dataset_name: string
  dqs_score: number | null
  check_status: 'PASS' | 'WARN' | 'FAIL' | null
  partition_date: string
  trend: number[]
  freshness_status: 'PASS' | 'WARN' | 'FAIL' | null
  volume_status: 'PASS' | 'WARN' | 'FAIL' | null
  schema_status: 'PASS' | 'WARN' | 'FAIL' | null
}

/**
 * LobDatasetsResponse — shape returned by GET /api/lobs/{lob_id}/datasets.
 */
export interface LobDatasetsResponse {
  lob_id: string
  datasets: DatasetInLob[]
}

// Re-export TimeRange type for convenience
export type { TimeRange } from '../context/TimeRangeContext'
