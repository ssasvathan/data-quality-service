# Postgres Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize the shared Postgres database schema comprising `dataset`, `dq_run`, `dq_check_result`, and `dq_metric_snapshot` tables.

**Architecture:** Pure SQL DDL scripts to create the normalized schema tables. The tables will act as the single source of truth for DQS results. Developer environment variables (`PGHOST`, `PGUSER`, `PGDATABASE`, `PGPASSWORD`) will route execution to the Dev Postgres server.

**Tech Stack:** PostgreSQL (SQL DDL), bash (for test verification)

---

### Task 1: Create `dataset` Table

**Files:**
- Create: `db/test_schema.sh`
- Create: `db/init_schema.sql`

- [ ] **Step 1: Write the failing test**

```bash
# db/test_schema.sh
#!/bin/bash
# Uses standard PGHOST, PGPORT, PGUSER, PGDATABASE, PGPASSWORD env vars
psql -c "SELECT dataset_id, src_sys_nm FROM dataset LIMIT 1;"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `chmod +x db/test_schema.sh && ./db/test_schema.sh`
Expected: FAIL with `relation "dataset" does not exist`

- [ ] **Step 3: Write minimal implementation**

```sql
-- db/init_schema.sql
CREATE TABLE IF NOT EXISTS dataset (
    dataset_id SERIAL PRIMARY KEY,
    src_sys_nm VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(src_sys_nm)
);
```

- [ ] **Step 4: Execute DDL and verify test passes**

Run: `psql -f db/init_schema.sql && ./db/test_schema.sh`
Expected: PASS (Returns 0 rows successfully)

- [ ] **Step 5: Commit**

```bash
git add db/test_schema.sh db/init_schema.sql
git commit -m "feat: add dataset schema table"
```

### Task 2: Create `dq_run` Table

**Files:**
- Modify: `db/test_schema.sh`
- Modify: `db/init_schema.sql`

- [ ] **Step 1: Write the failing test**

```bash
# append to db/test_schema.sh:
psql -c "SELECT run_id, dataset_id, status FROM dq_run LIMIT 1;"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./db/test_schema.sh`
Expected: FAIL on `dq_run` with `relation "dq_run" does not exist`

- [ ] **Step 3: Write minimal implementation**

```sql
-- append to db/init_schema.sql:
CREATE TABLE IF NOT EXISTS dq_run (
    run_id SERIAL PRIMARY KEY,
    dataset_id INTEGER REFERENCES dataset(dataset_id),
    run_timestamp TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    duration_ms BIGINT
);
```

- [ ] **Step 4: Execute DDL and verify test passes**

Run: `psql -f db/init_schema.sql && ./db/test_schema.sh`
Expected: PASS (Runs successfully with 0 rows)

- [ ] **Step 5: Commit**

```bash
git add db/test_schema.sh db/init_schema.sql
git commit -m "feat: add dq_run schema table"
```

### Task 3: Create `dq_check_result` and `dq_metric_snapshot` Tables

**Files:**
- Modify: `db/test_schema.sh`
- Modify: `db/init_schema.sql`

- [ ] **Step 1: Write the failing test**

```bash
# append to db/test_schema.sh:
psql -c "SELECT check_id, status FROM dq_check_result LIMIT 1;"
psql -c "SELECT metric_id, metric_value FROM dq_metric_snapshot LIMIT 1;"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./db/test_schema.sh`
Expected: FAIL with relation does not exist for the new tables

- [ ] **Step 3: Write minimal implementation**

```sql
-- append to db/init_schema.sql:
CREATE TABLE IF NOT EXISTS dq_check_result (
    check_id SERIAL PRIMARY KEY,
    run_id INTEGER REFERENCES dq_run(run_id),
    dataset_id INTEGER REFERENCES dataset(dataset_id),
    check_type VARCHAR(100) NOT NULL,
    column_name VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    failure_reason TEXT
);

CREATE TABLE IF NOT EXISTS dq_metric_snapshot (
    metric_id SERIAL PRIMARY KEY,
    run_id INTEGER REFERENCES dq_run(run_id),
    dataset_id INTEGER REFERENCES dataset(dataset_id),
    metric_name VARCHAR(100) NOT NULL,
    column_name VARCHAR(255),
    metric_value NUMERIC NOT NULL
);
```

- [ ] **Step 4: Execute DDL and verify test passes**

Run: `psql -f db/init_schema.sql && ./db/test_schema.sh`
Expected: PASS successfully for all four tables.

- [ ] **Step 5: Commit**

```bash
git add db/test_schema.sh db/init_schema.sql
git commit -m "feat: add check_result and metric_snapshot tables"
```
