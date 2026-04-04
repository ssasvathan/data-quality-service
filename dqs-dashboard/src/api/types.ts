/**
 * API response type definitions — all types defined here, never inline.
 * snake_case matches Postgres columns and Python API conventions.
 */

export interface HealthResponse {
  status: string
}

export interface LobSummary {
  lob_id: number
  lob_name: string
  dqs_score: number
  dataset_count: number
  last_run_date: string
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
