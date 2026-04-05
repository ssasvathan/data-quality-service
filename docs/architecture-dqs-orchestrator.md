# Architecture — dqs-orchestrator

_Python 3.12+ CLI — spark-submit orchestration, run tracking, rerun management, and email notifications._

---

## Overview

dqs-orchestrator is a Python CLI that manages the execution of dqs-spark jobs across multiple HDFS parent paths. It provides per-path failure isolation, orchestration run tracking in PostgreSQL, rerun management with metric expiration, and summary email notifications.

**Entry point:** `python -m orchestrator.cli`

---

## Module Structure

```
src/orchestrator/
├── cli.py       # CLI entry: parse_args, load config, orchestrate full flow
├── runner.py    # spark-submit execution + per-path failure isolation
├── db.py        # psycopg2 helpers (create/finalize runs, expire metrics)
├── email.py     # Email composition (pure function) + SMTP delivery
└── models.py    # JobResult, RunSummary dataclasses
```

---

## Orchestration Flow

```
1. Parse CLI args (--config, --date, --datasets, --rerun)
       ↓
2. Load orchestrator.yaml + parent_paths.yaml (strict validation)
       ↓
3. Create orchestration_run records in PostgreSQL (non-fatal on DB error)
       ↓
4. [If --rerun] Expire previous metrics for specified datasets (atomic transaction)
       ↓
5. Run spark-submit per parent path (per-path failure isolation)
       ↓
6. Finalize orchestration_run with pass/fail counts (non-fatal)
       ↓
7. Send summary email via SMTP (non-fatal)
       ↓
8. Exit 0 (all pass) or Exit 1 (any path failed)
```

---

## CLI Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--config` | `config/orchestrator.yaml` | Path to orchestrator config |
| `--date` | Today | Partition date (yyyyMMdd format) |
| `--datasets` | All | Specific dataset names for filtered run |
| `--rerun` | False | Enable rerun mode (expire + re-execute) |

---

## spark-submit Command

```bash
spark-submit \
  --master yarn \
  --deploy-mode cluster \
  --driver-memory 2g \
  --executor-memory 4g \
  dqs-spark.jar \
  --parent-path /data/finance/loans \
  --date 20260402 \
  --orchestration-run-id 12345 \
  --rerun-number 0
```

Timeout: 600 seconds. Non-zero exit or timeout → `JobResult.success=False`.

---

## Failure Isolation

| Layer | Strategy |
|-------|----------|
| **Per-path** | `run_all_paths()` wraps each spark-submit in try/except; failed path logged, others continue |
| **DB errors** | create/finalize orchestration_run failures logged, never affect exit code |
| **SMTP errors** | SMTPException and OSError caught, logged, never re-raised |
| **Config errors** | Missing/malformed YAML exits immediately before spark (fail fast) |

---

## Rerun Management

Triggered by `--rerun --datasets <names>`:

1. For each dataset: `expire_previous_run(conn, dataset_name, partition_date, now)`
   - Marks active `dq_run` as expired (sets expiry_date to current timestamp)
   - Cascades to `dq_metric_numeric` and `dq_metric_detail` in ONE transaction
   - Returns previous rerun_number
2. Compute `next_rerun_number = (previous or 0) + 1`
3. Pass `--rerun-number` to spark-submit

Full audit history preserved — expired records remain in tables, active views filter by sentinel.

---

## Email Notifications

**Subject:** `DQS Run Summary — {parent_path} — {date} — PASSED|FAILED ({N} failures)`

**Body sections:**
1. Run header (run_id, parent_path, date, times)
2. Results (total/passed/failed counts)
3. Top failures by check type (sorted by count descending)
4. Failed dataset rerun commands: `python -m orchestrator.cli --datasets {name} --rerun`
5. Dashboard URL link

---

## Configuration

**orchestrator.yaml:**
```yaml
database:
  url: postgresql://postgres:localdev@localhost:5432/postgres
spark:
  submit_path: spark-submit
  master: yarn
  deploy_mode: cluster
  driver_memory: 2g
  executor_memory: 4g
  app_jar: dqs-spark.jar
email:
  smtp_host: localhost
  smtp_port: 25
  from_address: dqs-alerts@example.com
  to_addresses: [data-engineering@example.com]
  dashboard_url: http://localhost:5173/summary
```

**parent_paths.yaml:**
```yaml
parent_paths:
  - path: /data/finance/loans
    description: Finance loan dataset parent path
  - path: /data/finance/deposits
  - path: /data/risk/credit
```

---

## Test Strategy

- **Mock subprocess.run** for spark-submit (no Spark cluster required)
- **Mock psycopg2.connect** for DB operations
- **Tempfile-based** config files via `tmp_path` fixture
- Test files: `test_cli.py`, `test_runner.py`, `test_db.py`, `test_email.py`
