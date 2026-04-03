-- dqs-serve owns all schema DDL
-- Temporal pattern: create_date + expiry_date as TIMESTAMP, sentinel '9999-12-31 23:59:59'
-- Composite unique constraints enforce no duplicate active records (same natural key + expiry_date)

CREATE TABLE dq_run (
    id                   SERIAL PRIMARY KEY,
    dataset_name         TEXT NOT NULL,
    partition_date       DATE NOT NULL,
    lookup_code          TEXT,
    check_status         TEXT NOT NULL,
    dqs_score            NUMERIC(5,2),
    rerun_number         INTEGER NOT NULL DEFAULT 0,
    orchestration_run_id INTEGER,
    error_message        TEXT,
    create_date          TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date          TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dq_run_dataset_name_partition_date_rerun_number_expiry_date
        UNIQUE (dataset_name, partition_date, rerun_number, expiry_date)
);

CREATE INDEX idx_dq_run_dataset_name_partition_date
    ON dq_run (dataset_name, partition_date);

CREATE TABLE dq_metric_numeric (
    id           SERIAL PRIMARY KEY,
    dq_run_id    INTEGER NOT NULL,
    check_type   TEXT NOT NULL,
    metric_name  TEXT NOT NULL,
    metric_value NUMERIC,
    create_date  TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT fk_dq_metric_numeric_dq_run
        FOREIGN KEY (dq_run_id) REFERENCES dq_run(id),
    CONSTRAINT uq_dq_metric_numeric_dq_run_id_check_type_metric_name_expiry_date
        UNIQUE (dq_run_id, check_type, metric_name, expiry_date)
);

CREATE TABLE dq_metric_detail (
    id           SERIAL PRIMARY KEY,
    dq_run_id    INTEGER NOT NULL,
    check_type   TEXT NOT NULL,
    detail_type  TEXT NOT NULL,
    detail_value JSONB,
    create_date  TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT fk_dq_metric_detail_dq_run
        FOREIGN KEY (dq_run_id) REFERENCES dq_run(id),
    CONSTRAINT uq_dq_metric_detail_dq_run_id_check_type_detail_type_expiry_date
        UNIQUE (dq_run_id, check_type, detail_type, expiry_date)
);
