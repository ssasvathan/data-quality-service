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

/**
 * DatasetDetail — shape returned by GET /api/datasets/{dataset_id}.
 * Used by useDatasetDetail() for the dataset detail endpoint.
 */
export interface DatasetDetail {
  dataset_id: number
  dataset_name: string
  lob_id: string
  source_system: string
  format: string
  hdfs_path: string
  parent_path: string | null
  partition_date: string
  row_count: number | null
  previous_row_count: number | null
  last_updated: string
  run_id: number
  rerun_number: number
  dqs_score: number | null
  check_status: 'PASS' | 'WARN' | 'FAIL' | null
  error_message: string | null
}

/**
 * CheckMetric — a single numeric metric from a check result.
 */
export interface CheckMetric {
  metric_name: string
  metric_value: number
}

/**
 * CheckDetailMetric — a single detail metric from a check result.
 */
export interface CheckDetailMetric {
  detail_type: string
  detail_value: unknown
}

/**
 * CheckResult — one check group in the dataset metrics response.
 */
export interface CheckResult {
  check_type: string
  status: 'PASS' | 'WARN' | 'FAIL' | null
  numeric_metrics: CheckMetric[]
  detail_metrics: CheckDetailMetric[]
}

/**
 * DatasetMetricsResponse — shape returned by GET /api/datasets/{dataset_id}/metrics.
 */
export interface DatasetMetricsResponse {
  dataset_id: number
  check_results: CheckResult[]
}

/**
 * TrendPoint — a single data point in the dataset trend response.
 */
export interface TrendPoint {
  date: string
  dqs_score: number
}

/**
 * DatasetTrendResponse — shape returned by GET /api/datasets/{dataset_id}/trend.
 */
export interface DatasetTrendResponse {
  dataset_id: number
  time_range: string
  trend: TrendPoint[]
}

/**
 * SearchResult — a single result from GET /api/search?q=...
 * Mirrors the Python Pydantic SearchResult model in dqs-serve/src/serve/routes/search.py.
 */
export interface SearchResult {
  dataset_id: number
  dataset_name: string
  lob_id: string | null
  dqs_score: number | null
  check_status: 'PASS' | 'WARN' | 'FAIL' | null
}

/**
 * SearchResponse — shape returned by GET /api/search?q=...
 * Always returns an object with results array (never 404, never null).
 */
export interface SearchResponse {
  results: SearchResult[]
}

// Re-export TimeRange type for convenience
export type { TimeRange } from '../context/TimeRangeContext'
