# Architecture — dqs-spark

_Java 17 / Maven / Spark 3.5.0 — Data quality check execution engine._

---

## Overview

dqs-spark is the batch processing engine that scans HDFS datasets and runs configurable data quality checks. It implements 16 check types using the strategy pattern, writes results to PostgreSQL via JDBC batch operations, and supports per-dataset failure isolation.

**Entry point:** `DqsJob.main(String[] args)` via `spark-submit`

---

## Package Structure

```
com.bank.dqs
├── DqsJob                     # Main orchestrator: scan → check → write
├── model/
│   ├── DatasetContext          # Immutable context (name, df, format, partition, lookup_code)
│   ├── DqMetric                # Marker interface for metric types
│   ├── MetricNumeric           # Numeric metric (check_type, metric_name, value)
│   ├── MetricDetail            # JSONB detail metric (check_type, detail_type, value)
│   └── DqsConstants            # EXPIRY_SENTINEL, shared constants
├── checks/
│   ├── DqCheck                 # Strategy interface: execute(DatasetContext) → List<DqMetric>
│   ├── CheckFactory            # Registry with LIKE pattern matching on check_config
│   ├── [16 check classes]      # Tier 1, 2, 3 implementations
│   └── DqsScoreCheck           # Composite score from Tier 1 (always last)
├── scanner/
│   ├── PathScanner             # Interface for dataset discovery
│   ├── ConsumerZoneScanner     # HDFS scanner (partition_date/src_sys_nm structure)
│   └── EnrichmentResolver      # src_sys_nm → lookup_code via v_dataset_enrichment_active
├── reader/
│   └── DatasetReader           # Avro/Parquet → Spark DataFrame
└── writer/
    └── BatchWriter             # JDBC batch: dq_run + metrics in single transaction
```

---

## Data Flow

```
HDFS Consumer Zone
    ↓ ConsumerZoneScanner.scan(parentPath, partitionDate)
DatasetContext[] (empty DataFrames)
    ↓ EnrichmentResolver.resolve(datasetName) → lookup_code
DatasetContext[] (enriched with lookup_code)
    ↓ DatasetReader.read(path, format)
DatasetContext[] (with loaded DataFrames)
    ↓ CheckFactory.getEnabledChecks(datasetName, connection)
    ↓   → queries check_config WHERE dataset_name LIKE dataset_pattern
DqCheck[] (ordered list of enabled checks)
    ↓ For each check: check.execute(context) → List<DqMetric>
    ↓   [Baseline providers query historical metrics via JDBC]
    ↓   DqsScoreCheck runs last (reads accumulated Tier 1 results)
List<DqMetric> (all metrics for this dataset)
    ↓ BatchWriter.write(context, metrics, orchestrationRunId, rerunNumber)
    ↓   Single JDBC transaction: INSERT dq_run + batch INSERT metrics
PostgreSQL (dq_run, dq_metric_numeric, dq_metric_detail)
```

---

## Check Types (16 Total)

### Tier 1 — Foundational

| Check | Metrics | Classification |
|-------|---------|----------------|
| **FRESHNESS** | hours_since_update (numeric), freshness_status (detail) | PASS/WARN/FAIL based on baseline + stddev |
| **VOLUME** | row_count, pct_change, row_count_stddev (numeric), volume_status (detail) | Statistical deviation from 30-run baseline |
| **SCHEMA** | added/removed/changed_fields_count (numeric), schema_hash, schema_diff (detail) | Any field change triggers WARN/FAIL |
| **OPS** | null_rate_pct::{col}, empty_string_rate_pct::{col} (numeric), ops_anomalies (detail) | Per-column anomaly detection |

### Tier 2 — Extended

| Check | Purpose |
|-------|---------|
| **SLA_COUNTDOWN** | Hours remaining until SLA breach (from partition-date midnight) |
| **ZERO_ROW** | Empty dataset detection (immediate FAIL) |
| **BREAKING_CHANGE** | Field removal/renaming detection (coexists with SCHEMA check) |
| **DISTRIBUTION** | Column value distribution anomalies |
| **TIMESTAMP_SANITY** | Timestamp consistency validation |

### Tier 3 — Intelligence

| Check | Purpose |
|-------|---------|
| **CLASSIFICATION_WEIGHTED** | Classification-based scoring with priority multiplier signals |
| **SOURCE_SYSTEM_HEALTH** | Source system KPI tracking |
| **CORRELATION** | Cross-dataset correlation validation |
| **INFERRED_SLA** | SLA prediction from historical patterns |
| **LINEAGE** | Data lineage tracking |
| **ORPHAN_DETECTION** | Orphaned record detection |
| **CROSS_DESTINATION** | Multi-destination consistency |

### Composite

**DQS_SCORE** — Weighted composite from Tier 1 only:
- Weights: FRESHNESS 0.30, VOLUME 0.30, SCHEMA 0.20, OPS 0.20
- Mapping: PASS→100, WARN→50, FAIL→0
- Thresholds: score >= 80 → PASS, >= 50 → WARN, < 50 → FAIL
- Handles partial availability with weight redistribution
- Always runs LAST in the check sequence

---

## Design Patterns

**Strategy Pattern (DqCheck):** All checks implement the same `execute(DatasetContext) → List<DqMetric>` contract. Adding a new check requires zero changes to serve/API/dashboard.

**Factory Pattern (CheckFactory):** Queries `check_config` table with reversed LIKE pattern (`dataset_name LIKE dataset_pattern AND enabled = TRUE`). Returns ordered list of enabled checks.

**Provider Pattern:** Checks accept functional interfaces for baseline stats, SLA config, and classification weights. Lambda-injectable suppliers enable per-dataset failure isolation and testability.

**Per-Dataset Failure Isolation:** Exceptions caught at the dataset level — one failure doesn't crash the JVM. Failed datasets get `NOT_RUN` detail metrics and continue to next dataset.

---

## Database Interactions

| Operation | Table/View | Direction |
|-----------|-----------|-----------|
| Read check config | `check_config` (WHERE enabled AND expiry_date = sentinel) | Read |
| Read enrichment | `v_dataset_enrichment_active` (LIKE pattern match) | Read |
| Read baselines | `dq_metric_numeric` (last 30 runs for mean/stddev) | Read |
| Read schema baseline | `dq_metric_detail` (most recent schema_hash) | Read |
| Write run record | `dq_run` | Write |
| Write numeric metrics | `dq_metric_numeric` (batch INSERT) | Write |
| Write detail metrics | `dq_metric_detail` (batch INSERT, JSONB) | Write |

---

## Configuration

**CLI Arguments:**
- `--parent-path <path>` (required) — HDFS parent path to scan
- `--date <yyyyMMdd>` (optional, default: today)
- `--orchestration-run-id <long>` (optional) — Correlation ID from orchestrator
- `--rerun-number <int>` (optional, default: 0)

**config/application.properties:**
```properties
db.url=jdbc:postgresql://localhost:5432/postgres
db.user=postgres
db.password=localdev
spark.app.name=dqs-spark
spark.master=local[*]
```

---

## Test Strategy

- **H2 in-memory database** for JDBC integration tests (SQL compatible with both H2 and Postgres)
- **Static SparkSession** per test class (`@BeforeAll/@AfterAll`, `master("local[1]")`)
- **Mockito** for Dataset mocking in exception-path tests
- **Maven Surefire** forks test JVM with Java 21 + 12 `--add-opens` flags
- **Test naming:** `<action><expectation>` (e.g., `volumeCheckFlagsAnomaliesOutsideStdDev`)
- **20+ test classes** covering all checks, factory, reader, scanner, writer
