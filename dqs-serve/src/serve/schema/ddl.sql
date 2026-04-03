-- dqs-serve owns all schema DDL
-- Temporal pattern: create_date + expiry_date as TIMESTAMP, sentinel '9999-12-31 23:59:59'
-- Composite unique constraints enforce no duplicate active records (same natural key + expiry_date)

CREATE TABLE dq_orchestration_run (
    id               SERIAL PRIMARY KEY,
    parent_path      TEXT NOT NULL,
    run_status       TEXT NOT NULL,
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    total_datasets   INTEGER,
    passed_datasets  INTEGER,
    failed_datasets  INTEGER,
    error_summary    TEXT,
    create_date      TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date      TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dq_orchestration_run_parent_path_expiry_date
        UNIQUE (parent_path, expiry_date)
);

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

CREATE INDEX IF NOT EXISTS idx_dq_run_dataset_name_partition_date
    ON dq_run (dataset_name, partition_date);

ALTER TABLE dq_run
    ADD CONSTRAINT fk_dq_run_orchestration_run
    FOREIGN KEY (orchestration_run_id) REFERENCES dq_orchestration_run(id);

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

CREATE TABLE check_config (
    id              SERIAL PRIMARY KEY,
    dataset_pattern TEXT NOT NULL,
    check_type      TEXT NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    explosion_level INTEGER NOT NULL DEFAULT 0,
    create_date     TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_check_config_dataset_pattern_check_type_expiry_date
        UNIQUE (dataset_pattern, check_type, expiry_date)
);

CREATE TABLE dataset_enrichment (
    id              SERIAL PRIMARY KEY,
    dataset_pattern TEXT NOT NULL,
    lookup_code     TEXT,
    custom_weights  JSONB,
    sla_hours       NUMERIC,
    explosion_level INTEGER NOT NULL DEFAULT 0,
    create_date     TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dataset_enrichment_dataset_pattern_expiry_date
        UNIQUE (dataset_pattern, expiry_date)
);

CREATE INDEX IF NOT EXISTS idx_dq_metric_numeric_dq_run_id
    ON dq_metric_numeric (dq_run_id);

CREATE INDEX IF NOT EXISTS idx_dq_metric_detail_dq_run_id
    ON dq_metric_detail (dq_run_id);

CREATE INDEX IF NOT EXISTS idx_check_config_dataset_pattern
    ON check_config (dataset_pattern);

CREATE INDEX IF NOT EXISTS idx_dataset_enrichment_dataset_pattern
    ON dataset_enrichment (dataset_pattern);

CREATE INDEX IF NOT EXISTS idx_dq_orchestration_run_parent_path
    ON dq_orchestration_run (parent_path);
