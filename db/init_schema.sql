CREATE TABLE IF NOT EXISTS dataset (
    dataset_id SERIAL PRIMARY KEY,
    src_sys_nm VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(src_sys_nm)
);

CREATE TABLE IF NOT EXISTS dq_run (
    run_id SERIAL PRIMARY KEY,
    dataset_id INTEGER REFERENCES dataset(dataset_id),
    run_timestamp TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    duration_ms BIGINT
);

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
