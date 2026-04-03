# Epic 6: Tier 2 Quality Checks (Phase 2)

Deeper quality coverage with SLA monitoring, zero-row detection, breaking change protection, statistical distribution analysis, and timestamp validation.

## Story 6.1: SLA Countdown Check (#2)

As a **data steward**,
I want the SLA Countdown check to measure time remaining against the expected delivery window,
So that I can monitor whether datasets are arriving within their service level agreements.

**Acceptance Criteria:**

**Given** a dataset with SLA hours configured in `dataset_enrichment`
**When** the SLA Countdown check executes
**Then** it computes hours remaining until SLA breach and writes a `MetricNumeric` with check_type=`sla_countdown`
**And** it determines pass (within SLA), warn (approaching), or fail (breached)

**Given** a dataset with no SLA configuration
**When** the SLA Countdown check is invoked
**Then** it skips gracefully and returns no metrics (check not applicable)

**And** the check implements `DqCheck`, is registered in `CheckFactory`, and requires zero changes to serve/API/dashboard

## Story 6.2: Zero-Row Check (#4)

As a **data steward**,
I want the Zero-Row check to detect datasets with no records,
So that empty deliveries are caught immediately.

**Acceptance Criteria:**

**Given** a dataset DataFrame with zero rows
**When** the Zero-Row check executes
**Then** it writes a `MetricNumeric` with check_type=`zero_row` and status=fail

**Given** a dataset DataFrame with rows
**When** the Zero-Row check executes
**Then** it writes a `MetricNumeric` with status=pass and the row count

**And** the check implements `DqCheck`, is registered in `CheckFactory`, and requires zero changes to serve/API/dashboard

## Story 6.3: Breaking Change Check (#6)

As a **data steward**,
I want the Breaking Change check to detect schema changes that remove or rename fields,
So that destructive schema modifications are flagged separately from additive drift.

**Acceptance Criteria:**

**Given** a dataset whose schema has removed fields compared to the previous run
**When** the Breaking Change check executes
**Then** it writes a `MetricDetail` with check_type=`breaking_change` listing removed/renamed fields and status=fail

**Given** a dataset whose schema only added new fields
**When** the Breaking Change check executes
**Then** it passes (additive changes are not breaking)

**Given** a dataset with no previous schema
**When** the Breaking Change check executes
**Then** it passes (no comparison possible, first run)

**And** the check implements `DqCheck`, is registered in `CheckFactory`, and requires zero changes to serve/API/dashboard

## Story 6.4: Distribution Checks (#7-10) with eventAttribute Explosion

As a **data steward**,
I want Distribution checks to detect statistical distribution shifts across columns, including recursively exploded eventAttribute JSON keys with configurable explosion depth,
So that subtle data quality degradation is caught through statistical analysis.

**Acceptance Criteria:**

**Given** a dataset with numeric columns and historical distribution data
**When** the Distribution checks execute
**Then** they compute distribution metrics (mean, median, stddev, percentiles) per column and compare against historical baselines
**And** columns with significant distribution shifts are flagged with `MetricNumeric` and `MetricDetail` entries

**Given** a dataset with eventAttribute JSON columns
**When** explosion_level is set to ALL in `check_config`
**Then** JSON keys are recursively exploded into virtual columns for distribution analysis
**And** key names (e.g., `paymentDetails.items[0].amount`) appear in metrics but actual values do not

**Given** explosion_level is set to TOP-LEVEL
**When** eventAttribute is processed
**Then** only first-level keys are exploded, nested structures are not traversed

**Given** explosion_level is set to OFF
**When** the dataset is processed
**Then** eventAttribute columns are skipped entirely for distribution analysis

**And** the 4-position dial (ALL / CRITICAL / TOP-LEVEL / OFF) is configurable per dataset via `check_config`
**And** the checks implement `DqCheck`, are registered in `CheckFactory`, and require zero changes to serve/API/dashboard

## Story 6.5: Timestamp Sanity Check (#18)

As a **data steward**,
I want the Timestamp Sanity check to detect out-of-range or future-dated timestamps,
So that time-based anomalies in source data are flagged.

**Acceptance Criteria:**

**Given** a dataset with timestamp columns
**When** the Timestamp Sanity check executes
**Then** it identifies timestamps that are in the future (beyond current date + tolerance)
**And** it identifies timestamps that are unreasonably old (beyond configurable threshold)
**And** it writes `MetricNumeric` with percentages of anomalous timestamps per column

**Given** a column with >5% future-dated timestamps
**When** the check evaluates
**Then** it flags the column as fail with details in `MetricDetail`

**And** the check implements `DqCheck`, is registered in `CheckFactory`, and requires zero changes to serve/API/dashboard

---
