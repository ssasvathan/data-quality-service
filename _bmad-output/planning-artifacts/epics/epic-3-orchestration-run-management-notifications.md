# Epic 3: Orchestration, Run Management & Notifications

Platform ops can trigger orchestration runs, handle failures with isolation, manage reruns with full audit trail, and receive summary email notifications.

## Story 3.1: Python CLI & Parent Path Configuration

As a **platform operator**,
I want a Python CLI that reads parent paths from a YAML config file and accepts runtime parameters (date, rerun flags, dataset filter),
So that I can trigger orchestration runs with flexible configuration.

**Acceptance Criteria:**

**Given** a `parent_paths.yaml` config file with 2 parent paths configured
**When** I run `python -m orchestrator.cli`
**Then** it reads the config and identifies the parent paths to process

**Given** CLI arguments `--date 20260325 --datasets ue90-omni-transactions`
**When** the CLI parses arguments
**Then** it passes the date and dataset filter to the runner

**Given** a missing or malformed config file
**When** the CLI starts
**Then** it exits with a clear error message indicating the config problem

## Story 3.2: Spark-Submit Runner with Failure Isolation

As a **platform operator**,
I want the orchestrator to invoke spark-submit for each parent path with per-parent-path failure isolation,
So that one failed path does not halt processing of other paths.

**Acceptance Criteria:**

**Given** 3 parent paths configured
**When** the orchestrator runs
**Then** it invokes spark-submit once per parent path, passing the path and any runtime parameters

**Given** spark-submit for parent path 2 fails with a non-zero exit code
**When** the orchestrator processes all paths
**Then** paths 1 and 3 are still processed successfully
**And** the failure for path 2 is captured with the exit code and stderr output

**Given** spark-submit for a path returns exit code 0
**When** the orchestrator checks the result
**Then** it records the path as successful

## Story 3.3: Orchestration Run Tracking & Error Capture

As a **platform operator**,
I want every orchestration run tracked in `dq_orchestration_run` with dataset counts, status, and error summaries,
So that I have a complete audit trail of every run for debugging and compliance.

**Acceptance Criteria:**

**Given** an orchestration run starts
**When** the CLI begins processing
**Then** it creates a `dq_orchestration_run` record with start_time and run_status=`running`

**Given** all parent paths have been processed
**When** the orchestrator finalizes the run
**Then** it updates the `dq_orchestration_run` record with end_time, total_datasets, passed_datasets, failed_datasets, and run_status=`completed` or `completed_with_errors`
**And** error_summary contains a text summary of any failures

**Given** individual dq_run records created by Spark
**When** the orchestrator links them
**Then** each `dq_run.orchestration_run_id` references the correct `dq_orchestration_run.id`

## Story 3.4: Rerun Management with Metric Expiration

As a **platform operator**,
I want to rerun specific datasets with incrementing rerun_number and automatic expiration of previous metrics,
So that I can fix failures without losing audit trail of the original run.

**Acceptance Criteria:**

**Given** a dataset that was previously processed with rerun_number=1
**When** a rerun is triggered via `--datasets ue90-omni-transactions --rerun`
**Then** the orchestrator passes a `--datasets` filter to spark-submit so only the specified datasets are processed
**And** the new dq_run record has rerun_number=2

**Given** a rerun is triggered for a dataset
**When** the Spark job creates new results
**Then** the previous dq_run's expiry_date is set to the current timestamp (expired)
**And** all associated dq_metric_numeric and dq_metric_detail records from the previous run are also expired
**And** active-record views only return the new rerun's data

**Given** a rerun with rerun_number=3
**When** I query the database directly (not via views)
**Then** I can see all 3 historical run records with their respective expiry_dates for full audit trail

## Story 3.5: Summary Email Notification

As a **platform operator**,
I want a summary email sent via SMTP after each orchestration run containing run details, pass/fail counts, top failures, and a dashboard link,
So that the SRE team is notified of overnight results without checking the dashboard manually.

**Acceptance Criteria:**

**Given** an orchestration run has completed
**When** the orchestrator composes the summary email
**Then** it queries Postgres for the run summary: run ID, start/end time, total/passed/failed dataset counts
**And** it includes top failures grouped by check type (e.g., "3 volume failures, 2 schema drift")
**And** it includes a dashboard link to the summary view
**And** it includes actionable rerun commands for failed datasets (e.g., `python -m orchestrator.cli --datasets <name> --rerun`)

**Given** the SMTP server is configured
**When** the email is sent
**Then** it is delivered to the configured SRE distribution list

**Given** the SMTP server is unreachable
**When** the email send fails
**Then** the error is logged but does not cause the orchestration run to be marked as failed

---
