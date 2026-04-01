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
