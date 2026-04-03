# Epic 2: Dataset Discovery & Tier 1 Quality Checks

System automatically discovers datasets on HDFS and computes quality metrics using all 5 Tier 1 checks, with results persisted to the database.

## Story 2.1: DqCheck Strategy Interface, Metric Models & Check Factory

As a **developer**,
I want a `DqCheck` strategy interface with `DqMetric` models (`MetricNumeric`, `MetricDetail`) and a `CheckFactory` for registration,
So that all current and future checks share a consistent contract and can be discovered via factory lookup.

**Acceptance Criteria:**

**Given** the dqs-spark Maven project exists
**When** the interface and models are implemented
**Then** `DqCheck` interface defines a method that accepts a `DatasetContext` and returns a list of `DqMetric` results
**And** `MetricNumeric` model maps to `dq_metric_numeric` columns (check_type, metric_name, metric_value)
**And** `MetricDetail` model maps to `dq_metric_detail` columns (check_type, detail_type, detail_value)
**And** `CheckFactory` registers check implementations by check_type string and returns enabled checks for a given dataset
**And** `CheckFactory` reads `check_config` from Postgres to determine which checks are enabled per dataset pattern
**And** `DatasetContext` holds dataset_name, lookup_code, partition_date, parent_path, DataFrame reference, and format type

## Story 2.2: HDFS Path Scanner with Consumer Zone Implementation

As a **platform operator**,
I want an extensible HDFS path scanner that discovers dataset subdirectories and extracts dataset name, lookup code, and partition date,
So that DQS automatically finds all datasets under a configured parent path without manual registration.

**Acceptance Criteria:**

**Given** a configured parent HDFS path (e.g., `/prod/abc/data/consumerzone/`)
**When** the `ConsumerZoneScanner` scans the path
**Then** it discovers all dataset subdirectories (e.g., `src_sys_nm=cb10/partition_date=20260330`)
**And** it extracts the dataset name including lookup code from the path structure
**And** it extracts the partition date from the path
**And** `PathScanner` is an interface that `ConsumerZoneScanner` implements, allowing future scanner implementations for different path structures
**And** the scanner returns a list of `DatasetContext` objects ready for check execution

## Story 2.3: Dataset Reading (Avro & Parquet) with Backfill Support

As a **platform operator**,
I want DqsJob to read both Avro and Parquet format files from discovered dataset paths, with a `--date` parameter for backfill runs,
So that quality checks work across all data formats and historical dates can be reprocessed.

**Acceptance Criteria:**

**Given** a discovered dataset path containing Avro files
**When** DqsJob processes the dataset
**Then** the data is read into a Spark DataFrame successfully

**Given** a discovered dataset path containing Parquet files
**When** DqsJob processes the dataset
**Then** the data is read into a Spark DataFrame successfully

**Given** DqsJob is invoked with `--date 20260325`
**When** the scanner discovers datasets
**Then** it scans for partition_date=20260325 instead of the current date

**Given** a dataset with an unrecognized format
**When** DqsJob attempts to read it
**Then** it logs an error for that dataset and continues to the next dataset without crashing

## Story 2.4: Legacy Path Resolution via Dataset Enrichment

As a **platform operator**,
I want the scanner to resolve legacy dataset paths (e.g., `src_sys_nm=omni`) to proper lookup codes via the `dataset_enrichment` table,
So that datasets with non-standard naming are correctly identified for quality tracking.

**Acceptance Criteria:**

**Given** a dataset path with legacy format `src_sys_nm=omni`
**When** the scanner processes this path
**Then** it queries `v_dataset_enrichment_active` for a matching pattern and resolves to the correct lookup code

**Given** a dataset path with no matching enrichment record
**When** the scanner processes this path
**Then** it uses the raw path segment as the lookup code and logs a warning

**Given** a `dataset_enrichment` row maps `omni` to lookup code `UE90`
**When** the scanner resolves the path
**Then** the `DatasetContext` contains lookup_code = `UE90`

## Story 2.5: Freshness Check Implementation

As a **data steward**,
I want the Freshness check (#1) to detect staleness by comparing the latest `source_event_timestamp` against expected recency,
So that I can identify datasets receiving stale or delayed data.

**Acceptance Criteria:**

**Given** a dataset DataFrame with a `source_event_timestamp` column
**When** the Freshness check executes
**Then** it computes the staleness metric (hours since latest timestamp) and writes a `MetricNumeric` with check_type=`freshness`
**And** it determines pass/warn/fail based on staleness compared to historical baseline
**And** the check implements the `DqCheck` interface and is registered in `CheckFactory`

**Given** a dataset with no `source_event_timestamp` column
**When** the Freshness check executes
**Then** it returns a metric indicating the check could not run, with an appropriate detail message

## Story 2.6: Volume Check Implementation

As a **data steward**,
I want the Volume check (#3) to detect anomalous row count changes compared to historical baseline,
So that I can catch unexpected data drops or spikes before downstream consumers are impacted.

**Acceptance Criteria:**

**Given** a dataset DataFrame and historical row counts in `dq_metric_numeric`
**When** the Volume check executes
**Then** it computes the current row count and the percentage change from the historical mean
**And** it writes a `MetricNumeric` with check_type=`volume`, including row_count, pct_change, and stddev metrics
**And** it determines pass/warn/fail based on deviation from historical baseline (e.g., >2 stddev = fail)

**Given** a dataset with no historical baseline (first run)
**When** the Volume check executes
**Then** it records the current row count as the baseline and passes (no comparison possible)

## Story 2.7: Schema Check Implementation

As a **data steward**,
I want the Schema check (#5) to detect schema drift by comparing today's discovered schema against yesterday's stored schema hash,
So that I can identify unexpected structural changes in source data.

**Acceptance Criteria:**

**Given** a dataset DataFrame and a previously stored schema hash in `dq_metric_detail`
**When** the Schema check executes
**Then** it computes the current schema hash and compares against the stored hash
**And** if the schema changed, it writes a `MetricDetail` with check_type=`schema` containing the diff (added/removed/changed fields)
**And** it determines pass (no change) or warn/fail (schema drift detected)

**Given** a dataset with no previous schema hash (first run)
**When** the Schema check executes
**Then** it stores the current schema hash as baseline and passes

**Given** a schema change involving only added fields (no removals)
**When** the Schema check executes
**Then** it reports the change with detail showing the new fields

## Story 2.8: Ops Check Implementation

As a **data steward**,
I want the Ops check (#24) to detect operational anomalies (null rates, empty strings, data type consistency),
So that I can catch data quality issues at the field level across all columns.

**Acceptance Criteria:**

**Given** a dataset DataFrame
**When** the Ops check executes
**Then** it computes per-column null rate percentages and writes `MetricNumeric` entries with check_type=`ops`
**And** it computes per-column empty string rates for string columns
**And** it determines pass/warn/fail based on null rate thresholds compared to historical patterns
**And** columns with null rates exceeding historical baseline by a significant margin are flagged in `MetricDetail`

**Given** a dataset with all-null columns
**When** the Ops check executes
**Then** it flags those columns as critical anomalies in the detail output

## Story 2.9: DQS Score Computation

As a **data steward**,
I want the DQS Score (#25) computed as a weighted composite across all executed checks with configurable weights,
So that I have a single health indicator per dataset that reflects overall quality.

**Acceptance Criteria:**

**Given** a dataset with completed Freshness, Volume, Schema, and Ops checks
**When** the DQS Score check executes
**Then** it reads per-check scores and applies configurable weights (from `check_config` or defaults)
**And** it computes a composite score (0-100) and writes a `MetricNumeric` with check_type=`dqs_score`
**And** the score breakdown (weight * check_score per check) is written to `MetricDetail` for transparency

**Given** a dataset with custom weights in `dataset_enrichment`
**When** the DQS Score check executes
**Then** it uses the custom weights instead of defaults

**Given** a dataset where one check could not run
**When** the DQS Score check executes
**Then** it computes the score from available checks, redistributing the missing check's weight proportionally

## Story 2.10: Batch DB Writer & DqsJob Integration

As a **platform operator**,
I want all check results collected in memory and written to Postgres in a single batch operation per dataset,
So that DB round trips are minimized and partial writes are avoided.

**Acceptance Criteria:**

**Given** DqsJob has executed all enabled checks for a dataset
**When** the `BatchWriter` writes results
**Then** it creates one `dq_run` record and all associated `dq_metric_numeric` and `dq_metric_detail` records in a single JDBC transaction
**And** if the transaction fails, no partial records are left in the database

**Given** DqsJob processes multiple datasets within a parent path
**When** one dataset's batch write fails
**Then** the error is logged and captured, and processing continues to the next dataset

**Given** DqsJob runs end-to-end for a parent path with 3 test datasets
**When** execution completes
**Then** `dq_run` contains 3 records, each with associated metrics in `dq_metric_numeric` and `dq_metric_detail`
**And** each `dq_run` record has a valid `dqs_score`, `check_status`, and `lookup_code`

---
