# Data Quality Service (DQS) - Architecture Design

## 1. Overview
The Data Quality Service (DQS) proactively detects data issues (such as missing records, null values, or SLA breaches) in a large-scale Hadoop (HDFS) environment. DQS operates at the metadata level, utilizing compute, storage, and presentation layers to serve data stewards and expose data quality intelligence to enterprise applications.

This document outlines the high-level architecture and boundaries of the three independent subsystems that make up DQS.

## 2. Core Components & Responsibilities
The system consists of three tightly integrated but independently deployable components, adhering to **Direct Database Integration**.

### 2.1 Compute: DQS Spark Job (Java/Scala)
*   **Trigger**: A nightly batch job triggered via enterprise orchestrator (e.g., Airflow/Control-M) dynamically post-partition load.
*   **Input**: Reads partitioned data directly from HDFS (e.g., directory format `src_sys_nm=UET0_-_mobile_banking`). Supports Avro and Parquet formats.
*   **Config**: Employs a local configuration file (`rules.yaml` / JSON) deployed alongside the job to determine column rules, freshness SLAs, and thresholds.
*   **Output**: Computes distribution, freshness, volume, and schema checks, writing the results directly to the shared Postgres database via JDBC.
*   **Security**: Authenticates to the cluster via strict Kerberos (SPNEGO/Keytab) in production. 

### 2.2 Storage: DQS Shared Database (PostgreSQL)
*   Acts as the unified state layer and single source of truth.
*   **Tables & Schemas**:
    *   `dataset`: Registers dataset taxonomy and ownership.
    *   `dq_run`: Timestamps and metadata for each nightly Spark execution.
    *   `dq_check_result`: Pass/fail status for specific rules run against the dataset, including exact failure reasons (e.g., "Row count 500 is below threshold 1000").
    *   `dq_metric_snapshot`: Granular numeric records of the run (e.g., complete null counts) utilized for historical trending.

### 2.3 Serve: DQS Serving Layer (Python/FastAPI)
*   A lightweight, read-only REST API handling connectivity to Postgres. It serves two distinct unauthenticated frontends (secured via internal network/VPN boundary):
    *   **Reporting UI**: A vanilla HTML/JS executive dashboard and detailed data steward view exposing pass-fail rates and metric trends.
    *   **MCP Server**: A Python service that translates dataset health metrics into an LLM-callable format.

## 3. Deployment & Local Development
Given the production dependence on Hadoop and Kerberos, local iterability is carefully isolated.

### 3.1 Local Development
*   **Compute**: The DQS Spark application runs in `local[*]` execution mode on developer machines. It reads from local folder structures mimicking HDFS paths (`file://` URIs).
*   **Storage**: Rather than overhead of local container setups, all developers utilize the dedicated **Dev Postgres Server** for local testing. A "local" properties profile natively points the Spark JDBC sink and the FastAPI backend to this DEV Postgres instance, utilizing standard username/password authentication (bypassing Kerberos).
*   **Serve**: FastAPI runs directly via Uvicorn locally. The UI is a static vanilla JS application served via the API.

### 3.2 Automated Testing
*   **Unit Tests**: Asserts correct computational logic for individual rule evaluations (e.g. valid threshold boundaries).
*   **Integration Tests**: Runs temporary in-memory Spark contexts to ingest dummy local partitions and assert accurate JDBC pushes to the database schema.

## 4. Error Handling
*   **System Disruption**: If Spark fails natively due to environment, Kerberos, or corrupt data formats, the orchestrator registers a failed sequence and logs traces to YARN.
*   **Check Failures**: If the Spark job succeeds but a data partition violates a business rule, the job exits cleanly. A `FAILED` status is written to Postgres in the `dq_check_result` table, which is immediately propagated up to the Reporting UI and MCP layer.
