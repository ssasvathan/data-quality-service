# API Contracts

_REST API and MCP tool specifications for dqs-serve._

---

## Base URL

- Local development: `http://localhost:8000`
- Dashboard proxy: `/api` → `http://localhost:8000` (via Vite dev server)

## Authentication

None (internal service). No auth middleware configured.

---

## REST API Endpoints

### Health Check

```
GET /health
```

**Response:** `200 OK`
```json
{"status": "ok"}
```

---

### Summary

```
GET /api/summary
```

Returns aggregated LOB summary with 7-day trend data. Fixed 7-day window (not parameterized).

**Response:** `200 OK`
```json
{
  "total_datasets": 6,
  "healthy_count": 3,
  "degraded_count": 1,
  "critical_count": 2,
  "lobs": [
    {
      "lob_id": "LOB_RETAIL",
      "dataset_count": 3,
      "aggregate_score": 71.83,
      "healthy_count": 1,
      "degraded_count": 1,
      "critical_count": 1,
      "trend": [98.5, 98.5, 98.5, 98.5, 98.5, 98.5, 71.83]
    }
  ],
  "last_run_at": "2026-04-02T06:45:00",
  "run_failed": true
}
```

---

### LOBs

```
GET /api/lobs
```

Returns all LOBs with dataset counts and aggregate scores.

**Response:** `200 OK` — `list[LobDetail]`
```json
[
  {
    "lob_id": "LOB_RETAIL",
    "dataset_count": 3,
    "aggregate_score": 71.83,
    "healthy_count": 1,
    "degraded_count": 1,
    "critical_count": 1
  }
]
```

---

### LOB Datasets

```
GET /api/lobs/{lob_id}/datasets?time_range=7d
```

**Path params:** `lob_id` (string) — LOB lookup code
**Query params:** `time_range` (string, optional) — `7d` | `30d` | `90d` (default: `7d`)

**Response:** `200 OK`
```json
{
  "lob_id": "LOB_RETAIL",
  "datasets": [
    {
      "dataset_id": 1,
      "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
      "dqs_score": 98.50,
      "check_status": "PASS",
      "partition_date": "2026-04-02",
      "trend": [98.5, 98.5, 98.5, 98.5, 98.5, 98.5, 98.5],
      "freshness_status": "PASS",
      "volume_status": null,
      "schema_status": null
    }
  ]
}
```

**Error:** `404` if LOB not found
```json
{"detail": "LOB LOB_NONEXISTENT not found", "error_code": "NOT_FOUND"}
```

---

### Dataset Detail

```
GET /api/datasets/{dataset_id}
```

**Path params:** `dataset_id` (integer) — dq_run.id

**Response:** `200 OK`
```json
{
  "dataset_id": 1,
  "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
  "lob_id": "LOB_RETAIL",
  "source_system": "alpha",
  "format": "parquet",
  "hdfs_path": "lob=retail/src_sys_nm=alpha/dataset=sales_daily/partition_date=20260402",
  "parent_path": "lob=retail/src_sys_nm=alpha",
  "partition_date": "2026-04-02",
  "row_count": 103876,
  "previous_row_count": 96103,
  "last_updated": "2026-04-02T06:45:00",
  "run_id": 1,
  "rerun_number": 0,
  "dqs_score": 98.50,
  "check_status": "PASS",
  "error_message": null,
  "lob_name": "Retail Banking",
  "owner": "Jane Doe",
  "classification": "Tier 1 Critical"
}
```

**Error:** `404` if dataset not found

---

### Dataset Metrics

```
GET /api/datasets/{dataset_id}/metrics
```

Returns all check results with numeric and detail metrics grouped by check_type.

**Response:** `200 OK`
```json
{
  "dataset_id": 1,
  "check_results": [
    {
      "check_type": "FRESHNESS",
      "status": "PASS",
      "numeric_metrics": [
        {"metric_name": "hours_since_update", "metric_value": 2.0}
      ],
      "detail_metrics": [
        {"detail_type": "freshness_status", "detail_value": {"status": "PASS", "threshold": 24}}
      ]
    },
    {
      "check_type": "VOLUME",
      "status": "PASS",
      "numeric_metrics": [
        {"metric_name": "row_count", "metric_value": 103876}
      ],
      "detail_metrics": []
    }
  ]
}
```

---

### Dataset Trend

```
GET /api/datasets/{dataset_id}/trend?time_range=7d
```

**Query params:** `time_range` — `7d` | `30d` | `90d` (default: `7d`)

**Response:** `200 OK`
```json
{
  "dataset_id": 1,
  "time_range": "7d",
  "trend": [
    {"date": "2026-03-27", "dqs_score": 98.50},
    {"date": "2026-03-28", "dqs_score": 98.50},
    {"date": "2026-04-02", "dqs_score": 98.50}
  ]
}
```

---

### Search

```
GET /api/search?q=sales
```

**Query params:** `q` (string, required) — Search query (min 2 characters from dashboard)

Returns up to 10 results, prefix matches sorted first (ILIKE with `%query%`).

**Response:** `200 OK`
```json
{
  "results": [
    {
      "dataset_id": 1,
      "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
      "lob_id": "LOB_RETAIL",
      "dqs_score": 98.50,
      "check_status": "PASS"
    }
  ]
}
```

---

### Executive Report

```
GET /api/executive/report
```

Returns executive reporting data (LOB monthly scores, source system accountability, improvement deltas).

**Response:** `200 OK`
```json
{
  "lob_monthly_scores": [
    {"lob_id": "LOB_RETAIL", "month": "2026-01", "avg_score": 95.2}
  ],
  "source_system_scores": [
    {
      "src_sys_nm": "alpha",
      "dataset_count": 3,
      "avg_score": 71.83,
      "healthy_count": 1,
      "critical_count": 1
    }
  ],
  "improvement_summary": [
    {
      "lob_id": "LOB_RETAIL",
      "baseline_score": 85.0,
      "current_score": 95.2,
      "delta": 10.2
    }
  ]
}
```

---

## Error Response Format

All error responses follow this structure:

```json
{
  "detail": "Human-readable error message",
  "error_code": "NOT_FOUND"
}
```

Error codes: `NOT_FOUND`, `SERVICE_UNAVAILABLE`, `INTERNAL_ERROR`

Stack traces are **never** returned in error responses.

---

## MCP Tools

Mounted at `/mcp/` via FastMCP HTTP transport.

### query_failures

```
Tool: query_failures(time_range: str = "latest")
```

Returns plain text summary of failed datasets grouped by LOB. Designed for LLM consumption ("What failed last night?").

### query_dataset_trend

```
Tool: query_dataset_trend(dataset_name: str, time_range: str = "7d")
```

Returns plain text trend report for a specific dataset: current score, trend direction (improving/degrading/stable), score history, flagged checks.

### compare_lob_quality

```
Tool: compare_lob_quality()
```

Returns plain text LOB ranking from worst to best by aggregate score, with dataset counts and failing check types.

---

## Key API Patterns

- **All JSON keys are snake_case** — matches Postgres columns and Python conventions
- **Time range parameter** — `7d`, `30d`, `90d` parsed to integer days
- **Trend queries** use `MAX(partition_date) - CAST(:days_back AS INTEGER) * INTERVAL '1 day'` CTE pattern
- **ROW_NUMBER() CTE** — used for "latest per dataset" queries (avoids N+1)
- **Batched queries** — summary LOB trends and metric lookups use `= ANY(:array)` for efficiency
- **ReferenceDataService** — LOB lookup resolved server-side with 12h cache (not per-request DB query)
