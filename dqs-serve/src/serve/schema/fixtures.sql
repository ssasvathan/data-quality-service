-- dqs-serve test data fixtures
-- Temporal pattern: all active rows carry expiry_date = '9999-12-31 23:59:59' (EXPIRY_SENTINEL)
-- Execution order (mandatory): ddl.sql → views.sql → fixtures.sql
-- AC coverage: AC1 (3+ source systems, Avro+Parquet), AC2 (mixed anomalies),
--              AC3 (legacy omni path + dataset_enrichment), AC4 (JSONB literal types),
--              AC5 (7-day historical baseline), AC6 (check_config + dataset_enrichment rows)

-- ---------------------------------------------------------------------------
-- dq_orchestration_run rows (2 runs: one complete, one with errors)
-- ---------------------------------------------------------------------------

INSERT INTO dq_orchestration_run
    (parent_path, run_status, start_time, end_time, total_datasets, passed_datasets, failed_datasets, error_summary, expiry_date)
VALUES
    ('lob=retail/src_sys_nm=alpha', 'COMPLETE_WITH_ERRORS', '2026-04-02 06:00:00', '2026-04-02 06:45:00', 3, 1, 2, 'SCHEMA_DRIFT and HIGH_NULLS detected', '9999-12-31 23:59:59'),
    ('lob=commercial/src_sys_nm=beta', 'COMPLETE_WITH_ERRORS', '2026-04-02 06:00:00', '2026-04-02 06:30:00', 2, 0, 2, 'STALE_PARTITION and ZERO_ROWS detected', '9999-12-31 23:59:59');

-- ---------------------------------------------------------------------------
-- dq_run rows: 7-day historical baseline for sales_daily (AC5)
-- partition_dates 2026-03-27 through 2026-04-02, all PASS
-- orchestration_run_id references the retail/alpha run
-- ---------------------------------------------------------------------------

INSERT INTO dq_run
    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, orchestration_run_id, expiry_date)
SELECT
    'lob=retail/src_sys_nm=alpha/dataset=sales_daily',
    gs::DATE,
    'LOB_RETAIL',
    'PASS',
    98.50,
    0,
    (SELECT id FROM dq_orchestration_run WHERE parent_path = 'lob=retail/src_sys_nm=alpha' AND expiry_date = '9999-12-31 23:59:59'),
    '9999-12-31 23:59:59'
FROM generate_series('2026-03-27'::DATE, '2026-04-02'::DATE, '1 day'::INTERVAL) gs;

-- dq_run rows: retail/alpha additional datasets (products, customers) — healthy runs (AC1, AC2)
INSERT INTO dq_run
    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, orchestration_run_id, expiry_date)
VALUES
    ('lob=retail/src_sys_nm=alpha/dataset=products',   '2026-04-02', 'LOB_RETAIL', 'FAIL', 45.00, 0,
     (SELECT id FROM dq_orchestration_run WHERE parent_path = 'lob=retail/src_sys_nm=alpha' AND expiry_date = '9999-12-31 23:59:59'),
     '9999-12-31 23:59:59'),
    ('lob=retail/src_sys_nm=alpha/dataset=customers',  '2026-04-02', 'LOB_RETAIL', 'WARN', 72.00, 0,
     (SELECT id FROM dq_orchestration_run WHERE parent_path = 'lob=retail/src_sys_nm=alpha' AND expiry_date = '9999-12-31 23:59:59'),
     '9999-12-31 23:59:59');

-- dq_run rows: commercial/beta (Avro format, anomaly datasets — AC1, AC2)
INSERT INTO dq_run
    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, orchestration_run_id, expiry_date)
VALUES
    ('lob=commercial/src_sys_nm=beta/dataset=transactions', '2026-04-02', 'LOB_COMMERCIAL', 'WARN', 60.00, 0,
     (SELECT id FROM dq_orchestration_run WHERE parent_path = 'lob=commercial/src_sys_nm=beta' AND expiry_date = '9999-12-31 23:59:59'),
     '9999-12-31 23:59:59'),
    ('lob=commercial/src_sys_nm=beta/dataset=payments',     '2026-04-02', 'LOB_COMMERCIAL', 'FAIL',  0.00, 0,
     (SELECT id FROM dq_orchestration_run WHERE parent_path = 'lob=commercial/src_sys_nm=beta' AND expiry_date = '9999-12-31 23:59:59'),
     '9999-12-31 23:59:59');

-- dq_run row: legacy omni path (no lob= prefix — AC3)
INSERT INTO dq_run
    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, expiry_date)
VALUES
    ('src_sys_nm=omni/dataset=customer_profile', '2026-04-02', 'LOB_LEGACY', 'PASS', 95.00, 0, '9999-12-31 23:59:59');

-- ---------------------------------------------------------------------------
-- dq_metric_numeric rows: 7-day VOLUME baseline for sales_daily (AC5)
-- metric_value varies realistically between 95000–110000 for volume anomaly detection
-- ---------------------------------------------------------------------------

INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date)
SELECT
    r.id,
    'VOLUME',
    'row_count',
    CASE r.partition_date
        WHEN '2026-03-27' THEN 102345
        WHEN '2026-03-28' THEN 98721
        WHEN '2026-03-29' THEN 105612
        WHEN '2026-03-30' THEN 99988
        WHEN '2026-03-31' THEN 107234
        WHEN '2026-04-01' THEN 96103
        WHEN '2026-04-02' THEN 103876
        ELSE 100000
    END,
    '9999-12-31 23:59:59'
FROM dq_run r
WHERE r.dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND r.expiry_date = '9999-12-31 23:59:59';

-- dq_metric_numeric: FRESHNESS metric for sales_daily (latest date, AC1 coverage)
INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date)
SELECT id, 'FRESHNESS', 'hours_since_update', 2, '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- dq_metric_numeric: anomaly — stale partition (transactions, WARN — AC2)
INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date)
SELECT id, 'FRESHNESS', 'hours_since_update', 72, '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=commercial/src_sys_nm=beta/dataset=transactions'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- dq_metric_numeric: anomaly — zero rows (payments, FAIL — AC2)
INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date)
SELECT id, 'VOLUME', 'row_count', 0, '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=commercial/src_sys_nm=beta/dataset=payments'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- dq_metric_numeric: anomaly — schema drift / missing columns (products, FAIL — AC2)
INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date)
SELECT id, 'SCHEMA', 'missing_columns', 3, '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=products'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- dq_metric_numeric: anomaly — high null rate (customers, WARN — AC2)
INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date)
SELECT id, 'OPS', 'null_rate', 0.87, '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=customers'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- dq_metric_numeric: legacy omni path volume metric (AC3)
INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date)
SELECT id, 'VOLUME', 'row_count', 54321, '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'src_sys_nm=omni/dataset=customer_profile'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- ---------------------------------------------------------------------------
-- dq_metric_detail rows: eventAttribute JSONB — all 6 literal types (AC4)
-- Using the latest sales_daily run (2026-04-02) as the anchor run.
-- unique constraint: (dq_run_id, check_type, detail_type, expiry_date)
-- ---------------------------------------------------------------------------

-- String literal: a single JSON string value
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
SELECT id, 'SCHEMA', 'eventAttribute_field_name', '"transaction_id"', '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- Number literal: a JSON numeric value
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
SELECT id, 'SCHEMA', 'eventAttribute_field_count', '42', '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- Boolean literal: a JSON boolean value
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
SELECT id, 'SCHEMA', 'eventAttribute_nullable', 'true', '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- Array literal: a JSON array of field names
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
SELECT id, 'SCHEMA', 'eventAttribute_field_list', '["id","name","amount","currency"]', '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- Object literal: a JSON object describing a single field's metadata
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
SELECT id, 'SCHEMA', 'eventAttribute_field_meta', '{"type":"string","nullable":false}', '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- Nested literal: an object containing an array of field descriptors (object + array nesting)
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
SELECT id, 'SCHEMA', 'eventAttribute_schema', '{"fields":[{"name":"id","type":"long"},{"name":"amount","type":"double"},{"name":"currency","type":"string"}],"version":2}', '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- Parquet format marker row (beta/transactions — Avro; alpha/sales_daily — Parquet)
-- This detail row for beta confirms Avro-format dataset coverage (AC1)
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
SELECT id, 'SCHEMA', 'eventAttribute_format', '"avro"', '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=commercial/src_sys_nm=beta/dataset=transactions'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';

-- ---------------------------------------------------------------------------
-- check_config rows (AC6)
-- ---------------------------------------------------------------------------

INSERT INTO check_config (dataset_pattern, check_type, enabled, explosion_level, expiry_date)
VALUES
    -- Wildcard pattern: all retail datasets, FRESHNESS enabled
    ('lob=retail/%',                                            'FRESHNESS', TRUE,  0, '9999-12-31 23:59:59'),
    -- Specific disable: beta/payments VOLUME check disabled
    ('lob=commercial/src_sys_nm=beta/dataset=payments',         'VOLUME',    FALSE, 0, '9999-12-31 23:59:59'),
    -- Non-zero explosion_level: alpha subtree, OPS check enabled with level 2
    ('lob=retail/src_sys_nm=alpha/%',                           'OPS',       TRUE,  2, '9999-12-31 23:59:59'),
    -- Additional: all commercial datasets, SCHEMA enabled
    ('lob=commercial/%',                                        'SCHEMA',    TRUE,  0, '9999-12-31 23:59:59');

-- ---------------------------------------------------------------------------
-- dataset_enrichment rows (AC3, AC6)
-- ---------------------------------------------------------------------------

INSERT INTO dataset_enrichment (dataset_pattern, lookup_code, custom_weights, sla_hours, explosion_level, expiry_date)
VALUES
    -- Legacy path: no lob= prefix, exercises enrichment lookup for omni datasets (AC3)
    ('src_sys_nm=omni/%',
     'LOB_LEGACY',
     NULL,
     24,
     0,
     '9999-12-31 23:59:59'),
    -- Commercial path with custom_weights JSONB — also doubles as JSONB object test (AC6)
    ('lob=commercial/%',
     'LOB_COMMERCIAL',
     '{"FRESHNESS":0.4,"VOLUME":0.3,"SCHEMA":0.2,"OPS":0.1}',
     12,
     0,
     '9999-12-31 23:59:59'),
    -- Retail path enrichment
    ('lob=retail/%',
     'LOB_RETAIL',
     '{"FRESHNESS":0.3,"VOLUME":0.3,"SCHEMA":0.25,"OPS":0.15}',
     8,
     0,
     '9999-12-31 23:59:59');
