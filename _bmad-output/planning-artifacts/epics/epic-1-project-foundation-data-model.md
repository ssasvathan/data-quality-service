# Epic 1: Project Foundation & Data Model

Platform team has a deployable stack with an auditable, temporal database schema and test data ready for development.

## Story 1.1: Initialize Project Structure & Development Environment

As a **developer**,
I want all 4 component skeletons initialized from Architecture starter templates with a working docker-compose for local development,
So that I can start building any component immediately with a consistent, deployable stack.

**Acceptance Criteria:**

**Given** a fresh clone of the repository
**When** I run `docker-compose up`
**Then** Postgres starts on port 5432, FastAPI (dqs-serve) starts on port 8000, and Vite dev server (dqs-dashboard) starts on port 5173
**And** dqs-spark has a Maven project structure with `pom.xml` and Spark as `provided` dependency
**And** dqs-orchestrator has a Python project with `pyproject.toml` and its own config/ directory
**And** each component has its own config/, dependencies, and build — no shared resources between components

## Story 1.2: Implement Core Schema with Temporal Pattern

As a **platform operator**,
I want the core `dq_run` table with the temporal pattern (create_date/expiry_date as TIMESTAMP, sentinel `9999-12-31 23:59:59`, composite unique constraint on natural keys + expiry_date),
So that every quality run is auditable with no hard deletes from the start.

**Acceptance Criteria:**

**Given** the Postgres database is running
**When** the DDL script is executed
**Then** the `dq_run` table exists with columns: id (serial PK), dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, orchestration_run_id, error_message, create_date (TIMESTAMP), expiry_date (TIMESTAMP DEFAULT '9999-12-31 23:59:59')
**And** a composite unique constraint exists on (dataset_name, partition_date, rerun_number, expiry_date)
**And** the `EXPIRY_SENTINEL` constant is defined in both dqs-serve (Python) and documented for dqs-spark (Java)
**And** inserting two active records with the same natural key is rejected by the DB constraint

## Story 1.3: Implement Metric Storage Schema

As a **platform operator**,
I want metric storage tables (`dq_metric_numeric` and `dq_metric_detail`) with the temporal pattern,
So that quality check results have structured, auditable storage for both numeric values and structured text.

**Acceptance Criteria:**

**Given** the `dq_run` table exists
**When** the DDL script is executed
**Then** `dq_metric_numeric` exists with columns: id, dq_run_id (FK), check_type, metric_name, metric_value (numeric), create_date, expiry_date — following the temporal pattern
**And** `dq_metric_detail` exists with columns: id, dq_run_id (FK), check_type, detail_type, detail_value (text/jsonb), create_date, expiry_date — following the temporal pattern
**And** both tables have composite unique constraints on their natural keys + expiry_date
**And** both tables have foreign key references to `dq_run.id`

## Story 1.4: Implement Configuration & Enrichment Schema

As a **platform operator**,
I want configuration tables (`check_config` and `dataset_enrichment`) with the temporal pattern,
So that check behavior and dataset overrides can be managed via database without code changes.

**Acceptance Criteria:**

**Given** the Postgres database is running
**When** the DDL script is executed
**Then** `check_config` exists with columns: id, dataset_pattern, check_type, enabled (boolean), explosion_level, create_date, expiry_date — following the temporal pattern
**And** `dataset_enrichment` exists with columns: id, dataset_pattern, lookup_code, custom_weights (jsonb), sla_hours, explosion_level, create_date, expiry_date — following the temporal pattern
**And** both tables have composite unique constraints on their natural keys + expiry_date
**And** a `check_config` row can disable a specific check type for a dataset pattern
**And** a `dataset_enrichment` row can override the lookup code for a legacy path pattern

## Story 1.5: Implement Orchestration Schema

As a **platform operator**,
I want the `dq_orchestration_run` table with temporal pattern,
So that every orchestration run is tracked with start/end times, dataset counts, and status for audit and debugging.

**Acceptance Criteria:**

**Given** the Postgres database is running
**When** the DDL script is executed
**Then** `dq_orchestration_run` exists with columns: id, parent_path, run_status, start_time, end_time, total_datasets, passed_datasets, failed_datasets, error_summary, create_date, expiry_date — following the temporal pattern
**And** `dq_run.orchestration_run_id` references `dq_orchestration_run.id`
**And** the table supports tracking multiple parent paths per orchestration run

## Story 1.6: Implement Active-Record Views & Indexing

As a **developer**,
I want active-record views (`v_*_active`) filtering on the sentinel expiry_date and composite indexes on all DQS tables,
So that queries always return current data safely and primary query patterns perform well.

**Acceptance Criteria:**

**Given** all DQS tables exist
**When** the views DDL is executed
**Then** `v_dq_run_active`, `v_dq_metric_numeric_active`, `v_dq_metric_detail_active`, `v_check_config_active`, `v_dataset_enrichment_active`, `v_dq_orchestration_run_active` views exist
**And** each view filters on `expiry_date = '9999-12-31 23:59:59'`
**And** composite indexes exist on natural key + expiry_date for all tables (e.g., `idx_dq_run_dataset_name_partition_date`)
**And** querying a view after expiring a record (setting expiry_date to now) no longer returns that record

## Story 1.7: Create Test Data Fixtures

As a **developer**,
I want comprehensive test data fixtures with 3-5 mock source systems, mixed anomalies, legacy paths, and mocked historical baselines,
So that all components can be developed and tested against realistic data.

**Acceptance Criteria:**

**Given** the complete schema is deployed
**When** the test fixture SQL is loaded
**Then** at least 3 mock source systems exist covering both Avro and Parquet formats with different path structures
**And** test data includes mixed anomalies within otherwise healthy datasets (stale partitions, zero rows, schema drift, high nulls)
**And** at least one legacy-format path (e.g., `src_sys_nm=omni`) exercises the dataset_enrichment lookup table resolution
**And** eventAttribute test values cover all JSON literal types: strings, numbers, booleans, arrays, objects, and nested combinations
**And** historical baseline data spanning at least 7 days is mocked in `dq_metric_numeric` for statistical checks (single date on disk, mocked history in DB)
**And** test data populates `check_config` and `dataset_enrichment` with sample configuration rows

---
